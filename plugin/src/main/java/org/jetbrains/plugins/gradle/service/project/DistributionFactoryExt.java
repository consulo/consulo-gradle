/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.gradle.service.project;

import consulo.platform.Platform;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.time.Clock;
import org.gradle.internal.time.Time;
import org.gradle.tooling.BuildCancelledException;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.internal.consumer.ConnectionParameters;
import org.gradle.tooling.internal.consumer.Distribution;
import org.gradle.tooling.internal.consumer.DistributionFactory;
import org.gradle.tooling.internal.consumer.DistributionInstaller;
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener;
import org.gradle.util.GradleVersion;
import org.gradle.util.internal.DistributionLocator;
import org.gradle.wrapper.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;

/**
 * @author Vladislav.Soroka
 * @since 2013-08-23
 */
public class DistributionFactoryExt extends DistributionFactory {
    public DistributionFactoryExt() {
        super(Time.clock());
    }

    /**
     * Returns the default distribution to use for the specified project.
     */
    public Distribution getWrappedDistribution(File propertiesFile) {
        //noinspection UseOfSystemOutOrSystemErr
        WrapperExecutor wrapper = WrapperExecutor.forWrapperPropertiesFile(propertiesFile);
        if (wrapper.getDistribution() != null) {
            return new ZippedDistribution(wrapper.getConfiguration(), Time.clock());
        }

        return getDownloadedDistribution(GradleVersion.current().getVersion());
    }

    private Distribution getDownloadedDistribution(String gradleVersion) {
        URI distUri = new DistributionLocator().getDistributionFor(GradleVersion.version(gradleVersion));
        return getDistribution(distUri);
    }

    private static class ProgressReportingDownload implements IDownload {
        private final ProgressLoggerFactory progressLoggerFactory;

        private ProgressReportingDownload(ProgressLoggerFactory progressLoggerFactory) {
            this.progressLoggerFactory = progressLoggerFactory;
        }

        @Override
        public void download(URI address, File destination) throws Exception {
            ProgressLogger progressLogger = progressLoggerFactory.newOperation(DistributionFactory.class);
            progressLogger.setDescription(String.format("Download %s", address));
            progressLogger.started();
            try {
                new Download(
                    new Logger(false),
                    "Gradle Tooling API",
                    GradleVersion.current().getVersion()
                ).download(address, destination);
            }
            finally {
                progressLogger.completed();
            }
        }
    }

    private static class InstalledDistribution implements Distribution {
        private final File gradleHomeDir;
        private final String displayName;
        private final String locationDisplayName;

        public InstalledDistribution(File gradleHomeDir, String displayName, String locationDisplayName) {
            this.gradleHomeDir = gradleHomeDir;
            this.displayName = displayName;
            this.locationDisplayName = locationDisplayName;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public ClassPath getToolingImplementationClasspath(
            ProgressLoggerFactory progressLoggerFactory,
            InternalBuildProgressListener internalBuildProgressListener,
            ConnectionParameters connectionParameters,
            BuildCancellationToken buildCancellationToken
        ) {
            ProgressLogger progressLogger = progressLoggerFactory.newOperation(DistributionFactory.class);
            progressLogger.setDescription("Validate distribution");
            progressLogger.started();
            try {
                return getToolingImpl();
            }
            finally {
                progressLogger.completed();
            }
        }

        private ClassPath getToolingImpl() {
            if (!gradleHomeDir.exists()) {
                throw new IllegalArgumentException(String.format("The specified %s does not exist.", locationDisplayName));
            }
            if (!gradleHomeDir.isDirectory()) {
                throw new IllegalArgumentException(String.format("The specified %s is not a directory.", locationDisplayName));
            }
            File libDir = new File(gradleHomeDir, "lib");
            if (!libDir.isDirectory()) {
                throw new IllegalArgumentException(String.format(
                    "The specified %s does not appear to contain a Gradle distribution.",
                    locationDisplayName
                ));
            }
            Set<File> files = new LinkedHashSet<>();
            //noinspection ConstantConditions
            for (File file : libDir.listFiles()) {
                if (file.getName().endsWith(".jar")) {
                    files.add(file);
                }
            }
            return DefaultClassPath.of(files);
        }
    }

    private static class ZippedDistribution implements Distribution {
        private InstalledDistribution installedDistribution;
        private final WrapperConfiguration wrapperConfiguration;
        private final Clock clock;

        private ZippedDistribution(WrapperConfiguration wrapperConfiguration, Clock clock) {
            this.wrapperConfiguration = wrapperConfiguration;
            this.clock = clock;
        }

        @Override
        public String getDisplayName() {
            return String.format("Gradle distribution '%s'", wrapperConfiguration.getDistribution());
        }

        @Override
        public ClassPath getToolingImplementationClasspath(
            ProgressLoggerFactory progressLoggerFactory,
            InternalBuildProgressListener progressListener,
            ConnectionParameters connectionParameters,
            BuildCancellationToken cancellationToken
        ) {
            if (installedDistribution == null) {
                final DistributionInstaller installer = new DistributionInstaller(progressLoggerFactory, progressListener, clock);
                File installDir;
                try {
                    cancellationToken.addCallback(installer::cancel);
                    installDir = installer.install(
                        determineRealUserHomeDir(connectionParameters),
                        determineRootDir(connectionParameters),
                        wrapperConfiguration,
                        determineSystemProperties(connectionParameters)
                    );
                }
                catch (CancellationException e) {
                    throw new BuildCancelledException(
                        String.format(
                            "Distribution download cancelled. Using distribution from '%s'.",
                            wrapperConfiguration.getDistribution()
                        ),
                        e
                    );
                }
                catch (FileNotFoundException e) {
                    throw new IllegalArgumentException(String.format("The specified %s does not exist.", getDisplayName()), e);
                }
                catch (Exception e) {
                    throw new GradleConnectionException(
                        String.format("Could not install Gradle distribution from '%s'.", wrapperConfiguration.getDistribution()),
                        e
                    );
                }
                installedDistribution = new InstalledDistribution(installDir, getDisplayName(), getDisplayName());
            }
            return installedDistribution.getToolingImplementationClasspath(
                progressLoggerFactory,
                progressListener,
                connectionParameters,
                cancellationToken
            );
        }

        private static Map<String, String> determineSystemProperties(ConnectionParameters connectionParameters) {
            Map<String, String> systemProperties = new HashMap<>();
            systemProperties.putAll(Platform.current().jvm().getRuntimeProperties());
            systemProperties.putAll(SystemPropertiesHandler.getSystemProperties(
                new File(determineRootDir(connectionParameters), "gradle.properties")
            ));
            systemProperties.putAll(SystemPropertiesHandler.getSystemProperties(
                new File(determineRealUserHomeDir(connectionParameters), "gradle.properties")
            ));
            return systemProperties;
        }

        private static File determineRootDir(ConnectionParameters connectionParameters) {
            return (new BuildLayoutFactory()).getLayoutFor(
                connectionParameters.getProjectDir(),
                connectionParameters.isSearchUpwards() != null ? connectionParameters.isSearchUpwards() : true
            ).getRootDirectory();
        }

        private static File determineRealUserHomeDir(ConnectionParameters connectionParameters) {
            File distributionBaseDir = connectionParameters.getDistributionBaseDir();
            if (distributionBaseDir != null) {
                return distributionBaseDir;
            }
            else {
                File userHomeDir = connectionParameters.getGradleUserHomeDir();
                return userHomeDir != null ? userHomeDir : GradleUserHomeLookup.gradleUserHome();
            }
        }
    }
}

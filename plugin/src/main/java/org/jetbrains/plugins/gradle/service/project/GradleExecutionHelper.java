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

import com.google.gson.GsonBuilder;
import consulo.container.plugin.PluginManager;
import consulo.execution.util.CommandLineTokenizer;
import consulo.externalSystem.model.task.ExternalSystemTaskId;
import consulo.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import consulo.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import consulo.externalSystem.rt.model.DefaultExternalProject;
import consulo.externalSystem.rt.model.ExternalSystemException;
import consulo.gradle.GradleConstants;
import consulo.gradle.setting.DistributionType;
import consulo.gradle.setting.GradleExecutionSettings;
import consulo.ide.impl.idea.util.PathUtil;
import consulo.logging.Logger;
import consulo.platform.Platform;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.io.FileUtil;
import consulo.util.io.StreamUtil;
import consulo.util.lang.ExceptionUtil;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.gradle.tooling.*;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.gradle.tooling.internal.consumer.Distribution;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.jetbrains.plugins.gradle.GradleManager;
import org.jetbrains.plugins.gradle.util.GradleEnvironment;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * @author Denis Zhdanov
 * @since 2013-03-14
 */
public class GradleExecutionHelper {
    private static final Logger LOG = Logger.getInstance(GradleExecutionHelper.class);

    @SuppressWarnings("MethodMayBeStatic")
    @Nonnull
    public <T> ModelBuilder<T> getModelBuilder(
        @Nonnull Class<T> modelType,
        @Nonnull final ExternalSystemTaskId id,
        @Nullable GradleExecutionSettings settings,
        @Nonnull ProjectConnection connection,
        @Nonnull ExternalSystemTaskNotificationListener listener,
        @Nonnull List<String> extraJvmArgs
    ) {
        ModelBuilder<T> result = connection.model(modelType);
        prepare(result, id, settings, listener, extraJvmArgs, new ArrayList<>(), connection);
        return result;
    }

    @SuppressWarnings("MethodMayBeStatic")
    @Nonnull
    public BuildLauncher getBuildLauncher(
        @Nonnull final ExternalSystemTaskId id,
        @Nonnull ProjectConnection connection,
        @Nullable GradleExecutionSettings settings,
        @Nonnull ExternalSystemTaskNotificationListener listener,
        @Nonnull final List<String> vmOptions,
        @Nonnull final List<String> commandLineArgs
    ) {
        BuildLauncher result = connection.newBuild();
        prepare(result, id, settings, listener, vmOptions, commandLineArgs, connection);
        return result;
    }

    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    public static void prepare(
        @Nonnull LongRunningOperation operation,
        @Nonnull final ExternalSystemTaskId id,
        @Nullable GradleExecutionSettings settings,
        @Nonnull final ExternalSystemTaskNotificationListener listener,
        @Nonnull List<String> extraJvmArgs,
        @Nonnull List<String> commandLineArgs,
        @Nonnull ProjectConnection connection
    ) {
        prepare(
            operation,
            id,
            settings,
            listener,
            extraJvmArgs,
            commandLineArgs,
            connection,
            new OutputWrapper(listener, id, true),
            new OutputWrapper(listener, id, false)
        );
    }


    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    public static void prepare(
        @Nonnull LongRunningOperation operation,
        @Nonnull final ExternalSystemTaskId id,
        @Nullable GradleExecutionSettings settings,
        @Nonnull final ExternalSystemTaskNotificationListener listener,
        @Nonnull List<String> extraJvmArgs,
        @Nonnull List<String> commandLineArgs,
        @Nonnull ProjectConnection connection,
        @Nonnull final OutputStream standardOutput,
        @Nonnull final OutputStream standardError
    ) {
        if (settings == null) {
            return;
        }

        Set<String> jvmArgs = new HashSet<>();

        String vmOptions = settings.getDaemonVmOptions();
        if (!StringUtil.isEmpty(vmOptions)) {
            CommandLineTokenizer tokenizer = new CommandLineTokenizer(vmOptions);
            while (tokenizer.hasMoreTokens()) {
                String vmOption = tokenizer.nextToken();
                if (!StringUtil.isEmpty(vmOption)) {
                    jvmArgs.add(vmOption);
                }
            }
        }

        jvmArgs.addAll(extraJvmArgs);

        if (!jvmArgs.isEmpty()) {
            BuildEnvironment buildEnvironment = getBuildEnvironment(connection);
            Collection<String> merged =
                buildEnvironment != null ? mergeJvmArgs(buildEnvironment.getJava().getJvmArguments(), jvmArgs) : jvmArgs;

            // filter nulls and empty strings
            List<String> filteredArgs = ContainerUtil.mapNotNull(merged, s -> StringUtil.isEmpty(s) ? null : s);

            filteredArgs = new ArrayList<>(filteredArgs);
            operation.setJvmArguments(ArrayUtil.toStringArray(filteredArgs));
        }

        if (settings.isOfflineWork()) {
            commandLineArgs.add(GradleConstants.OFFLINE_MODE_CMD_OPTION);
        }

        if (!commandLineArgs.isEmpty()) {
            LOG.info("Passing command-line args to Gradle Tooling API: " + commandLineArgs);
            // filter nulls and empty strings
            List<String> filteredArgs = ContainerUtil.mapNotNull(commandLineArgs, s -> StringUtil.isEmpty(s) ? null : s);

            // TODO remove this replacement when --tests option will become available for tooling API
            replaceTestCommandOptionWithInitScript(filteredArgs);
            operation.withArguments(ArrayUtil.toStringArray(filteredArgs));
        }

        listener.onStart(id);
        final String javaHome = settings.getJavaHome();
        if (javaHome != null && new File(javaHome).isDirectory()) {
            operation.setJavaHome(new File(javaHome));
        }
        operation.addProgressListener(
            (ProgressListener) event -> listener.onStatusChange(new ExternalSystemTaskNotificationEvent(id, event.getDescription()))
        );
        operation.setStandardOutput(standardOutput);
        operation.setStandardError(standardError);
    }

    public <T> T execute(
        @Nonnull String projectPath,
        @Nullable GradleExecutionSettings settings,
        @Nonnull Function<ProjectConnection, T> f
    ) {

        final String projectDir;
        final File projectPathFile = new File(projectPath);
        if (projectPathFile.isFile() && projectPath.endsWith(GradleConstants.EXTENSION) && projectPathFile.getParent() != null) {
            projectDir = projectPathFile.getParent();
        }
        else {
            projectDir = projectPath;
        }

        String userDir = null;
        if (!GradleEnvironment.ADJUST_USER_DIR) {
            try {
                userDir = System.getProperty("user.dir");
                if (userDir != null) {
                    System.setProperty("user.dir", projectDir);
                }
            }
            catch (Exception ignore) {
            }
        }
        ProjectConnection connection = getConnection(projectDir, settings);
        try {
            return f.apply(connection);
        }
        catch (ExternalSystemException e) {
            throw e;
        }
        catch (Throwable e) {
            LOG.debug("Gradle execution error", e);
            Throwable rootCause = ExceptionUtil.getRootCause(e);
            throw new ExternalSystemException(ExceptionUtil.getMessage(rootCause));
        }
        finally {
            try {
                connection.close();
                if (userDir != null) {
                    // restore original user.dir property
                    System.setProperty("user.dir", userDir);
                }
            }
            catch (Throwable e) {
                // ignore
            }
        }
    }

    public void ensureInstalledWrapper(
        @Nonnull ExternalSystemTaskId id,
        @Nonnull String projectPath,
        @Nonnull GradleExecutionSettings settings,
        @Nonnull ExternalSystemTaskNotificationListener listener
    ) {
        if (!settings.getDistributionType().isWrapped()) {
            return;
        }

        if (settings.getDistributionType() == DistributionType.DEFAULT_WRAPPED
            && GradleUtil.findDefaultWrapperPropertiesFile(projectPath) != null) {
            return;
        }

        ProjectConnection connection = getConnection(projectPath, settings);
        try {
            try {
                final File tempFile = FileUtil.createTempFile("wrap", ".gradle");
                tempFile.deleteOnExit();
                final File wrapperPropertyFileLocation = FileUtil.createTempFile("wrap", "loc");
                wrapperPropertyFileLocation.deleteOnExit();
                final String[] lines = {
                    "gradle.taskGraph.afterTask { Task task ->",
                    "    if (task instanceof Wrapper) {",
                    "        def wrapperPropertyFileLocation = task.jarFile.getCanonicalPath() - '.jar' + '.properties'",
                    "        new File('" +
                        StringUtil.escapeBackSlashes(wrapperPropertyFileLocation.getCanonicalPath()) +
                        "').write wrapperPropertyFileLocation",
                    "}}",
                };
                FileUtil.writeToFile(tempFile, StringUtil.join(lines, Platform.current().os().lineSeparator().getSeparatorString()));

                BuildLauncher launcher = getBuildLauncher(
                    id,
                    connection,
                    settings,
                    listener,
                    new ArrayList<>(),
                    ContainerUtil.newArrayList(
                        GradleConstants.INIT_SCRIPT_CMD_OPTION,
                        tempFile.getAbsolutePath()
                    )
                );
                launcher.forTasks("wrapper");
                launcher.run();
                String wrapperPropertyFile = consulo.ide.impl.idea.openapi.util.io.FileUtil.loadFile(wrapperPropertyFileLocation);
                settings.setWrapperPropertyFile(wrapperPropertyFile);
            }
            catch (IOException e) {
                LOG.warn("Can't update wrapper", e);
            }
        }
        catch (Throwable e) {
            LOG.warn("Can't update wrapper", e);
        }
        finally {
            try {
                connection.close();
            }
            catch (Throwable e) {
                // ignore
            }
        }
    }

    private static List<String> mergeJvmArgs(Iterable<String> jvmArgs1, Iterable<String> jvmArgs2) {
        return ContainerUtil.newArrayList(ContainerUtil.concat(jvmArgs1, jvmArgs2));
    }

    /**
     * Allows to retrieve gradle api connection to use for the given project.
     *
     * @param projectPath target project path
     * @param settings    execution settings to use
     * @return connection to use
     * @throws IllegalStateException if it's not possible to create the connection
     */
    @Nonnull
    private static ProjectConnection getConnection(@Nonnull String projectPath, @Nullable GradleExecutionSettings settings) throws
        IllegalStateException {
        File projectDir = new File(projectPath);
        GradleConnector connector = GradleConnector.newConnector();
        int ttl = -1;

        if (settings != null) {
            //noinspection EnumSwitchStatementWhichMissesCases
            switch (settings.getDistributionType()) {
                case LOCAL:
                    String gradleHome = settings.getGradleHome();
                    if (gradleHome != null) {
                        try {
                            // There were problems with symbolic links processing at the gradle side.
                            connector.useInstallation(new File(gradleHome).getCanonicalFile());
                        }
                        catch (IOException e) {
                            connector.useInstallation(new File(settings.getGradleHome()));
                        }
                    }
                    break;
            }

            // Setup service directory if necessary.
            String serviceDirectory = settings.getServiceDirectory();
            if (serviceDirectory != null) {
                connector.useGradleUserHomeDir(new File(serviceDirectory));
            }

            // Setup logging if necessary.
            if (settings.isVerboseProcessing() && connector instanceof DefaultGradleConnector defaultGradleConnector) {
                defaultGradleConnector.setVerboseLogging(true);
            }
            ttl = (int) settings.getRemoteProcessIdleTtlInMs();
        }

        if (ttl > 0 && connector instanceof DefaultGradleConnector defaultGradleConnector) {
            defaultGradleConnector.daemonMaxIdleTime(ttl, TimeUnit.MILLISECONDS);
        }
        connector.forProjectDirectory(projectDir);
        ProjectConnection connection = connector.connect();
        if (connection == null) {
            throw new IllegalStateException(String.format(
                "Can't create connection to the target project via gradle tooling api. Project path: '%s'",
                projectPath
            ));
        }
        return connection;
    }

    /**
     * Utility to set field in object if there is no public setter for it.
     * It's not recommended to use this method.
     * FIXME: remove this workaround after gradle API changed
     *
     * @param obj        Object to be modified
     * @param fieldName  name of object's field
     * @param fieldValue value to be set for field
     * @throws SecurityException
     * @throws NoSuchFieldException
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     */
    public static void setField(Object obj, String fieldName, Object fieldValue) throws SecurityException, NoSuchFieldException,
        IllegalArgumentException, IllegalAccessException {
        final Field field = obj.getClass().getDeclaredField(fieldName);
        final boolean isAccessible = field.isAccessible();
        field.setAccessible(true);
        field.set(obj, fieldValue);
        field.setAccessible(isAccessible);
    }

    @Nullable
    public static File generateInitScript(boolean isBuildSrcProject, @Nonnull Set<File> toolingExtensionFiles) {
        InputStream stream = GradleExecutionHelper.class.getResourceAsStream("/org/jetbrains/plugins/gradle/service/project/init.gradle_");
        try {
            if (stream == null) {
                LOG.warn("Can't get init script template");
                return null;
            }

            ToolingExtensionPaths toolingExtensionPaths = new ToolingExtensionPaths();
            toolingExtensionPaths.addJarByClass(GsonBuilder.class);
            toolingExtensionPaths.addJarByClass(DefaultExternalProject.class);

            for (File file : toolingExtensionFiles) {
                toolingExtensionPaths.addJar(file);
            }

            File gradlePluginPath = PluginManager.getPluginPath(GradleManager.class);
            toolingExtensionPaths.addJar(new File(gradlePluginPath, "gradle-rt/gradle-ext-api.jar"));
            toolingExtensionPaths.addJar(new File(gradlePluginPath, "gradle-rt/gradle-ext-impl.jar"));

            String s = FileUtil.loadTextAndClose(stream)
                .replaceFirst(Pattern.quote("${EXTENSIONS_JARS_PATH}"), toolingExtensionPaths.toArrayExpression());
            if (isBuildSrcProject) {
                String buildSrcDefaultInitScript = getBuildSrcDefaultInitScript();
                if (buildSrcDefaultInitScript == null) {
                    return null;
                }
                s += buildSrcDefaultInitScript;
            }

            final File tempFile = FileUtil.createTempFile("ijinit", '.' + GradleConstants.EXTENSION, true);
            FileUtil.writeToFile(tempFile, s);
            return tempFile;
        }
        catch (Exception e) {
            LOG.warn("Can't generate IJ gradle init script", e);
            return null;
        }
        finally {
            StreamUtil.closeStream(stream);
        }
    }

    @Nullable
    public static String getBuildSrcDefaultInitScript() {
        InputStream stream =
            GradleExecutionHelper.class.getResourceAsStream("/org/jetbrains/plugins/gradle/service/project/buildSrcInit.gradle_");
        try {
            if (stream == null) {
                return null;
            }
            return FileUtil.loadTextAndClose(stream);
        }
        catch (Exception e) {
            LOG.warn("Can't use IJ gradle init script", e);
            return null;
        }
        finally {
            StreamUtil.closeStream(stream);
        }
    }

    @Nullable
    private static BuildEnvironment getBuildEnvironment(@Nonnull ProjectConnection connection) {
        try {
            return connection.getModel(BuildEnvironment.class);
        }
        catch (Exception e) {
            LOG.warn("can not get BuildEnvironment model", e);
            return null;
        }
    }

    private static void replaceTestCommandOptionWithInitScript(@Nonnull List<String> args) {
        Set<String> testIncludePatterns = new LinkedHashSet<>();
        Iterator<String> it = args.iterator();
        while (it.hasNext()) {
            final String next = it.next();
            if ("--tests".equals(next)) {
                it.remove();
                if (it.hasNext()) {
                    testIncludePatterns.add(it.next());
                    it.remove();
                }
            }
        }
        if (!testIncludePatterns.isEmpty()) {
            StringBuilder buf = new StringBuilder();
            buf.append('[');
            for (Iterator<String> iterator = testIncludePatterns.iterator(); iterator.hasNext(); ) {
                String pattern = iterator.next();
                buf.append('\"').append(pattern).append('\"');
                if (iterator.hasNext()) {
                    buf.append(',');
                }
            }
            buf.append(']');

            InputStream stream =
                GradleExecutionHelper.class.getResourceAsStream("/org/jetbrains/plugins/gradle/service/project/testFilterInit.gradle_");
            try {
                if (stream == null) {
                    LOG.warn("Can't get test filter init script template");
                    return;
                }
                String s = FileUtil.loadTextAndClose(stream).replaceFirst(Pattern.quote("${TEST_NAME_INCLUDES}"), buf.toString());
                final File tempFile = FileUtil.createTempFile("ijinit", '.' + GradleConstants.EXTENSION, true);
                FileUtil.writeToFile(tempFile, s);
                ContainerUtil.addAll(args, GradleConstants.INIT_SCRIPT_CMD_OPTION, tempFile.getAbsolutePath());
            }
            catch (Exception e) {
                LOG.warn("Can't generate IJ gradle test filter init script", e);
            }
            finally {
                StreamUtil.closeStream(stream);
            }
        }
    }
}

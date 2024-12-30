package org.jetbrains.plugins.gradle.service;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.externalSystem.util.ExternalSystemApiUtil;
import consulo.externalSystem.util.ExternalSystemConstants;
import consulo.gradle.setting.DistributionType;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.module.content.layer.OrderEnumerator;
import consulo.platform.Platform;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.Ref;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.archive.ArchiveVfsUtil;
import jakarta.inject.Singleton;
import org.gradle.StartParameter;
import org.gradle.api.tasks.wrapper.Wrapper;
import org.gradle.util.GradleVersion;
import org.gradle.util.internal.DistributionLocator;
import org.gradle.wrapper.PathAssembler;
import org.gradle.wrapper.WrapperConfiguration;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleEnvironment;
import org.jetbrains.plugins.gradle.util.GradleLog;
import org.jetbrains.plugins.gradle.util.GradleUtil;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Encapsulates algorithm of gradle libraries discovery.
 * <p/>
 * Thread-safe.
 *
 * @author Denis Zhdanov
 * @since 2011-08-04
 */
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
@Singleton
public class GradleInstallationManager {
    public static final Pattern GRADLE_JAR_FILE_PATTERN;
    public static final Pattern ANY_GRADLE_JAR_FILE_PATTERN;
    public static final Pattern ANT_JAR_PATTERN = Pattern.compile("ant(-(.*))?\\.jar");
    public static final Pattern IVY_JAR_PATTERN = Pattern.compile("ivy(-(.*))?\\.jar");

    private static final String[] GRADLE_START_FILE_NAMES;
    @NonNls
    private static final String GRADLE_ENV_PROPERTY_NAME;

    static {
        // Init static data with ability to redefine it locally.
        GRADLE_JAR_FILE_PATTERN = Pattern.compile("gradle-(core-)?(\\d.*)\\.jar");
        ANY_GRADLE_JAR_FILE_PATTERN = Pattern.compile("gradle-(.*)\\.jar");
        GRADLE_START_FILE_NAMES = "gradle:gradle.cmd:gradle.sh".split(":");
        GRADLE_ENV_PROPERTY_NAME = "GRADLE_HOME";
    }

    @Nullable
    private Ref<File> myCachedGradleHomeFromPath;

    /**
     * Allows to get file handles for the gradle binaries to use.
     *
     * @param gradleHome gradle sdk home
     * @return file handles for the gradle binaries; <code>null</code> if gradle is not discovered
     */
    @Nullable
    public Collection<File> getAllLibraries(@Nullable File gradleHome) {

        if (gradleHome == null || !gradleHome.isDirectory()) {
            return null;
        }

        List<File> result = new ArrayList<>();

        File libs = new File(gradleHome, "lib");
        File[] files = libs.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(".jar")) {
                    result.add(file);
                }
            }
        }

        File plugins = new File(libs, "plugins");
        files = plugins.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(".jar")) {
                    result.add(file);
                }
            }
        }
        return result.isEmpty() ? null : result;
    }

    @Nullable
    public File getGradleHome(@Nullable Project project, @Nonnull String linkedProjectPath) {
        return doGetGradleHome(project, linkedProjectPath);
    }

    /**
     * Tries to return file handle that points to the gradle installation home.
     *
     * @param project           target project (if any)
     * @param linkedProjectPath path to the target linked project config
     * @return file handle that points to the gradle installation home (if any)
     */
    @Nullable
    private File doGetGradleHome(@Nullable Project project, @Nonnull String linkedProjectPath) {
        if (project == null) {
            return null;
        }
        GradleProjectSettings settings = GradleSettings.getInstance(project).getLinkedProjectSettings(linkedProjectPath);
        if (settings == null || settings.getDistributionType() == null) {
            return null;
        }
        return getGradleHome(settings.getDistributionType(), linkedProjectPath, settings.getGradleHome());
    }

    @Nullable
    public File getGradleHome(@Nonnull DistributionType distributionType, @Nonnull String linkedProjectPath, @Nullable String gradleHome) {
        File candidate = null;
        switch (distributionType) {
            case LOCAL:
                if (gradleHome != null) {
                    candidate = new File(gradleHome);
                }
                break;
            case DEFAULT_WRAPPED:
                WrapperConfiguration wrapperConfiguration = GradleUtil.getWrapperConfiguration(linkedProjectPath);
                candidate = getWrappedGradleHome(linkedProjectPath, wrapperConfiguration);
                break;
            case WRAPPED:
                // not supported yet
                break;
            case BUNDLED:
                WrapperConfiguration bundledWrapperSettings = new WrapperConfiguration();
                DistributionLocator distributionLocator = new DistributionLocator();
                bundledWrapperSettings.setDistribution(distributionLocator.getDistributionFor(GradleVersion.current()));
                candidate = getWrappedGradleHome(linkedProjectPath, bundledWrapperSettings);
                break;
        }

        File result = null;
        if (candidate != null) {
            result = isGradleSdkHome(candidate) ? candidate : null;
        }

        if (result != null) {
            return result;
        }
        return getAutodetectedGradleHome();
    }

    /**
     * Tries to deduce gradle location from current environment.
     *
     * @return gradle home deduced from the current environment (if any); <code>null</code> otherwise
     */
    @Nullable
    public File getAutodetectedGradleHome() {
        File result = getGradleHomeFromPath();
        return result == null ? getGradleHomeFromEnvProperty() : result;
    }

    /**
     * Tries to return gradle home that is defined as a dependency to the given module.
     *
     * @param module target module
     * @return file handle that points to the gradle installation home defined as a dependency of the given module (if any)
     */
    @Nullable
    public VirtualFile getGradleHome(@Nullable Module module) {
        if (module == null) {
            return null;
        }
        final VirtualFile[] roots = OrderEnumerator.orderEntries(module).getAllLibrariesAndSdkClassesRoots();
        if (roots == null) {
            return null;
        }
        for (VirtualFile root : roots) {
            if (root != null && isGradleSdkHome(root)) {
                return root;
            }
        }
        return null;
    }

    /**
     * Tries to return gradle home defined as a dependency of the given module; falls back to the project-wide settings otherwise.
     *
     * @param module  target module that can have gradle home as a dependency
     * @param project target project which gradle home setting should be used if module-specific gradle location is not defined
     * @return gradle home derived from the settings of the given entities (if any); <code>null</code> otherwise
     */
    @Nullable
    public VirtualFile getGradleHome(@Nullable Module module, @Nullable Project project, @Nonnull String linkedProjectPath) {
        final VirtualFile result = getGradleHome(module);
        if (result != null) {
            return result;
        }

        final File home = getGradleHome(project, linkedProjectPath);
        return home == null ? null : LocalFileSystem.getInstance().refreshAndFindFileByIoFile(home);
    }

    /**
     * Tries to discover gradle installation path from the configured system path
     *
     * @return file handle for the gradle directory if it's possible to deduce from the system path; <code>null</code> otherwise
     */
    @Nullable
    public File getGradleHomeFromPath() {
        Ref<File> ref = myCachedGradleHomeFromPath;
        if (ref != null) {
            return ref.get();
        }
        String path = Platform.current().os().getEnvironmentVariable("PATH");
        if (path == null) {
            return null;
        }
        for (String pathEntry : path.split(File.pathSeparator)) {
            File dir = new File(pathEntry);
            if (!dir.isDirectory()) {
                continue;
            }
            for (String fileName : GRADLE_START_FILE_NAMES) {
                File startFile = new File(dir, fileName);
                if (startFile.isFile()) {
                    File candidate = dir.getParentFile();
                    if (isGradleSdkHome(candidate)) {
                        myCachedGradleHomeFromPath = new Ref<>(candidate);
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Tries to discover gradle installation via environment property.
     *
     * @return file handle for the gradle directory deduced from the system property (if any)
     */
    @Nullable
    public File getGradleHomeFromEnvProperty() {
        String path = Platform.current().os().getEnvironmentVariable(GRADLE_ENV_PROPERTY_NAME);
        if (path == null) {
            return null;
        }
        File candidate = new File(path);
        return isGradleSdkHome(candidate) ? candidate : null;
    }

    /**
     * Does the same job as {@link #isGradleSdkHome(File)} for the given virtual file.
     *
     * @param file gradle installation home candidate
     * @return <code>true</code> if given file points to the gradle installation; <code>false</code> otherwise
     */
    public boolean isGradleSdkHome(@Nullable VirtualFile file) {
        return file != null && isGradleSdkHome(new File(file.getPath()));
    }

    /**
     * Allows to answer if given virtual file points to the gradle installation root.
     *
     * @param file gradle installation root candidate
     * @return <code>true</code> if we consider that given file actually points to the gradle installation root;
     * <code>false</code> otherwise
     */
    public boolean isGradleSdkHome(@Nullable File file) {
        if (file == null) {
            return false;
        }
        final File libs = new File(file, "lib");
        if (!libs.isDirectory()) {
            if (GradleEnvironment.DEBUG_GRADLE_HOME_PROCESSING) {
                GradleLog.LOG.info(String.format(
                    "Gradle sdk check failed for the path '%s'. Reason: it doesn't have a child directory named 'lib'",
                    file.getAbsolutePath()
                ));
            }
            return false;
        }

        final boolean found = isGradleSdk(libs.listFiles());
        if (GradleEnvironment.DEBUG_GRADLE_HOME_PROCESSING) {
            GradleLog.LOG.info(String.format(
                "Gradle home check %s for the path '%s'",
                found ? "passed" : "failed",
                file.getAbsolutePath()
            ));
        }
        return found;
    }

    /**
     * Allows to answer if given virtual file points to the gradle installation root.
     *
     * @param file gradle installation root candidate
     * @return <code>true</code> if we consider that given file actually points to the gradle installation root;
     * <code>false</code> otherwise
     */
    public boolean isGradleSdkHome(String gradleHomePath) {
        return isGradleSdkHome(new File(gradleHomePath));
    }

    /**
     * Allows to answer if given files contain the one from gradle installation.
     *
     * @param files files to process
     * @return <code>true</code> if one of the given files is from the gradle installation; <code>false</code> otherwise
     */
    public boolean isGradleSdk(@Nullable VirtualFile... files) {
        if (files == null) {
            return false;
        }
        File[] arg = new File[files.length];
        for (int i = 0; i < files.length; i++) {
            arg[i] = new File(files[i].getPath());
        }
        return isGradleSdk(arg);
    }

    private boolean isGradleSdk(@Nullable File... files) {
        return findGradleJar(files) != null;
    }

    @Nullable
    private File findGradleJar(@Nullable File... files) {
        if (files == null) {
            return null;
        }
        for (File file : files) {
            if (GRADLE_JAR_FILE_PATTERN.matcher(file.getName()).matches()) {
                return file;
            }
        }

        if (GradleEnvironment.DEBUG_GRADLE_HOME_PROCESSING) {
            StringBuilder filesInfo = new StringBuilder();
            for (File file : files) {
                filesInfo.append(file.getAbsolutePath()).append(';');
            }
            if (filesInfo.length() > 0) {
                filesInfo.setLength(filesInfo.length() - 1);
            }
            GradleLog.LOG.info(String.format(
                "Gradle sdk check fails. Reason: no one of the given files matches gradle jar pattern (%s). Files: %s",
                GRADLE_JAR_FILE_PATTERN.toString(), filesInfo
            ));
        }

        return null;
    }

    /**
     * Allows to ask for the classpath roots of the classes that are additionally provided by the gradle integration (e.g. gradle class
     * files, bundled groovy-all jar etc).
     *
     * @param project target project to use for gradle home retrieval
     * @return classpath roots of the classes that are additionally provided by the gradle integration (if any);
     * <code>null</code> otherwise
     */
    @Nullable
    public List<VirtualFile> getClassRoots(@Nullable Project project) {
        List<File> files = getClassRoots(project, null);
        if (files == null) {
            return null;
        }
        final LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
        return ContainerUtil.mapNotNull(
            files,
            file -> {
                final VirtualFile virtualFile = localFileSystem.refreshAndFindFileByIoFile(file);
                return virtualFile != null ? ArchiveVfsUtil.getArchiveRootForLocalFile(virtualFile) : null;
            }
        );
    }

    @Nullable
    @RequiredReadAction
    public List<File> getClassRoots(@Nullable Project project, @Nullable String rootProjectPath) {
        if (project == null) {
            return null;
        }

        if (rootProjectPath == null) {
            for (Module module : ModuleManager.getInstance(project).getModules()) {
                rootProjectPath = ExternalSystemApiUtil.getExtensionSystemOption(module, ExternalSystemConstants.ROOT_PROJECT_PATH_KEY);
                List<File> result = findGradleSdkClasspath(project, rootProjectPath);
                if (!result.isEmpty()) {
                    return result;
                }
            }
        }
        else {
            return findGradleSdkClasspath(project, rootProjectPath);
        }

        return null;
    }

    private List<File> findGradleSdkClasspath(Project project, String rootProjectPath) {
        List<File> result = new ArrayList<>();

        if (StringUtil.isEmpty(rootProjectPath)) {
            return result;
        }

        File gradleHome = getGradleHome(project, rootProjectPath);

        if (gradleHome == null || !gradleHome.isDirectory()) {
            return result;
        }

        final Collection<File> libraries = getAllLibraries(gradleHome);
        if (libraries == null) {
            return result;
        }

        for (File file : libraries) {
            if (isGradleBuildClasspathLibrary(file)) {
                ContainerUtil.addIfNotNull(result, file);
            }
        }

        File src = new File(gradleHome, "src");
        if (src.isDirectory()) {
            if (new File(src, "org").isDirectory()) {
                addRoots(result, src);
            }
            else {
                addRoots(result, src.listFiles());
            }
        }

        return result;
    }

    private boolean isGradleBuildClasspathLibrary(File file) {
        String fileName = file.getName();
        return ANY_GRADLE_JAR_FILE_PATTERN.matcher(fileName).matches()
            || ANT_JAR_PATTERN.matcher(fileName).matches()
            || IVY_JAR_PATTERN.matcher(fileName).matches()
            || GroovyConfigUtils.matchesGroovyAll(fileName);
    }

    private void addRoots(@Nonnull List<File> result, @Nullable File... files) {
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file == null || !file.isDirectory()) {
                continue;
            }
            result.add(0, file);
        }
    }

    private File getWrappedGradleHome(String linkedProjectPath, @Nullable final WrapperConfiguration wrapperConfiguration) {
        if (wrapperConfiguration == null) {
            return null;
        }
        File gradleSystemDir;

        if (Wrapper.PathBase.PROJECT.name().equals(wrapperConfiguration.getDistributionBase())) {
            gradleSystemDir = new File(linkedProjectPath, ".gradle");
        }
        else {
            gradleSystemDir = StartParameter.DEFAULT_GRADLE_USER_HOME;
        }
        if (!gradleSystemDir.isDirectory()) {
            return null;
        }

        PathAssembler.LocalDistribution localDistribution =
            new PathAssembler(gradleSystemDir, new File(linkedProjectPath)).getDistribution(wrapperConfiguration);

        if (localDistribution.getDistributionDir() == null) {
            return null;
        }

        File[] distFiles =
            localDistribution.getDistributionDir().listFiles(f -> f.isDirectory() && StringUtil.startsWith(f.getName(), "gradle-"));

        return distFiles == null || distFiles.length == 0 ? null : distFiles[0];
    }
}

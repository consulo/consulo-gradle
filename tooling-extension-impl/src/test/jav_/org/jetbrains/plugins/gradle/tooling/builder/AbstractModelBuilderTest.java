/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.tooling.builder;

import com.intellij.openapi.externalSystem.model.ExternalProject;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.util.GradleVersion;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.gradle.tooling.model.ProjectImportAction;
import org.jetbrains.plugins.gradle.service.project.GradleExecutionHelper;
import org.jetbrains.plugins.gradle.tooling.impl.builder.ModelBuildScriptClasspathBuilderImpl;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Vladislav.Soroka
 * @since 11/29/13
 */
@RunWith(value = Parameterized.class)
public abstract class AbstractModelBuilderTest {

  public static final Object[][] SUPPORTED_GRADLE_VERSIONS = {
    {"1.9"}, {"1.10"}, {"1.11"}, {"1.12"},
    {"2.0"}
  };

  public static final Pattern TEST_METHOD_NAME_PATTERN = Pattern.compile("(.*)\\[(\\d*: with Gradle-.*)\\]");

  private static File ourTempDir;

  @Nonnull
  private final String gradleVersion;
  protected File testDir;
  protected ProjectImportAction.AllModels allModels;

  @Rule public TestName name = new TestName();

  public AbstractModelBuilderTest(@Nonnull String gradleVersion) {
    this.gradleVersion = gradleVersion;
  }

  @Parameterized.Parameters(name = "{index}: with Gradle-{0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(SUPPORTED_GRADLE_VERSIONS);
  }


  @Before
  public void setUp() throws Exception {
    ensureTempDirCreated();

    String methodName = name.getMethodName();
    Matcher m = TEST_METHOD_NAME_PATTERN.matcher(methodName);
    if (m.matches()) {
      methodName = m.group(1);
    }

    testDir = new File(ourTempDir, methodName);
    FileUtil.ensureExists(testDir);

    FileUtil.writeToFile(
      new File(testDir, GradleConstants.DEFAULT_SCRIPT_NAME),
      FileUtil.loadTextAndClose(getClass().getResourceAsStream("/" + methodName + "/" + GradleConstants.DEFAULT_SCRIPT_NAME))
    );

    FileUtil.writeToFile(
      new File(testDir, GradleConstants.SETTINGS_FILE_NAME),
      FileUtil.loadTextAndClose(getClass().getResourceAsStream("/" + methodName + "/" + GradleConstants.SETTINGS_FILE_NAME))
    );

    GradleConnector connector = GradleConnector.newConnector();

    final URI distributionUri = new DistributionLocator().getDistributionFor(GradleVersion.version(gradleVersion));
    connector.useDistribution(distributionUri);
    connector.forProjectDirectory(testDir);
    int daemonMaxIdleTime = 10;
    try {
      daemonMaxIdleTime = Integer.parseInt(System.getProperty("gradleDaemonMaxIdleTime", "10"));
    }
    catch (NumberFormatException ignore) {}

    ((DefaultGradleConnector)connector).daemonMaxIdleTime(daemonMaxIdleTime, TimeUnit.SECONDS);
    ProjectConnection connection = connector.connect();

    final ProjectImportAction projectImportAction = new ProjectImportAction(false);
    projectImportAction.addExtraProjectModelClasses(getModels());
    BuildActionExecuter<ProjectImportAction.AllModels> buildActionExecutor = connection.action(projectImportAction);
    File initScript = GradleExecutionHelper.generateInitScript(false, getToolingExtensionClasses());
    assertNotNull(initScript);
    buildActionExecutor.withArguments("--recompile-scripts", GradleConstants.INIT_SCRIPT_CMD_OPTION, initScript.getAbsolutePath());
    allModels = buildActionExecutor.run();
    assertNotNull(allModels);
  }

  @Nonnull
  private Set<Class> getToolingExtensionClasses() {
    final Set<Class> classes = ContainerUtil.<Class>set(
      ExternalProject.class,
      // gradle-tooling-extension-api jar
      ProjectImportAction.class,
      // gradle-tooling-extension-impl jar
      ModelBuildScriptClasspathBuilderImpl.class
    );

    ContainerUtil.addAllNotNull(classes, doGetToolingExtensionClasses());
    return classes;
  }

  @Nonnull
  protected Set<Class> doGetToolingExtensionClasses() {
    return Collections.emptySet();
  }

  @After
  public void tearDown() throws Exception {
    if (testDir != null) {
      FileUtil.delete(testDir);
    }
  }

  protected abstract Set<Class> getModels();


  protected <T> Map<String, T> getModulesMap(final Class<T> aClass) {
    final DomainObjectSet<? extends IdeaModule> ideaModules = allModels.getIdeaProject().getModules();

    final String filterKey = "to_filter";
    final Map<String, T> map = ContainerUtil.map2Map(ideaModules, new Function<IdeaModule, Pair<String, T>>() {
      @Override
      public Pair<String, T> fun(IdeaModule module) {
        final T value = allModels.getExtraProject(module, aClass);
        final String key = value != null ? module.getGradleProject().getPath() : filterKey;
        return Pair.create(key, value);
      }
    });

    map.remove(filterKey);
    return map;
  }

  protected void assertBuildClasspath(String projectPath, String... classpath) {
    final Map<String, BuildScriptClasspathModel> classpathModelMap = getModulesMap(BuildScriptClasspathModel.class);
    final BuildScriptClasspathModel classpathModel = classpathModelMap.get(projectPath);

    assertNotNull(classpathModel);

    final List<? extends ClasspathEntryModel> classpathEntryModels = classpathModel.getClasspath().getAll();
    assertEquals(classpath.length, classpathEntryModels.size());

    for (int i = 0, length = classpath.length; i < length; i++) {
      String classpathEntry = classpath[i];
      final ClasspathEntryModel classpathEntryModel = classpathEntryModels.get(i);
      assertNotNull(classpathEntryModel);
      assertEquals(1, classpathEntryModel.getClasses().size());
      final String path = classpathEntryModel.getClasses().iterator().next();
      assertEquals(classpathEntry, new File(path).getName());
    }
  }

  private static void ensureTempDirCreated() throws IOException {
    if (ourTempDir != null) return;

    ourTempDir = new File(FileUtil.getTempDirectory(), "gradleTests");
    FileUtil.delete(ourTempDir);
    FileUtil.ensureExists(ourTempDir);
  }

  public static class DistributionLocator {
    private static final String RELEASE_REPOSITORY_ENV = "GRADLE_RELEASE_REPOSITORY";
    private static final String SNAPSHOT_REPOSITORY_ENV = "GRADLE_SNAPSHOT_REPOSITORY";
    private static final String INTELLIJ_LABS_GRADLE_RELEASE_MIRROR =
      "http://services.gradle.org-mirror.labs.intellij.net/distributions";
    private static final String INTELLIJ_LABS_GRADLE_SNAPSHOT_MIRROR =
      "http://services.gradle.org-mirror.labs.intellij.net/distributions-snapshots";
    private static final String GRADLE_RELEASE_REPO = "http://services.gradle.org/distributions";
    private static final String GRADLE_SNAPSHOT_REPO = "http://services.gradle.org/distributions-snapshots";

    @Nonnull
    private final String myReleaseRepoUrl;
    @Nonnull
    private final String mySnapshotRepoUrl;

    public DistributionLocator() {
      this(DistributionLocator.getRepoUrl(false), DistributionLocator.getRepoUrl(true));
    }

    public DistributionLocator(@Nonnull String releaseRepoUrl, @Nonnull String snapshotRepoUrl) {
      myReleaseRepoUrl = releaseRepoUrl;
      mySnapshotRepoUrl = snapshotRepoUrl;
    }

    @Nonnull
    public URI getDistributionFor(@Nonnull GradleVersion version) throws URISyntaxException {
      return getDistribution(getDistributionRepository(version), version, "gradle", "bin");
    }

    @Nonnull
    private String getDistributionRepository(@Nonnull GradleVersion version) {
      return version.isSnapshot() ? mySnapshotRepoUrl : myReleaseRepoUrl;
    }

    private static URI getDistribution(@Nonnull String repositoryUrl,
                                       @Nonnull GradleVersion version,
                                       @Nonnull String archiveName,
                                       @Nonnull String archiveClassifier) throws URISyntaxException {
      return new URI(String.format("%s/%s-%s-%s.zip", repositoryUrl, archiveName, version.getVersion(), archiveClassifier));
    }

    @Nonnull
    public static String getRepoUrl(boolean isSnapshotUrl) {
      final String envRepoUrl = System.getenv(isSnapshotUrl ? SNAPSHOT_REPOSITORY_ENV : RELEASE_REPOSITORY_ENV);
      if (envRepoUrl != null) return envRepoUrl;

      if (UsefulTestCase.IS_UNDER_TEAMCITY) {
        return isSnapshotUrl ? INTELLIJ_LABS_GRADLE_SNAPSHOT_MIRROR : INTELLIJ_LABS_GRADLE_RELEASE_MIRROR;
      }

      return isSnapshotUrl ? GRADLE_SNAPSHOT_REPO : GRADLE_RELEASE_REPO;
    }
  }
}

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
package org.jetbrains.plugins.gradle.config;

import com.intellij.java.language.impl.psi.NonClasspathDirectoriesScope;
import com.intellij.java.language.psi.JavaPsiFacade;
import consulo.compiler.execution.CompileStepBeforeRun;
import consulo.compiler.execution.CompileStepBeforeRunNoErrorCheck;
import consulo.execution.CantRunException;
import consulo.execution.RunManager;
import consulo.execution.action.Location;
import consulo.execution.configuration.RunProfile;
import consulo.execution.executor.Executor;
import consulo.externalSystem.util.ExternalSystemApiUtil;
import consulo.externalSystem.util.ExternalSystemConstants;
import consulo.gradle.icon.GradleIconGroup;
import consulo.ide.ServiceManager;
import consulo.ide.impl.idea.openapi.externalSystem.psi.search.ExternalModuleBuildGlobalSearchScope;
import consulo.ide.impl.idea.openapi.roots.impl.LibraryScopeCache;
import consulo.ide.impl.idea.openapi.vfs.VfsUtilCore;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.layer.OrderEnumerator;
import consulo.module.content.layer.orderEntry.ModuleExtensionWithSdkOrderEntry;
import consulo.module.content.layer.orderEntry.OrderEntry;
import consulo.process.ExecutionException;
import consulo.project.Project;
import consulo.ui.image.Image;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.plugins.gradle.execution.GradleTaskLocation;
import org.jetbrains.plugins.gradle.service.GradleBuildClasspathManager;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;
import org.jetbrains.plugins.gradle.service.resolve.GradleResolverUtil;
import consulo.gradle.GradleConstants;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptType;
import org.jetbrains.plugins.groovy.impl.extensions.GroovyRunnableScriptType;
import org.jetbrains.plugins.groovy.impl.runner.GroovyScriptRunConfiguration;
import org.jetbrains.plugins.groovy.impl.runner.GroovyScriptRunner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author peter
 */
public class GradleScriptType extends GroovyRunnableScriptType {
  private static final Pattern MAIN_CLASS_NAME_PATTERN = Pattern.compile("\nSTARTER_MAIN_CLASS=(.*)\n");

  public static final GroovyScriptType INSTANCE = new GradleScriptType();

  private GradleScriptType() {
    super(GradleConstants.EXTENSION);
  }

  @Nonnull
  @Override
  public Image getScriptIcon() {
    return GradleIconGroup.gradle();
  }

  @Override
  public boolean isConfigurationByLocation(@Nonnull GroovyScriptRunConfiguration existing, @Nonnull Location location) {
    final String params = existing.getScriptParameters();
    if (params == null) {
      return false;
    }

    final List<String> tasks = getTasksTarget(location);
    if (tasks == null) {
      return false;
    }

    String s = StringUtil.join(tasks, " ");
    return params.startsWith(s + " ") || params.equals(s);
  }

  @Override
  public void tuneConfiguration(@Nonnull GroovyFile file, @Nonnull GroovyScriptRunConfiguration configuration, Location location) {
    List<String> tasks = getTasksTarget(location);
    if (tasks != null) {
      String s = StringUtil.join(tasks, " ");
      configuration.setScriptParameters(s);
      configuration.setName("gradle:" + s);
    }

    RunManager.getInstance(file.getProject()).disableTasks(configuration, CompileStepBeforeRun.ID, CompileStepBeforeRunNoErrorCheck.ID);
  }

  @Nullable
  private static List<String> getTasksTarget(Location location) {
    if (location instanceof GradleTaskLocation) {
      return ((GradleTaskLocation)location).getTasks();
    }

    PsiElement parent = location.getPsiElement();
    while (parent.getParent() != null && !(parent.getParent() instanceof PsiFile)) {
      parent = parent.getParent();
    }

    if (isCreateTaskMethod(parent)) {
      final GrExpression[] arguments = ((GrMethodCallExpression)parent).getExpressionArguments();
      if (arguments.length > 0 && arguments[0] instanceof GrLiteral && ((GrLiteral)arguments[0]).getValue() instanceof String) {
        return Collections.singletonList((String)((GrLiteral)arguments[0]).getValue());
      }
    }
    else if (parent instanceof GrApplicationStatement) {
      PsiElement shiftExpression = parent.getChildren()[1].getChildren()[0];
      if (GradleResolverUtil.isLShiftElement(shiftExpression)) {
        PsiElement shiftiesChild = shiftExpression.getChildren()[0];
        if (shiftiesChild instanceof GrReferenceExpression) {
          return Collections.singletonList(shiftiesChild.getText());
        }
        else if (shiftiesChild instanceof GrMethodCallExpression) {
          return Collections.singletonList(shiftiesChild.getChildren()[0].getText());
        }
      }
      else if (shiftExpression instanceof GrMethodCallExpression) {
        return Collections.singletonList(shiftExpression.getChildren()[0].getText());
      }
    }

    return null;
  }

  private static boolean isCreateTaskMethod(PsiElement parent) {
    return parent instanceof GrMethodCallExpression && PsiUtil.isMethodCall((GrMethodCallExpression)parent, "createTask");
  }

  @Override
  public GroovyScriptRunner getRunner() {
    return new GroovyScriptRunner() {
      @Override
      public boolean shouldRefreshAfterFinish() {
        return true;
      }

      @Override
      public boolean isValidModule(@Nonnull Module module) {
        GradleInstallationManager libraryManager = ServiceManager.getService(GradleInstallationManager.class);
        return libraryManager.isGradleSdk(OrderEnumerator.orderEntries(module).getAllLibrariesAndSdkClassesRoots());
      }

      @Override
      public boolean ensureRunnerConfigured(@Nullable Module module,
                                            RunProfile profile,
                                            Executor executor,
                                            final Project project) throws ExecutionException {
        if (project != null && profile instanceof GroovyScriptRunConfiguration) {
          GroovyScriptRunConfiguration configuration = (GroovyScriptRunConfiguration)profile;
          String parameters = configuration.getScriptParameters();
          if (parameters != null) {
            // TODO den implement
            //            GradleTasksList list = GradleUtil.getToolWindowElement(GradleTasksList.class, project,
            // ExternalSystemDataKeys.RECENT_TASKS_LIST);
            //            if (list != null) {
            //              ExternalSystemTaskDescriptor descriptor = new ExternalSystemTaskDescriptor(parameters, null);
            //              descriptor.setExecutorId(executor.getId());
            //              list.setFirst(descriptor);
            //              GradleLocalSettings.getInstance(project).setRecentTasks(list.getModel().getTasks());
            //            }
          }
        }
        final GradleInstallationManager libraryManager = ServiceManager.getService(GradleInstallationManager.class);
        // TODO den implement
        //if (libraryManager.getGradleHome(module, project) == null) {
        //  int result = 0;
        //          int result = Messages.showOkCancelDialog(
        //            ExternalSystemBundle.message("gradle.run.no.sdk.text"),
        //            ExternalSystemBundle.message("gradle.run.no.sdk.title"),
        //            GradleIcons.Gradle
        //          );
        //          if (result == 0) {
        //            ShowSettingsUtil.getInstance().editConfigurable(project, new AbstractExternalProjectConfigurable(project));
        //          }
        //          if (libraryManager.getGradleHome(module, project) == null) {
        //            return false;
        //          }
        //        }
        return true;
      }

      @Override
      public void configureCommandLine(OwnJavaParameters params,
                                       @Nullable Module module,
                                       boolean tests,
                                       VirtualFile script,
                                       GroovyScriptRunConfiguration configuration) throws CantRunException {
        final Project project = configuration.getProject();
        String scriptParameters = configuration.getScriptParameters();

        final GradleInstallationManager libraryManager = ServiceManager.getService(GradleInstallationManager.class);
        if (module == null) {
          throw new CantRunException("Target module is undefined");
        }
        String rootProjectPath = ExternalSystemApiUtil.getExtensionSystemOption(module, ExternalSystemConstants.ROOT_PROJECT_PATH_KEY);
        if (StringUtil.isEmpty(rootProjectPath)) {
          throw new CantRunException(String.format("Module '%s' is not backed by gradle", module.getName()));
        }
        final VirtualFile gradleHome = libraryManager.getGradleHome(module, project, rootProjectPath);
        if (gradleHome == null) {
          throw new CantRunException("Gradle home can not be found");
        }

        params.setMainClass(findMainClass(gradleHome, script, project));

        final File[] groovyJars = GroovyConfigUtils.getGroovyAllJars(gradleHome.getPath() + "/lib/");
        if (groovyJars.length > 0) {
          params.getClassPath().add(groovyJars[0].getAbsolutePath());
        }
        else {
          final VirtualFile groovyJar = findGroovyJar(module);
          if (groovyJar != null) {
            params.getClassPath().add(groovyJar);
          }
        }

        final String userDefinedClasspath = System.getProperty("gradle.launcher.classpath");
        if (StringUtil.isNotEmpty(userDefinedClasspath)) {
          params.getClassPath().add(userDefinedClasspath);
        }
        else {
          final Collection<VirtualFile> roots = libraryManager.getClassRoots(project);
          if (roots != null) {
            params.getClassPath().addVirtualFiles(roots);
          }
        }

        params.getVMParametersList().addParametersString(configuration.getVMParameters());


        params.getVMParametersList().add("-Dgradle.home=" + FileUtil.toSystemDependentName(gradleHome.getPath()));

        setToolsJar(params);

        final String scriptPath = configuration.getScriptPath();
        if (scriptPath == null) {
          throw new CantRunException("Target script is undefined");
        }
        params.getProgramParametersList().add("--build-file");
        params.getProgramParametersList().add(FileUtil.toSystemDependentName(scriptPath));
        params.getProgramParametersList().addParametersString(configuration.getProgramParameters());
        params.getProgramParametersList().addParametersString(scriptParameters);
      }
    };
  }

  @Nonnull
  private static String findMainClass(VirtualFile gradleHome, VirtualFile script, Project project) {
    final String userDefined = System.getProperty("gradle.launcher.class");
    if (StringUtil.isNotEmpty(userDefined)) {
      return userDefined;
    }

    VirtualFile launcher = gradleHome.findFileByRelativePath("bin/gradle");
    if (launcher == null) {
      launcher = gradleHome.findFileByRelativePath("bin/gradle.bat");
    }
    if (launcher != null) {
      try {
        final String text = StringUtil.convertLineSeparators(VfsUtilCore.loadText(launcher));
        final Matcher matcher = MAIN_CLASS_NAME_PATTERN.matcher(text);
        if (matcher.find()) {
          String candidate = matcher.group(1);
          if (StringUtil.isNotEmpty(candidate)) {
            return candidate;
          }
        }
      }
      catch (IOException ignored) {
      }
    }

    final PsiFile grFile = PsiManager.getInstance(project).findFile(script);
    if (grFile != null && JavaPsiFacade.getInstance(project).findClass("org.gradle.BootstrapMain", grFile.getResolveScope()) != null) {
      return "org.gradle.BootstrapMain";
    }

    return "org.gradle.launcher.GradleMain";
  }

  @Override
  public GlobalSearchScope patchResolveScope(@Nonnull GroovyFile file, @Nonnull GlobalSearchScope baseScope) {
    if (!FileUtil.extensionEquals(file.getName(), GradleConstants.EXTENSION)) {
      return baseScope;
    }

    final Module module = ModuleUtilCore.findModuleForPsiElement(file);
    if (module == null) {
      return GlobalSearchScope.EMPTY_SCOPE;
    }

    Project project = module.getProject();
    GlobalSearchScope result = GlobalSearchScope.EMPTY_SCOPE;
    for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
      if (entry instanceof ModuleExtensionWithSdkOrderEntry) {
        GlobalSearchScope scopeForSdk = LibraryScopeCache.getInstance(project).getScopeForSdk((ModuleExtensionWithSdkOrderEntry)
                                                                                                entry);
        result = result.uniteWith(scopeForSdk);
      }
    }

    String modulePath = ExternalSystemApiUtil.getExternalProjectPath(module);
    if (modulePath == null) {
      return result;
    }

    final Collection<VirtualFile> files = GradleBuildClasspathManager.getInstance(project).getModuleClasspathEntries(modulePath);

    result = new ExternalModuleBuildGlobalSearchScope(project, result.uniteWith(new NonClasspathDirectoriesScope(files)), modulePath);

    return result;
  }
}

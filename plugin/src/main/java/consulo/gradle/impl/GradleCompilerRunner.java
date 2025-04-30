package consulo.gradle.impl;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressIndicatorListener;
import consulo.compiler.*;
import consulo.compiler.scope.CompileScope;
import consulo.compiler.util.ModuleCompilerUtil;
import consulo.disposer.Disposable;
import consulo.execution.event.ExecutionListener;
import consulo.execution.executor.DefaultRunExecutor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.runner.ProgramRunner;
import consulo.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import consulo.externalSystem.service.module.extension.ExternalSystemModuleExtension;
import consulo.externalSystem.util.ExternalSystemApiUtil;
import consulo.externalSystem.util.ExternalSystemConstants;
import consulo.gradle.GradleConstants;
import consulo.gradle.icon.GradleIconGroup;
import consulo.gradle.localize.GradleLocalize;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.event.ProcessEvent;
import consulo.process.event.ProcessListener;
import consulo.project.Project;
import consulo.ui.image.Image;
import consulo.util.lang.Pair;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @author VISTALL
 * @since 2025-04-16
 */
@ExtensionImpl
public class GradleCompilerRunner implements CompilerRunner {
    private static final Logger LOG = Logger.getInstance(GradleCompilerRunner.class);

    private final Project myProject;
    private final Provider<GradleSettings> myGradleSettings;

    @Inject
    public GradleCompilerRunner(Project project, Provider<GradleSettings> gradleSettings) {
        myProject = project;
        myGradleSettings = gradleSettings;
    }

    @Override
    public boolean isAvailable() {
        return myGradleSettings.get().isEnableCompilerOverride();
    }

    @Nonnull
    @Override
    public LocalizeValue getName() {
        return GradleLocalize.gradleName();
    }

    @Nonnull
    @Override
    public Image getBuildIcon() {
        return GradleIconGroup.gradlebuild();
    }

    @Override
    public boolean build(CompileDriver compileDriver, CompileContextEx context, boolean isRebuild, boolean forceCompile, boolean onlyCheckStatus) throws ExitException {
        ExternalSystemTaskExecutionSettings settings = new ExternalSystemTaskExecutionSettings();

        CompileScope compileScope = context.getCompileScope();

        List<Module> modules = new ArrayList<>(List.of(compileScope.getAffectedModules()));
        ModuleCompilerUtil.sortModules(context.getProject(), modules);

        List<String> tasks = new ArrayList<>();

        for (Module module : modules) {
            ExternalSystemModuleExtension extension = module.getExtension(ExternalSystemModuleExtension.class);
            if (extension == null) {
                continue;
            }

            String projectId = extension.getOption(ExternalSystemConstants.LINKED_PROJECT_ID_KEY);
            if (projectId == null) {
                continue;
            }

            String projectPath = extension.getOption(ExternalSystemConstants.LINKED_PROJECT_PATH_KEY);
            String rootPath = extension.getOption(ExternalSystemConstants.ROOT_PROJECT_PATH_KEY);
            if (projectPath != null && projectPath.equals(rootPath)) {
                // in case single module project do not add project id, gradle can't find by name
                projectId = "";
            }

            tasks.add(projectId + ":compileJava");

            if (compileScope.includeTestScope()) {
                tasks.add(projectId + ":compileTestJava");
            }
        }

        settings.setExternalProjectPath(myProject.getBasePath());

        settings.setTaskNames(tasks);

        execute(context, settings);

        return true;
    }

    @Override
    public void cleanUp(CompileDriver compileDriver, CompileContextEx context) {
        try {
            ExternalSystemTaskExecutionSettings settings = new ExternalSystemTaskExecutionSettings();
            settings.setTaskNames(List.of("clean"));
            settings.setExternalProjectPath(myProject.getBasePath());
            
            execute(context, settings);
        }
        catch (ExitException ignored) {
        }
    }

    private void execute(CompileContextEx context, ExternalSystemTaskExecutionSettings executionSettings) throws ExitException {
        ProgressIndicator progressIndicator = context.getProgressIndicator();

        executionSettings.setExternalSystemIdString(GradleConstants.SYSTEM_ID.toString());
        
        Disposable disposable = Disposable.newDisposable("build gradle waiter");

        CompletableFuture<Void> result = new CompletableFuture<>();
        result.whenComplete((unused, throwable) -> {
            // dispose on any
            disposable.disposeWithTree();
        });

        Project project = context.getProject();

        Pair<ProgramRunner, ExecutionEnvironment> pair =
            ExternalSystemApiUtil.createRunner(executionSettings, DefaultRunExecutor.EXECUTOR_ID, project, GradleConstants.SYSTEM_ID);

        ProgramRunner runner = pair.first;
        ExecutionEnvironment environment = pair.second;

        SimpleReference<ProcessHandler> ref = new SimpleReference<>();

        project.getMessageBus().connect(disposable).subscribe(ExecutionListener.class, new ExecutionListener() {
            @Override
            public void processNotStarted(@Nonnull final String executorIdLocal, @Nonnull final ExecutionEnvironment environmentLocal) {
                if (environment.equals(environmentLocal)) {
                    result.completeExceptionally(new Exception("processNotStarted"));
                }
            }

            @Override
            public void processStarted(@Nonnull final String executorIdLocal,
                                       @Nonnull final ExecutionEnvironment environmentLocal,
                                       @Nonnull final ProcessHandler handler) {
                if (environment.equals(environmentLocal)) {
                    handler.addProcessListener(new ProcessListener() {
                        @Override
                        public void processTerminated(ProcessEvent event) {
                            if (event.getExitCode() == 0) {
                                result.complete(null);
                            }
                            else {
                                result.completeExceptionally(new Exception("Process exited wrong: " + event.getExitCode()));
                            }

                            environmentLocal.getContentToReuse();
                        }
                    });

                    ref.set(handler);
                }
            }
        });

        progressIndicator.addListener(new ProgressIndicatorListener() {
            @Override
            public void canceled() {
                result.cancel(false);

                ProcessHandler processHandler = ref.get();
                if (processHandler != null) {
                    processHandler.destroyProcess();
                }
            }
        });

        project.getUIAccess().give(() -> {
            try {
                runner.execute(environment);
            }
            catch (ExecutionException e) {
                result.completeExceptionally(e);
                LOG.warn(e);
            }
        });

        try {
            result.get();
        }
        catch (InterruptedException | java.util.concurrent.ExecutionException e) {
            throw new ExitException(ExitStatus.CANCELLED);
        }
    }
}

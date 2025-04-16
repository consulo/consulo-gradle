package consulo.gradle.impl;

import consulo.annotation.component.ExtensionImpl;
import consulo.compiler.CompileContextEx;
import consulo.compiler.CompileDriver;
import consulo.compiler.CompilerRunner;
import consulo.compiler.ExitException;
import consulo.gradle.icon.GradleIconGroup;
import consulo.gradle.localize.GradleLocalize;
import consulo.localize.LocalizeValue;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

/**
 * @author VISTALL
 * @since 2025-04-16
 */
@ExtensionImpl
public class GradleCompilerRunner implements CompilerRunner {
    private final Provider<GradleSettings> myGradleSettings;

    @Inject
    public GradleCompilerRunner(Provider<GradleSettings> gradleSettings) {
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
        // TODO
        return false;
    }

    @Override
    public void cleanUp(CompileDriver compileDriver, CompileContextEx context) {
        // TODO
    }
}

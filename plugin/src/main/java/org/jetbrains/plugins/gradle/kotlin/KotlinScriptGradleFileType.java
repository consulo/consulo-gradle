package org.jetbrains.plugins.gradle.kotlin;

import consulo.gradle.icon.GradleIconGroup;
import consulo.language.file.LanguageFileType;
import consulo.language.plain.PlainTextLanguage;
import consulo.localize.LocalizeValue;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 2024-12-24
 */
public class KotlinScriptGradleFileType extends LanguageFileType {
    public static final KotlinScriptGradleFileType INSTANCE = new KotlinScriptGradleFileType();

    public KotlinScriptGradleFileType() {
        super(PlainTextLanguage.INSTANCE);
    }

    @Nonnull
    @Override
    public String getId() {
        return "KOTLIN_GRADLE";
    }

    @Nonnull
    @Override
    public LocalizeValue getDescription() {
        return LocalizeValue.of("Kotlin Gradle File");
    }

    @Nonnull
    @Override
    public Image getIcon() {
        return GradleIconGroup.kotlingradlescript();
    }
}

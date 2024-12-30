package org.jetbrains.plugins.gradle.service.resolve.dsl;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.annotation.Annotator;
import consulo.language.editor.annotation.AnnotatorFactory;
import org.jetbrains.plugins.groovy.GroovyLanguage;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author VISTALL
 * @since 2023-03-31
 */
@ExtensionImpl
public class GradleDslAnnotatorFactory implements AnnotatorFactory {
    @Nullable
    @Override
    public Annotator createAnnotator() {
        return new GradleDslAnnotator();
    }

    @Nonnull
    @Override
    public Language getLanguage() {
        return GroovyLanguage.INSTANCE;
    }
}

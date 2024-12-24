package org.jetbrains.plugins.gradle.kotlin;

import consulo.annotation.component.ExtensionImpl;
import consulo.gradle.GradleConstants;
import consulo.virtualFileSystem.fileType.FileNameMatcherFactory;
import consulo.virtualFileSystem.fileType.FileTypeConsumer;
import consulo.virtualFileSystem.fileType.FileTypeFactory;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;

/**
 * @author VISTALL
 * @since 2024-12-24
 */
@ExtensionImpl
public class KotlinScriptGradleFileTypeFactory extends FileTypeFactory {
    private final FileNameMatcherFactory myFileNameMatcherFactory;

    @Inject
    public KotlinScriptGradleFileTypeFactory(FileNameMatcherFactory fileNameMatcherFactory) {
        myFileNameMatcherFactory = fileNameMatcherFactory;
    }

    @Override
    public void createFileTypes(@Nonnull FileTypeConsumer fileTypeConsumer) {
        fileTypeConsumer.consume(KotlinScriptGradleFileType.INSTANCE,
            myFileNameMatcherFactory.createExactFileNameMatcher(GradleConstants.KOTLIN_DSL_SCRIPT_NAME));
    }
}

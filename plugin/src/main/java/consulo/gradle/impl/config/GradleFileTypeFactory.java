package consulo.gradle.impl.config;

import consulo.annotation.component.ExtensionImpl;
import consulo.virtualFileSystem.fileType.FileTypeConsumer;
import consulo.virtualFileSystem.fileType.FileTypeFactory;
import consulo.gradle.GradleConstants;
import org.jetbrains.plugins.groovy.GroovyFileType;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 22/10/2021
 */
@ExtensionImpl
public class GradleFileTypeFactory extends FileTypeFactory {
    @Override
    public void createFileTypes(@Nonnull FileTypeConsumer fileTypeConsumer) {
        fileTypeConsumer.consume(GroovyFileType.INSTANCE, GradleConstants.EXTENSION);
    }
}

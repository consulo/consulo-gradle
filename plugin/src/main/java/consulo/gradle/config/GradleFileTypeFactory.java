package consulo.gradle.config;

import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.groovy.GroovyFileType;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 22/10/2021
 */
public class GradleFileTypeFactory extends FileTypeFactory {
    @Override
    public void createFileTypes(@Nonnull FileTypeConsumer fileTypeConsumer) {
        fileTypeConsumer.consume(GroovyFileType.GROOVY_FILE_TYPE, GradleConstants.EXTENSION);
    }
}

package consulo.gradle.impl.module.extension;

import consulo.annotation.component.ExtensionImpl;
import consulo.externalSystem.service.module.extension.ExternalSystemModuleExtensionProvider;
import consulo.gradle.GradleConstants;
import jakarta.annotation.Nullable;

/**
 * @author VISTALL
 * @since 31/03/2023
 */
@ExtensionImpl
public class GradleModuleExtensionProvider extends ExternalSystemModuleExtensionProvider {
    public GradleModuleExtensionProvider() {
        super(GradleConstants.SYSTEM_ID);
    }

    @Nullable
    @Override
    public String getParentId() {
        return "java";
    }
}

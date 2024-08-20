package consulo.gradle.impl.importProvider;

import com.intellij.java.impl.externalSystem.JavaProjectData;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import consulo.annotation.component.ExtensionImpl;
import consulo.content.bundle.Sdk;
import consulo.externalSystem.model.DataNode;
import consulo.externalSystem.service.project.ProjectData;
import consulo.externalSystem.util.ExternalSystemApiUtil;
import consulo.gradle.GradleConstants;
import consulo.gradle.icon.GradleIconGroup;
import consulo.gradle.localize.GradleLocalize;
import consulo.ide.impl.externalSystem.service.module.wizard.AbstractExternalModuleImportProvider;
import consulo.ide.impl.externalSystem.service.module.wizard.ExternalModuleImportContext;
import consulo.ide.impl.idea.openapi.externalSystem.service.project.manage.ProjectDataManager;
import consulo.java.language.bundle.JavaSdkTypeUtil;
import consulo.project.Project;
import consulo.ui.image.Image;
import consulo.util.io.FileUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.inject.Inject;
import org.jetbrains.plugins.gradle.service.settings.ImportFromGradleControl;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.List;

/**
 * @author VISTALL
 * @since 31-Jan-17
 */
@ExtensionImpl
public class GradleModuleImportProvider extends AbstractExternalModuleImportProvider<ImportFromGradleControl> {
    @Nonnull
    public static GradleModuleImportProvider getInstance() {
        return EP_NAME.findExtensionOrFail(GradleModuleImportProvider.class);
    }

    @Inject
    public GradleModuleImportProvider(@Nonnull ProjectDataManager dataManager) {
        super(dataManager, new ImportFromGradleControl(), GradleConstants.SYSTEM_ID);
    }

    @Nonnull
    @Override
    public String getName() {
        return GradleLocalize.gradleName().get();
    }

    @Nullable
    @Override
    public Image getIcon() {
        return GradleIconGroup.gradle();
    }

    @Override
    public boolean canImport(@Nonnull File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            return new File(fileOrDirectory, GradleConstants.DEFAULT_SCRIPT_NAME).exists();
        }
        else {
            String extension = FileUtil.getExtension(fileOrDirectory.getName());
            return GradleConstants.EXTENSION.equalsIgnoreCase(extension);
        }
    }

    @Nonnull
    @Override
    public String getFileSample() {
        return "<b>Gradle</b> build script (*.gradle)";
    }

    @Override
    protected void doPrepare(@Nonnull ExternalModuleImportContext<ImportFromGradleControl> context) {
        String importFile = context.getFileToImport();
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(importFile);
        if (file != null && !file.isDirectory()) {
            //getControl().setLinkedProjectPath(file.getParent().getPath());
        }
    }

    @Override
    protected void beforeCommit(@Nonnull DataNode<ProjectData> dataNode, @Nonnull Project project) {

    }

    @Nonnull
    @Override
    protected File getExternalProjectConfigToUse(@Nonnull File file) {
        return file.isDirectory() ? file : file.getParentFile();
    }

    @Override
    protected void applyExtraSettings(@Nonnull ExternalModuleImportContext<ImportFromGradleControl> context) {
        DataNode<ProjectData> node = getExternalProjectNode();
        if (node == null) {
            return;
        }

        DataNode<JavaProjectData> javaProjectNode = ExternalSystemApiUtil.find(node, JavaProjectData.KEY);
        if (javaProjectNode != null) {
            JavaProjectData data = javaProjectNode.getData();
            // todo context.setCompilerOutputDirectory(data.getCompileOutputPath());
            JavaSdkVersion version = data.getJdkVersion();
            Sdk jdk = findJdk(version);
            if (jdk != null) {
                //context.setProjectJdk(jdk);
            }
        }
    }

    @Nullable
    private static Sdk findJdk(@Nonnull JavaSdkVersion version) {
        List<Sdk> javaSdks = JavaSdkTypeUtil.getAllJavaSdks();
        Sdk candidate = null;
        for (Sdk sdk : javaSdks) {
            JavaSdkVersion v = JavaSdkTypeUtil.getVersion(sdk);
            if (v == version) {
                return sdk;
            }
            else if (candidate == null && v != null && version.getMaxLanguageLevel().isAtLeast(version.getMaxLanguageLevel())) {
                candidate = sdk;
            }
        }
        return candidate;
    }
}

package consulo.gradle.importProvider;

import java.io.File;
import java.util.List;

import javax.annotation.Nonnull;

import org.jetbrains.plugins.gradle.service.settings.ImportFromGradleControl;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import com.intellij.externalSystem.JavaProjectData;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTable;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import consulo.externalSystem.service.module.wizard.AbstractExternalModuleImportProvider;
import consulo.ui.image.Image;
import icons.GradleIcons;

/**
 * @author VISTALL
 * @since 31-Jan-17
 */
public class GradleModuleImportProvider extends AbstractExternalModuleImportProvider<ImportFromGradleControl>
{
	public GradleModuleImportProvider(@Nonnull ProjectDataManager dataManager)
	{
		super(dataManager, new ImportFromGradleControl(), GradleConstants.SYSTEM_ID);
	}

	@Nonnull
	@Override
	public String getName()
	{
		return GradleBundle.message("gradle.name");
	}

	@javax.annotation.Nullable
	@Override
	public Image getIcon()
	{
		return GradleIcons.Gradle;
	}

	@Override
	public boolean canImport(@Nonnull File fileOrDirectory)
	{
		if(fileOrDirectory.isDirectory())
		{
			return new File(fileOrDirectory, GradleConstants.DEFAULT_SCRIPT_NAME).exists();
		}
		else
		{
			String extension = FileUtilRt.getExtension(fileOrDirectory.getName());
			return GradleConstants.EXTENSION.equalsIgnoreCase(extension);
		}
	}

	@Override
	public String getPathToBeImported(@Nonnull VirtualFile file)
	{
		return file.isDirectory() ? file.findChild(GradleConstants.DEFAULT_SCRIPT_NAME).getPath() : file.getPath();
	}

	@Nonnull
	@Override
	public String getFileSample()
	{
		return "<b>Gradle</b> build script (*.gradle)";
	}

	@Override
	protected void doPrepare(@Nonnull WizardContext context)
	{
		String pathToUse = context.getProjectFileDirectory();
		VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(pathToUse);
		if(file != null && !file.isDirectory())
		{
			getControl(null).setLinkedProjectPath(file.getParent().getPath());
		}
	}

	@Override
	protected void beforeCommit(@Nonnull DataNode<ProjectData> dataNode, @Nonnull Project project)
	{

	}

	@Nonnull
	@Override
	protected File getExternalProjectConfigToUse(@Nonnull File file)
	{
		return file.isDirectory() ? file : file.getParentFile();
	}

	@Override
	protected void applyExtraSettings(@Nonnull WizardContext context)
	{
		DataNode<ProjectData> node = getExternalProjectNode();
		if(node == null)
		{
			return;
		}

		DataNode<JavaProjectData> javaProjectNode = ExternalSystemApiUtil.find(node, JavaProjectData.KEY);
		if(javaProjectNode != null)
		{
			JavaProjectData data = javaProjectNode.getData();
			context.setCompilerOutputDirectory(data.getCompileOutputPath());
			JavaSdkVersion version = data.getJdkVersion();
			Sdk jdk = findJdk(version);
			if(jdk != null)
			{
				//context.setProjectJdk(jdk);
			}
		}
	}

	@javax.annotation.Nullable
	private static Sdk findJdk(@Nonnull JavaSdkVersion version)
	{
		JavaSdk javaSdk = JavaSdk.getInstance();
		List<Sdk> javaSdks = SdkTable.getInstance().getSdksOfType(javaSdk);
		Sdk candidate = null;
		for(Sdk sdk : javaSdks)
		{
			JavaSdkVersion v = javaSdk.getVersion(sdk);
			if(v == version)
			{
				return sdk;
			}
			else if(candidate == null && v != null && version.getMaxLanguageLevel().isAtLeast(version.getMaxLanguageLevel()))
			{
				candidate = sdk;
			}
		}
		return candidate;
	}
}

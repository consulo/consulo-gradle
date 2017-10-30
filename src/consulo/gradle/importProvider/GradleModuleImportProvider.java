package consulo.gradle.importProvider;

import java.io.File;
import java.util.List;

import javax.swing.Icon;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
import icons.GradleIcons;

/**
 * @author VISTALL
 * @since 31-Jan-17
 */
public class GradleModuleImportProvider extends AbstractExternalModuleImportProvider<ImportFromGradleControl>
{
	public GradleModuleImportProvider(@NotNull ProjectDataManager dataManager)
	{
		super(dataManager, new ImportFromGradleControl(), GradleConstants.SYSTEM_ID);
	}

	@NotNull
	@Override
	public String getName()
	{
		return GradleBundle.message("gradle.name");
	}

	@Nullable
	@Override
	public Icon getIcon()
	{
		return GradleIcons.Gradle;
	}

	@Override
	public boolean canImport(@NotNull File fileOrDirectory)
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
	public String getPathToBeImported(@NotNull VirtualFile file)
	{
		return file.isDirectory() ? file.findChild(GradleConstants.DEFAULT_SCRIPT_NAME).getPath() : file.getPath();
	}

	@NotNull
	@Override
	public String getFileSample()
	{
		return "<b>Gradle</b> build script (*.gradle)";
	}

	@Override
	protected void doPrepare(@NotNull WizardContext context)
	{
		String pathToUse = context.getProjectFileDirectory();
		VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(pathToUse);
		if(file != null && !file.isDirectory())
		{
			getControl(null).setLinkedProjectPath(file.getParent().getPath());
		}
	}

	@Override
	protected void beforeCommit(@NotNull DataNode<ProjectData> dataNode, @NotNull Project project)
	{

	}

	@NotNull
	@Override
	protected File getExternalProjectConfigToUse(@NotNull File file)
	{
		return file.isDirectory() ? file : file.getParentFile();
	}

	@Override
	protected void applyExtraSettings(@NotNull WizardContext context)
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

	@Nullable
	private static Sdk findJdk(@NotNull JavaSdkVersion version)
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

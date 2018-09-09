package org.jetbrains.plugins.gradle.service.settings;

import java.io.File;
import java.util.List;

import org.gradle.tooling.GradleConnector;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.settings.ExternalSystemConfigLocator;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;

/**
 * We store not gradle config file but its parent dir path instead. That is implied by gradle design
 * ({@link GradleConnector#forProjectDirectory(File)}).
 * <p/>
 * That's why we need to provide special code which maps that directory to exact config file.
 *
 * @author Denis Zhdanov
 * @since 7/16/13 3:43 PM
 */
public class GradleConfigLocator implements ExternalSystemConfigLocator
{

	@Nonnull
	@Override
	public ProjectSystemId getTargetExternalSystemId()
	{
		return GradleConstants.SYSTEM_ID;
	}

	@Nullable
	@Override
	public VirtualFile adjust(@Nonnull VirtualFile configPath)
	{
		if(!configPath.isDirectory())
		{
			return configPath;
		}

		VirtualFile result = configPath.findChild(GradleConstants.DEFAULT_SCRIPT_NAME);
		if(result != null)
		{
			return result;
		}

		for(VirtualFile child : configPath.getChildren())
		{
			String name = child.getName();
			if(!name.endsWith(GradleConstants.EXTENSION))
			{
				continue;
			}
			if(!GradleConstants.SETTINGS_FILE_NAME.equals(name) && !child.isDirectory())
			{
				return child;
			}
		}
		return null;
	}

	@Nonnull
	@Override
	public List<VirtualFile> findAll(@Nonnull ExternalProjectSettings externalProjectSettings)
	{
		List<VirtualFile> list = ContainerUtil.newArrayList();
		for(String path : externalProjectSettings.getModules())
		{
			VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(path));
			if(vFile != null)
			{
				for(VirtualFile child : vFile.getChildren())
				{
					String name = child.getName();
					if(!child.isDirectory() && name.endsWith(GradleConstants.EXTENSION))
					{
						list.add(child);
					}
				}
			}
		}
		return list;
	}
}

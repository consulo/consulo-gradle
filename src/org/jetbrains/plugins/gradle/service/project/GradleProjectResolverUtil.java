package org.jetbrains.plugins.gradle.service.project;

import javax.annotation.Nullable;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.LibraryData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;

public class GradleProjectResolverUtil
{
	public static boolean linkProjectLibrary(@Nullable DataNode<ProjectData> ideProject, @NotNull final LibraryData library)
	{
		if(ideProject == null)
		{
			return false;
		}

		String libraryName = library.getExternalName();
		DataNode<LibraryData> libraryData = ExternalSystemApiUtil.find(ideProject, ProjectKeys.LIBRARY, node -> libraryName.equals(node.getData()
				.getExternalName()));
		if(libraryData == null)
		{
			ideProject.createChild(ProjectKeys.LIBRARY, library);
			return true;
		}
		return libraryData.getData().equals(library);
	}
}

package org.jetbrains.plugins.gradle.service.project;

import consulo.externalSystem.model.DataNode;
import consulo.externalSystem.model.ProjectKeys;
import consulo.externalSystem.model.project.LibraryData;
import consulo.externalSystem.service.project.ProjectData;
import consulo.externalSystem.util.ExternalSystemApiUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class GradleProjectResolverUtil {
    public static boolean linkProjectLibrary(@Nullable DataNode<ProjectData> ideProject, @Nonnull final LibraryData library) {
        if (ideProject == null) {
            return false;
        }

        String libraryName = library.getExternalName();
        DataNode<LibraryData> libraryData = ExternalSystemApiUtil.find(
            ideProject,
            ProjectKeys.LIBRARY,
            node -> libraryName.equals(node.getData().getExternalName())
        );
        if (libraryData == null) {
            ideProject.createChild(ProjectKeys.LIBRARY, library);
            return true;
        }
        return libraryData.getData().equals(library);
    }
}

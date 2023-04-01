package org.jetbrains.plugins.gradle.settings;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.externalSystem.setting.AbstractExternalSystemLocalSettings;
import consulo.ide.ServiceManager;
import consulo.project.Project;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import consulo.gradle.GradleConstants;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Denis Zhdanov
 * @since 5/3/12 6:16 PM
 */
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
@Singleton
@State(name = "GradleLocalSettings", storages = {@Storage(file = StoragePathMacros.WORKSPACE_FILE)})
public class GradleLocalSettings extends AbstractExternalSystemLocalSettings
  implements PersistentStateComponent<AbstractExternalSystemLocalSettings.State> {

  @Inject
  public GradleLocalSettings(@Nonnull Project project) {
    super(GradleConstants.SYSTEM_ID, project);
  }

  @Nonnull
  public static GradleLocalSettings getInstance(@Nonnull Project project) {
    return ServiceManager.getService(project, GradleLocalSettings.class);
  }

  @Nullable
  @Override
  public State getState() {
    State state = new State();
    fillState(state);
    return state;
  }

  @Override
  public void loadState(@Nonnull State state) {
    super.loadState(state);
  }
}

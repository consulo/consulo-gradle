package org.jetbrains.plugins.gradle.settings;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.externalSystem.setting.AbstractExternalSystemLocalSettings;
import consulo.project.Project;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import consulo.gradle.GradleConstants;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Denis Zhdanov
 * @since 5/3/12 6:16 PM
 */
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
@Singleton
@State(name = "GradleLocalSettings", storages = {@Storage(file = StoragePathMacros.WORKSPACE_FILE)})
public class GradleLocalSettings extends AbstractExternalSystemLocalSettings
  implements PersistentStateComponent<GradleLocalSettings.MyState> {

  private final Map<String, String> myGradleHomes = new ConcurrentHashMap<>();
  private final Map<String, String> myGradleVersions = new ConcurrentHashMap<>();

  @Inject
  public GradleLocalSettings(@Nonnull Project project) {
    super(GradleConstants.SYSTEM_ID, project);
  }

  @Nonnull
  public static GradleLocalSettings getInstance(@Nonnull Project project) {
    return project.getInstance(GradleLocalSettings.class);
  }

  @Nullable
  public String getGradleHome(String linkedProjectPath) {
    return myGradleHomes.get(linkedProjectPath);
  }

  @Nullable
  public String getGradleVersion(String linkedProjectPath) {
    return myGradleVersions.get(linkedProjectPath);
  }

  public void setGradleHome(@Nonnull String linkedProjectPath, @Nonnull String gradleHome, @Nullable String gradleVersion) {
    myGradleHomes.put(linkedProjectPath, gradleHome);
    if (gradleVersion == null) {
      myGradleVersions.remove(linkedProjectPath);
    }
    else {
      myGradleVersions.put(linkedProjectPath, gradleVersion);
    }
  }

  @Override
  public void forgetExternalProjects(@Nonnull Set<String> linkedProjectPathsToForget) {
    super.forgetExternalProjects(linkedProjectPathsToForget);
    for (String path : linkedProjectPathsToForget) {
      myGradleHomes.remove(path);
      myGradleVersions.remove(path);
    }
  }

  @Nullable
  @Override
  public MyState getState() {
    MyState state = new MyState();
    fillState(state);
    state.myGradleHomes = new HashMap<>(myGradleHomes);
    state.myGradleVersions = new HashMap<>(myGradleVersions);
    return state;
  }

  @Override
  public void loadState(@Nonnull MyState state) {
    myGradleHomes.clear();
    if (state.myGradleHomes != null) {
      myGradleHomes.putAll(state.myGradleHomes);
    }
    myGradleVersions.clear();
    if (state.myGradleVersions != null) {
      myGradleVersions.putAll(state.myGradleVersions);
    }
    super.loadState(state);
  }

  public static class MyState extends AbstractExternalSystemLocalSettings.State {
    public Map<String/* project path */, String> myGradleHomes = new HashMap<>();
    public Map<String/* project path */, String> myGradleVersions = new HashMap<>();
  }
}

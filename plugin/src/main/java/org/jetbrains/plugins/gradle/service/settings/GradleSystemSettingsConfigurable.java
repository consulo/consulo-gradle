/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.gradle.service.settings;

import consulo.disposer.Disposable;
import consulo.externalSystem.service.setting.ExternalSystemSettingsConfigurable;
import consulo.externalSystem.util.ExternalSystemApiUtil;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.fileChooser.FileChooserTextBoxBuilder;
import consulo.gradle.GradleConstants;
import consulo.gradle.localize.GradleLocalize;
import consulo.localize.LocalizeValue;
import consulo.platform.Platform;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.TextBox;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.VerticalLayout;
import consulo.ui.util.LabeledBuilder;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jspecify.annotations.Nullable;

import java.io.File;

/**
 * Manages gradle settings not specific to particular project (e.g. 'use wrapper' is project-level setting but 'gradle user home' is
 * a global one).
 *
 * @author Denis Zhdanov
 * @since 2013-04-28
 */
public class GradleSystemSettingsConfigurable extends ExternalSystemSettingsConfigurable<GradleSettings> {
    private FileChooserTextBoxBuilder.@Nullable Controller myServiceDirectoryBox;
    private @Nullable TextBox myGradleVmOptionsBox;
    private @Nullable CheckBox myCompilerOverrideBox;

    private boolean myServiceDirectoryModifiedByUser;

    public GradleSystemSettingsConfigurable(GradleSettings settings) {
        super(settings);
    }

    @Override
    @RequiredUIAccess
    public Component createUIComponent(Disposable uiDisposable) {
        GradleSettings settings = getSettings();

        FileChooserTextBoxBuilder.Controller serviceDirectoryBox = FileChooserTextBoxBuilder.create(null)
            .uiDisposable(uiDisposable)
            .dialogTitle(GradleLocalize.gradleSettingsTitleServiceDirPath())
            .fileChooserDescriptor(new FileChooserDescriptor(false, true, false, false, false, false))
            .build();
        myServiceDirectoryBox = serviceDirectoryBox;

        String path = settings.getServiceDirectoryPath();
        serviceDirectoryBox.setValue(StringUtil.isEmpty(path) ? deduceServiceDirectory() : path, false);
        serviceDirectoryBox.getComponent().addValueListener(event -> myServiceDirectoryModifiedByUser = true);

        myGradleVmOptionsBox = TextBox.create(trimIfPossible(settings.getGradleVmOptions()));

        myCompilerOverrideBox = CheckBox.create(
            LocalizeValue.localizeTODO("Override builtin compiler by Gradle"),
            settings.isEnableCompilerOverride()
        );

        VerticalLayout layout = VerticalLayout.create();
        layout.add(LabeledBuilder.filled(GradleLocalize.gradleSettingsTextServiceDirPath(), serviceDirectoryBox));
        layout.add(LabeledBuilder.filled(GradleLocalize.gradleSettingsTextVmOptions(), myGradleVmOptionsBox));
        layout.add(myCompilerOverrideBox);
        return layout;
    }

    private static String deduceServiceDirectory() {
        String path = Platform.current().os().getEnvironmentVariable(GradleConstants.SYSTEM_DIRECTORY_PATH_KEY);
        if (StringUtil.isEmpty(path)) {
            path = new File(Platform.current().jvm().getRuntimeProperty("user.home"), ".gradle").getAbsolutePath();
        }
        return path;
    }

    @Override
    @RequiredUIAccess
    public boolean isModified() {
        GradleSettings settings = getSettings();

        if (myServiceDirectoryModifiedByUser && myServiceDirectoryBox != null
            && !Comparing.equal(
            ExternalSystemApiUtil.normalizePath(myServiceDirectoryBox.getValue()),
            ExternalSystemApiUtil.normalizePath(settings.getServiceDirectoryPath())
        )) {
            return true;
        }

        if (myGradleVmOptionsBox != null
            && !Comparing.equal(trimIfPossible(myGradleVmOptionsBox.getValue()), trimIfPossible(settings.getGradleVmOptions()))) {
            return true;
        }

        return myCompilerOverrideBox != null && myCompilerOverrideBox.getValue() != settings.isEnableCompilerOverride();
    }

    private static @Nullable String trimIfPossible(@Nullable String s) {
        if (s == null) {
            return null;
        }
        String result = s.trim();
        return result.isEmpty() ? null : result;
    }

    @Override
    @RequiredUIAccess
    public void apply() {
        GradleSettings settings = getSettings();

        if (myServiceDirectoryModifiedByUser && myServiceDirectoryBox != null) {
            settings.setServiceDirectoryPath(ExternalSystemApiUtil.normalizePath(myServiceDirectoryBox.getValue()));
        }
        if (myGradleVmOptionsBox != null) {
            settings.setGradleVmOptions(trimIfPossible(myGradleVmOptionsBox.getValue()));
        }
        if (myCompilerOverrideBox != null) {
            settings.setEnableCompilerOverride(myCompilerOverrideBox.getValue());
        }
    }
}

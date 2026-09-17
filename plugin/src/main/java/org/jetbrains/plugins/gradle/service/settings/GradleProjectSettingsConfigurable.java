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

import consulo.application.Application;
import consulo.configurable.ConfigurationException;
import consulo.disposer.Disposable;
import consulo.externalSystem.service.setting.AbstractExternalProjectSettingsConfigurable;
import consulo.externalSystem.service.setting.ExternalSystemSettingsPlace;
import consulo.fileChooser.FileChooserTextBoxBuilder;
import consulo.gradle.localize.GradleLocalize;
import com.intellij.java.language.projectRoots.JavaSdkType;
import consulo.localize.LocalizeValue;
import consulo.module.ui.BundleBox;
import consulo.module.ui.BundleBoxBuilder;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.Component;
import consulo.ui.RadioButton;
import consulo.ui.RadioGroup;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.VerticalLayout;
import consulo.ui.util.LabeledBuilder;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;
import consulo.gradle.setting.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.util.GradleUtil;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Objects;

/**
 * Settings of a single gradle project - which distribution to build it with, and the jre to run that build on.
 *
 * @author Denis Zhdanov
 * @since 2013-04-24
 */
public class GradleProjectSettingsConfigurable extends AbstractExternalProjectSettingsConfigurable<GradleProjectSettings> {
    private final GradleInstallationManager myInstallationManager;

    private @Nullable RadioGroup<DistributionType> myDistributionGroup;
    private @Nullable RadioButton myUseWrapperButton;
    private FileChooserTextBoxBuilder.@Nullable Controller myGradleHomeBox;
    private @Nullable BundleBox myBundleBox;

    public GradleProjectSettingsConfigurable(GradleProjectSettings settings, ExternalSystemSettingsPlace place) {
        super(settings, place);
        myInstallationManager = Application.get().getInstance(GradleInstallationManager.class);
    }

    @Override
    @RequiredUIAccess
    protected Component createExtraUIComponent(Disposable uiDisposable) {
        GradleProjectSettings settings = getSettings();

        FileChooserTextBoxBuilder.Controller gradleHomeBox = FileChooserTextBoxBuilder.create(null)
            .uiDisposable(uiDisposable)
            .dialogTitle(GradleLocalize.gradleSettingsTextHomePath())
            .fileChooserDescriptor(GradleUtil.getGradleHomeFileChooserDescriptor())
            .build();
        myGradleHomeBox = gradleHomeBox;
        gradleHomeBox.setValue(StringUtil.notNullize(settings.getGradleHome()), false);

        RadioGroup<DistributionType> distributionGroup = RadioGroup.create();
        myDistributionGroup = distributionGroup;
        myUseWrapperButton = distributionGroup.newButton(
            GradleLocalize.gradleSettingsTextUseDefault_wrapperConfigured(),
            DistributionType.DEFAULT_WRAPPED
        );
        RadioButton useLocalButton = distributionGroup.newButton(
            GradleLocalize.gradleSettingsTextUseLocalDistribution(),
            DistributionType.LOCAL
        );
        distributionGroup.addValueListener(value -> onDistributionChange());

        BundleBoxBuilder bundleBoxBuilder = BundleBoxBuilder.create(uiDisposable);
        bundleBoxBuilder.withNoneItem(LocalizeValue.localizeTODO("Auto Select"), PlatformIconGroup.actionsFind());
        bundleBoxBuilder.withSdkTypeFilterByClass(JavaSdkType.class);
        myBundleBox = bundleBoxBuilder.build();
        myBundleBox.setSelectedBundle(settings.getJreName());

        VerticalLayout layout = VerticalLayout.create();
        layout.add(myUseWrapperButton);
        layout.add(useLocalButton);
        layout.add(LabeledBuilder.filled(GradleLocalize.gradleSettingsTextHomePath(), gradleHomeBox));
        layout.add(LabeledBuilder.sided(LocalizeValue.localizeTODO("JRE"), myBundleBox));

        updateWrapperControls(settings.getExternalProjectPath());
        return layout;
    }

    @Override
    @RequiredUIAccess
    public void onLinkedProjectPathChange(String path) {
        updateWrapperControls(path);
    }

    /**
     * Picks the distribution which suits the given project, and enables the gradle home field only when the user has to fill it in.
     */
    @RequiredUIAccess
    private void updateWrapperControls(@Nullable String linkedProjectPath) {
        RadioGroup<DistributionType> distributionGroup = myDistributionGroup;
        RadioButton useWrapperButton = myUseWrapperButton;
        if (distributionGroup == null || useWrapperButton == null) {
            return;
        }

        if (StringUtil.isEmpty(linkedProjectPath)) {
            distributionGroup.setValue(DistributionType.LOCAL);
            return;
        }

        if (GradleUtil.isGradleDefaultWrapperFilesExist(linkedProjectPath)) {
            useWrapperButton.setEnabled(true);
            useWrapperButton.setLabelText(GradleLocalize.gradleSettingsTextUseDefault_wrapperConfigured());
            distributionGroup.setValue(DistributionType.DEFAULT_WRAPPED);
        }
        else {
            useWrapperButton.setEnabled(false);
            useWrapperButton.setLabelText(GradleLocalize.gradleSettingsTextUseDefault_wrapperNot_configured());
            distributionGroup.setValue(DistributionType.LOCAL);
        }

        DistributionType storedType = getSettings().getDistributionType();
        if (storedType == DistributionType.LOCAL || (storedType == DistributionType.DEFAULT_WRAPPED && useWrapperButton.isEnabled())) {
            distributionGroup.setValue(storedType);
        }
    }

    /**
     * The gradle home is only asked for by the local distribution, and is guessed when the user has not answered it yet.
     */
    @RequiredUIAccess
    private void onDistributionChange() {
        FileChooserTextBoxBuilder.Controller gradleHomeBox = myGradleHomeBox;
        if (gradleHomeBox == null) {
            return;
        }

        boolean local = myDistributionGroup != null && myDistributionGroup.getValue() == DistributionType.LOCAL;
        gradleHomeBox.getComponent().setEnabled(local);

        if (local && StringUtil.isEmpty(gradleHomeBox.getValue())) {
            File autodetected = myInstallationManager.getAutodetectedGradleHome();
            if (autodetected != null) {
                gradleHomeBox.setValue(autodetected.getPath(), false);
            }
        }
    }

    @Override
    @RequiredUIAccess
    protected boolean isExtraModified() {
        GradleProjectSettings settings = getSettings();

        if (myDistributionGroup != null && myDistributionGroup.getValue() != settings.getDistributionType()) {
            return true;
        }

        if (myBundleBox != null && !Objects.equals(myBundleBox.getSelectedBundleName(), settings.getJreName())) {
            return true;
        }

        if (myGradleHomeBox == null) {
            return false;
        }

        String gradleHome = FileUtil.toCanonicalPath(myGradleHomeBox.getValue());
        return StringUtil.isEmpty(gradleHome)
            ? !StringUtil.isEmpty(settings.getGradleHome())
            : !gradleHome.equals(settings.getGradleHome());
    }

    @Override
    @RequiredUIAccess
    protected void applyExtra() throws ConfigurationException {
        GradleProjectSettings settings = getSettings();

        DistributionType distributionType = myDistributionGroup == null ? null : myDistributionGroup.getValue();
        String gradleHome = myGradleHomeBox == null ? null : FileUtil.toCanonicalPath(myGradleHomeBox.getValue());

        if (distributionType == DistributionType.LOCAL) {
            if (StringUtil.isEmpty(gradleHome)) {
                throw new ConfigurationException(GradleLocalize.gradleHomeSettingTypeExplicitEmpty());
            }
            if (!myInstallationManager.isGradleSdkHome(new File(gradleHome))) {
                throw new ConfigurationException(GradleLocalize.gradleHomeSettingTypeExplicitIncorrect(gradleHome));
            }
        }

        if (StringUtil.isEmpty(gradleHome)) {
            settings.setGradleHome(null);
        }
        else {
            settings.setGradleHome(gradleHome);
            GradleUtil.storeLastUsedGradleHome(gradleHome);
        }

        if (distributionType != null) {
            settings.setDistributionType(distributionType);
        }
        if (myBundleBox != null) {
            settings.setJreName(myBundleBox.getSelectedBundleName());
        }
    }
}

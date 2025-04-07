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

import com.intellij.java.language.projectRoots.JavaSdkType;
import consulo.configurable.ConfigurationException;
import consulo.disposer.Disposable;
import consulo.externalSystem.ui.awt.ExternalSystemUiUtil;
import consulo.externalSystem.ui.awt.PaintAwarePanel;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.gradle.GradleBundle;
import consulo.gradle.GradleConstants;
import consulo.gradle.localize.GradleLocalize;
import consulo.gradle.setting.DistributionType;
import consulo.ide.ServiceManager;
import consulo.ide.impl.idea.openapi.externalSystem.model.settings.LocationSettingType;
import consulo.ide.impl.idea.openapi.externalSystem.service.settings.AbstractExternalProjectSettingsControl;
import consulo.localize.LocalizeValue;
import consulo.module.ui.BundleBox;
import consulo.module.ui.BundleBoxBuilder;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.NotificationType;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.JBRadioButton;
import consulo.ui.ex.awt.TextComponentAccessor;
import consulo.ui.ex.awt.TextFieldWithBrowseButton;
import consulo.ui.ex.awt.util.Alarm;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.util.LabeledBuilder;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author Denis Zhdanov
 * @since 2013-04-24
 */
public class GradleProjectSettingsControl extends AbstractExternalProjectSettingsControl<GradleProjectSettings> {
    private static final long BALLOON_DELAY_MILLIS = TimeUnit.SECONDS.toMillis(1);

    @Nonnull
    private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

    @Nonnull
    private LocationSettingType myGradleHomeSettingType = LocationSettingType.UNKNOWN;

    @Nonnull
    private final GradleInstallationManager myInstallationManager;

    @SuppressWarnings("FieldCanBeLocal") // Used implicitly by reflection at disposeUIResources() and showUi()
    private JLabel myGradleHomeLabel;
    private TextFieldWithBrowseButton myGradleHomePathField;

    private JBRadioButton myUseWrapperButton;
    private JBRadioButton myUseLocalDistributionButton;

    private boolean myShowBalloonIfNecessary;

    private BundleBox myBundleBox;

    public GradleProjectSettingsControl(@Nonnull GradleProjectSettings initialSettings) {
        super(initialSettings);
        myInstallationManager = ServiceManager.getService(GradleInstallationManager.class);
    }

    @Override
    @RequiredUIAccess
    protected void fillExtraControls(@Nonnull Disposable uiDisposable, @Nonnull PaintAwarePanel content, int indentLevel) {
        content.setPaintCallback(graphics -> showBalloonIfNecessary());

        content.addPropertyChangeListener(evt -> {
            if (!"ancestor".equals(evt.getPropertyName())) {
                return;
            }

            // Configure the balloon to show on initial configurable drawing.
            myShowBalloonIfNecessary = evt.getNewValue() != null && evt.getOldValue() == null;

            if (evt.getNewValue() == null && evt.getOldValue() != null) {
                // Cancel delayed balloons when the configurable is hidden.
                myAlarm.cancelAllRequests();
            }
        });

        myGradleHomeLabel = new JBLabel(GradleLocalize.gradleSettingsTextHomePath().get());
        initGradleHome();

        initControls();

        content.add(myUseWrapperButton, ExternalSystemUiUtil.getFillLineConstraints(indentLevel));
        content.add(myUseLocalDistributionButton, ExternalSystemUiUtil.getFillLineConstraints(indentLevel));

        content.add(myGradleHomeLabel, ExternalSystemUiUtil.getLabelConstraints(indentLevel));
        content.add(myGradleHomePathField, ExternalSystemUiUtil.getFillLineConstraints(0));

        BundleBoxBuilder builder = BundleBoxBuilder.create(uiDisposable);
        builder.withNoneItem("Auto Select", PlatformIconGroup.actionsFind());
        builder.withSdkTypeFilterByClass(JavaSdkType.class);

        myBundleBox = builder.build();

        content.add(
            TargetAWT.to(LabeledBuilder.sided(LocalizeValue.localizeTODO("JRE"), myBundleBox)),
            ExternalSystemUiUtil.getFillLineConstraints(0)
        );
    }

    private void initControls() {
        ActionListener listener = e -> {
            boolean localDistributionEnabled = myUseLocalDistributionButton.isSelected();
            myGradleHomePathField.setEnabled(localDistributionEnabled);
            if (localDistributionEnabled) {
                if (myGradleHomePathField.getText().isEmpty()) {
                    deduceGradleHomeIfPossible();
                }
                else if (myInstallationManager.isGradleSdkHome(myGradleHomePathField.getText())) {
                    myGradleHomeSettingType = LocationSettingType.EXPLICIT_CORRECT;
                }
                else {
                    myGradleHomeSettingType = LocationSettingType.EXPLICIT_INCORRECT;
                    myShowBalloonIfNecessary = true;
                }
                showBalloonIfNecessary();
            }
            else {
                myAlarm.cancelAllRequests();
            }
        };

        myUseWrapperButton = new JBRadioButton(GradleLocalize.gradleSettingsTextUseDefault_wrapperConfigured().get());
        myUseWrapperButton.addActionListener(listener);

        myUseLocalDistributionButton = new JBRadioButton(GradleLocalize.gradleSettingsTextUseLocalDistribution().get());
        myUseLocalDistributionButton.addActionListener(listener);

        ButtonGroup buttonGroup = new ButtonGroup();
        buttonGroup.add(myUseWrapperButton);
        buttonGroup.add(myUseLocalDistributionButton);
    }

    private void initGradleHome() {
        myGradleHomePathField = new TextFieldWithBrowseButton();

        FileChooserDescriptor fileChooserDescriptor = GradleUtil.getGradleHomeFileChooserDescriptor();

        myGradleHomePathField.addBrowseFolderListener(
            "",
            GradleLocalize.gradleSettingsTextHomePath().get(),
            null,
            fileChooserDescriptor,
            TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT,
            false
        );
        myGradleHomePathField.getTextField().getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                myGradleHomePathField.getTextField().setForeground(LocationSettingType.EXPLICIT_CORRECT.getColor());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                myGradleHomePathField.getTextField().setForeground(LocationSettingType.EXPLICIT_CORRECT.getColor());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
            }
        });
    }

    @Override
    public boolean validate(@Nonnull GradleProjectSettings settings) throws ConfigurationException {
        String gradleHomePath = FileUtil.toCanonicalPath(myGradleHomePathField.getText());
        if (myUseLocalDistributionButton.isSelected()) {
            if (StringUtil.isEmpty(gradleHomePath)) {
                myGradleHomeSettingType = LocationSettingType.UNKNOWN;
                throw new ConfigurationException(GradleBundle.message("gradle.home.setting.type.explicit.empty", gradleHomePath));
            }
            else if (!myInstallationManager.isGradleSdkHome(new File(gradleHomePath))) {
                myGradleHomeSettingType = LocationSettingType.EXPLICIT_INCORRECT;
                new DelayedBalloonInfo(NotificationType.ERROR, myGradleHomeSettingType, 0).run();
                throw new ConfigurationException(GradleLocalize.gradleHomeSettingTypeExplicitIncorrect(gradleHomePath).get());
            }
        }
        return true;
    }

    @Override
    protected void applyExtraSettings(@Nonnull GradleProjectSettings settings) {
        String gradleHomePath = FileUtil.toCanonicalPath(myGradleHomePathField.getText());
        if (StringUtil.isEmpty(gradleHomePath)) {
            settings.setGradleHome(null);
        }
        else {
            settings.setGradleHome(gradleHomePath);
            GradleUtil.storeLastUsedGradleHome(gradleHomePath);
        }

        if (myUseLocalDistributionButton.isSelected()) {
            settings.setDistributionType(DistributionType.LOCAL);
        }
        else if (myUseWrapperButton.isSelected()) {
            settings.setDistributionType(DistributionType.DEFAULT_WRAPPED);
        }

        settings.setJreName(myBundleBox.getSelectedBundleName());
    }

    @Override
    protected void updateInitialExtraSettings() {
        String gradleHomePath = FileUtil.toCanonicalPath(myGradleHomePathField.getText());
        getInitialSettings().setGradleHome(StringUtil.isEmpty(gradleHomePath) ? null : gradleHomePath);
        if (myUseLocalDistributionButton.isSelected()) {
            getInitialSettings().setDistributionType(DistributionType.LOCAL);
        }
        else if (myUseWrapperButton.isSelected()) {
            getInitialSettings().setDistributionType(DistributionType.DEFAULT_WRAPPED);
        }
    }

    @Override
    protected boolean isExtraSettingModified() {
        DistributionType distributionType = getInitialSettings().getDistributionType();
        if (myUseWrapperButton.isSelected() && distributionType != DistributionType.DEFAULT_WRAPPED) {
            return true;
        }

        if (myUseLocalDistributionButton.isSelected() && distributionType != DistributionType.LOCAL) {
            return true;
        }

        if (!Objects.equals(myBundleBox.getSelectedBundleName(), getInitialSettings().getJreName())) {
            return true;
        }

        String gradleHome = FileUtil.toCanonicalPath(myGradleHomePathField.getText());
        if (StringUtil.isEmpty(gradleHome)) {
            return !StringUtil.isEmpty(getInitialSettings().getGradleHome());
        }
        else {
            return !gradleHome.equals(getInitialSettings().getGradleHome());
        }
    }

    @Override
    @RequiredUIAccess
    protected void resetExtraSettings(boolean isDefaultModuleCreation) {
        String gradleHome = getInitialSettings().getGradleHome();
        myGradleHomePathField.setText(gradleHome == null ? "" : gradleHome);
        myGradleHomePathField.getTextField().setForeground(LocationSettingType.EXPLICIT_CORRECT.getColor());

        myBundleBox.setSelectedBundle(getInitialSettings().getJreName());

        updateWrapperControls(getInitialSettings().getExternalProjectPath(), isDefaultModuleCreation);
        if (!myUseLocalDistributionButton.isSelected()) {
            myGradleHomePathField.setEnabled(false);
            return;
        }

        if (StringUtil.isEmpty(gradleHome)) {
            myGradleHomeSettingType = LocationSettingType.UNKNOWN;
            deduceGradleHomeIfPossible();
        }
        else {
            myGradleHomeSettingType = myInstallationManager.isGradleSdkHome(new File(gradleHome))
                ? LocationSettingType.EXPLICIT_CORRECT
                : LocationSettingType.EXPLICIT_INCORRECT;
            myAlarm.cancelAllRequests();
            if (myGradleHomeSettingType == LocationSettingType.EXPLICIT_INCORRECT
                && getInitialSettings().getDistributionType() == DistributionType.LOCAL) {
                new DelayedBalloonInfo(NotificationType.ERROR, myGradleHomeSettingType, 0).run();
            }
        }
    }

    public void updateWrapperControls(@Nullable String linkedProjectPath, boolean isDefaultModuleCreation) {
        if (StringUtil.isEmpty(linkedProjectPath) && !isDefaultModuleCreation) {
            myUseLocalDistributionButton.setSelected(true);
            myGradleHomePathField.setEnabled(true);
            return;
        }

        final boolean isGradleDefaultWrapperFilesExist = GradleUtil.isGradleDefaultWrapperFilesExist(linkedProjectPath);
        if (isGradleDefaultWrapperFilesExist || isDefaultModuleCreation) {
            myUseWrapperButton.setEnabled(true);
            myUseWrapperButton.setSelected(true);
            myGradleHomePathField.setEnabled(false);
            myUseWrapperButton.setText(GradleLocalize.gradleSettingsTextUseDefault_wrapperConfigured().get());
        }
        else {
            myUseWrapperButton.setEnabled(false);
            myUseLocalDistributionButton.setSelected(true);
            myGradleHomePathField.setEnabled(true);
            myUseWrapperButton.setText(GradleLocalize.gradleSettingsTextUseDefault_wrapperNot_configured().get());
        }

        if (getInitialSettings().getDistributionType() == null) {
            return;
        }

        switch (getInitialSettings().getDistributionType()) {
            case LOCAL:
                myGradleHomePathField.setEnabled(true);
                myUseLocalDistributionButton.setSelected(true);
                break;
            case DEFAULT_WRAPPED:
                myGradleHomePathField.setEnabled(false);
                myUseWrapperButton.setSelected(true);
                myUseWrapperButton.setEnabled(true);
                break;
        }
    }

    /**
     * Updates GUI of the gradle configurable in order to show deduced path to gradle (if possible).
     */
    private void deduceGradleHomeIfPossible() {
        File gradleHome = myInstallationManager.getAutodetectedGradleHome();
        if (gradleHome == null) {
            new DelayedBalloonInfo(NotificationType.WARNING, LocationSettingType.UNKNOWN, BALLOON_DELAY_MILLIS).run();
            return;
        }
        myGradleHomeSettingType = LocationSettingType.DEDUCED;
        new DelayedBalloonInfo(NotificationType.INFO, LocationSettingType.DEDUCED, BALLOON_DELAY_MILLIS).run();
        myGradleHomePathField.setText(gradleHome.getPath());
        myGradleHomePathField.getTextField().setForeground(LocationSettingType.DEDUCED.getColor());
    }

    void showBalloonIfNecessary() {
        if (!myShowBalloonIfNecessary || !myGradleHomePathField.isEnabled()) {
            return;
        }
        myShowBalloonIfNecessary = false;
        NotificationType messageType = null;
        switch (myGradleHomeSettingType) {
            case DEDUCED:
                messageType = NotificationType.INFO;
                break;
            case EXPLICIT_INCORRECT:
            case UNKNOWN:
                messageType = NotificationType.ERROR;
                break;
            default:
        }
        if (messageType != null) {
            new DelayedBalloonInfo(messageType, myGradleHomeSettingType, BALLOON_DELAY_MILLIS).run();
        }
    }

    private class DelayedBalloonInfo implements Runnable {
        private final NotificationType myMessageType;
        private final String myText;
        private final long myTriggerTime;

        DelayedBalloonInfo(@Nonnull NotificationType messageType, @Nonnull LocationSettingType settingType, long delayMillis) {
            myMessageType = messageType;
            myText = settingType.getDescription(GradleConstants.SYSTEM_ID);
            myTriggerTime = System.currentTimeMillis() + delayMillis;
        }

        @Override
        public void run() {
            long diff = myTriggerTime - System.currentTimeMillis();
            if (diff > 0) {
                myAlarm.cancelAllRequests();
                myAlarm.addRequest(this, diff);
                return;
            }
            if (myGradleHomePathField == null || !myGradleHomePathField.isShowing()) {
                // Don't schedule the balloon if the configurable is hidden.
                return;
            }
            ExternalSystemUiUtil.showBalloon(myGradleHomePathField, myMessageType, myText);
        }
    }
}

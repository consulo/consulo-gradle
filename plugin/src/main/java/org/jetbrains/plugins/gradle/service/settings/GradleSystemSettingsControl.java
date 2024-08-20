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

import consulo.configurable.ConfigurationException;
import consulo.externalSystem.service.execution.ExternalSystemSettingsControl;
import consulo.externalSystem.ui.awt.ExternalSystemUiUtil;
import consulo.externalSystem.ui.awt.PaintAwarePanel;
import consulo.externalSystem.util.ExternalSystemApiUtil;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.gradle.GradleConstants;
import consulo.gradle.localize.GradleLocalize;
import consulo.ide.impl.idea.openapi.externalSystem.model.settings.LocationSettingType;
import consulo.platform.Platform;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.JBTextField;
import consulo.ui.ex.awt.TextComponentAccessor;
import consulo.ui.ex.awt.TextFieldWithBrowseButton;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.io.File;

/**
 * Manages gradle settings not specific to particular project (e.g. 'use wrapper' is project-level setting but 'gradle user home' is
 * a global one).
 *
 * @author Denis Zhdanov
 * @since 2013-04-28
 */
public class GradleSystemSettingsControl implements ExternalSystemSettingsControl<GradleSettings> {
    @Nonnull
    private final GradleSettings myInitialSettings;

    @SuppressWarnings("FieldCanBeLocal") // Used by reflection at showUi() and disposeUiResources()
    private JBLabel myServiceDirectoryLabel;
    private TextFieldWithBrowseButton myServiceDirectoryPathField;
    @SuppressWarnings("FieldCanBeLocal")  // Used by reflection at showUi() and disposeUiResources()
    private JBLabel myGradleVmOptionsLabel;
    private JBTextField myGradleVmOptionsField;
    private boolean myServiceDirectoryPathModifiedByUser;

    public GradleSystemSettingsControl(@Nonnull GradleSettings settings) {
        myInitialSettings = settings;
    }

    @Override
    public void fillUi(@Nonnull PaintAwarePanel canvas, int indentLevel) {
        myServiceDirectoryLabel = new JBLabel(GradleLocalize.gradleSettingsTextServiceDirPath().get());
        preparePathControl();
        canvas.add(myServiceDirectoryLabel, ExternalSystemUiUtil.getLabelConstraints(indentLevel));
        canvas.add(myServiceDirectoryPathField, ExternalSystemUiUtil.getFillLineConstraints(indentLevel));

        myGradleVmOptionsLabel = new JBLabel(GradleLocalize.gradleSettingsTextVmOptions().get());
        canvas.add(myGradleVmOptionsLabel, ExternalSystemUiUtil.getLabelConstraints(indentLevel));
        myGradleVmOptionsField = new JBTextField();
        canvas.add(myGradleVmOptionsField, ExternalSystemUiUtil.getFillLineConstraints(indentLevel));
    }

    @Override
    public void showUi(boolean show) {
        ExternalSystemUiUtil.showUi(this, show);
    }

    private void preparePathControl() {
        myServiceDirectoryPathField = new TextFieldWithBrowseButton();
        myServiceDirectoryPathField.addBrowseFolderListener(
            "",
            GradleLocalize.gradleSettingsTitleServiceDirPath().get(),
            null,
            new FileChooserDescriptor(false, true, false, false, false, false),
            TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT,
            false
        );
        myServiceDirectoryPathField.getTextField().getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                myServiceDirectoryPathModifiedByUser = true;
                myServiceDirectoryPathField.getTextField().setForeground(LocationSettingType.EXPLICIT_CORRECT.getColor());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                myServiceDirectoryPathModifiedByUser = true;
                myServiceDirectoryPathField.getTextField().setForeground(LocationSettingType.EXPLICIT_CORRECT.getColor());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
            }
        });
    }

    @Override
    public void reset() {
        myServiceDirectoryPathField.getTextField().setForeground(LocationSettingType.EXPLICIT_CORRECT.getColor());
        myServiceDirectoryPathField.setText("");
        String path = myInitialSettings.getServiceDirectoryPath();
        if (StringUtil.isEmpty(path)) {
            deduceServiceDirectoryIfPossible();
        }
        else {
            myServiceDirectoryPathField.setText(path);
        }

        myGradleVmOptionsField.setText(trimIfPossible(myInitialSettings.getGradleVmOptions()));
    }

    private void deduceServiceDirectoryIfPossible() {
        String path = Platform.current().os().getEnvironmentVariable(GradleConstants.SYSTEM_DIRECTORY_PATH_KEY);
        if (StringUtil.isEmpty(path)) {
            path = new File(Platform.current().jvm().getRuntimeProperty("user.home"), ".gradle").getAbsolutePath();
        }
        myServiceDirectoryPathField.setText(path);
        myServiceDirectoryPathField.getTextField().setForeground(LocationSettingType.DEDUCED.getColor());
        myServiceDirectoryPathModifiedByUser = false;
    }

    @Override
    public boolean isModified() {
        return (myServiceDirectoryPathModifiedByUser
            && !Comparing.equal(
                ExternalSystemApiUtil.normalizePath(myServiceDirectoryPathField.getText()),
                ExternalSystemApiUtil.normalizePath(myInitialSettings.getServiceDirectoryPath())
            ))
            || !Comparing.equal(trimIfPossible(myGradleVmOptionsField.getText()), trimIfPossible(myInitialSettings.getGradleVmOptions()));
    }

    @Nullable
    private static String trimIfPossible(@Nullable String s) {
        if (s == null) {
            return null;
        }
        String result = s.trim();
        return result.isEmpty() ? null : result;
    }

    @Override
    public void apply(@Nonnull GradleSettings settings) {
        if (myServiceDirectoryPathModifiedByUser) {
            settings.setServiceDirectoryPath(ExternalSystemApiUtil.normalizePath(myServiceDirectoryPathField.getText()));
        }
        settings.setGradleVmOptions(trimIfPossible(myGradleVmOptionsField.getText()));
    }

    @Override
    public boolean validate(@Nonnull GradleSettings settings) throws ConfigurationException {
        return true;
    }

    @Override
    public void disposeUIResources() {
        ExternalSystemUiUtil.disposeUi(this);
    }
}

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
package org.jetbrains.plugins.gradle.codeInsight.actions;

import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.annotation.component.ActionRefAnchor;
import consulo.application.AllIcons;
import consulo.codeEditor.Editor;
import consulo.gradle.GradleConstants;
import consulo.gradle.GradleDocumentationBundle;
import consulo.gradle.localize.GradleLocalize;
import consulo.language.editor.action.CodeInsightAction;
import consulo.language.editor.action.CodeInsightActionHandler;
import consulo.language.psi.PsiCompiledElement;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import org.jetbrains.plugins.groovy.GroovyFileType;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 10/22/13
 */
@ActionImpl(id = "AddGradleDslPluginAction", parents = @ActionParentRef(value = @ActionRef(id = "GenerateGroup"), anchor = ActionRefAnchor.FIRST))
public class AddGradleDslPluginAction extends CodeInsightAction {
    private final Pair[] myPlugins;

    public AddGradleDslPluginAction() {
        getTemplatePresentation().setDescriptionValue(GradleLocalize.gradleCodeinsightActionApply_pluginDescription());
        getTemplatePresentation().setTextValue(GradleLocalize.gradleCodeinsightActionApply_pluginText());
        getTemplatePresentation().setIcon(AllIcons.Nodes.Plugin);

        final List<String> plugins = StringUtil.split(
            "java,groovy,idea,eclipse,scala,antlr,application,ear,jetty,maven,osgi,war,announce," +
                "build-announcements,checkstyle,codenarc,eclipse-wtp,findbugs,jdepend,pmd,project-report,signing,sonar", ",");

        myPlugins = new Pair[plugins.size()];
        ContainerUtil.map2Array(plugins, myPlugins, o -> createPluginKey(o));
        Arrays.sort(myPlugins, (o1, o2) -> String.valueOf(o1.getFirst()).compareTo(String.valueOf(o2.getFirst())));
    }

    @Nonnull
    @Override
    protected CodeInsightActionHandler getHandler() {
        return new AddGradleDslPluginActionHandler(myPlugins);
    }

    @Override
    protected boolean isValidForFile(@Nonnull Project project, @Nonnull Editor editor, @Nonnull PsiFile file) {
        if (file instanceof PsiCompiledElement || !GroovyFileType.GROOVY_FILE_TYPE.equals(file.getFileType())) {
            return false;
        }
        return !GradleConstants.SETTINGS_FILE_NAME.equals(file.getName()) && file.getName().endsWith(GradleConstants.EXTENSION);
    }

    @Nonnull
    private static Pair<String, String> createPluginKey(@Nonnull String pluginName) {
        String description = GradleDocumentationBundle.messageOrDefault(
            String.format("gradle.documentation.org.gradle.api.Project.apply.plugin.%s.non-html", pluginName), "");
        return Pair.create(pluginName, description);
    }
}

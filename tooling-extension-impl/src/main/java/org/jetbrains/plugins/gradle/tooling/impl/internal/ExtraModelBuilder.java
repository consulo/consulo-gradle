/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.tooling.impl.internal;

import org.gradle.api.Project;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.util.GradleVersion;
import org.jetbrains.plugins.gradle.tooling.impl.ErrorMessageBuilder;
import org.jetbrains.plugins.gradle.tooling.impl.ModelBuilderService;
import org.jetbrains.plugins.gradle.tooling.impl.annotation.TargetVersions;

import java.util.ServiceLoader;

/**
 * @author Vladislav.Soroka
 * @since 11/5/13
 */
@SuppressWarnings("UnusedDeclaration")
public class ExtraModelBuilder implements ToolingModelBuilder {
    private static final String RANGE_TOKEN = " <=> ";
    private static ServiceLoader<ModelBuilderService> buildersLoader =
        ServiceLoader.load(ModelBuilderService.class, ExtraModelBuilder.class.getClassLoader());

    private final GradleVersion myCurrentGradleVersion;

    public ExtraModelBuilder() {
        this.myCurrentGradleVersion = GradleVersion.current();
    }

    public ExtraModelBuilder(GradleVersion gradleVersion) {
        this.myCurrentGradleVersion = gradleVersion;
    }

    @Override
    public boolean canBuild(String modelName) {
        for (ModelBuilderService service : buildersLoader) {
            if (service.canBuild(modelName) && isVersionMatch(service)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object buildAll(String modelName, Project project) {
        for (ModelBuilderService service : buildersLoader) {
            if (service.canBuild(modelName) && isVersionMatch(service)) {
                try {
                    return service.buildAll(modelName, project);
                }
                catch (Exception e) {
                    ErrorMessageBuilder builderError = service.getErrorMessageBuilder(project, e);
                    project.getLogger().error(builderError.build());
                }
                return null;
            }
        }
        throw new IllegalArgumentException("Unsupported model: " + modelName);
    }

    private boolean isVersionMatch(ModelBuilderService builderService) {
        TargetVersions targetVersions = builderService.getClass().getAnnotation(TargetVersions.class);
        if (targetVersions == null || targetVersions.value().isEmpty()) {
            return true;
        }

        final GradleVersion current = adjust(myCurrentGradleVersion, targetVersions.checkBaseVersions());

        if (targetVersions.value().endsWith("+")) {
            String minVersion = targetVersions.value().substring(0, targetVersions.value().length() - 1);
            return compare(current, minVersion, targetVersions.checkBaseVersions()) >= 0;
        }
        else {
            final int rangeIndex = targetVersions.value().indexOf(RANGE_TOKEN);
            if (rangeIndex != -1) {
                String minVersion = targetVersions.value().substring(0, rangeIndex);
                String maxVersion = targetVersions.value().substring(rangeIndex + RANGE_TOKEN.length());
                return compare(current, minVersion, targetVersions.checkBaseVersions()) >= 0 &&
                    compare(current, maxVersion, targetVersions.checkBaseVersions()) <= 0;
            }
            else {
                return compare(current, targetVersions.value(), targetVersions.checkBaseVersions()) == 0;
            }
        }
    }

    private static int compare(GradleVersion gradleVersion, String otherGradleVersion, boolean checkBaseVersions) {
        return gradleVersion.compareTo(adjust(GradleVersion.version(otherGradleVersion), checkBaseVersions));
    }

    private static GradleVersion adjust(GradleVersion version, boolean checkBaseVersions) {
        return checkBaseVersions ? version.getBaseVersion() : version;
    }
}

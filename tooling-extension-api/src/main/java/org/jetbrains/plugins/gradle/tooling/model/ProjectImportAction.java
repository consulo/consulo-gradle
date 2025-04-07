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
package org.jetbrains.plugins.gradle.tooling.model;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.idea.BasicIdeaProject;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;

import java.io.Serializable;
import java.util.*;

/**
 * @author Vladislav.Soroka
 * @since 10/14/13
 */
public class ProjectImportAction implements BuildAction<ProjectImportAction.AllModels>, Serializable {
    private static final long serialVersionUID = 257773272670650022L;

    private final Set<Class> myExtraProjectModelClasses = new HashSet<Class>();
    private final boolean myIsPreviewMode;

    public ProjectImportAction(boolean isPreviewMode) {
        myIsPreviewMode = isPreviewMode;
    }

    public void addExtraProjectModelClasses(Set<Class> projectModelClasses) {
        myExtraProjectModelClasses.addAll(projectModelClasses);
    }

    @Override
    public AllModels execute(final BuildController controller) {
        //outer conditional is needed to be compatible with 1.8
        final IdeaProject ideaProject = myIsPreviewMode ? controller.getModel(BasicIdeaProject.class) : controller.getModel(IdeaProject.class);
        if (ideaProject == null || ideaProject.getModules().isEmpty()) {
            return null;
        }

        AllModels allModels = new AllModels(ideaProject);

        // TODO ask gradle guys why there is always null got for BuildEnvironment model
        //allModels.setBuildEnvironment(controller.findModel(BuildEnvironment.class));

        addExtraProject(controller, allModels, null);
        for (IdeaModule module : ideaProject.getModules()) {
            addExtraProject(controller, allModels, module);
        }

        return allModels;
    }

    private void addExtraProject(BuildController controller, AllModels allModels, IdeaModule model) {
        for (Class aClass : myExtraProjectModelClasses) {
            try {
                Object extraProject = controller.findModel(model, aClass);
                if (extraProject == null) {
                    continue;
                }
                allModels.addExtraProject(extraProject, aClass, model);
            }
            catch (Exception e) {
                // do not fail project import in a preview mode
                if (!myIsPreviewMode) {
                    throw e;
                }
            }
        }
    }

    public static class AllModels implements Serializable {
        private static final long serialVersionUID = -5856781288747689870L;

        private final Map<String, Object> projectsByPath = new HashMap<String, Object>();
        private final IdeaProject myIdeaProject;

        private BuildEnvironment myBuildEnvironment;

        public AllModels(IdeaProject project) {
            myIdeaProject = project;
        }

        public IdeaProject getIdeaProject() {
            return myIdeaProject;
        }


        public BuildEnvironment getBuildEnvironment() {
            return myBuildEnvironment;
        }

        public void setBuildEnvironment(BuildEnvironment buildEnvironment) {
            myBuildEnvironment = buildEnvironment;
        }


        public <T> T getExtraProject(Class<T> modelClazz) {
            return getExtraProject(null, modelClazz);
        }

        public <T> T getExtraProject(IdeaModule module, Class<T> modelClazz) {
            Object extraProject = projectsByPath.get(extractMapKey(modelClazz, module));
            if (modelClazz.isInstance(extraProject)) {
                //noinspection unchecked
                return (T) extraProject;
            }
            return null;
        }

        /**
         * Return collection path of modules provides the model
         *
         * @param modelClazz extra project model
         * @return modules path collection
         */
        public Collection<String> findModulesWithModel(Class modelClazz) {
            List<String> modules = new ArrayList<String>();
            for (Map.Entry<String, Object> set : projectsByPath.entrySet()) {
                if (modelClazz.isInstance(set.getValue())) {
                    modules.add(extractModulePath(modelClazz, set.getKey()));
                }
            }
            return modules;
        }

        public void addExtraProject(Object project, Class modelClazz) {
            projectsByPath.put(extractMapKey(modelClazz, null), project);
        }

        public void addExtraProject(Object project, Class modelClazz, IdeaModule module) {
            projectsByPath.put(extractMapKey(modelClazz, module), project);
        }

        private String extractMapKey(Class modelClazz, IdeaModule module) {
            return modelClazz.getName() + '@' + (module != null ? module.getGradleProject().getPath() : "root" + myIdeaProject.getName().hashCode());
        }

        private static String extractModulePath(Class modelClazz, String key) {
            return key.replaceFirst(modelClazz.getName() + '@', "");
        }

        @Override
        public String toString() {
            return "AllModels{" +
                "projectsByPath=" + projectsByPath +
                ", myIdeaProject=" + myIdeaProject +
                '}';
        }
    }
}

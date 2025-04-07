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
package org.jetbrains.plugins.gradle.tooling.impl.builder;

import com.google.gson.GsonBuilder;
import consulo.externalSystem.rt.model.*;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.ContentFilterable;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.util.PatternFilterable;
import org.jetbrains.plugins.gradle.tooling.impl.ErrorMessageBuilder;
import org.jetbrains.plugins.gradle.tooling.impl.ModelBuilderService;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ExternalProjectBuilderImpl implements ModelBuilderService {

    private final Map<String, ExternalProject> cache = new ConcurrentHashMap<>();

    @Override
    public boolean canBuild(String modelName) {
        return ExternalProject.class.getName().equals(modelName);
    }

    @Override
    public Object buildAll(final String modelName, final Project project) {
        ExternalProject externalProject = cache.get(project.getPath());
        if (externalProject != null) {
            return externalProject;
        }

        DefaultExternalProject defaultExternalProject = new DefaultExternalProject();
        defaultExternalProject.setExternalSystemId("GRADLE");
        defaultExternalProject.setName(project.getName());
        defaultExternalProject.setQName(":".equals(project.getPath()) ? project.getName() : project.getPath());
        defaultExternalProject.setVersion(wrap(project.getVersion()));
        defaultExternalProject.setDescription(project.getDescription());
        defaultExternalProject.setBuildDir(project.getBuildDir());
        defaultExternalProject.setBuildFile(project.getBuildFile());
        defaultExternalProject.setGroup(wrap(project.getGroup()));
        defaultExternalProject.setProjectDir(project.getProjectDir());
        defaultExternalProject.setSourceSets(getSourceSets(project));
        defaultExternalProject.setTasks(getTasks(project));
        defaultExternalProject.setPlugins(getPlugins(project));
        // defaultExternalProject.setProperties(project.getProperties());

        Map<String, ExternalProject> childProjects = new HashMap<>(project.getChildProjects().size());
        for (Map.Entry<String, Project> projectEntry : project.getChildProjects().entrySet()) {
            Object externalProjectChild = buildAll(modelName, projectEntry.getValue());
            if (externalProjectChild instanceof ExternalProject) {
                childProjects.put(projectEntry.getKey(), (ExternalProject) externalProjectChild);
            }
        }
        defaultExternalProject.setChildProjects(childProjects);
        cache.put(project.getPath(), defaultExternalProject);

        return defaultExternalProject;
    }

    public static Map<String, ExternalPlugin> getPlugins(Project project) {
        Map<String, ExternalPlugin> result = new HashMap<>();
        // Assuming project.getConvention().getPlugins() returns a Map<String, Object>
        Map<String, Object> plugins = project.getConvention().getPlugins();
        for (Map.Entry<String, Object> entry : plugins.entrySet()) {
            DefaultExternalPlugin externalPlugin = new DefaultExternalPlugin();
            externalPlugin.setId(entry.getKey());
            result.put(entry.getKey(), externalPlugin);
        }
        return result;
    }

    public static Map<String, ExternalTask> getTasks(Project project) {
        Map<String, ExternalTask> result = new HashMap<>();
        for (Task task : project.getTasks()) {
            DefaultExternalTask externalTask = new DefaultExternalTask();
            externalTask.setName(task.getName());
            externalTask.setDescription(task.getDescription());
            externalTask.setGroup(task.getGroup());
            externalTask.setQName(task.getPath());
            result.put(externalTask.getQName(), externalTask);
        }
        return result;
    }

    public static Map<String, ExternalSourceSet> getSourceSets(Project project) {
        Map<String, ExternalSourceSet> result = new HashMap<>();
        if (!project.hasProperty("sourceSets") || !(project.property("sourceSets") instanceof SourceSetContainer)) {
            return result;
        }
        SourceSetContainer sourceSets = (SourceSetContainer) project.property("sourceSets");

        Object[] resourcesFilters = getFilters(project, "processResources");
        List resourcesIncludes = (List) resourcesFilters[0];
        List resourcesExcludes = (List) resourcesFilters[1];
        List filterReaders = (List) resourcesFilters[2];

        Object[] testResourcesFilters = getFilters(project, "processTestResources");
        List testResourcesIncludes = (List) testResourcesFilters[0];
        List testResourcesExcludes = (List) testResourcesFilters[1];
        List testFilterReaders = (List) testResourcesFilters[2];

        for (SourceSet sourceSet : sourceSets) {
            DefaultExternalSourceSet externalSourceSet = new DefaultExternalSourceSet();
            externalSourceSet.setName(sourceSet.getName());

            Map<IExternalSystemSourceType, ExternalSourceDirectorySet> sources = new HashMap<>();

            DefaultExternalSourceDirectorySet resourcesDirectorySet = new DefaultExternalSourceDirectorySet();
            resourcesDirectorySet.setName(sourceSet.getResources().getName());
            resourcesDirectorySet.setSrcDirs(sourceSet.getResources().getSrcDirs());
            resourcesDirectorySet.setOutputDir(sourceSet.getOutput().getResourcesDir());

            DefaultExternalSourceDirectorySet javaDirectorySet = new DefaultExternalSourceDirectorySet();
            javaDirectorySet.setName(sourceSet.getAllJava().getName());
            javaDirectorySet.setSrcDirs(sourceSet.getAllJava().getSrcDirs());
            javaDirectorySet.setOutputDir(sourceSet.getOutput().getClassesDirs().getSingleFile());
            // javaDirectorySet.setExcludes(concatLists(javaExcludes, new ArrayList<>(sourceSet.getJava().getExcludes())));
            // javaDirectorySet.setIncludes(concatLists(javaIncludes, new ArrayList<>(sourceSet.getJava().getIncludes())));

            if (SourceSet.TEST_SOURCE_SET_NAME.equals(sourceSet.getName())) {
                resourcesDirectorySet.setExcludes(concatLists(testResourcesExcludes, new ArrayList<>(sourceSet.getResources().getExcludes())));
                resourcesDirectorySet.setIncludes(concatLists(testResourcesIncludes, new ArrayList<>(sourceSet.getResources().getIncludes())));
                resourcesDirectorySet.setFilters(testFilterReaders);
                sources.put(ExternalSystemSourceType.TEST, javaDirectorySet);
                sources.put(ExternalSystemSourceType.TEST_RESOURCE, resourcesDirectorySet);
            }
            else {
                resourcesDirectorySet.setExcludes(concatLists(resourcesExcludes, new ArrayList<>(sourceSet.getResources().getExcludes())));
                resourcesDirectorySet.setIncludes(concatLists(resourcesIncludes, new ArrayList<>(sourceSet.getResources().getIncludes())));
                resourcesDirectorySet.setFilters(filterReaders);
                sources.put(ExternalSystemSourceType.SOURCE, javaDirectorySet);
                sources.put(ExternalSystemSourceType.RESOURCE, resourcesDirectorySet);
            }

            externalSourceSet.setSources(sources);
            result.put(sourceSet.getName(), externalSourceSet);
        }
        return result;
    }

    private static <T> Set<T> concatLists(List<T> list1, List<T> list2) {
        Set<T> result = new HashSet<T>();
        if (list1 != null) {
            result.addAll(list1);
        }
        if (list2 != null) {
            result.addAll(list2);
        }
        return result;
    }

    public static Object[] getFilters(Project project, String taskName) {
        List<String> includes = new ArrayList<>();
        List<String> excludes = new ArrayList<>();
        List<ExternalFilter> filterReaders = new ArrayList<>();

        Task filterableTask = project.getTasks().findByName(taskName);
        if (filterableTask instanceof PatternFilterable) {
            PatternFilterable patternTask = (PatternFilterable) filterableTask;
            includes.addAll(patternTask.getIncludes());
            excludes.addAll(patternTask.getExcludes());
        }

        if (Boolean.parseBoolean(System.getProperty("idea.disable.gradle.resource.filtering", "false"))) {
            return new Object[]{includes, excludes, filterReaders};
        }

        try {
            if (filterableTask instanceof ContentFilterable) {
                // Check if getMainSpec method exists via reflection.
                try {
                    Method getMainSpec = filterableTask.getClass().getMethod("getMainSpec");
                    if (getMainSpec != null) {
                        Object mainSpec = getMainSpec.invoke(filterableTask);
                        Field propertiesField = mainSpec.getClass().getField("properties");
                        Object properties = propertiesField.get(mainSpec);
                        Object copyActions = null;
                        try {
                            Field allCopyActionsField = properties.getClass().getField("allCopyActions");
                            copyActions = allCopyActionsField.get(properties);
                        }
                        catch (NoSuchFieldException nsfe) {
                            try {
                                Field copyActionsField = properties.getClass().getField("copyActions");
                                copyActions = copyActionsField.get(properties);
                            }
                            catch (NoSuchFieldException nsfe2) {
                                // leave copyActions as null
                            }
                        }
                        if (copyActions instanceof Iterable) {
                            for (Object actionObj : (Iterable) copyActions) {
                                try {
                                    Field filterTypeField = actionObj.getClass().getDeclaredField("val$filterType");
                                    Field propertiesField2 = actionObj.getClass().getDeclaredField("val$properties");
                                    filterTypeField.setAccessible(true);
                                    propertiesField2.setAccessible(true);
                                    Class<?> filterType = (Class<?>) filterTypeField.get(actionObj);
                                    String filterTypeName = filterType.getName();
                                    DefaultExternalFilter filter = new DefaultExternalFilter();
                                    filter.setFilterType(filterTypeName);
                                    Object props = propertiesField2.get(actionObj);
                                    if (props != null) {
                                        String json = new GsonBuilder().create().toJson(props);
                                        filter.setPropertiesAsJsonMap(json);
                                    }
                                    filterReaders.add(filter);
                                }
                                catch (NoSuchFieldException e) {
                                    if ("RenamingCopyAction".equals(actionObj.getClass().getSimpleName())) {
                                        try {
                                            Field transformerField = actionObj.getClass().getDeclaredField("transformer");
                                            transformerField.setAccessible(true);
                                            Object transformer = transformerField.get(actionObj);
                                            Field matcherField = transformer.getClass().getDeclaredField("matcher");
                                            Field replacementField = transformer.getClass().getDeclaredField("replacement");
                                            matcherField.setAccessible(true);
                                            replacementField.setAccessible(true);
                                            Object matcher = matcherField.get(transformer);
                                            Object replacement = replacementField.get(transformer);
                                            if (matcher != null && replacement != null) {
                                                Method patternMethod = matcher.getClass().getMethod("pattern");
                                                Object patternObj = patternMethod.invoke(matcher);
                                                String pattern = patternObj != null ? patternObj.toString() : "";
                                                String replacementStr = replacement.toString();
                                                DefaultExternalFilter filter = new DefaultExternalFilter();
                                                filter.setFilterType("RenamingCopyFilter");
                                                if (!pattern.isEmpty() && !replacementStr.isEmpty()) {
                                                    Map<String, String> map = new HashMap<>();
                                                    map.put("pattern", pattern);
                                                    map.put("replacement", replacementStr);
                                                    String json = new GsonBuilder().create().toJson(map);
                                                    filter.setPropertiesAsJsonMap(json);
                                                    filterReaders.add(filter);
                                                }
                                            }
                                        }
                                        catch (Exception ex) {
                                            // Ignore any exceptions for this action.
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                catch (NoSuchMethodException nsme) {
                    // getMainSpec not available; ignore.
                }
            }
        }
        catch (Exception ignore) {
            // Ignore any exceptions.
        }

        return new Object[]{includes, excludes, filterReaders};
    }

    private static String wrap(Object o) {
        return o instanceof CharSequence ? o.toString() : "";
    }

    @Override
    public ErrorMessageBuilder getErrorMessageBuilder(Project project, Exception e) {
        return ErrorMessageBuilder.create(project, e, "Project resolve errors")
            .withDescription("Unable to resolve additional project configuration.");
    }
}

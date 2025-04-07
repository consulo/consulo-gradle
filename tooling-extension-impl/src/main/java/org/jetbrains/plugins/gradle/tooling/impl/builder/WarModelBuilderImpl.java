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

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.plugins.WarPlugin;
import org.gradle.api.tasks.bundling.War;
import org.gradle.util.GradleVersion;
import org.jetbrains.plugins.gradle.tooling.impl.ErrorMessageBuilder;
import org.jetbrains.plugins.gradle.tooling.impl.ModelBuilderService;
import org.jetbrains.plugins.gradle.tooling.impl.internal.web.WarModelImpl;
import org.jetbrains.plugins.gradle.tooling.impl.internal.web.WebConfigurationImpl;
import org.jetbrains.plugins.gradle.tooling.impl.internal.web.WebResourceImpl;
import org.jetbrains.plugins.gradle.tooling.web.WebConfiguration;

import java.io.File;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class WarModelBuilderImpl implements ModelBuilderService {

    private static final String WEB_APP_DIR_PROPERTY = "webAppDir";
    private static final String WEB_APP_DIR_NAME_PROPERTY = "webAppDirName";

    @Override
    public boolean canBuild(String modelName) {
        return WebConfiguration.class.getName().equals(modelName);
    }

    @Override
    public Object buildAll(String modelName, final Project project) {
        WarPlugin warPlugin = project.getPlugins().findPlugin(WarPlugin.class);
        if (warPlugin == null) {
            return null;
        }

        final String webAppDirName = !project.hasProperty(WEB_APP_DIR_NAME_PROPERTY)
            ? "src/main/webapp"
            : String.valueOf(project.property(WEB_APP_DIR_NAME_PROPERTY));

        final File webAppDir = !project.hasProperty(WEB_APP_DIR_PROPERTY)
            ? new File(project.getProjectDir(), webAppDirName)
            : (File) project.property(WEB_APP_DIR_PROPERTY);

        List<WebConfiguration.WarModel> warModels = new ArrayList<>();

        // Iterate through each task in the project
        for (Task task : project.getTasks()) {
            if (task instanceof War) {
                final War warTask = (War) task;
                final WarModelImpl warModel = new WarModelImpl(warTask.getArchiveName(), webAppDirName, webAppDir);
                final List<WebConfiguration.WebResource> webResources = new ArrayList<>();

                warModel.setWebXml(warTask.getWebXml());
                warTask.getRootSpec().setIncludeEmptyDirs(true);

                // Walk the rootSpec; using an Action with reflection to emulate Groovy's dynamic behavior.
                warTask.getRootSpec().walk(new Action<Object>() {
                    @Override
                    public void execute(Object resolver) {
                        try {
                            // If method setIncludeEmptyDirs(boolean) exists, invoke it.
                            try {
                                Method setIncludeEmptyDirs = resolver.getClass().getMethod("setIncludeEmptyDirs", boolean.class);
                                if (setIncludeEmptyDirs != null) {
                                    setIncludeEmptyDirs.invoke(resolver, true);
                                }
                            }
                            catch (NoSuchMethodException nsme) {
                                // Method not present – ignore.
                            }

                            // Check that getDestPath() and getSource() methods exist.
                            boolean hasGetDestPath = hasMethod(resolver, "getDestPath");
                            boolean hasGetSource = hasMethod(resolver, "getSource");
                            if (!hasGetDestPath || !hasGetSource) {
                                throw new RuntimeException(GradleVersion.current() + " is not supported by web artifact importer");
                            }

                            // Retrieve relativePath from resolver.destPath.pathString
                            String relativePath;
                            try {
                                Method getDestPathMethod = resolver.getClass().getMethod("getDestPath");
                                Object destPath = getDestPathMethod.invoke(resolver);
                                Method getPathStringMethod = destPath.getClass().getMethod("getPathString");
                                relativePath = (String) getPathStringMethod.invoke(destPath);
                            }
                            catch (Exception e) {
                                throw new RuntimeException(e);
                            }

                            // Determine sourcePaths dynamically.
                            Object sourcePaths = null;
                            try {
                                Method getSourcePathsMethod = resolver.getClass().getMethod("getSourcePaths");
                                sourcePaths = getSourcePathsMethod.invoke(resolver);
                            }
                            catch (NoSuchMethodException e) {
                                try {
                                    Field sourcePathsField = resolver.getClass().getField("sourcePaths");
                                    sourcePaths = sourcePathsField.get(resolver);
                                }
                                catch (Exception e2) {
                                    try {
                                        Field this$0Field = resolver.getClass().getField("this$0");
                                        Object parent = this$0Field.get(resolver);
                                        try {
                                            Method getSourcePathsMethod2 = parent.getClass().getMethod("getSourcePaths");
                                            sourcePaths = getSourcePathsMethod2.invoke(parent);
                                        }
                                        catch (NoSuchMethodException e3) {
                                            Field sourcePathsField2 = parent.getClass().getField("sourcePaths");
                                            sourcePaths = sourcePathsField2.get(parent);
                                        }
                                    }
                                    catch (Exception e3) {
                                        // Leave sourcePaths as null if not found.
                                    }
                                }
                            }

                            if (sourcePaths != null && sourcePaths instanceof Collection) {
                                @SuppressWarnings("unchecked")
                                Collection<Object> sourcePathsCol = (Collection<Object>) sourcePaths;
                                List<Object> flatPaths = flatten(sourcePathsCol);
                                for (Object pathObj : flatPaths) {
                                    if (pathObj instanceof String) {
                                        String pathStr = (String) pathObj;
                                        File file = new File(warTask.getProject().getProjectDir(), pathStr);
                                        addPath(webResources, relativePath, "", file);
                                    }
                                }
                            }

                            // Get the source and invoke its visit(FileVisitor) method.
                            Object source;
                            try {
                                Method getSourceMethod = resolver.getClass().getMethod("getSource");
                                source = getSourceMethod.invoke(resolver);
                            }
                            catch (Exception e) {
                                throw new RuntimeException(e);
                            }

                            // Invoke the visit method on the source passing a FileVisitor instance.
                            try {
                                Method visitMethod = source.getClass().getMethod("visit", FileVisitor.class);
                                visitMethod.invoke(source, new FileVisitor() {
                                    @Override
                                    public void visitDir(FileVisitDetails dirDetails) {
                                        try {
                                            addPath(webResources, getRelativePath(resolver), dirDetails.getPath(), dirDetails.getFile());
                                        }
                                        catch (Exception ignore) {
                                        }
                                    }

                                    @Override
                                    public void visitFile(FileVisitDetails fileDetails) {
                                        try {
                                            File xmlFile = warTask.getWebXml();
                                            if (xmlFile == null || !fileDetails.getFile().getCanonicalPath().equals(xmlFile.getCanonicalPath())) {
                                                addPath(webResources, getRelativePath(resolver), fileDetails.getPath(), fileDetails.getFile());
                                            }
                                        }
                                        catch (Exception ignore) {
                                        }
                                    }
                                });
                            }
                            catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                        catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }

                    // Helper method to retrieve relativePath from the resolver via reflection.
                    private String getRelativePath(Object resolver) throws Exception {
                        Method getDestPathMethod = resolver.getClass().getMethod("getDestPath");
                        Object destPath = getDestPathMethod.invoke(resolver);
                        Method getPathStringMethod = destPath.getClass().getMethod("getPathString");
                        return (String) getPathStringMethod.invoke(destPath);
                    }
                });

                warModel.setWebResources(webResources);
                warModel.setClasspath(warTask.getClasspath().getFiles());

                Manifest manifest = warTask.getManifest();
                if (manifest != null) {
                    StringWriter writer = new StringWriter();
                    manifest.writeTo(writer);
                    warModel.setManifestContent(writer.toString());
                }
                warModels.add(warModel);
            }
        }

        return new WebConfigurationImpl(warModels);
    }

    @Override
    public ErrorMessageBuilder getErrorMessageBuilder(Project project, Exception e) {
        return ErrorMessageBuilder.create(project, e, "Web project import errors")
            .withDescription("Web Facets/Artifacts will not be configured");
    }

    /**
     * Helper method to add a web resource.
     */
    private static void addPath(List<WebConfiguration.WebResource> webResources, String warRelativePath, String fileRelativePath, File file) {
        if (warRelativePath == null) {
            warRelativePath = "";
        }
        WebConfiguration.WebResource webResource = new WebResourceImpl(warRelativePath, fileRelativePath, file);
        webResources.add(webResource);
    }

    /**
     * Checks if the given object has a public method with the specified name and no parameters (or matching parameters if provided).
     */
    private static boolean hasMethod(Object obj, String methodName, Class<?>... parameterTypes) {
        try {
            obj.getClass().getMethod(methodName, parameterTypes);
            return true;
        }
        catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * Recursively flattens a collection.
     */
    private static List<Object> flatten(Collection<?> collection) {
        List<Object> flatList = new ArrayList<>();
        for (Object item : collection) {
            if (item instanceof Collection) {
                flatList.addAll(flatten((Collection<?>) item));
            }
            else {
                flatList.add(item);
            }
        }
        return flatList;
    }
}

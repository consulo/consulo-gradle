// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.progress.ProgressManager;
import consulo.gradle.GradleConstants;
import consulo.logging.Logger;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

@ExtensionImpl
public final class GradleScriptCollector implements GradleAutoReloadSettingsCollector {
    private static final Logger LOG = Logger.getInstance(GradleScriptCollector.class);

    @Override
    public List<Path> collectSettingsFiles(GradleProjectSettings projectSettings) {
        List<Path> paths = new ArrayList<>();

        for (String modulePath : projectSettings.getModules()) {
            ProgressManager.checkCanceled();

            try {
                Files.walkFileTree(Paths.get(modulePath), EnumSet.noneOf(FileVisitOption.class), 1, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                        String fileName = path.getFileName().toString();
                        if (fileName.endsWith('.' + GradleConstants.EXTENSION) ||
                            fileName.endsWith('.' + GradleConstants.KOTLIN_DSL_SCRIPT_EXTENSION)) {
                            if (Files.isRegularFile(path)) {
                                paths.add(path);
                            }
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            catch (IOException | InvalidPathException e) {
                LOG.debug(e);
            }
        }

        return paths;
    }
}

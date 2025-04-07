package org.jetbrains.plugins.gradle.service.project;

import consulo.ide.impl.idea.util.PathUtil;
import consulo.util.io.FileUtil;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author VISTALL
 * @since 2025-04-07
 */
public class ToolingExtensionPaths {
    private final Set<String> myPaths = new LinkedHashSet<>();

    public ToolingExtensionPaths addJarByClass(Class<?> clazz) {
        myPaths.add(PathUtil.getCanonicalPath(PathUtil.getJarPathForClass(clazz)));
        return this;
    }

    public ToolingExtensionPaths addJar(File file) {
        myPaths.add(FileUtil.toCanonicalPath(file.getPath()));
        return this;
    }

    public String toArrayExpression() {
        StringBuilder buf = new StringBuilder();
        buf.append('[');
        for (Iterator<String> it = myPaths.iterator(); it.hasNext(); ) {
            String jarPath = FileUtil.toSystemIndependentName(it.next());
            
            buf.append('\"').append(jarPath).append('\"');
            if (it.hasNext()) {
                buf.append(',');
            }
        }
        buf.append(']');
        return buf.toString();
    }
}

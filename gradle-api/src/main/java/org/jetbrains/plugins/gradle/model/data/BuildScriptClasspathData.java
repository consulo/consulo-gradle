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
package org.jetbrains.plugins.gradle.model.data;

import consulo.externalSystem.model.Key;
import consulo.externalSystem.model.ProjectKeys;
import consulo.externalSystem.model.ProjectSystemId;
import consulo.externalSystem.service.project.AbstractExternalEntityData;

import jakarta.annotation.Nonnull;
import java.io.Serializable;
import java.util.List;
import java.util.Set;

/**
 * @author Vladislav.Soroka
 * @since 12/20/13
 */
public class BuildScriptClasspathData extends AbstractExternalEntityData
{
	private static final long serialVersionUID = 1L;
	@Nonnull
	public static final Key<BuildScriptClasspathData> KEY = Key.create(BuildScriptClasspathData.class,
			ProjectKeys.LIBRARY_DEPENDENCY.getProcessingWeight() + 1);

	@Nonnull
	private final List<ClasspathEntry> myClasspathEntries;


	public BuildScriptClasspathData(@Nonnull ProjectSystemId owner, @Nonnull List<ClasspathEntry> classpathEntries)
	{
		super(owner);
		myClasspathEntries = classpathEntries;
	}

	@Nonnull
	public List<ClasspathEntry> getClasspathEntries()
	{
		return myClasspathEntries;
	}

	public static class ClasspathEntry implements Serializable
	{

		private static final long serialVersionUID = 1L;

		@Nonnull
		private final Set<String> myClassesFile;

		@Nonnull
		private final Set<String> mySourcesFile;

		@Nonnull
		private final Set<String> myJavadocFile;

		public ClasspathEntry(@Nonnull Set<String> classesFile, @Nonnull Set<String> sourcesFile, @Nonnull Set<String> javadocFile)
		{
			myClassesFile = classesFile;
			mySourcesFile = sourcesFile;
			myJavadocFile = javadocFile;
		}

		@Nonnull
		public Set<String> getClassesFile()
		{
			return myClassesFile;
		}

		@Nonnull
		public Set<String> getSourcesFile()
		{
			return mySourcesFile;
		}

		@Nonnull
		public Set<String> getJavadocFile()
		{
			return myJavadocFile;
		}

		@Override
		public boolean equals(Object o)
		{
			if(this == o)
			{
				return true;
			}
			if(!(o instanceof ClasspathEntry))
			{
				return false;
			}

			ClasspathEntry entry = (ClasspathEntry) o;

			if(!myClassesFile.equals(entry.myClassesFile))
			{
				return false;
			}
			if(!myJavadocFile.equals(entry.myJavadocFile))
			{
				return false;
			}
			if(!mySourcesFile.equals(entry.mySourcesFile))
			{
				return false;
			}

			return true;
		}

		@Override
		public int hashCode()
		{
			int result = myClassesFile.hashCode();
			result = 31 * result + mySourcesFile.hashCode();
			result = 31 * result + myJavadocFile.hashCode();
			return result;
		}
	}
}

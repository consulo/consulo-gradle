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

import jakarta.annotation.Nonnull;
import java.io.File;
import java.io.Serializable;

/**
 * @author Vladislav.Soroka
 * @since 2/10/14
 */
public class WebResource implements Serializable
{
	private static final long serialVersionUID = 1L;

	@Nonnull
	private final WarDirectory myWarDirectory;
	@Nonnull
	private final String warRelativePath;
	@Nonnull
	private final File file;

	public WebResource(@Nonnull WarDirectory warDirectory, @Nonnull String warRelativePath, @Nonnull File file)
	{
		myWarDirectory = warDirectory;
		this.warRelativePath = getAdjustedPath(warRelativePath);
		this.file = file;
	}

	@Nonnull
	public WarDirectory getWarDirectory()
	{
		return myWarDirectory;
	}

	@Nonnull
	public String getWarRelativePath()
	{
		return warRelativePath;
	}

	@Nonnull
	public File getFile()
	{
		return file;
	}

	private static String getAdjustedPath(final @Nonnull String path)
	{
		return path.isEmpty() || path.charAt(0) != '/' ? '/' + path : path;
	}

	@Override
	public boolean equals(Object o)
	{
		if(this == o)
		{
			return true;
		}
		if(!(o instanceof WebResource))
		{
			return false;
		}

		WebResource resource = (WebResource) o;
		if(!file.getPath().equals(resource.file.getPath()))
		{
			return false;
		}
		if(myWarDirectory != resource.myWarDirectory)
		{
			return false;
		}
		if(!warRelativePath.equals(resource.warRelativePath))
		{
			return false;
		}

		return true;
	}

	@Override
	public int hashCode()
	{
		int result = myWarDirectory.hashCode();
		result = 31 * result + warRelativePath.hashCode();
		result = 31 * result + file.getPath().hashCode();
		return result;
	}

	@Override
	public String toString()
	{
		return "WebResource{" +
				"myWarDirectory=" + myWarDirectory +
				", warRelativePath='" + warRelativePath + '\'' +
				", file=" + file +
				'}';
	}
}

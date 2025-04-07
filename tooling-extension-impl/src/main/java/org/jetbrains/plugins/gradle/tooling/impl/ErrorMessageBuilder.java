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
package org.jetbrains.plugins.gradle.tooling.impl;

import org.gradle.api.Project;

import java.io.File;

/**
 * @author Vladislav.Soroka
 * @since 5/13/2014
 */
public class ErrorMessageBuilder {
  public static final String GROUP_TAG = "<ij_msg_gr>";
  public static final String NAV_TAG = "<ij_nav>";
  public static final String EOL_TAG = "<eol>";

  private final Project myProject;
  private final Exception myException;
  private final String myGroup;
  private String myDescription;

  private ErrorMessageBuilder(Project project, Exception exception, String group) {
    myProject = project;
    myException = exception;
    myGroup = group;
  }

  public static ErrorMessageBuilder create(Project project, String group) {
    return new ErrorMessageBuilder(project, null, group);
  }

  public static ErrorMessageBuilder create(Project project, Exception exception, String group) {
    return new ErrorMessageBuilder(project, exception, group);
  }

  public ErrorMessageBuilder withDescription(String description) {
    myDescription = description;
    return this;
  }

  public String build() {
    String group = myGroup.replaceAll("\r\n|\n\r|\n|\r", " ");
    final File projectBuildFile = myProject.getBuildFile();
    return (
      GROUP_TAG + group + GROUP_TAG +
      (projectBuildFile != null ? (NAV_TAG + projectBuildFile.getPath() + NAV_TAG) : "") +
      (
        "<i>" +
        "<b>" + myProject + ((myDescription != null) ? ": " + myDescription : "") + "</b>" +
        (myException != null ? "\nDetails: " + getErrorMessage(myException) : "") +
        "</i>"
      ).replaceAll("\r\n|\n\r|\n|\r", EOL_TAG)
    );
  }


  private static String getErrorMessage(Throwable e) {
    StringBuilder buf = new StringBuilder();
    Throwable cause = e;
    while (cause != null) {
      if (buf.length() != 0) {
        buf.append("\nCaused by: ");
      }
      buf.append(cause.getClass().getName()).append(": ").append(cause.getMessage());
      cause = cause.getCause();
    }
    return buf.toString();
  }
}

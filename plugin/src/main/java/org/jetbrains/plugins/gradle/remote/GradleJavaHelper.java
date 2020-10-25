package org.jetbrains.plugins.gradle.remote;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTable;
import consulo.java.projectRoots.OwnJdkUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates functionality of deciding what java should be used by the gradle process.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/27/12 2:20 PM
 */
public class GradleJavaHelper {

  public static final String GRADLE_JAVA_HOME_KEY = "gradle.java.home";
  
  @SuppressWarnings("MethodMayBeStatic")
  @javax.annotation.Nullable
  public String getJdkHome(@javax.annotation.Nullable Project project) {
    List<String> candidates = new ArrayList<>();
    candidates.add(System.getProperty(GRADLE_JAVA_HOME_KEY));
    candidates.add(System.getenv("JAVA_HOME"));
    for (String candidate : candidates) {
      if (candidate != null && OwnJdkUtil.checkForJdk(new File(candidate))) {
        return candidate;
      }
    }

    Sdk[] sdks = SdkTable.getInstance().getAllSdks();
    if (sdks != null) {
      for (Sdk sdk : sdks) {
        String path = sdk.getHomePath();
        if (path != null && OwnJdkUtil.checkForJdk(new File(path))) {
          return path;
        }
      }
    }
    
    return null;
  }
}

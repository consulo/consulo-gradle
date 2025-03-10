package org.jetbrains.plugins.gradle.remote.impl;

import com.intellij.openapi.externalSystem.model.project.LibraryData;
import jakarta.annotation.Nonnull;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @author Denis Zhdanov
 * @since 10/19/11 5:25 PM
 */
public class GradleLibraryNamesMixerTest {
  
  private GradleLibraryNamesMixer myMixer;
  
  @Before
  public void setUp() {
    myMixer = new GradleLibraryNamesMixer();
  }
  
  @Test
  public void sourceVsTest() {
    doTest(
      t("resources", "my-module-resources", "dir1/dir2/my-module/src/main/resources"),
      t("resources", "my-module-test-resources", "dir1/dir2/my-module/src/test/resources"),
      t("resources", "my-another-module-resources", "dir1/dir2/my-another-module/src/main/resources"),
      t("resources", "my-another-module-test-resources", "dir1/dir2/my-another-module/src/test/resources")
    );
  }

  private void doTest(TestDataEntry... entries) {
    Map<LibraryData, String> expected = new IdentityHashMap<LibraryData, String>();
    List<LibraryData> libraries = new ArrayList<LibraryData>();
    // TODO den implement
//    for (TestDataEntry entry : entries) {
//      LibraryData library = new LibraryData(entry.initialName);
//      library.addPath(LibraryPathType.BINARY, entry.path);
//      libraries.add(library);
//      expected.put(library, entry.expectedName);
//    }
//    
//    myMixer.mixNames(libraries);
//    for (LibraryData library : libraries) {
//      assertEquals(expected.get(library), library.getName());
//    }
  }
  
  private static class TestDataEntry {
    
    public String initialName;
    public String expectedName;
    public String path;

    TestDataEntry(@Nonnull String initialName, @Nonnull String expectedName, @Nonnull String path) {
      this.initialName = initialName;
      this.expectedName = expectedName;
      this.path = path;
    }
  }

  public static TestDataEntry t(@Nonnull String initialName, @Nonnull String expectedName, @Nonnull String path) {
    return new TestDataEntry(initialName, expectedName, path);
  }
}

package org.jetbrains.plugins.gradle.remote.impl;

import consulo.externalSystem.model.DataNode;
import consulo.externalSystem.model.project.LibraryData;
import consulo.externalSystem.model.project.LibraryPathType;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.File;
import java.util.*;

/**
 * Encapsulates logic of checking if particular collection of gradle libraries contains libraries with the same names and
 * tries to diversify them in the case of the positive answer.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2011-10-19 2:04 PM
 */
public class GradleLibraryNamesMixer {

  /**
   * Holds mappings like <code>('file name'; boolean)</code> where <code>'file name'</code> defines 'too common' file/dir
   * name that should not be used during library name generation. Boolean flag indicates if 'common file name' may be used
   * if 'non-common' files are the same.
   * <p/>
   * Example: consider the following file system tree:
   * <pre>
   *   module
   *     |_src
   *        |_main
   *        |  |_resources
   *        |
   *        |_test
   *           |_resources
   * </pre>
   * Let's say we have two libraries where one of them points to <code>'src/main/resources'</code> and another one
   * to <code>'src/test/resources'</code>. We want to generate names <code>'module-resources'</code> and
   * <code>'module-test-resources'</code> respectively because <code>'test'</code> entry at the current collection is
   * stored with <code>'true'</code> flag.
   */
  private static final Map<String, Boolean> NON_UNIQUE_PATH_ENTRIES = new HashMap<String, Boolean>();
  static {
    NON_UNIQUE_PATH_ENTRIES.put("src", false);
    NON_UNIQUE_PATH_ENTRIES.put("main", false);
    NON_UNIQUE_PATH_ENTRIES.put("test", true);
    NON_UNIQUE_PATH_ENTRIES.put("resources", false);
    NON_UNIQUE_PATH_ENTRIES.put("java", false);
    NON_UNIQUE_PATH_ENTRIES.put("groovy", false);
  }
  private static final char NAME_SEPARATOR = '-';

  /**
   * Tries to ensure that given libraries have distinct names, i.e. traverses all of them and tries to generate
   * unique name for those with equal names.
   * 
   * @param libraries  libraries to process
   */
  @SuppressWarnings("MethodMayBeStatic")
  public void mixNames(@Nonnull Collection<DataNode<LibraryData>> libraries) {
    if (libraries.isEmpty()) {
      return;
    }
    Map<String, Wrapped> names = new HashMap<>();
    List<Wrapped> data = new ArrayList<>();
    for (DataNode<LibraryData> library : libraries) {
      Wrapped wrapped = new Wrapped(library.getData());
      data.add(wrapped);
    }
    boolean mixed = false;
    while (!mixed) {
      mixed = doMixNames(data, names);
    }
  }

  /**
   * Does the same as {@link #mixNames(Collection)} but uses given <code>('library name; wrapped library'}</code> mappings cache.
   * 
   * @param libraries  libraries to process
   * @param cache      cache to use
   * @return           <code>true</code> if all of the given libraries have distinct names now; <code>false</code> otherwise
   */
  private static boolean doMixNames(@Nonnull Collection<Wrapped> libraries, @Nonnull Map<String, Wrapped> cache) {
    cache.clear();
    for (Wrapped current : libraries) {
      Wrapped previous = cache.remove(current.library.getExternalName());
      if (previous == null) {
        cache.put(current.library.getExternalName(), current);
      }
      else {
        mixNames(current, previous);
        return current.library.getExternalName().equals(previous.library.getExternalName()); // Stop processing if it's not possible to generate
      }
    }
    return true;
  }

  /**
   * Tries to generate distinct names for the given wrapped libraries (assuming that they have equal names at the moment).
   * 
   * @param wrapped1  one of the libraries with equal names
   * @param wrapped2  another library which name is equal to the name of the given one
   */
  @SuppressWarnings("AssignmentToForLoopParameter")
  private static void mixNames(@Nonnull Wrapped wrapped1, @Nonnull Wrapped wrapped2) {
    if (!wrapped1.prepare() || !wrapped2.prepare()) {
      return;
    }
    String wrapped1AltText = null;
    String wrapped2AltText = null;
    
    for (File file1 = wrapped1.currentFile, file2 = wrapped2.currentFile;
         file1 != null && file2 != null;
         file1 = file1.getParentFile(), file2 = file2.getParentFile())
    {
      while (file1 != null && !StringUtil.isEmpty(file1.getName()) && NON_UNIQUE_PATH_ENTRIES.containsKey(file1.getName())) {
        if (NON_UNIQUE_PATH_ENTRIES.get(file1.getName())) {
          if (StringUtil.isEmpty(wrapped1AltText)) {
            wrapped1AltText = file1.getName();
          }
          else {
            wrapped1AltText += NAME_SEPARATOR + file1.getName();
          }
        }
        file1 = file1.getParentFile();
      }
      while (file2 != null && !StringUtil.isEmpty(file2.getName()) && NON_UNIQUE_PATH_ENTRIES.containsKey(file2.getName())) {
        if (NON_UNIQUE_PATH_ENTRIES.get(file2.getName())) {
          if (StringUtil.isEmpty(wrapped2AltText)) {
            wrapped2AltText = file2.getName();
          }
          else {
            wrapped2AltText += NAME_SEPARATOR + file2.getName();
          }
        }
        file2 = file2.getParentFile();
      }
      
      if (file1 == null) {
        wrapped1.nextFile();
      }
      else if (!wrapped1.library.getExternalName().startsWith(file1.getName())) {
        wrapped1.library.setExternalName(file1.getName() + NAME_SEPARATOR + wrapped1.library.getExternalName());
      }
      if (file2 == null) {
        wrapped2.nextFile();
      }
      else if (!wrapped2.library.getExternalName().startsWith(file2.getName())) {
        wrapped2.library.setExternalName(file2.getName() + NAME_SEPARATOR + wrapped2.library.getExternalName());
      }

      if (wrapped1.library.getExternalName().equals(wrapped2.library.getExternalName())) {
        if (wrapped1AltText != null) {
          diversifyName(wrapped1AltText, wrapped1, file1);
          return;
        }
        else if (wrapped2AltText != null) {
          diversifyName(wrapped2AltText, wrapped2, file1);
          return;
        }
      }
      else {
        return;
      }

      if (file1 == null || file2 == null) {
        return;
      }
    }
  }

  @SuppressWarnings("ConstantConditions")
  private static void diversifyName(@Nonnull String changeText, @Nonnull Wrapped wrapped, @Nullable File file) {
    String name = wrapped.library.getExternalName();
    int i = file == null ? - 1 : name.indexOf(file.getName());
    final String newName;
    if (i >= 0) {
      newName = name.substring(0, i + file.getName().length()) + NAME_SEPARATOR + changeText + name.substring(i + file.getName().length());
    }
    else {
      newName = changeText + NAME_SEPARATOR + name;
    }
    wrapped.library.setExternalName(newName);
  }

  /**
   * Wraps target library and hold auxiliary information required for the processing.
   */
  private static class Wrapped {
    /** Holds list of files that may be used for name generation. */
    public final Set<File> files = new HashSet<File>();
    /** File that was used for the current name generation. */
    public File        currentFile;
    /** Target library. */
    public LibraryData library;

    Wrapped(@Nonnull LibraryData library) {
      this.library = library;
      for (LibraryPathType pathType : LibraryPathType.values()) {
        for (String path : library.getPaths(pathType)) {
          files.add(new File(path));
        }
      }
    }

    public boolean prepare() {
      if (currentFile != null) {
        return true;
      }
      return nextFile();
    }

    public boolean nextFile() {
      if (files.isEmpty()) {
        return false;
      }
      Iterator<File> iterator = files.iterator();
      currentFile = iterator.next();
      iterator.remove();
      return true;
    }
  }
}

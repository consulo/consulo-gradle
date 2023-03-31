package consulo.gradle.tooling.impl.buildler.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author VISTALL
 * @since 30/12/2020
 */
public class ReflectionMethod<ReturnMethod, InstanceObject> {
  private Method myMethod;

  public ReflectionMethod(Class<? extends InstanceObject> clazz, String methodName) {
    try {
      myMethod = clazz.getDeclaredMethod(methodName);
      myMethod.setAccessible(true);
    }
    catch (Exception ignored) {
    }
  }

  @SuppressWarnings("unchecked")
  public ReturnMethod invoke(InstanceObject instanceObject) {
    try {
      if (myMethod == null) {
        return null;
      }
      return (ReturnMethod)myMethod.invoke(instanceObject);
    }
    catch (IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException();
    }
  }
}

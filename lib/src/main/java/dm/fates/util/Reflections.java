/*
 * Copyright 2018 Davide Maestroni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dm.fates.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;

/**
 * Created by davide-maestroni on 07/18/2017.
 */
public class Reflections {

  /**
   * Constant defining an empty argument array for methods or constructors.
   */
  public static final Object[] NO_ARGS = new Object[0];

  private static final HashMap<Class<?>, Class<?>> sBoxingClasses =
      new HashMap<Class<?>, Class<?>>(9) {{
        put(boolean.class, Boolean.class);
        put(byte.class, Byte.class);
        put(char.class, Character.class);
        put(double.class, Double.class);
        put(float.class, Float.class);
        put(int.class, Integer.class);
        put(long.class, Long.class);
        put(short.class, Short.class);
        put(void.class, Void.class);
      }};

  private Reflections() {
  }

  /**
   * Returns the specified objects as an array of arguments.
   *
   * @param args the argument objects.
   * @return the array.
   */
  @NotNull
  public static Object[] asArgs(@Nullable final Object... args) {
    return (args != null) ? args : NO_ARGS;
  }

  /**
   * Returns the class boxing the specified primitive type.
   * <p>
   * If the passed class does not represent a primitive type, the same class is returned.
   *
   * @param type the primitive type.
   * @return the boxing class.
   */
  @NotNull
  public static Class<?> boxingClass(@NotNull final Class<?> type) {
    if (!type.isPrimitive()) {
      return type;
    }

    return sBoxingClasses.get(type);
  }

  /**
   * Returns a clone of the specified array of argument objects.
   * <br>
   * The cloning is safe, that is, if {@code null} is passed, an empty array is returned.
   *
   * @param args the argument objects.
   * @return the array.
   */
  @NotNull
  public static Object[] cloneArgs(@Nullable final Object... args) {
    return (args != null) ? args.clone() : NO_ARGS;
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public static <T> Constructor<T> getDefaultConstructor(@NotNull final Class<T> type) {
    Constructor<?> defaultConstructor = null;
    for (final Constructor<?> constructor : type.getConstructors()) {
      if (constructor.getParameterTypes().length == 0) {
        defaultConstructor = constructor;
        break;
      }
    }

    if (defaultConstructor == null) {
      for (final Constructor<?> constructor : type.getDeclaredConstructors()) {
        if (constructor.getParameterTypes().length == 0) {
          defaultConstructor = constructor;
          break;
        }
      }

      if (defaultConstructor == null) {
        throw new IllegalArgumentException(
            "no default constructor found for type: " + type.getName());
      }
    }

    return (Constructor<T>) makeAccessible(defaultConstructor);
  }

  public static <T> boolean hasDefaultConstructor(@NotNull final Class<T> type) {
    for (final Constructor<?> constructor : type.getConstructors()) {
      if (constructor.getParameterTypes().length == 0) {
        makeAccessible(constructor);
        return true;
      }
    }

    for (final Constructor<?> constructor : type.getDeclaredConstructors()) {
      if (constructor.getParameterTypes().length == 0) {
        makeAccessible(constructor);
        return true;
      }
    }

    return false;
  }

  /**
   * Makes the specified method accessible.
   *
   * @param method the method instance.
   * @return the method.
   */
  @NotNull
  public static Method makeAccessible(@NotNull final Method method) {
    if (!method.isAccessible()) {
      AccessController.doPrivileged(new SetAccessibleMethodAction(method));
    }

    return method;
  }

  /**
   * Makes the specified constructor accessible.
   *
   * @param constructor the constructor instance.
   * @return the constructor.
   */
  @NotNull
  public static Constructor<?> makeAccessible(@NotNull final Constructor<?> constructor) {
    if (!constructor.isAccessible()) {
      AccessController.doPrivileged(new SetAccessibleConstructorAction(constructor));
    }

    return constructor;
  }

  /**
   * Privileged action used to grant accessibility to a constructor.
   */
  private static class SetAccessibleConstructorAction implements PrivilegedAction<Void> {

    private final Constructor<?> mmConstructor;

    /**
     * Constructor.
     *
     * @param constructor the constructor instance.
     */
    private SetAccessibleConstructorAction(@NotNull final Constructor<?> constructor) {
      mmConstructor = constructor;
    }

    public Void run() {
      mmConstructor.setAccessible(true);
      return null;
    }
  }

  /**
   * Privileged action used to grant accessibility to a method.
   */
  private static class SetAccessibleMethodAction implements PrivilegedAction<Void> {

    private final Method mMethod;

    /**
     * Constructor.
     *
     * @param method the method instance.
     */
    private SetAccessibleMethodAction(@NotNull final Method method) {
      mMethod = method;
    }

    public Void run() {
      mMethod.setAccessible(true);
      return null;
    }
  }
}

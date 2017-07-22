/*
 * Copyright 2017 Davide Maestroni
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

package dm.james.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * Created by davide-maestroni on 07/18/2017.
 */
public class ReflectionUtils {

  /**
   * Constant defining an empty argument array for methods or constructors.
   */
  public static final Object[] NO_ARGS = new Object[0];

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
}

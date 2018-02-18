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

package dm.jale.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Utility class for verifying constant conditions.
 * <p>
 * Created by davide-maestroni on 03/27/2016.
 */
public class ConstantConditions {

  /**
   * Avoid explicit instantiation.
   */
  protected ConstantConditions() {
    avoid();
  }

  /**
   * Asserts that the calling method is not called.
   *
   * @throws AssertionError if the method is called.
   */
  public static void avoid() {
    throw new AssertionError("method is not callable");
  }

  /**
   * Asserts that the specified number is not negative.
   *
   * @param number the number.
   * @return the number.
   * @throws IllegalArgumentException if the number is negative.
   */
  public static int notNegative(final int number) {
    return notNegative("number", number);
  }

  /**
   * Asserts that the specified number is not negative.
   *
   * @param name   the name of the parameter used to build the error message.
   * @param number the number.
   * @return the number.
   * @throws IllegalArgumentException if the number is negative.
   */
  public static int notNegative(final String name, final int number) {
    return (int) notNegative(name, (long) number);
  }

  /**
   * Asserts that the specified number is not negative.
   *
   * @param number the number.
   * @return the number.
   * @throws IllegalArgumentException if the number is negative.
   */
  public static long notNegative(final long number) {
    return notNegative("number", number);
  }

  /**
   * Asserts that the specified number is not negative.
   *
   * @param name   the name of the parameter used to build the error message.
   * @param number the number.
   * @return the number.
   * @throws IllegalArgumentException if the number is negative.
   */
  public static long notNegative(final String name, final long number) {
    if (number < 0) {
      throw new IllegalArgumentException(
          "the " + name + " must not be negative, but is: " + number);
    }

    return number;
  }

  /**
   * Asserts that the specified object is not null.
   *
   * @param object the object.
   * @param <T>    the object type.
   * @return the object.
   * @throws NullPointerException is the object is null.
   */
  @NotNull
  public static <T> T notNull(final T object) {
    return notNull("object", object);
  }

  /**
   * Asserts that the specified object is not null.
   *
   * @param name   the name of the parameter used to build the error message.
   * @param object the object.
   * @param <T>    the object type.
   * @return the object.
   * @throws NullPointerException is the object is null.
   */
  @NotNull
  public static <T> T notNull(final String name, final T object) {
    if (object == null) {
      throw new NullPointerException("the " + name + " must not be null");
    }

    return object;
  }

  @NotNull
  public static <E> E[] notNullElements(final E[] array) {
    return notNullElements("objects", array);
  }

  @NotNull
  public static <E> E[] notNullElements(final String name, final E[] array) {
    ConstantConditions.notNull(name, array);
    for (final E element : array) {
      if (element == null) {
        throw new NullPointerException("the " + name + " array must not contain null elements");
      }
    }

    return array;
  }

  @NotNull
  public static <T extends Iterable> T notNullElements(final T collection) {
    return notNullElements("objects", collection);
  }

  @NotNull
  public static <T extends Iterable> T notNullElements(final String name, final T collection) {
    ConstantConditions.notNull(name, collection);
    if (collection instanceof Collection) {
      if (((Collection<?>) collection).contains(null)) {
        throw new NullPointerException(
            "the " + name + " collection must not contain null elements");
      }

    } else {
      for (final Object element : collection) {
        if (element == null) {
          throw new NullPointerException(
              "the " + name + " collection must not contain null elements");
        }
      }
    }

    return collection;
  }

  /**
   * Asserts that the specified number is positive.
   *
   * @param number the number.
   * @return the number.
   * @throws IllegalArgumentException if the number is 0 or negative.
   */
  public static int positive(final int number) {
    return positive("number", number);
  }

  /**
   * Asserts that the specified number is positive.
   *
   * @param name   the name of the parameter used to build the error message.
   * @param number the number.
   * @return the number.
   * @throws IllegalArgumentException if the number is 0 or negative.
   */
  public static int positive(final String name, final int number) {
    return (int) positive(name, (long) number);
  }

  /**
   * Asserts that the specified number is positive.
   *
   * @param number the number.
   * @return the number.
   * @throws IllegalArgumentException if the number is 0 or negative.
   */
  public static long positive(final long number) {
    return positive("number", number);
  }

  /**
   * Asserts that the specified number is positive.
   *
   * @param name   the name of the parameter used to build the error message.
   * @param number the number.
   * @return the number.
   * @throws IllegalArgumentException if the number is 0 or negative.
   */
  public static long positive(final String name, final long number) {
    if (number <= 0) {
      throw new IllegalArgumentException("the " + name + " must be positive, but is: " + number);
    }

    return number;
  }

  /**
   * Asserts that the calling method is not called.
   *
   * @param <R> the desired return type.
   * @return nothing.
   * @throws UnsupportedOperationException if the method is called.
   */
  public static <R> R unsupported() {
    throw new UnsupportedOperationException(buildUnsupportedMethodName(null));
  }

  public static <R> R unsupported(final String message) {
    throw new UnsupportedOperationException(
        buildUnsupportedMethodName(null) + ((message != null) ? ": " + message : ""));
  }

  public static <R> R unsupported(final String message, @NotNull final String callerOfMethodName) {
    throw new UnsupportedOperationException(
        buildUnsupportedMethodName(notNull("callerOfMethodName", callerOfMethodName)) + (
            (message != null) ? ": " + message : ""));
  }

  @NotNull
  private static String buildUnsupportedMethodName(@Nullable final String callerOfMethodName) {
    final StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
    final int length = stackTrace.length;
    for (int i = 0; i < length; ++i) {
      final StackTraceElement traceElement = stackTrace[i];
      if (ConstantConditions.class.getName().equals(traceElement.getClassName())
          && "unsupported".equals(traceElement.getMethodName())) {
        if (callerOfMethodName == null) {
          if ((i + 1) < length) {
            return stackTrace[i + 1].getMethodName();
          }

          return "";

        } else {
          for (int j = i + 1; j < length; ++j) {
            if (callerOfMethodName.equals(stackTrace[j].getMethodName())) {
              if ((j + 1) < length) {
                return stackTrace[j + 1].getMethodName();
              }

              return "";
            }
          }
        }
      }
    }

    return "";
  }
}

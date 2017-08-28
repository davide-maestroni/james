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

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Created by davide-maestroni on 08/17/2017.
 */
public class Iterables {

  private Iterables() {
    // TODO: 17/08/2017 protected?
  }

  @NotNull
  public static <T, C extends Collection<T>> C addAll(@NotNull final Iterable<? extends T> iterable,
      @NotNull final C collection) {
    for (final T element : iterable) {
      collection.add(element);
    }

    return ConstantConditions.notNull("collection", collection);
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public static <T> List<T> asList(@NotNull final Iterable<T> iterable) {
    if (iterable instanceof List) {
      return (List<T>) iterable;
    }

    return toList(iterable);
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public static <T> Set<T> asSet(@NotNull final Iterable<T> iterable) {
    if (iterable instanceof Set) {
      return (Set<T>) iterable;
    }

    return toSet(iterable);
  }

  public static <T> T first(@NotNull final Iterable<T> iterable) {
    return get(iterable, 0);
  }

  @SuppressWarnings("unchecked")
  public static <T> T get(@NotNull final Iterable<T> iterable, final int index) {
    if (iterable instanceof List) {
      return ((List<T>) iterable).get(index);
    }

    final Iterator<T> iterator = iterable.iterator();
    int i = 0;
    while (++i < index) {
      iterator.next();
    }

    return iterator.next();
  }

  public static int size(@NotNull final Iterable<?> iterable) {
    int size;
    if (iterable instanceof Collection) {
      size = ((Collection<?>) iterable).size();

    } else {
      size = 0;
      for (final Object ignored : iterable) {
        ++size;
      }
    }

    return size;
  }

  @NotNull
  public static Object[] toArray(@NotNull final Iterable<?> iterable) {
    final Object[] array = new Object[size(iterable)];
    final Iterator<?> iterator = iterable.iterator();
    for (int i = 0; iterator.hasNext(); ++i) {
      array[i] = iterator.next();
    }

    return array;
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public static <T> T[] toArray(@NotNull final Iterable<?> iterable, @NotNull T[] array) {
    final int size = size(iterable);
    if (array.length < size) {
      final Class<? extends Object[]> arrayClass = array.getClass();
      if (arrayClass == Object[].class) {
        array = (T[]) new Object[size];

      } else {
        array = (T[]) Array.newInstance(arrayClass.getComponentType(), size);
      }
    }

    final Iterator<?> iterator = iterable.iterator();
    for (int i = 0; iterator.hasNext(); ++i) {
      array[i] = (T) iterator.next();
    }

    if (array.length > size) {
      array[size] = null;
    }

    return array;
  }

  @NotNull
  public static <T> List<T> toList(@NotNull final Iterable<T> iterable) {
    return addAll(iterable, new ArrayList<T>());
  }

  @NotNull
  public static <T> Set<T> toSet(@NotNull final Iterable<T> iterable) {
    return addAll(iterable, new HashSet<T>());
  }

  @NotNull
  public static String toString(@NotNull final Iterable<?> iterable) {
    return asList(iterable).toString();
  }
}

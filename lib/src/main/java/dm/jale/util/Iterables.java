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

  public static boolean contains(@NotNull final Iterable<?> iterable, final Object element) {
    if (iterable instanceof Collection) {
      return ((Collection<?>) iterable).contains(element);
    }

    if (element != null) {
      for (final Object object : iterable) {
        if ((object == element) || element.equals(object)) {
          return true;
        }
      }

    } else {
      for (final Object object : iterable) {
        if (object == null) {
          return true;
        }
      }
    }

    return false;
  }

  public static boolean containsAll(@NotNull final Iterable<?> iterable,
      @NotNull final Collection<?> collection) {
    if (iterable instanceof Collection) {
      return ((Collection<?>) iterable).containsAll(collection);
    }

    final ArrayList<Object> objects = new ArrayList<Object>(collection);
    for (final Object object : iterable) {
      boolean found = false;
      final Iterator<Object> iterator = objects.iterator();
      while (iterator.hasNext()) {
        final Object next = iterator.next();
        if ((object == next) || ((next != null) && next.equals(object))) {
          iterator.remove();
          found = true;
          break;
        }
      }

      if (!found) {
        return false;
      }
    }

    return true;
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

  public static boolean isEmpty(@NotNull final Iterable<?> iterable) {
    return (iterable instanceof Collection) ? ((Collection<?>) iterable).isEmpty()
        : !iterable.iterator().hasNext();
  }

  public static boolean remove(@NotNull final Iterable<?> iterable, final Object element) {
    if (iterable instanceof Collection) {
      return ((Collection<?>) iterable).remove(element);
    }

    if (element != null) {
      final Iterator<?> iterator = iterable.iterator();
      while (iterator.hasNext()) {
        final Object object = iterator.next();
        if ((object == element) || element.equals(object)) {
          iterator.remove();
          return true;
        }
      }

    } else {
      final Iterator<?> iterator = iterable.iterator();
      while (iterator.hasNext()) {
        final Object object = iterator.next();
        if (object == null) {
          iterator.remove();
          return true;
        }
      }
    }

    return false;
  }

  @SuppressWarnings("unchecked")
  public static boolean removeAll(@NotNull final Iterable<?> iterable,
      @NotNull final Collection<?> collection) {
    if (iterable instanceof Collection) {
      return ((Collection<Object>) iterable).removeAll(collection);
    }

    boolean found = false;
    for (final Object element : collection) {
      if (element != null) {
        final Iterator<?> iterator = iterable.iterator();
        while (iterator.hasNext()) {
          final Object object = iterator.next();
          if ((object == element) || element.equals(object)) {
            iterator.remove();
            found = true;
          }
        }

      } else {
        final Iterator<?> iterator = iterable.iterator();
        while (iterator.hasNext()) {
          final Object object = iterator.next();
          if (object == null) {
            iterator.remove();
            found = true;
          }
        }
      }
    }

    return found;
  }

  public static boolean retainAll(@NotNull final Iterable<?> iterable,
      @NotNull final Collection<?> collection) {
    if (iterable instanceof Collection) {
      return ((Collection<?>) iterable).retainAll(collection);
    }

    ConstantConditions.notNull("collection", collection);
    boolean isModified = false;
    final Iterator<?> iterator = iterable.iterator();
    while (iterator.hasNext()) {
      if (!collection.contains(iterator.next())) {
        iterator.remove();
        isModified = true;
      }
    }

    return isModified;
  }

  public static int size(@NotNull final Iterable<?> iterable) {
    int size;
    if (iterable instanceof Collection) {
      size = ((Collection<?>) iterable).size();

    } else {
      size = 0;
      for (final Iterator<?> iterator = iterable.iterator(); iterator.hasNext(); iterator.next()) {
        ++size;
      }
    }

    return size;
  }

  @NotNull
  public static Object[] toArray(@NotNull final Iterable<?> iterable) {
    if (iterable instanceof Collection) {
      final Object[] array = new Object[((Collection<?>) iterable).size()];
      final Iterator<?> iterator = iterable.iterator();
      for (int i = 0; iterator.hasNext(); ++i) {
        array[i] = iterator.next();
      }

      return array;
    }

    final ArrayList<Object> list = new ArrayList<Object>();
    addAll(iterable, list);
    return list.toArray();
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public static <T> T[] toArray(@NotNull final Iterable<? extends T> iterable, @NotNull T[] array) {
    if (iterable instanceof Collection) {
      final int size = ((Collection<?>) iterable).size();
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

    final ArrayList<T> list = new ArrayList<T>();
    addAll(iterable, list);
    return list.toArray(array);
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
    final Iterator<?> iterator = iterable.iterator();
    if (iterator.hasNext()) {
      final StringBuilder builder = new StringBuilder();
      builder.append('[');
      while (true) {
        builder.append(String.valueOf(iterator.next()));
        if (!iterator.hasNext()) {
          return builder.append(']').toString();
        }

        builder.append(',').append(' ');
      }
    }

    return "[]";
  }
}

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

package dm.jail.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Created by davide-maestroni on 07/21/2017.
 */
public abstract class SerializableProxy implements Serializable {

  private final ArrayList<Object> mObjects = new ArrayList<Object>();

  public SerializableProxy(@Nullable final Object... args) {
    if (args != null) {
      Collections.addAll(mObjects, args);
    }
  }

  @NotNull
  @SuppressWarnings("unchecked")
  protected static Serializable proxy(final Object object) {
    if (object instanceof Serializable) {
      return (Serializable) object;
    }

    return new SerializableObject(object);
  }

  @NotNull
  @SuppressWarnings("unchecked")
  protected Object[] deserializeArgs() throws Exception {
    final ArrayList<Object> objects = mObjects;
    final int size = objects.size();
    final Object[] args = new Object[size];
    for (int i = 0; i < size; ++i) {
      final Object object = objects.get(i);
      if (object instanceof SerializableObject) {
        args[i] = ((SerializableObject) object).deserialize();

      } else {
        args[i] = object;
      }
    }

    return args;
  }

  private static class SerializableObject implements Serializable {

    private final Class<?> mClass;

    private SerializableObject(final Object instance) {
      mClass = (instance != null) ? instance.getClass() : null;
    }

    Object deserialize() throws Exception {
      final Class<?> aClass = mClass;
      return (aClass != null) ? Reflections.getDefaultConstructor(aClass).newInstance() : null;
    }
  }
}

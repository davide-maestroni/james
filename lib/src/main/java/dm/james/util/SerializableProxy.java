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

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Created by davide-maestroni on 07/21/2017.
 */
public abstract class SerializableProxy implements Serializable {

  private final ArrayList<SerializableObject> mObjects = new ArrayList<SerializableObject>();

  public SerializableProxy(@Nullable final Object... args) {
    if (args != null) {
      final ArrayList<SerializableObject> objects = mObjects;
      for (final Object arg : args) {
        objects.add(new SerializableObject(arg));
      }
    }
  }

  @NotNull
  protected Object[] deserializeArgs() throws Exception {
    final ArrayList<SerializableObject> objects = mObjects;
    final int size = objects.size();
    final Object[] args = new Object[size];
    for (int i = 0; i < size; ++i) {
      args[i] = objects.get(i).deserialize();
    }

    return args;
  }

  private static class SerializableObject implements Serializable {

    private final Class<?> mClass;

    private final Object mInstance;

    private SerializableObject(final Object instance) {
      if (instance == null) {
        mInstance = null;
        mClass = null;

      } else if (instance instanceof Serializable) {
        mInstance = instance;
        mClass = null;

      } else {
        mInstance = null;
        mClass = instance.getClass();
      }
    }

    Object deserialize() throws Exception {
      Object instance = mInstance;
      if (instance == null) {
        final Class<?> aClass = mClass;
        if (aClass != null) {
          instance = ReflectionUtils.getDefaultConstructor(aClass).newInstance();
        }
      }

      return instance;
    }
  }
}

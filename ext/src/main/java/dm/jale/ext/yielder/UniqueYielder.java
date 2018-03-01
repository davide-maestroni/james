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

package dm.jale.ext.yielder;

import org.jetbrains.annotations.NotNull;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.IdentityHashMap;

import dm.jale.eventual.Loop.YieldOutputs;
import dm.jale.eventual.LoopYielder;
import dm.jale.ext.config.BuildConfig;

/**
 * Created by davide-maestroni on 02/19/2018.
 */
class UniqueYielder<V> implements LoopYielder<IdentityHashMap<V, Boolean>, V, V>, Serializable {

  private static final UniqueYielder<?> sInstance = new UniqueYielder<Object>();

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private UniqueYielder() {
  }

  @NotNull
  @SuppressWarnings("unchecked")
  static <V> UniqueYielder<V> instance() {
    return (UniqueYielder<V>) sInstance;
  }

  public void done(final IdentityHashMap<V, Boolean> stack,
      @NotNull final YieldOutputs<V> outputs) {
  }

  public IdentityHashMap<V, Boolean> failure(final IdentityHashMap<V, Boolean> stack,
      @NotNull final Throwable failure, @NotNull final YieldOutputs<V> outputs) {
    outputs.yieldFailure(failure);
    return stack;
  }

  public IdentityHashMap<V, Boolean> init() {
    return new IdentityHashMap<V, Boolean>();
  }

  public boolean loop(final IdentityHashMap<V, Boolean> stack) {
    return true;
  }

  public IdentityHashMap<V, Boolean> value(final IdentityHashMap<V, Boolean> stack, final V value,
      @NotNull final YieldOutputs<V> outputs) {
    if (stack.put(value, Boolean.TRUE) == null) {
      outputs.yieldValue(value);
    }

    return stack;
  }

  @NotNull
  private Object readResolve() throws ObjectStreamException {
    return sInstance;
  }
}

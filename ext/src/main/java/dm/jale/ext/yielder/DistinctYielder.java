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
import java.util.HashSet;

import dm.jale.eventual.Loop.YieldOutputs;
import dm.jale.eventual.LoopYielder;
import dm.jale.ext.config.BuildConfig;

/**
 * Created by davide-maestroni on 02/19/2018.
 */
class DistinctYielder<V> implements LoopYielder<HashSet<V>, V, V>, Serializable {

  private static final DistinctYielder<?> sInstance = new DistinctYielder<Object>();

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private DistinctYielder() {
  }

  @NotNull
  @SuppressWarnings("unchecked")
  static <V> DistinctYielder<V> instance() {
    return (DistinctYielder<V>) sInstance;
  }

  public void done(final HashSet<V> stack, @NotNull final YieldOutputs<V> outputs) {
  }

  public HashSet<V> failure(final HashSet<V> stack, @NotNull final Throwable failure,
      @NotNull final YieldOutputs<V> outputs) {
    outputs.yieldFailure(failure);
    return stack;
  }

  public HashSet<V> init() {
    return new HashSet<V>();
  }

  public boolean loop(final HashSet<V> stack) {
    return true;
  }

  public HashSet<V> value(final HashSet<V> stack, final V value,
      @NotNull final YieldOutputs<V> outputs) {
    if (stack.add(value)) {
      outputs.yieldValue(value);
    }

    return stack;
  }

  @NotNull
  private Object readResolve() throws ObjectStreamException {
    return sInstance;
  }
}

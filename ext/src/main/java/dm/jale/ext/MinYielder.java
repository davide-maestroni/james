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

package dm.jale.ext;

import org.jetbrains.annotations.NotNull;

import java.io.ObjectStreamException;
import java.io.Serializable;

import dm.jale.async.Loop.YieldOutputs;
import dm.jale.async.Loop.Yielder;
import dm.jale.ext.MinYielder.YielderStack;
import dm.jale.ext.config.BuildConfig;
import dm.jale.util.ConstantConditions;

/**
 * Created by davide-maestroni on 02/19/2018.
 */
class MinYielder<V extends Comparable<V>> implements Yielder<YielderStack<V>, V, V>, Serializable {

  private static final MinYielder<?> sInstance = new MinYielder();

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private MinYielder() {
  }

  @NotNull
  @SuppressWarnings("unchecked")
  static <V extends Comparable<V>> MinYielder<V> instance() {
    return (MinYielder<V>) sInstance;
  }

  public void done(final YielderStack<V> stack, @NotNull final YieldOutputs<V> outputs) {
    if (stack != null) {
      outputs.yieldValue(stack.min);
    }
  }

  public YielderStack<V> failure(final YielderStack<V> stack, @NotNull final Throwable failure,
      @NotNull final YieldOutputs<V> outputs) {
    outputs.yieldFailure(failure);
    return null;
  }

  public YielderStack<V> init() {
    return new YielderStack<V>();
  }

  public boolean loop(final YielderStack<V> stack) {
    return (stack != null);
  }

  public YielderStack<V> value(final YielderStack<V> stack, final V value,
      @NotNull final YieldOutputs<V> outputs) {
    if (stack.min == null) {
      stack.min = ConstantConditions.notNull("value", value);

    } else if (value.compareTo(stack.min) < 0) {
      stack.min = value;
    }

    return stack;
  }

  @NotNull
  private Object readResolve() throws ObjectStreamException {
    return sInstance;
  }

  static class YielderStack<V> {

    private V min;
  }
}

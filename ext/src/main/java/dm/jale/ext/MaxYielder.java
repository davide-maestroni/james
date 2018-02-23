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

import dm.jale.eventual.Loop.YieldOutputs;
import dm.jale.eventual.Loop.Yielder;
import dm.jale.ext.MaxYielder.YielderStack;
import dm.jale.ext.config.BuildConfig;
import dm.jale.util.ConstantConditions;

/**
 * Created by davide-maestroni on 02/19/2018.
 */
class MaxYielder<V extends Comparable<? super V>>
    implements Yielder<YielderStack<V>, V, V>, Serializable {

  private static final MaxYielder<?> sInstance = new MaxYielder();

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private MaxYielder() {
  }

  @NotNull
  @SuppressWarnings("unchecked")
  static <V extends Comparable<? super V>> MaxYielder<V> instance() {
    return (MaxYielder<V>) sInstance;
  }

  public void done(final YielderStack<V> stack, @NotNull final YieldOutputs<V> outputs) {
    if (stack != null) {
      outputs.yieldValue(stack.max);
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
    if (stack.max == null) {
      stack.max = ConstantConditions.notNull("value", value);

    } else if (value.compareTo(stack.max) > 0) {
      stack.max = value;
    }

    return stack;
  }

  @NotNull
  private Object readResolve() throws ObjectStreamException {
    return sInstance;
  }

  static class YielderStack<V> {

    private V max;
  }
}

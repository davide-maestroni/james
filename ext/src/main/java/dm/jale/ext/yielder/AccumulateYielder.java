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

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;

import dm.jale.eventual.Loop.YieldOutputs;
import dm.jale.eventual.Loop.Yielder;
import dm.jale.ext.config.BuildConfig;
import dm.jale.ext.eventual.BiMapper;
import dm.jale.ext.yielder.AccumulateYielder.YielderStack;
import dm.jale.util.ConstantConditions;
import dm.jale.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/26/2018.
 */
class AccumulateYielder<V> implements Yielder<YielderStack<V>, V, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final BiMapper<? super V, ? super V, ? extends V> mAccumulator;

  private final boolean mHasInitial;

  private final V mInitialValue;

  AccumulateYielder(@NotNull final BiMapper<? super V, ? super V, ? extends V> accumulator) {
    this(false, null, accumulator);
  }

  AccumulateYielder(final V initialValue,
      @NotNull final BiMapper<? super V, ? super V, ? extends V> accumulator) {
    this(true, initialValue, accumulator);
  }

  private AccumulateYielder(final boolean hasInitial, final V initialValue,
      @NotNull final BiMapper<? super V, ? super V, ? extends V> accumulator) {
    mAccumulator = ConstantConditions.notNull("accumulator", accumulator);
    mInitialValue = initialValue;
    mHasInitial = hasInitial;
  }

  public void done(final YielderStack<V> stack, @NotNull final YieldOutputs<V> outputs) {
    if (stack != null) {
      outputs.yieldValue(stack.accumulated);
    }
  }

  public YielderStack<V> failure(final YielderStack<V> stack, @NotNull final Throwable failure,
      @NotNull final YieldOutputs<V> outputs) {
    outputs.yieldFailure(failure);
    return null;
  }

  public YielderStack<V> init() {
    final YielderStack<V> stack = new YielderStack<V>();
    if (mHasInitial) {
      stack.accumulated = mInitialValue;
      stack.isAccumulated = true;
    }

    return stack;
  }

  public boolean loop(final YielderStack<V> stack) {
    return (stack != null);
  }

  public YielderStack<V> value(final YielderStack<V> stack, final V value,
      @NotNull final YieldOutputs<V> outputs) throws Exception {
    if (!stack.isAccumulated) {
      stack.accumulated = value;
      stack.isAccumulated = true;

    } else {
      stack.accumulated = mAccumulator.apply(stack.accumulated, value);
    }

    return stack;
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new YielderProxy<V>(mHasInitial, mInitialValue, mAccumulator);
  }

  static class YielderStack<V> {

    private V accumulated;

    private boolean isAccumulated;
  }

  private static class YielderProxy<V> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private YielderProxy(final boolean hasInitial, final V initialValue,
        @NotNull final BiMapper<? super V, ? super V, ? extends V> accumulator) {
      super(hasInitial, initialValue, proxy(accumulator));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new AccumulateYielder<V>((Boolean) args[0], (V) args[1],
            (BiMapper<? super V, ? super V, ? extends V>) args[2]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

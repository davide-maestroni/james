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
import org.jetbrains.annotations.Nullable;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;

import dm.jale.eventual.Loop.YieldOutputs;
import dm.jale.eventual.LoopYielder;
import dm.jale.eventual.Provider;
import dm.jale.ext.config.BuildConfig;
import dm.jale.ext.eventual.BiMapper;
import dm.jale.ext.yielder.AccumulateYielder.YielderStack;
import dm.jale.util.ConstantConditions;
import dm.jale.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/26/2018.
 */
class AccumulateYielder<V, R> implements LoopYielder<YielderStack<R>, V, R>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final BiMapper<? super R, ? super V, ? extends R> mAccumulator;

  private final boolean mHasInit;

  private final Provider<R> mInit;

  @SuppressWarnings("unchecked")
  AccumulateYielder(@NotNull final BiMapper<? super V, ? super V, ? extends V> accumulator) {
    this(false, null, (BiMapper<? super R, ? super V, ? extends R>) accumulator);
  }

  AccumulateYielder(@NotNull final Provider<R> init,
      @NotNull final BiMapper<? super R, ? super V, ? extends R> accumulator) {
    this(true, ConstantConditions.notNull("init", init), accumulator);
  }

  private AccumulateYielder(final boolean hasInit, @Nullable final Provider<R> init,
      @NotNull final BiMapper<? super R, ? super V, ? extends R> accumulator) {
    mAccumulator = ConstantConditions.notNull("accumulator", accumulator);
    mInit = init;
    mHasInit = hasInit;
  }

  public void done(final YielderStack<R> stack, @NotNull final YieldOutputs<R> outputs) {
    if (stack != null) {
      outputs.yieldValue(stack.accumulated);
    }
  }

  public YielderStack<R> failure(final YielderStack<R> stack, @NotNull final Throwable failure,
      @NotNull final YieldOutputs<R> outputs) {
    outputs.yieldFailure(failure);
    return null;
  }

  public YielderStack<R> init() throws Exception {
    final YielderStack<R> stack = new YielderStack<R>();
    if (mHasInit) {
      stack.accumulated = mInit.get();
      stack.isAccumulated = true;
    }

    return stack;
  }

  public boolean loop(final YielderStack<R> stack) {
    return (stack != null);
  }

  @SuppressWarnings("unchecked")
  public YielderStack<R> value(final YielderStack<R> stack, final V value,
      @NotNull final YieldOutputs<R> outputs) throws Exception {
    if (!stack.isAccumulated) {
      stack.accumulated = (R) value;
      stack.isAccumulated = true;

    } else {
      stack.accumulated = mAccumulator.apply(stack.accumulated, value);
    }

    return stack;
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new YielderProxy<V, R>(mHasInit, mInit, mAccumulator);
  }

  static class YielderStack<R> {

    private R accumulated;

    private boolean isAccumulated;
  }

  private static class YielderProxy<V, R> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private YielderProxy(final boolean hasInit, final Provider<R> initialValue,
        final BiMapper<? super R, ? super V, ? extends R> accumulator) {
      super(hasInit, proxy(initialValue), proxy(accumulator));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new AccumulateYielder<V, R>((Boolean) args[0], (Provider<R>) args[1],
            (BiMapper<? super R, ? super V, ? extends R>) args[2]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

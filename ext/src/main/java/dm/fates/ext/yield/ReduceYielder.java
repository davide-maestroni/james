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

package dm.fates.ext.yield;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;

import dm.fates.eventual.Loop.YieldOutputs;
import dm.fates.eventual.LoopYielder;
import dm.fates.eventual.Provider;
import dm.fates.ext.config.BuildConfig;
import dm.fates.ext.eventual.BiMapper;
import dm.fates.ext.yield.ReduceYielder.YielderStack;
import dm.fates.util.ConstantConditions;
import dm.fates.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/26/2018.
 */
class ReduceYielder<V, R> implements LoopYielder<YielderStack<R>, V, R>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final BiMapper<? super R, ? super V, ? extends R> mAccumulate;

  private final boolean mHasInit;

  private final Provider<R> mInit;

  @SuppressWarnings("unchecked")
  ReduceYielder(@NotNull final BiMapper<? super V, ? super V, ? extends V> accumulate) {
    this(false, null, (BiMapper<? super R, ? super V, ? extends R>) accumulate);
  }

  ReduceYielder(@NotNull final Provider<R> init,
      @NotNull final BiMapper<? super R, ? super V, ? extends R> accumulate) {
    this(true, ConstantConditions.notNull("init", init), accumulate);
  }

  private ReduceYielder(final boolean hasInit, @Nullable final Provider<R> init,
      @NotNull final BiMapper<? super R, ? super V, ? extends R> accumulate) {
    mAccumulate = ConstantConditions.notNull("accumulate", accumulate);
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
      stack.accumulated = mAccumulate.apply(stack.accumulated, value);
    }

    return stack;
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new YielderProxy<V, R>(mHasInit, mInit, mAccumulate);
  }

  static class YielderStack<R> {

    private R accumulated;

    private boolean isAccumulated;
  }

  private static class YielderProxy<V, R> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private YielderProxy(final boolean hasInit, final Provider<R> initialValue,
        final BiMapper<? super R, ? super V, ? extends R> accumulate) {
      super(hasInit, proxy(initialValue), proxy(accumulate));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new ReduceYielder<V, R>((Boolean) args[0], (Provider<R>) args[1],
            (BiMapper<? super R, ? super V, ? extends R>) args[2]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

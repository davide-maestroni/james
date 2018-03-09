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

package dm.fates;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;

import dm.fates.config.BuildConfig;
import dm.fates.eventual.Loop.YieldOutputs;
import dm.fates.eventual.LoopYielder;
import dm.fates.eventual.Mapper;
import dm.fates.eventual.Provider;
import dm.fates.eventual.Settler;
import dm.fates.eventual.Updater;
import dm.fates.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/05/2018.
 */
class ComposedLoopYielder<S, V, R> extends ComposedYielder<S, V, YieldOutputs<R>>
    implements LoopYielder<S, V, R> {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Settler<S, ? super YieldOutputs<R>> mDone;

  private final Updater<S, ? super Throwable, ? super YieldOutputs<R>> mFailure;

  private final Provider<S> mInit;

  private final Mapper<S, ? extends Boolean> mLoop;

  private final Updater<S, ? super V, ? super YieldOutputs<R>> mValue;

  ComposedLoopYielder(@Nullable final Provider<S> init,
      @Nullable final Mapper<S, ? extends Boolean> loop,
      @Nullable final Updater<S, ? super V, ? super YieldOutputs<R>> value,
      @Nullable final Updater<S, ? super Throwable, ? super YieldOutputs<R>> failure,
      @Nullable final Settler<S, ? super YieldOutputs<R>> done) {
    super(init, loop, value, failure, done);
    mInit = init;
    mLoop = loop;
    mValue = value;
    mFailure = failure;
    mDone = done;
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new YielderProxy<S, V, R>(mInit, mLoop, mValue, mFailure, mDone);
  }

  private static class YielderProxy<S, V, R> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private YielderProxy(final Provider<S> init, final Mapper<S, ? extends Boolean> loop,
        final Updater<S, ? super V, ? super YieldOutputs<R>> value,
        final Updater<S, ? super Throwable, ? super YieldOutputs<R>> failure,
        final Settler<S, ? super YieldOutputs<R>> done) {
      super(proxy(init), proxy(loop), proxy(value), proxy(failure), proxy(done));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new ComposedLoopYielder<S, V, R>((Provider<S>) args[0],
            (Mapper<S, ? extends Boolean>) args[1],
            (Updater<S, ? super V, ? super YieldOutputs<R>>) args[2],
            (Updater<S, ? super Throwable, ? super YieldOutputs<R>>) args[3],
            (Settler<S, ? super YieldOutputs<R>>) args[4]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

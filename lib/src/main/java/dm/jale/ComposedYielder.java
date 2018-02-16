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

package dm.jale;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;

import dm.jale.async.Loop.YieldOutputs;
import dm.jale.async.Loop.Yielder;
import dm.jale.async.Mapper;
import dm.jale.async.Provider;
import dm.jale.async.Settler;
import dm.jale.async.Updater;
import dm.jale.config.BuildConfig;
import dm.jale.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/05/2018.
 */
class ComposedYielder<S, V, R> implements Yielder<S, V, R>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Settler<S, ? super YieldOutputs<R>> mDone;

  private final Updater<S, ? super Throwable, ? super YieldOutputs<R>> mFailure;

  private final Provider<S> mInit;

  private final Mapper<S, ? extends Boolean> mLoop;

  private final Updater<S, ? super V, ? super YieldOutputs<R>> mValue;

  @SuppressWarnings("unchecked")
  ComposedYielder(@Nullable final Provider<S> init,
      @Nullable final Mapper<S, ? extends Boolean> loop,
      @Nullable final Updater<S, ? super V, ? super YieldOutputs<R>> value,
      @Nullable final Updater<S, ? super Throwable, ? super YieldOutputs<R>> failure,
      @Nullable final Settler<S, ? super YieldOutputs<R>> done) {
    mInit = (Provider<S>) ((init != null) ? init : DefaultInit.sInstance);
    mLoop = (Mapper<S, ? extends Boolean>) ((loop != null) ? loop : DefaultLoop.sInstance);
    mValue = (Updater<S, ? super V, ? super YieldOutputs<R>>) ((value != null) ? value
        : DefaultUpdater.sInstance);
    mFailure = (Updater<S, ? super Throwable, ? super YieldOutputs<R>>) ((failure != null) ? failure
        : DefaultUpdater.sInstance);
    mDone =
        (Settler<S, ? super YieldOutputs<R>>) ((done != null) ? done : DefaultSettler.sInstance);
  }

  public void done(final S stack, @NotNull final YieldOutputs<R> outputs) throws Exception {
    mDone.complete(stack, outputs);
  }

  public S failure(final S stack, @NotNull final Throwable failure,
      @NotNull final YieldOutputs<R> outputs) throws Exception {
    return mFailure.update(stack, failure, outputs);
  }

  public S init() throws Exception {
    return mInit.get();
  }

  public boolean loop(final S stack) throws Exception {
    return mLoop.apply(stack);
  }

  public S value(final S stack, final V value, @NotNull final YieldOutputs<R> outputs) throws
      Exception {
    return mValue.update(stack, value, outputs);
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new YielderProxy<S, V, R>(mInit, mLoop, mValue, mFailure, mDone);
  }

  private static class DefaultInit<S> implements Provider<S>, Serializable {

    private static final DefaultInit<?> sInstance = new DefaultInit<Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    public S get() {
      return null;
    }

    @NotNull
    private Object readResolve() throws ObjectStreamException {
      return sInstance;
    }
  }

  private static class DefaultLoop<S> implements Mapper<S, Boolean>, Serializable {

    private static final DefaultLoop<?> sInstance = new DefaultLoop<Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    public Boolean apply(final S input) {
      return Boolean.TRUE;
    }

    @NotNull
    private Object readResolve() throws ObjectStreamException {
      return sInstance;
    }
  }

  private static class DefaultSettler<S, V> implements Settler<S, YieldOutputs<V>>, Serializable {

    private static final DefaultSettler<?, ?> sInstance = new DefaultSettler<Object, Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    public void complete(final S stack, @NotNull final YieldOutputs<V> state) {
    }

    @NotNull
    private Object readResolve() throws ObjectStreamException {
      return sInstance;
    }
  }

  private static class DefaultUpdater<S, V, I>
      implements Updater<S, I, YieldOutputs<V>>, Serializable {

    private static final DefaultUpdater<?, ?, ?> sInstance =
        new DefaultUpdater<Object, Object, Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    public S update(final S stack, final I value, @NotNull final YieldOutputs<V> outputs) {
      return stack;
    }

    @NotNull
    private Object readResolve() throws ObjectStreamException {
      return sInstance;
    }
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
        return new ComposedYielder<S, V, R>((Provider<S>) args[0],
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

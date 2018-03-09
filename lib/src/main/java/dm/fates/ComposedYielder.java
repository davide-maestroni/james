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
import java.io.Serializable;

import dm.fates.config.BuildConfig;
import dm.fates.eventual.Loop.YieldOutputs;
import dm.fates.eventual.Loop.Yielder;
import dm.fates.eventual.Provider;
import dm.fates.eventual.Settler;
import dm.fates.eventual.Tester;
import dm.fates.eventual.Updater;
import dm.fates.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/05/2018.
 */
class ComposedYielder<S, V, O> implements Yielder<S, V, O>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Settler<S, ? super O> mDone;

  private final Updater<S, ? super Throwable, ? super O> mFailure;

  private final Provider<S> mInit;

  private final Tester<S> mLoop;

  private final Updater<S, ? super V, ? super O> mValue;

  @SuppressWarnings("unchecked")
  ComposedYielder(@Nullable final Provider<S> init, @Nullable final Tester<S> loop,
      @Nullable final Updater<S, ? super V, ? super O> value,
      @Nullable final Updater<S, ? super Throwable, ? super O> failure,
      @Nullable final Settler<S, ? super O> done) {
    mInit = (Provider<S>) ((init != null) ? init : DefaultInit.sInstance);
    mLoop = (Tester<S>) ((loop != null) ? loop : DefaultLoop.sInstance);
    mValue =
        (Updater<S, ? super V, ? super O>) ((value != null) ? value : DefaultUpdater.sInstance);
    mFailure = (Updater<S, ? super Throwable, ? super O>) ((failure != null) ? failure
        : DefaultUpdater.sInstance);
    mDone = (Settler<S, ? super O>) ((done != null) ? done : DefaultSettler.sInstance);
  }

  public void done(final S stack, @NotNull final O outputs) throws Exception {
    mDone.complete(stack, outputs);
  }

  public S failure(final S stack, @NotNull final Throwable failure, @NotNull final O outputs) throws
      Exception {
    return mFailure.update(stack, failure, outputs);
  }

  public S init() throws Exception {
    return mInit.get();
  }

  public boolean loop(final S stack) throws Exception {
    return mLoop.test(stack);
  }

  public S value(final S stack, final V value, @NotNull final O outputs) throws Exception {
    return mValue.update(stack, value, outputs);
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new YielderProxy<S, V, O>(mInit, mLoop, mValue, mFailure, mDone);
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

  private static class DefaultLoop<S> implements Tester<S>, Serializable {

    private static final DefaultLoop<?> sInstance = new DefaultLoop<Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    public boolean test(final S input) throws Exception {
      return true;
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

    private YielderProxy(final Provider<S> init, final Tester<S> loop,
        final Updater<S, ? super V, ? super R> value,
        final Updater<S, ? super Throwable, ? super R> failure, final Settler<S, ? super R> done) {
      super(proxy(init), proxy(loop), proxy(value), proxy(failure), proxy(done));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new ComposedYielder<S, V, R>((Provider<S>) args[0], (Tester<S>) args[1],
            (Updater<S, ? super V, ? super R>) args[2],
            (Updater<S, ? super Throwable, ? super R>) args[3], (Settler<S, ? super R>) args[4]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

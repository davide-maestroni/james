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

package dm.jail;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;

import dm.jail.async.AsyncLoop.YieldCompleter;
import dm.jail.async.AsyncLoop.YieldResults;
import dm.jail.async.AsyncLoop.YieldUpdater;
import dm.jail.async.AsyncLoop.Yielder;
import dm.jail.async.Mapper;
import dm.jail.async.Provider;
import dm.jail.config.BuildConfig;
import dm.jail.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/05/2018.
 */
class ComposedYielder<S, V, R> implements Yielder<S, V, R>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final YieldCompleter<S, ? super YieldResults<R>> mDone;

  private final YieldUpdater<S, ? super Throwable, ? super YieldResults<R>> mFailure;

  private final Provider<S> mInit;

  private final Mapper<S, ? extends Boolean> mLoop;

  private final YieldUpdater<S, ? super V, ? super YieldResults<R>> mValue;

  @SuppressWarnings("unchecked")
  ComposedYielder(@Nullable final Provider<S> init,
      @Nullable final Mapper<S, ? extends Boolean> loop,
      @Nullable final YieldUpdater<S, ? super V, ? super YieldResults<R>> value,
      @Nullable final YieldUpdater<S, ? super Throwable, ? super YieldResults<R>> failure,
      @Nullable final YieldCompleter<S, ? super YieldResults<R>> done) {
    mInit = (Provider<S>) ((init != null) ? init : DefaultInit.sInstance);
    mLoop = (Mapper<S, ? extends Boolean>) ((loop != null) ? loop : DefaultLoop.sInstance);
    mValue = (YieldUpdater<S, ? super V, ? super YieldResults<R>>) ((value != null) ? value
        : DefaultUpdateValue.sInstance);
    mFailure =
        (YieldUpdater<S, ? super Throwable, ? super YieldResults<R>>) ((failure != null) ? failure
            : DefaultUpdateValue.sInstance);
    mDone = (YieldCompleter<S, ? super YieldResults<R>>) ((done != null) ? done
        : DefaultComplete.sInstance);
  }

  public void done(final S stack, @NotNull final YieldResults<R> results) throws Exception {
    mDone.complete(stack, results);
  }

  public S failure(final S stack, @NotNull final Throwable failure,
      @NotNull final YieldResults<R> results) throws Exception {
    return mFailure.update(stack, failure, results);
  }

  public S init() throws Exception {
    return mInit.get();
  }

  public boolean loop(final S stack) throws Exception {
    return mLoop.apply(stack);
  }

  public S value(final S stack, final V value, @NotNull final YieldResults<R> results) throws
      Exception {
    return mValue.update(stack, value, results);
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new YielderProxy<S, V, R>(mInit, mLoop, mValue, mFailure, mDone);
  }

  private static class DefaultComplete<S, V>
      implements YieldCompleter<S, YieldResults<V>>, Serializable {

    private static final DefaultComplete<?, ?> sInstance = new DefaultComplete<Object, Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    public void complete(final S stack, @NotNull final YieldResults<V> state) {
    }

    @NotNull
    Object readResolve() throws ObjectStreamException {
      return sInstance;
    }
  }

  private static class DefaultInit<S> implements Provider<S>, Serializable {

    private static final DefaultInit<?> sInstance = new DefaultInit<Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    public S get() {
      return null;
    }

    @NotNull
    Object readResolve() throws ObjectStreamException {
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
    Object readResolve() throws ObjectStreamException {
      return sInstance;
    }
  }

  private static class DefaultUpdateValue<S, V, I>
      implements YieldUpdater<S, I, YieldResults<V>>, Serializable {

    private static final DefaultUpdateValue<?, ?, ?> sInstance =
        new DefaultUpdateValue<Object, Object, Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    public S update(final S stack, final I value, @NotNull final YieldResults<V> results) {
      return stack;
    }

    @NotNull
    Object readResolve() throws ObjectStreamException {
      return sInstance;
    }
  }

  private static class YielderProxy<S, V, R> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private YielderProxy(final Provider<S> init, final Mapper<S, ? extends Boolean> loop,
        final YieldUpdater<S, ? super V, ? super YieldResults<R>> value,
        final YieldUpdater<S, ? super Throwable, ? super YieldResults<R>> failure,
        final YieldCompleter<S, ? super YieldResults<R>> done) {
      super(proxy(init), proxy(loop), proxy(value), proxy(failure), proxy(done));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new ComposedYielder<S, V, R>((Provider<S>) args[0],
            (Mapper<S, ? extends Boolean>) args[1],
            (YieldUpdater<S, ? super V, ? super YieldResults<R>>) args[2],
            (YieldUpdater<S, ? super Throwable, ? super YieldResults<R>>) args[3],
            (YieldCompleter<S, ? super YieldResults<R>>) args[4]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

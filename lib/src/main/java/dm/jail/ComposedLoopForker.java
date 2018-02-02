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

import dm.jail.async.AsyncLoop;
import dm.jail.async.AsyncResultCollection;
import dm.jail.async.AsyncStatement.ForkCompleter;
import dm.jail.async.AsyncStatement.ForkUpdater;
import dm.jail.async.AsyncStatement.Forker;
import dm.jail.async.Mapper;
import dm.jail.config.BuildConfig;
import dm.jail.util.SerializableProxy;

/**
 * Created by davide-maestroni on 01/19/2018.
 */
class ComposedLoopForker<S, V>
    implements Forker<S, AsyncLoop<V>, V, AsyncResultCollection<V>>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final ForkCompleter<S, ? super AsyncLoop<V>> mDone;

  private final ForkUpdater<S, ? super AsyncLoop<V>, ? super Throwable> mFailure;

  private final Mapper<? super AsyncLoop<V>, S> mInit;

  private final ForkUpdater<S, ? super AsyncLoop<V>, ? super AsyncResultCollection<? extends V>>
      mLoop;

  private final ForkUpdater<S, ? super AsyncLoop<V>, ? super V> mValue;

  @SuppressWarnings("unchecked")
  ComposedLoopForker(@Nullable final Mapper<? super AsyncLoop<V>, S> init,
      @Nullable final ForkUpdater<S, ? super AsyncLoop<V>, ? super V> value,
      @Nullable final ForkUpdater<S, ? super AsyncLoop<V>, ? super Throwable> failure,
      @Nullable final ForkCompleter<S, ? super AsyncLoop<V>> done,
      @Nullable final ForkUpdater<S, ? super AsyncLoop<V>, ? super AsyncResultCollection<V>> loop) {
    mInit = (Mapper<? super AsyncLoop<V>, S>) ((init != null) ? init : DefaultInit.sInstance);
    mValue = (ForkUpdater<S, ? super AsyncLoop<V>, ? super V>) ((value != null) ? value
        : DefaultUpdateValue.sInstance);
    mFailure =
        (ForkUpdater<S, ? super AsyncLoop<V>, ? super Throwable>) ((failure != null) ? failure
            : DefaultUpdateValue.sInstance);
    mDone = (ForkCompleter<S, ? super AsyncLoop<V>>) ((done != null) ? done
        : DefaultComplete.sInstance);
    mLoop = (ForkUpdater<S, ? super AsyncLoop<V>, ? super AsyncResultCollection<? extends V>>) (
        (loop != null) ? loop : DefaultUpdateResult.sInstance);
  }

  public S done(@NotNull final AsyncLoop<V> loop, final S stack) throws Exception {
    return mDone.complete(loop, stack);
  }

  public S failure(@NotNull final AsyncLoop<V> loop, final S stack,
      @NotNull final Throwable failure) throws Exception {
    return mFailure.update(loop, stack, failure);
  }

  public S init(@NotNull final AsyncLoop<V> loop) throws Exception {
    return mInit.apply(loop);
  }

  public S statement(@NotNull final AsyncLoop<V> loop, final S stack,
      @NotNull final AsyncResultCollection<V> result) throws Exception {
    return mLoop.update(loop, stack, result);
  }

  public S value(@NotNull final AsyncLoop<V> loop, final S stack, final V value) throws Exception {
    return mValue.update(loop, stack, value);
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new BuffererProxy<S, V>(mInit, mValue, mFailure, mDone, mLoop);
  }

  private static class BuffererProxy<S, V> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private BuffererProxy(final Mapper<? super AsyncLoop<V>, S> init,
        final ForkUpdater<S, ? super AsyncLoop<V>, ? super V> value,
        final ForkUpdater<S, ? super AsyncLoop<V>, ? super Throwable> failure,
        final ForkCompleter<S, ? super AsyncLoop<V>> done,
        final ForkUpdater<S, ? super AsyncLoop<V>, ? super AsyncResultCollection<? extends V>>
            loop) {
      super(proxy(init), proxy(value), proxy(failure), proxy(done), proxy(loop));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new ComposedLoopForker<S, V>((Mapper<? super AsyncLoop<V>, S>) args[0],
            (ForkUpdater<S, ? super AsyncLoop<V>, ? super V>) args[1],
            (ForkUpdater<S, ? super AsyncLoop<V>, ? super Throwable>) args[2],
            (ForkCompleter<S, ? super AsyncLoop<V>>) args[3],
            (ForkUpdater<S, ? super AsyncLoop<V>, ? super AsyncResultCollection<? extends V>>)
                args[4]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }

  private static class DefaultComplete<S, V>
      implements ForkCompleter<S, AsyncLoop<V>>, Serializable {

    private static final DefaultComplete<?, ?> sInstance = new DefaultComplete<Object, Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    public S complete(@NotNull final AsyncLoop<V> loop, final S stack) {
      return stack;
    }

    @NotNull
    Object readResolve() throws ObjectStreamException {
      return sInstance;
    }
  }

  private static class DefaultInit<S, V> implements Mapper<AsyncLoop<V>, S>, Serializable {

    private static final DefaultInit<?, ?> sInstance = new DefaultInit<Object, Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    public S apply(final AsyncLoop<V> loop) {
      return null;
    }

    @NotNull
    Object readResolve() throws ObjectStreamException {
      return sInstance;
    }
  }

  private static class DefaultUpdateResult<S, V>
      implements ForkUpdater<S, AsyncLoop<V>, AsyncResultCollection<V>>, Serializable {

    private static final DefaultUpdateResult<?, ?> sInstance =
        new DefaultUpdateResult<Object, Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    public S update(@NotNull final AsyncLoop<V> loop, final S stack,
        final AsyncResultCollection<V> result) {
      result.addFailure(new UnsupportedOperationException()).set();
      return stack;
    }

    @NotNull
    Object readResolve() throws ObjectStreamException {
      return sInstance;
    }
  }

  private static class DefaultUpdateValue<S, V, I>
      implements ForkUpdater<S, AsyncLoop<V>, I>, Serializable {

    private static final DefaultUpdateValue<?, ?, ?> sInstance =
        new DefaultUpdateValue<Object, Object, Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    @NotNull
    Object readResolve() throws ObjectStreamException {
      return sInstance;
    }

    public S update(@NotNull final AsyncLoop<V> loop, final S stack, final I input) {
      return stack;
    }
  }
}

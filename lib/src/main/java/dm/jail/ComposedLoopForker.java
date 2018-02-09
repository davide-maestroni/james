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

import dm.jail.async.AsyncEvaluations;
import dm.jail.async.AsyncLoop;
import dm.jail.async.AsyncStatement.Forker;
import dm.jail.async.Completer;
import dm.jail.async.Mapper;
import dm.jail.async.Updater;
import dm.jail.config.BuildConfig;
import dm.jail.util.SerializableProxy;

/**
 * Created by davide-maestroni on 01/19/2018.
 */
class ComposedLoopForker<S, V>
    implements Forker<S, AsyncLoop<V>, V, AsyncEvaluations<V>>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Completer<S, ? super AsyncLoop<V>> mDone;

  private final Updater<S, ? super Throwable, ? super AsyncLoop<V>> mFailure;

  private final Mapper<? super AsyncLoop<V>, S> mInit;

  private final Updater<S, ? super AsyncEvaluations<? extends V>, ? super AsyncLoop<V>> mLoop;

  private final Updater<S, ? super V, ? super AsyncLoop<V>> mValue;

  @SuppressWarnings("unchecked")
  ComposedLoopForker(@Nullable final Mapper<? super AsyncLoop<V>, S> init,
      @Nullable final Updater<S, ? super V, ? super AsyncLoop<V>> value,
      @Nullable final Updater<S, ? super Throwable, ? super AsyncLoop<V>> failure,
      @Nullable final Completer<S, ? super AsyncLoop<V>> done,
      @Nullable final Updater<S, ? super AsyncEvaluations<V>, ? super AsyncLoop<V>> loop) {
    mInit = (Mapper<? super AsyncLoop<V>, S>) ((init != null) ? init : DefaultInit.sInstance);
    mValue = (Updater<S, ? super V, ? super AsyncLoop<V>>) ((value != null) ? value
        : DefaultUpdater.sInstance);
    mFailure = (Updater<S, ? super Throwable, ? super AsyncLoop<V>>) ((failure != null) ? failure
        : DefaultUpdater.sInstance);
    mDone =
        (Completer<S, ? super AsyncLoop<V>>) ((done != null) ? done : DefaultCompleter.sInstance);
    mLoop =
        (Updater<S, ? super AsyncEvaluations<? extends V>, ? super AsyncLoop<V>>) ((loop != null)
            ? loop : DefaultEvaluationUpdater.sInstance);
  }

  public S done(final S stack, @NotNull final AsyncLoop<V> loop) throws Exception {
    return mDone.complete(stack, loop);
  }

  public S evaluation(final S stack, @NotNull final AsyncEvaluations<V> evaluation,
      @NotNull final AsyncLoop<V> loop) throws Exception {
    return mLoop.update(stack, evaluation, loop);
  }

  public S failure(final S stack, @NotNull final Throwable failure,
      @NotNull final AsyncLoop<V> loop) throws Exception {
    return mFailure.update(stack, failure, loop);
  }

  public S init(@NotNull final AsyncLoop<V> loop) throws Exception {
    return mInit.apply(loop);
  }

  public S value(final S stack, final V value, @NotNull final AsyncLoop<V> loop) throws Exception {
    return mValue.update(stack, value, loop);
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new BuffererProxy<S, V>(mInit, mValue, mFailure, mDone, mLoop);
  }

  private static class BuffererProxy<S, V> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private BuffererProxy(final Mapper<? super AsyncLoop<V>, S> init,
        final Updater<S, ? super V, ? super AsyncLoop<V>> value,
        final Updater<S, ? super Throwable, ? super AsyncLoop<V>> failure,
        final Completer<S, ? super AsyncLoop<V>> done,
        final Updater<S, ? super AsyncEvaluations<? extends V>, ? super AsyncLoop<V>> loop) {
      super(proxy(init), proxy(value), proxy(failure), proxy(done), proxy(loop));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new ComposedLoopForker<S, V>((Mapper<? super AsyncLoop<V>, S>) args[0],
            (Updater<S, ? super V, ? super AsyncLoop<V>>) args[1],
            (Updater<S, ? super Throwable, ? super AsyncLoop<V>>) args[2],
            (Completer<S, ? super AsyncLoop<V>>) args[3],
            (Updater<S, ? super AsyncEvaluations<? extends V>, ? super AsyncLoop<V>>) args[4]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }

  private static class DefaultCompleter<S, V> implements Completer<S, AsyncLoop<V>>, Serializable {

    private static final DefaultCompleter<?, ?> sInstance = new DefaultCompleter<Object, Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    public S complete(final S stack, @NotNull final AsyncLoop<V> loop) {
      return stack;
    }

    @NotNull
    Object readResolve() throws ObjectStreamException {
      return sInstance;
    }
  }

  private static class DefaultEvaluationUpdater<S, V>
      implements Updater<S, AsyncEvaluations<V>, AsyncLoop<V>>, Serializable {

    private static final DefaultEvaluationUpdater<?, ?> sInstance =
        new DefaultEvaluationUpdater<Object, Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    public S update(final S stack, final AsyncEvaluations<V> evaluation,
        @NotNull final AsyncLoop<V> loop) {
      evaluation.addFailure(new IllegalStateException("the loop cannot be chained")).set();
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

  private static class DefaultUpdater<S, V, I>
      implements Updater<S, I, AsyncLoop<V>>, Serializable {

    private static final DefaultUpdater<?, ?, ?> sInstance =
        new DefaultUpdater<Object, Object, Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    @NotNull
    Object readResolve() throws ObjectStreamException {
      return sInstance;
    }

    public S update(final S stack, final I input, @NotNull final AsyncLoop<V> loop) {
      return stack;
    }

  }
}

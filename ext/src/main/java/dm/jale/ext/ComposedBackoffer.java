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
import org.jetbrains.annotations.Nullable;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;

import dm.jale.async.Provider;
import dm.jale.async.Settler;
import dm.jale.async.Updater;
import dm.jale.ext.backoff.Backoffer;
import dm.jale.ext.backoff.PendingEvaluations;
import dm.jale.ext.config.BuildConfig;
import dm.jale.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/09/2018.
 */
class ComposedBackoffer<S, V> implements Backoffer<S, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Settler<S, ? super PendingEvaluations<V>> mDone;

  private final Updater<S, ? super Throwable, ? super PendingEvaluations<V>> mFailure;

  private final Provider<S> mInit;

  private final Updater<S, ? super V, ? super PendingEvaluations<V>> mValue;

  @SuppressWarnings("unchecked")
  ComposedBackoffer(@Nullable final Provider<S> init,
      @Nullable final Updater<S, ? super V, ? super PendingEvaluations<V>> value,
      @Nullable final Updater<S, ? super Throwable, ? super PendingEvaluations<V>> failure,
      @Nullable final Settler<S, ? super PendingEvaluations<V>> done) {
    mInit = (Provider<S>) ((init != null) ? init : DefaultInit.sInstance);
    mValue = (Updater<S, ? super V, ? super PendingEvaluations<V>>) ((value != null) ? value
        : DefaultUpdater.sInstance);
    mFailure =
        (Updater<S, ? super Throwable, ? super PendingEvaluations<V>>) ((failure != null) ? failure
            : DefaultUpdater.sInstance);
    mDone = (Settler<S, ? super PendingEvaluations<V>>) ((done != null) ? done
        : DefaultSettler.sInstance);
  }

  public void done(final S stack, @NotNull final PendingEvaluations<V> evaluations) throws
      Exception {
    mDone.complete(stack, evaluations);
  }

  public S failure(final S stack, @NotNull final Throwable failure,
      @NotNull final PendingEvaluations<V> evaluations) throws Exception {
    return mFailure.update(stack, failure, evaluations);
  }

  public S init() throws Exception {
    return mInit.get();
  }

  public S value(final S stack, final V value,
      @NotNull final PendingEvaluations<V> evaluations) throws Exception {
    return mValue.update(stack, value, evaluations);
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new ForkerProxy<S, V>(mInit, mValue, mFailure, mDone);
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

  private static class DefaultSettler<S, V>
      implements Settler<S, PendingEvaluations<V>>, Serializable {

    private static final DefaultSettler<?, ?> sInstance = new DefaultSettler<Object, Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    public void complete(final S stack, @NotNull final PendingEvaluations<V> evaluations) {
    }

    @NotNull
    private Object readResolve() throws ObjectStreamException {
      return sInstance;
    }
  }

  private static class DefaultUpdater<S, V, I>
      implements Updater<S, I, PendingEvaluations<V>>, Serializable {

    private static final DefaultUpdater<?, ?, ?> sInstance =
        new DefaultUpdater<Object, Object, Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    public S update(final S stack, final I input,
        @NotNull final PendingEvaluations<V> evaluations) {
      return stack;
    }

    @NotNull
    private Object readResolve() throws ObjectStreamException {
      return sInstance;
    }
  }

  private static class ForkerProxy<S, V> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private ForkerProxy(final Provider<S> init,
        final Updater<S, ? super V, ? super PendingEvaluations<V>> value,
        final Updater<S, ? super Throwable, ? super PendingEvaluations<V>> failure,
        final Settler<S, ? super PendingEvaluations<V>> done) {
      super(proxy(init), proxy(value), proxy(failure), proxy(done));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new ComposedBackoffer<S, V>((Provider<S>) args[0],
            (Updater<S, ? super V, ? super PendingEvaluations<V>>) args[2],
            (Updater<S, ? super Throwable, ? super PendingEvaluations<V>>) args[3],
            (Settler<S, ? super PendingEvaluations<V>>) args[4]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

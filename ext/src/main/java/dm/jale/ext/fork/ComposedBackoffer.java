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

package dm.jale.ext.fork;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ObjectStreamException;
import java.io.Serializable;

import dm.jale.async.Completer;
import dm.jale.async.Provider;
import dm.jale.async.Updater;
import dm.jale.ext.config.BuildConfig;

/**
 * Created by davide-maestroni on 02/09/2018.
 */
class ComposedBackoffer<S, V> implements Backoffer<S, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Completer<S, ? super PendingEvaluations<V>> mDone;

  private final Updater<S, ? super Throwable, ? super PendingEvaluations<V>> mFailure;

  private final Provider<S> mInit;

  private final Updater<S, ? super V, ? super PendingEvaluations<V>> mValue;

  @SuppressWarnings("unchecked")
  ComposedBackoffer(@Nullable final Provider<S> init,
      @Nullable final Updater<S, ? super V, ? super PendingEvaluations<V>> value,
      @Nullable final Updater<S, ? super Throwable, ? super PendingEvaluations<V>> failure,
      @Nullable final Completer<S, ? super PendingEvaluations<V>> done) {
    mInit = (Provider<S>) ((init != null) ? init : DefaultInit.sInstance);
    mValue = (Updater<S, ? super V, ? super PendingEvaluations<V>>) ((value != null) ? value
        : DefaultUpdater.sInstance);
    mFailure =
        (Updater<S, ? super Throwable, ? super PendingEvaluations<V>>) ((failure != null) ? failure
            : DefaultUpdater.sInstance);
    mDone = (Completer<S, ? super PendingEvaluations<V>>) ((done != null) ? done
        : DefaultCompleter.sInstance);
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

  private static class DefaultCompleter<S, V>
      implements Completer<S, PendingEvaluations<V>>, Serializable {

    private static final DefaultCompleter<?, ?> sInstance = new DefaultCompleter<Object, Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    public S complete(final S stack, @NotNull final PendingEvaluations<V> evaluations) {
      return stack;
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
    Object readResolve() throws ObjectStreamException {
      return sInstance;
    }
  }
}

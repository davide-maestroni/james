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
import dm.fates.eventual.Completer;
import dm.fates.eventual.EvaluationCollection;
import dm.fates.eventual.Loop;
import dm.fates.eventual.LoopForker;
import dm.fates.eventual.Mapper;
import dm.fates.eventual.Updater;
import dm.fates.util.SerializableProxy;

/**
 * Created by davide-maestroni on 01/19/2018.
 */
class ComposedLoopForker<S, V> implements LoopForker<S, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Completer<S, ? super Loop<V>> mDone;

  private final Updater<S, ? super EvaluationCollection<? extends V>, ? super Loop<V>> mEvaluation;

  private final Updater<S, ? super Throwable, ? super Loop<V>> mFailure;

  private final Mapper<? super Loop<V>, S> mInit;

  private final Updater<S, ? super V, ? super Loop<V>> mValue;

  @SuppressWarnings("unchecked")
  ComposedLoopForker(@Nullable final Mapper<? super Loop<V>, S> init,
      @Nullable final Updater<S, ? super V, ? super Loop<V>> value,
      @Nullable final Updater<S, ? super Throwable, ? super Loop<V>> failure,
      @Nullable final Completer<S, ? super Loop<V>> done,
      @Nullable final Updater<S, ? super EvaluationCollection<V>, ? super Loop<V>> evaluation) {
    mInit = (Mapper<? super Loop<V>, S>) ((init != null) ? init : DefaultInit.sInstance);
    mValue = (Updater<S, ? super V, ? super Loop<V>>) ((value != null) ? value
        : DefaultUpdater.sInstance);
    mFailure = (Updater<S, ? super Throwable, ? super Loop<V>>) ((failure != null) ? failure
        : DefaultUpdater.sInstance);
    mDone = (Completer<S, ? super Loop<V>>) ((done != null) ? done : DefaultCompleter.sInstance);
    mEvaluation = (Updater<S, ? super EvaluationCollection<? extends V>, ? super Loop<V>>) (
        (evaluation != null) ? evaluation : DefaultEvaluationUpdater.sInstance);
  }

  public S done(final S stack, @NotNull final Loop<V> context) throws Exception {
    return mDone.complete(stack, context);
  }

  public S evaluation(final S stack, @NotNull final EvaluationCollection<V> evaluation,
      @NotNull final Loop<V> context) throws Exception {
    return mEvaluation.update(stack, evaluation, context);
  }

  public S failure(final S stack, @NotNull final Throwable failure,
      @NotNull final Loop<V> context) throws Exception {
    return mFailure.update(stack, failure, context);
  }

  public S init(@NotNull final Loop<V> context) throws Exception {
    return mInit.apply(context);
  }

  public S value(final S stack, final V value, @NotNull final Loop<V> context) throws Exception {
    return mValue.update(stack, value, context);
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new ForkerProxy<S, V>(mInit, mValue, mFailure, mDone, mEvaluation);
  }

  private static class DefaultCompleter<S, V> implements Completer<S, Loop<V>>, Serializable {

    private static final DefaultCompleter<?, ?> sInstance = new DefaultCompleter<Object, Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    public S complete(final S stack, @NotNull final Loop<V> loop) {
      return stack;
    }

    @NotNull
    private Object readResolve() throws ObjectStreamException {
      return sInstance;
    }
  }

  private static class DefaultEvaluationUpdater<S, V>
      implements Updater<S, EvaluationCollection<V>, Loop<V>>, Serializable {

    private static final DefaultEvaluationUpdater<?, ?> sInstance =
        new DefaultEvaluationUpdater<Object, Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    public S update(final S stack, final EvaluationCollection<V> evaluation,
        @NotNull final Loop<V> loop) {
      evaluation.addFailure(new IllegalStateException("the loop evaluation cannot be propagated"))
          .set();
      return stack;
    }

    @NotNull
    private Object readResolve() throws ObjectStreamException {
      return sInstance;
    }
  }

  private static class DefaultInit<S, V> implements Mapper<Loop<V>, S>, Serializable {

    private static final DefaultInit<?, ?> sInstance = new DefaultInit<Object, Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    public S apply(final Loop<V> loop) {
      return null;
    }

    @NotNull
    private Object readResolve() throws ObjectStreamException {
      return sInstance;
    }
  }

  private static class DefaultUpdater<S, V, I> implements Updater<S, I, Loop<V>>, Serializable {

    private static final DefaultUpdater<?, ?, ?> sInstance =
        new DefaultUpdater<Object, Object, Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    @NotNull
    private Object readResolve() throws ObjectStreamException {
      return sInstance;
    }

    public S update(final S stack, final I input, @NotNull final Loop<V> loop) {
      return stack;
    }
  }

  private static class ForkerProxy<S, V> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private ForkerProxy(final Mapper<? super Loop<V>, S> init,
        final Updater<S, ? super V, ? super Loop<V>> value,
        final Updater<S, ? super Throwable, ? super Loop<V>> failure,
        final Completer<S, ? super Loop<V>> done,
        final Updater<S, ? super EvaluationCollection<? extends V>, ? super Loop<V>> loop) {
      super(proxy(init), proxy(value), proxy(failure), proxy(done), proxy(loop));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new ComposedLoopForker<S, V>((Mapper<? super Loop<V>, S>) args[0],
            (Updater<S, ? super V, ? super Loop<V>>) args[1],
            (Updater<S, ? super Throwable, ? super Loop<V>>) args[2],
            (Completer<S, ? super Loop<V>>) args[3],
            (Updater<S, ? super EvaluationCollection<? extends V>, ? super Loop<V>>) args[4]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

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

import dm.jale.async.Completer;
import dm.jale.async.Evaluation;
import dm.jale.async.Mapper;
import dm.jale.async.Statement;
import dm.jale.async.StatementForker;
import dm.jale.async.Updater;
import dm.jale.config.BuildConfig;
import dm.jale.util.SerializableProxy;

/**
 * Created by davide-maestroni on 01/19/2018.
 */
class ComposedStatementForker<S, V> implements StatementForker<S, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Completer<S, ? super Statement<V>> mDone;

  private final Updater<S, ? super Evaluation<? extends V>, ? super Statement<V>> mEvaluation;

  private final Updater<S, ? super Throwable, ? super Statement<V>> mFailure;

  private final Mapper<? super Statement<V>, S> mInit;

  private final Updater<S, ? super V, ? super Statement<V>> mValue;

  @SuppressWarnings("unchecked")
  ComposedStatementForker(@Nullable final Mapper<? super Statement<V>, S> init,
      @Nullable final Updater<S, ? super V, ? super Statement<V>> value,
      @Nullable final Updater<S, ? super Throwable, ? super Statement<V>> failure,
      @Nullable final Completer<S, ? super Statement<V>> done,
      @Nullable final Updater<S, ? super Evaluation<V>, ? super Statement<V>> evaluation) {
    mInit = (Mapper<? super Statement<V>, S>) ((init != null) ? init : DefaultInit.sInstance);
    mValue = (Updater<S, ? super V, ? super Statement<V>>) ((value != null) ? value
        : DefaultUpdater.sInstance);
    mFailure = (Updater<S, ? super Throwable, ? super Statement<V>>) ((failure != null) ? failure
        : DefaultUpdater.sInstance);
    mDone =
        (Completer<S, ? super Statement<V>>) ((done != null) ? done : DefaultCompleter.sInstance);
    mEvaluation =
        (Updater<S, ? super Evaluation<? extends V>, ? super Statement<V>>) ((evaluation != null)
            ? evaluation : DefaultEvaluationUpdater.sInstance);
  }

  public S done(final S stack, @NotNull final Statement<V> context) throws Exception {
    return mDone.complete(stack, context);
  }

  public S evaluation(final S stack, @NotNull final Evaluation<V> evaluation,
      @NotNull final Statement<V> context) throws Exception {
    return mEvaluation.update(stack, evaluation, context);
  }

  public S failure(final S stack, @NotNull final Throwable failure,
      @NotNull final Statement<V> context) throws Exception {
    return mFailure.update(stack, failure, context);
  }

  public S init(@NotNull final Statement<V> context) throws Exception {
    return mInit.apply(context);
  }

  public S value(final S stack, final V value, @NotNull final Statement<V> context) throws
      Exception {
    return mValue.update(stack, value, context);
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new ForkerProxy<S, V>(mInit, mValue, mFailure, mDone, mEvaluation);
  }

  private static class DefaultCompleter<S, V> implements Completer<S, Statement<V>>, Serializable {

    private static final DefaultCompleter<?, ?> sInstance = new DefaultCompleter<Object, Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    public S complete(final S stack, @NotNull final Statement<V> statement) {
      return stack;
    }

    @NotNull
    private Object readResolve() throws ObjectStreamException {
      return sInstance;
    }
  }

  private static class DefaultEvaluationUpdater<S, V>
      implements Updater<S, Evaluation<V>, Statement<V>>, Serializable {

    private static final DefaultEvaluationUpdater<?, ?> sInstance =
        new DefaultEvaluationUpdater<Object, Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    public S update(final S stack, final Evaluation<V> evaluation,
        @NotNull final Statement<V> statement) {
      evaluation.fail(new IllegalStateException("the statement evaluation cannot be propagated"));
      return stack;
    }

    @NotNull
    private Object readResolve() throws ObjectStreamException {
      return sInstance;
    }
  }

  private static class DefaultInit<S, V> implements Mapper<Statement<V>, S>, Serializable {

    private static final DefaultInit<?, ?> sInstance = new DefaultInit<Object, Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    public S apply(final Statement<V> statement) {
      return null;
    }

    @NotNull
    private Object readResolve() throws ObjectStreamException {
      return sInstance;
    }
  }

  private static class DefaultUpdater<S, V, I>
      implements Updater<S, I, Statement<V>>, Serializable {

    private static final DefaultUpdater<?, ?, ?> sInstance =
        new DefaultUpdater<Object, Object, Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    @NotNull
    private Object readResolve() throws ObjectStreamException {
      return sInstance;
    }

    public S update(final S stack, final I input, @NotNull final Statement<V> statement) {
      return stack;
    }
  }

  private static class ForkerProxy<S, V> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private ForkerProxy(final Mapper<? super Statement<V>, S> init,
        final Updater<S, ? super V, ? super Statement<V>> value,
        final Updater<S, ? super Throwable, ? super Statement<V>> failure,
        final Completer<S, ? super Statement<V>> done,
        final Updater<S, ? super Evaluation<? extends V>, ? super Statement<V>> statement) {
      super(proxy(init), proxy(value), proxy(failure), proxy(done), proxy(statement));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new ComposedStatementForker<S, V>((Mapper<? super Statement<V>, S>) args[0],
            (Updater<S, ? super V, ? super Statement<V>>) args[1],
            (Updater<S, ? super Throwable, ? super Statement<V>>) args[2],
            (Completer<S, ? super Statement<V>>) args[3],
            (Updater<S, ? super Evaluation<? extends V>, ? super Statement<V>>) args[4]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

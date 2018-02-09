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

import dm.jail.async.AsyncEvaluation;
import dm.jail.async.AsyncStatement;
import dm.jail.async.AsyncStatement.Forker;
import dm.jail.async.Completer;
import dm.jail.async.Mapper;
import dm.jail.async.Updater;
import dm.jail.config.BuildConfig;
import dm.jail.util.SerializableProxy;

/**
 * Created by davide-maestroni on 01/19/2018.
 */
class ComposedStatementForker<S, V>
    implements Forker<S, AsyncStatement<V>, V, AsyncEvaluation<V>>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Completer<S, ? super AsyncStatement<V>> mDone;

  private final Updater<S, ? super Throwable, ? super AsyncStatement<V>> mFailure;

  private final Mapper<? super AsyncStatement<V>, S> mInit;

  private final Updater<S, ? super AsyncEvaluation<? extends V>, ? super AsyncStatement<V>>
      mStatement;

  private final Updater<S, ? super V, ? super AsyncStatement<V>> mValue;

  @SuppressWarnings("unchecked")
  ComposedStatementForker(@Nullable final Mapper<? super AsyncStatement<V>, S> init,
      @Nullable final Updater<S, ? super V, ? super AsyncStatement<V>> value,
      @Nullable final Updater<S, ? super Throwable, ? super AsyncStatement<V>> failure,
      @Nullable final Completer<S, ? super AsyncStatement<V>> done,
      @Nullable final Updater<S, ? super AsyncEvaluation<V>, ? super AsyncStatement<V>> statement) {
    mInit = (Mapper<? super AsyncStatement<V>, S>) ((init != null) ? init : DefaultInit.sInstance);
    mValue = (Updater<S, ? super V, ? super AsyncStatement<V>>) ((value != null) ? value
        : DefaultUpdater.sInstance);
    mFailure =
        (Updater<S, ? super Throwable, ? super AsyncStatement<V>>) ((failure != null) ? failure
            : DefaultUpdater.sInstance);
    mDone = (Completer<S, ? super AsyncStatement<V>>) ((done != null) ? done
        : DefaultCompleter.sInstance);
    mStatement = (Updater<S, ? super AsyncEvaluation<? extends V>, ? super AsyncStatement<V>>) (
        (statement != null) ? statement : DefaultEvaluationUpdater.sInstance);
  }

  public S done(final S stack, @NotNull final AsyncStatement<V> statement) throws Exception {
    return mDone.complete(stack, statement);
  }

  public S evaluation(final S stack, @NotNull final AsyncEvaluation<V> evaluation,
      @NotNull final AsyncStatement<V> statement) throws Exception {
    return mStatement.update(stack, evaluation, statement);
  }

  public S failure(final S stack, @NotNull final Throwable failure,
      @NotNull final AsyncStatement<V> statement) throws Exception {
    return mFailure.update(stack, failure, statement);
  }

  public S init(@NotNull final AsyncStatement<V> statement) throws Exception {
    return mInit.apply(statement);
  }

  public S value(final S stack, final V value, @NotNull final AsyncStatement<V> statement) throws
      Exception {
    return mValue.update(stack, value, statement);
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new ForkerProxy<S, V>(mInit, mValue, mFailure, mDone, mStatement);
  }

  private static class DefaultCompleter<S, V>
      implements Completer<S, AsyncStatement<V>>, Serializable {

    private static final DefaultCompleter<?, ?> sInstance = new DefaultCompleter<Object, Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    public S complete(final S stack, @NotNull final AsyncStatement<V> statement) {
      return stack;
    }

    @NotNull
    Object readResolve() throws ObjectStreamException {
      return sInstance;
    }
  }

  private static class DefaultEvaluationUpdater<S, V>
      implements Updater<S, AsyncEvaluation<V>, AsyncStatement<V>>, Serializable {

    private static final DefaultEvaluationUpdater<?, ?> sInstance =
        new DefaultEvaluationUpdater<Object, Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    public S update(final S stack, final AsyncEvaluation<V> evaluation,
        @NotNull final AsyncStatement<V> statement) {
      evaluation.fail(new IllegalStateException("the statement cannot be chained"));
      return stack;
    }

    @NotNull
    Object readResolve() throws ObjectStreamException {
      return sInstance;
    }
  }

  private static class DefaultInit<S, V> implements Mapper<AsyncStatement<V>, S>, Serializable {

    private static final DefaultInit<?, ?> sInstance = new DefaultInit<Object, Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    public S apply(final AsyncStatement<V> statement) {
      return null;
    }

    @NotNull
    Object readResolve() throws ObjectStreamException {
      return sInstance;
    }
  }

  private static class DefaultUpdater<S, V, I>
      implements Updater<S, I, AsyncStatement<V>>, Serializable {

    private static final DefaultUpdater<?, ?, ?> sInstance =
        new DefaultUpdater<Object, Object, Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    @NotNull
    Object readResolve() throws ObjectStreamException {
      return sInstance;
    }

    public S update(final S stack, final I input, @NotNull final AsyncStatement<V> statement) {
      return stack;
    }
  }

  private static class ForkerProxy<S, V> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private ForkerProxy(final Mapper<? super AsyncStatement<V>, S> init,
        final Updater<S, ? super V, ? super AsyncStatement<V>> value,
        final Updater<S, ? super Throwable, ? super AsyncStatement<V>> failure,
        final Completer<S, ? super AsyncStatement<V>> done,
        final Updater<S, ? super AsyncEvaluation<? extends V>, ? super AsyncStatement<V>>
            statement) {
      super(proxy(init), proxy(value), proxy(failure), proxy(done), proxy(statement));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new ComposedStatementForker<S, V>((Mapper<? super AsyncStatement<V>, S>) args[0],
            (Updater<S, ? super V, ? super AsyncStatement<V>>) args[1],
            (Updater<S, ? super Throwable, ? super AsyncStatement<V>>) args[2],
            (Completer<S, ? super AsyncStatement<V>>) args[3],
            (Updater<S, ? super AsyncEvaluation<? extends V>, ? super AsyncStatement<V>>) args[4]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

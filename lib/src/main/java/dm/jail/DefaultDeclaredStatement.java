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

import java.io.Closeable;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import dm.jail.async.Action;
import dm.jail.async.AsyncResult;
import dm.jail.async.AsyncResultCollection;
import dm.jail.async.AsyncStatement;
import dm.jail.async.DeclaredStatement;
import dm.jail.async.FailureException;
import dm.jail.async.Mapper;
import dm.jail.async.Observer;
import dm.jail.executor.ScheduledExecutor;

/**
 * Created by davide-maestroni on 01/18/2018.
 */
class DefaultDeclaredStatement<V> implements DeclaredStatement<V> {

  private final boolean mAutoEvaluate;

  private final AsyncStatement<V> mStatement;

  DefaultDeclaredStatement(
      @NotNull final Mapper<Observer<AsyncResult<V>>, AsyncStatement<V>> factory) {
    final DeferredObserver<V> observer = new DeferredObserver<V>();
    try {
      mStatement = factory.apply(observer);

    } catch (final Exception e) {
      throw FailureException.wrap(e);
    }

    mAutoEvaluate = false;
  }

  private DefaultDeclaredStatement(@NotNull final AsyncStatement<V> statement,
      boolean autoEvaluate) {
    mStatement = statement;
    mAutoEvaluate = autoEvaluate;
  }

  public void addTo(@NotNull final AsyncResultCollection<? super V> result) {
    mStatement.addTo(result);
  }

  @NotNull
  public Throwable failure() {
    return mStatement.failure();
  }

  public boolean isCancelled() {
    return mStatement.isCancelled();
  }

  public boolean isEvaluating() {
    return mStatement.isEvaluating();
  }

  public boolean isFailed() {
    return mStatement.isFailed();
  }

  public boolean isSet() {
    return mStatement.isSet();
  }

  public void to(@NotNull final AsyncResult<? super V> result) {
    throw new UnsupportedOperationException();
  }

  public V value() {
    return mStatement.value();
  }

  @NotNull
  public DeclaredStatement<V> autoEvaluate() {
    return new DefaultDeclaredStatement<V>(mStatement, true);
  }

  @NotNull
  public DeclaredStatement<V> elseCatch(
      @NotNull final Mapper<? super Throwable, ? extends V> mapper,
      @Nullable final Class<?>[] exceptionTypes) {
    return newInstance(mStatement.elseCatch(mapper, exceptionTypes));
  }

  @NotNull
  public DeclaredStatement<V> elseDo(@NotNull final Observer<? super Throwable> observer,
      @Nullable final Class<?>[] exceptionTypes) {
    return newInstance(mStatement.elseDo(observer, exceptionTypes));
  }

  @NotNull
  public DeclaredStatement<V> elseIf(
      @NotNull final Mapper<? super Throwable, ? extends AsyncStatement<? extends V>> mapper,
      @Nullable final Class<?>[] exceptionTypes) {
    return newInstance(mStatement.elseIf(mapper, exceptionTypes));
  }

  @NotNull
  public <S> DeclaredStatement<V> fork(
      @NotNull final Forker<S, ? super AsyncStatement<V>, ? super V, ? super AsyncResult<V>>
          forker) {
    return newInstance(mStatement.fork(forker));
  }

  @NotNull
  public <S> DeclaredStatement<V> fork(@Nullable final Mapper<? super AsyncStatement<V>, S> init,
      @Nullable final ForkUpdater<S, ? super AsyncStatement<V>, ? super V> value,
      @Nullable final ForkUpdater<S, ? super AsyncStatement<V>, ? super Throwable> failure,
      @Nullable final ForkCompleter<S, ? super AsyncStatement<V>> done,
      @Nullable final ForkUpdater<S, ? super AsyncStatement<V>, ? super AsyncResult<V>> statement) {
    return newInstance(mStatement.fork(init, value, failure, done, statement));
  }

  @NotNull
  public DeclaredStatement<V> on(@NotNull final ScheduledExecutor executor) {
    return newInstance(mStatement.on(executor));
  }

  @NotNull
  public <R> DeclaredStatement<R> then(@NotNull final Mapper<? super V, R> mapper) {
    return newInstance(mStatement.then(mapper));
  }

  @NotNull
  public DeclaredStatement<V> thenDo(@NotNull final Observer<? super V> observer) {
    return newInstance(mStatement.thenDo(observer));
  }

  @NotNull
  public <R> DeclaredStatement<R> thenIf(
      @NotNull final Mapper<? super V, ? extends AsyncStatement<R>> mapper) {
    return newInstance(mStatement.thenIf(mapper));
  }

  @NotNull
  public <R> DeclaredStatement<R> thenTry(
      @NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final Mapper<? super V, R> mapper) {
    return newInstance(mStatement.thenTry(closeable, mapper));
  }

  @NotNull
  public DeclaredStatement<V> thenTryDo(
      @NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final Observer<? super V> observer) {
    return newInstance(mStatement.thenTryDo(closeable, observer));
  }

  @NotNull
  public <R> DeclaredStatement<R> thenTryIf(
      @NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final Mapper<? super V, ? extends AsyncStatement<R>> mapper) {
    return newInstance(mStatement.thenTryIf(closeable, mapper));
  }

  @NotNull
  public DeclaredStatement<V> whenDone(@NotNull final Action action) {
    return newInstance(mStatement.whenDone(action));
  }

  public boolean cancel(final boolean mayInterruptIfRunning) {
    return mStatement.cancel(mayInterruptIfRunning);
  }

  public boolean isDone() {
    return mStatement.isDone();
  }

  public V get() throws InterruptedException, ExecutionException {
    throw new UnsupportedOperationException();
  }

  public V get(final long timeout, @NotNull final TimeUnit timeUnit) throws InterruptedException,
      ExecutionException, TimeoutException {
    throw new UnsupportedOperationException();
  }

  @NotNull
  public AsyncStatement<V> evaluate() {
    return mStatement.evaluate();
  }

  @Nullable
  public FailureException getFailure() {
    throw new UnsupportedOperationException();
  }

  @Nullable
  public FailureException getFailure(final long timeout, @NotNull final TimeUnit timeUnit) {
    throw new UnsupportedOperationException();
  }

  public V getValue() {
    throw new UnsupportedOperationException();
  }

  public V getValue(final long timeout, @NotNull final TimeUnit timeUnit) {
    throw new UnsupportedOperationException();
  }

  public boolean isFinal() {
    return mStatement.isFinal();
  }

  public void waitDone() {
    throw new UnsupportedOperationException();
  }

  public boolean waitDone(final long timeout, @NotNull final TimeUnit timeUnit) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  private <R> DeclaredStatement<R> newInstance(@NotNull final AsyncStatement<R> statement) {
    final AsyncStatement<R> newStatement = (mAutoEvaluate) ? statement.evaluate() : statement;
    return new DefaultDeclaredStatement<R>(newStatement, false);
  }

  private static class DeferredObserver<V>
      implements RenewableObserver<AsyncResult<V>>, Serializable {

    public void accept(final AsyncResult<V> result) {
    }

    @NotNull
    @SuppressWarnings("unchecked")
    public NullObserver<V> renew() {
      return (NullObserver<V>) NullObserver.sInstance;
    }
  }

  private static class NullObserver<V> implements Observer<AsyncResult<V>>, Serializable {

    private static final NullObserver<?> sInstance = new NullObserver<Object>();

    @NotNull
    Object readResolve() throws ObjectStreamException {
      return sInstance;
    }

    public void accept(final AsyncResult<V> result) {
      result.set(null);
    }
  }
}

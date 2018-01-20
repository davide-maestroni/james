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
import java.io.Serializable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import dm.jail.async.Action;
import dm.jail.async.AsyncResult;
import dm.jail.async.AsyncResultCollection;
import dm.jail.async.AsyncStatement;
import dm.jail.async.DeferredStatement;
import dm.jail.async.FailureException;
import dm.jail.async.Mapper;
import dm.jail.async.Observer;
import dm.jail.executor.ScheduledExecutor;

/**
 * Created by davide-maestroni on 01/18/2018.
 */
class DefaultDeferredStatement<V> implements DeferredStatement<V> {

  private final Mapper<?, ?> mFactory;

  private final DeferredObserver<?> mObserver;

  private final AsyncStatement<V> mStatement;

  DefaultDeferredStatement(
      @NotNull final Mapper<Observer<AsyncResult<V>>, AsyncStatement<V>> factory) {
    final DeferredObserver<V> observer = new DeferredObserver<V>();
    try {
      mStatement = factory.apply(observer);

    } catch (final Exception e) {
      throw FailureException.wrap(e);
    }

    mFactory = factory;
    mObserver = observer;
  }

  private DefaultDeferredStatement(@NotNull final Mapper<?, ?> factory,
      @NotNull final DeferredObserver<?> observer, @NotNull final AsyncStatement<V> statement) {
    mFactory = factory;
    mObserver = observer;
    mStatement = statement;
  }

  public boolean cancel(final boolean mayInterruptIfRunning) {
    return mStatement.cancel(mayInterruptIfRunning);
  }

  public boolean isDone() {
    return mStatement.isDone();
  }

  public V get() throws InterruptedException, ExecutionException {
    return mStatement.get();
  }

  public V get(final long timeout, @NotNull final TimeUnit timeUnit) throws InterruptedException,
      ExecutionException, TimeoutException {
    return mStatement.get(timeout, timeUnit);
  }

  @NotNull
  public DeferredStatement<V> elseCatch(
      @NotNull final Mapper<? super Throwable, ? extends V> mapper,
      @Nullable final Class<?>[] exceptionTypes) {
    return newInstance(mStatement.elseCatch(mapper, exceptionTypes));
  }

  @NotNull
  public DeferredStatement<V> elseDo(@NotNull final Observer<? super Throwable> observer,
      @Nullable final Class<?>[] exceptionTypes) {
    return newInstance(mStatement.elseDo(observer, exceptionTypes));
  }

  @NotNull
  public DeferredStatement<V> elseIf(
      @NotNull final Mapper<? super Throwable, ? extends AsyncStatement<? extends V>> mapper,
      @Nullable final Class<?>[] exceptionTypes) {
    return newInstance(mStatement.elseIf(mapper, exceptionTypes));
  }

  @NotNull
  public <S> DeferredStatement<V> fork(
      @NotNull final Forker<S, ? super AsyncStatement<V>, ? super V, ? extends V> forker) {
    return newInstance(mStatement.fork(forker));
  }

  @NotNull
  public <S> DeferredStatement<V> fork(@Nullable final Mapper<? super AsyncStatement<V>, S> init,
      @Nullable final ForkUpdater<S, ? super AsyncStatement<V>, ? super V> value,
      @Nullable final ForkUpdater<S, ? super AsyncStatement<V>, ? super Throwable> failure,
      @Nullable final ForkUpdater<S, ? super AsyncStatement<V>, ? super AsyncResult<? extends V>>
          statement,
      @Nullable final ForkUpdater<S, ? super AsyncStatement<V>, ? super AsyncResultCollection<?
          extends V>> loop,
      @Nullable ForkCompleter<S, ? super AsyncStatement<V>> done) {
    return newInstance(mStatement.fork(init, value, failure, statement, loop, done));
  }

  @NotNull
  public DeferredStatement<V> on(@NotNull final ScheduledExecutor executor) {
    return newInstance(mStatement.on(executor));
  }

  @NotNull
  public AsyncStatement<V> renew() {
    evaluate();
    return mStatement.renew();
  }

  @NotNull
  public <R> DeferredStatement<R> then(@NotNull final Mapper<? super V, R> mapper) {
    return newInstance(mStatement.then(mapper));
  }

  @NotNull
  public DeferredStatement<V> thenDo(@NotNull final Observer<? super V> observer) {
    return newInstance(mStatement.thenDo(observer));
  }

  @NotNull
  public <R> DeferredStatement<R> thenIf(
      @NotNull final Mapper<? super V, ? extends AsyncStatement<R>> mapper) {
    return newInstance(mStatement.thenIf(mapper));
  }

  @NotNull
  public <R> DeferredStatement<R> thenTry(
      @NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final Mapper<? super V, R> mapper) {
    return newInstance(mStatement.thenTry(closeable, mapper));
  }

  @NotNull
  public DeferredStatement<V> thenTryDo(
      @NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final Observer<? super V> observer) {
    return newInstance(mStatement.thenTryDo(closeable, observer));
  }

  @NotNull
  public <R> DeferredStatement<R> thenTryIf(
      @NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final Mapper<? super V, ? extends AsyncStatement<R>> mapper) {
    return newInstance(mStatement.thenTryIf(closeable, mapper));
  }

  @NotNull
  public DeferredStatement<V> whenDone(@NotNull final Action action) {
    return newInstance(mStatement.whenDone(action));
  }

  @NotNull
  public AsyncStatement<V> evaluate() {
    mObserver.evaluate();
    return mStatement;
  }

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

  public V value() {
    return mStatement.value();
  }

  @Nullable
  public FailureException getFailure() {
    return mStatement.getFailure();
  }

  @Nullable
  public FailureException getFailure(final long timeout, @NotNull final TimeUnit timeUnit) {
    return mStatement.getFailure(timeout, timeUnit);
  }

  public V getValue() {
    return mStatement.getValue();
  }

  public V getValue(final long timeout, @NotNull final TimeUnit timeUnit) {
    return mStatement.getValue(timeout, timeUnit);
  }

  public boolean isFinal() {
    return mStatement.isFinal();
  }

  public void waitDone() {
    mStatement.waitDone();
  }

  public boolean waitDone(final long timeout, @NotNull final TimeUnit timeUnit) {
    return mStatement.waitDone(timeout, timeUnit);
  }

  @NotNull
  private <R> DeferredStatement<R> newInstance(@NotNull final AsyncStatement<R> statement) {
    return new DefaultDeferredStatement<R>(mFactory, mObserver, statement);
  }

  private static class DeferredObserver<V> implements Observer<AsyncResult<V>>, Serializable {

    private transient final Object mMutex = new Object();

    private boolean mAccepted;

    private transient AsyncResult<V> mResult;

    public void accept(final AsyncResult<V> result) throws Exception {
      final AsyncResult<V> currentResult;
      synchronized (mMutex) {
        if (!mAccepted) {
          mAccepted = true;
          mResult = result;
          currentResult = null;

        } else {
          currentResult = result;
        }
      }

      if (currentResult != null) {
        currentResult.set(null);
      }
    }

    void evaluate() {
      final AsyncResult<V> result;
      synchronized (mMutex) {
        result = mResult;
      }

      result.set(null);
    }
  }
}

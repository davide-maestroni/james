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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import dm.jail.async.Action;
import dm.jail.async.AsyncEvaluation;
import dm.jail.async.AsyncStatement;
import dm.jail.async.Completer;
import dm.jail.async.FailureException;
import dm.jail.async.Mapper;
import dm.jail.async.Observer;
import dm.jail.async.Updater;
import dm.jail.util.ConstantConditions;

/**
 * Created by davide-maestroni on 01/30/2018.
 */
abstract class AsyncStatementDecorator<V> implements AsyncStatement<V> {

  private final AsyncStatement<V> mStatement;

  AsyncStatementDecorator(@NotNull final AsyncStatement<V> statement) {
    mStatement = ConstantConditions.notNull("statement", statement);
  }

  public boolean cancel(final boolean b) {
    return false;
  }

  public boolean isDone() {
    return false;
  }

  public V get() throws InterruptedException, ExecutionException {
    return null;
  }

  public V get(final long l, @NotNull final TimeUnit timeUnit) throws InterruptedException,
      ExecutionException, TimeoutException {
    return null;
  }

  public void consume() {
    mStatement.consume();
  }

  @NotNull
  public AsyncStatement<V> elseCatch(@NotNull final Mapper<? super Throwable, ? extends V> mapper,
      @Nullable final Class<?>[] exceptionTypes) {
    return newInstance(mStatement.elseCatch(mapper, exceptionTypes));
  }

  @NotNull
  public AsyncStatement<V> elseDo(@NotNull final Observer<? super Throwable> observer,
      @Nullable final Class<?>[] exceptionTypes) {
    return newInstance(mStatement.elseDo(observer, exceptionTypes));
  }

  @NotNull
  public AsyncStatement<V> elseIf(
      @NotNull final Mapper<? super Throwable, ? extends AsyncStatement<? extends V>> mapper,
      @Nullable final Class<?>[] exceptionTypes) {
    return newInstance(mStatement.elseIf(mapper, exceptionTypes));
  }

  @NotNull
  public AsyncStatement<V> evaluate() {
    return newInstance(mStatement.evaluate());
  }

  @NotNull
  public AsyncStatement<V> evaluated() {
    return newInstance(mStatement.evaluated());
  }

  @NotNull
  public <S> AsyncStatement<V> fork(
      @NotNull final Forker<S, ? super AsyncStatement<V>, ? super V, ? super AsyncEvaluation<V>>
          forker) {
    return newInstance(mStatement.fork(forker));
  }

  @NotNull
  public <S> AsyncStatement<V> fork(@Nullable final Mapper<? super AsyncStatement<V>, S> init,
      @Nullable final Updater<S, ? super V, ? super AsyncStatement<V>> value,
      @Nullable final Updater<S, ? super Throwable, ? super AsyncStatement<V>> failure,
      @Nullable final Completer<S, ? super AsyncStatement<V>> done,
      @Nullable final Updater<S, ? super AsyncEvaluation<V>, ? super AsyncStatement<V>> statement) {
    return newInstance(mStatement.fork(init, value, failure, done, statement));
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

  @NotNull
  public AsyncStatement<V> on(@NotNull final Executor executor) {
    return newInstance(mStatement.on(executor));
  }

  @NotNull
  public <R> AsyncStatement<R> then(@NotNull final Mapper<? super V, R> mapper) {
    return newInstance(mStatement.then(mapper));
  }

  @NotNull
  public AsyncStatement<V> thenDo(@NotNull final Observer<? super V> observer) {
    return newInstance(mStatement.thenDo(observer));
  }

  @NotNull
  public <R> AsyncStatement<R> thenIf(
      @NotNull final Mapper<? super V, ? extends AsyncStatement<R>> mapper) {
    return newInstance(mStatement.thenIf(mapper));
  }

  @NotNull
  public <R> AsyncStatement<R> thenTry(
      @NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final Mapper<? super V, R> mapper) {
    return newInstance(mStatement.thenTry(closeable, mapper));
  }

  @NotNull
  public AsyncStatement<V> thenTryDo(
      @NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final Observer<? super V> observer) {
    return newInstance(mStatement.thenTryDo(closeable, observer));
  }

  @NotNull
  public <R> AsyncStatement<R> thenTryIf(
      @NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final Mapper<? super V, ? extends AsyncStatement<R>> mapper) {
    return newInstance(mStatement.thenTryIf(closeable, mapper));
  }

  public void waitDone() {
    mStatement.waitDone();
  }

  public boolean waitDone(final long timeout, @NotNull final TimeUnit timeUnit) {
    return mStatement.waitDone(timeout, timeUnit);
  }

  @NotNull
  public AsyncStatement<V> whenDone(@NotNull final Action action) {
    return newInstance(mStatement.whenDone(action));
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

  public void to(@NotNull final AsyncEvaluation<? super V> evaluation) {
    mStatement.to(evaluation);
  }

  public V value() {
    return mStatement.value();
  }

  @NotNull
  abstract <R> AsyncStatement<R> newInstance(@NotNull AsyncStatement<R> statement);
}

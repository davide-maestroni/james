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
import java.util.List;

import dm.jail.async.AsyncLoop;
import dm.jail.async.AsyncResult;
import dm.jail.async.AsyncResultCollection;
import dm.jail.async.AsyncStatement;
import dm.jail.async.Mapper;
import dm.jail.async.Observer;
import dm.jail.executor.ScheduledExecutor;
import dm.jail.log.LogPrinter;
import dm.jail.log.LogPrinter.Level;
import dm.jail.util.RuntimeInterruptedException;
import dm.james.util.ConstantConditions;
import dm.james.util.SerializableProxy;

/**
 * Created by davide-maestroni on 01/12/2018.
 */
public class Async {

  // TODO: 12/01/2018 lazy => AsyncStatementLazy.evaluate()?
  // TODO: 18/01/2018 AsyncExpression extends AsyncDeclaration => AsyncExpression.evaluate()?

  private final ScheduledExecutor mExecutor;

  private final Level mLogLevel;

  private final LogPrinter mLogPrinter;

  public Async() {
    this(null, null, null);
  }

  private Async(@Nullable final ScheduledExecutor executor, @Nullable final LogPrinter printer,
      @Nullable final Level level) {
    mExecutor = executor;
    mLogPrinter = printer;
    mLogLevel = level;
  }

  private static void test() {

  }

  @NotNull
  public <V> AsyncStatement<V> failure(@Nullable final Throwable failure) {
    return statement(new FailureObserver<V>(failure));
  }

  @NotNull
  public <V> AsyncLoop<V> failures(@NotNull final Iterable<? extends Throwable> failures) {
    return null;
  }

  @NotNull
  public Async logWith(@Nullable final Level level) {
    return new Async(mExecutor, mLogPrinter, level);
  }

  @NotNull
  public Async logWith(@Nullable final LogPrinter printer) {
    return new Async(mExecutor, printer, mLogLevel);
  }

  @NotNull
  public <V> AsyncLoop<V> loop(@NotNull final Observer<AsyncResultCollection<V>> observer) {
    return null;
  }

  @NotNull
  public <S, V, R> AsyncLoop<R> loopOf(
      @NotNull final Combiner<S, ? super AsyncStatement<V>, ? super V, ? super
          AsyncResultCollection<? extends R>> combiner,
      @NotNull final Iterable<? extends AsyncStatement<? extends V>> statements) {
    return null;
  }

  @NotNull
  public <S, V, R> AsyncLoop<R> loopOf(@Nullable final Mapper<? super AsyncStatement<V>, S> init,
      @Nullable final CombinationUpdater<S, ? super AsyncStatement<V>, ? super V, ? super
          AsyncResultCollection<? extends R>> value,
      @Nullable final CombinationUpdater<S, ? super AsyncStatement<V>, ? super Throwable, ? super
          AsyncResultCollection<? extends R>> failure,
      @Nullable final CombinationSetter<S, ? super AsyncStatement<V>, ? super
          AsyncResultCollection<? extends R>> set,
      @Nullable final CombinationCompleter<S, ? super AsyncStatement<V>, ? super
          AsyncResultCollection<? extends R>> done,
      @NotNull final Iterable<? extends AsyncStatement<? extends V>> statements) {
    return null;
  }

  @NotNull
  public Async on(@NotNull final ScheduledExecutor executor) {
    return new Async(executor, mLogPrinter, mLogLevel);
  }

  @NotNull
  public <V> AsyncStatement<V> statement(@NotNull final Observer<AsyncResult<V>> observer) {
    final ScheduledExecutor executor = mExecutor;
    if (executor != null) {
      new DefaultAsyncStatement<V>(new ExecutorObserver<V>(observer, executor), executor,
          mLogPrinter, mLogLevel);
    }

    return new DefaultAsyncStatement<V>(observer, mLogPrinter, mLogLevel);
  }

  @NotNull
  public <S, V, R> AsyncStatement<R> statementOf(
      @NotNull final Combiner<S, ? super AsyncStatement<V>, ? super V, ? super AsyncResult<?
          extends R>> combiner,
      @NotNull final Iterable<? extends AsyncStatement<? extends V>> statements) {
    return null;
  }

  @NotNull
  public <S, V, R> AsyncStatement<R> statementOf(
      @Nullable final Mapper<? super AsyncStatement<V>, S> init,
      @Nullable final CombinationUpdater<S, ? super AsyncStatement<V>, ? super V, ? super
          AsyncResult<? extends R>> value,
      @Nullable final CombinationUpdater<S, ? super AsyncStatement<V>, ? super Throwable, ? super
          AsyncResult<? extends R>> failure,
      @Nullable final CombinationSetter<S, ? super AsyncStatement<V>, ? super AsyncResult<?
          extends R>> set,
      @Nullable final CombinationCompleter<S, ? super AsyncStatement<V>, ? super AsyncResult<?
          extends R>> done,
      @NotNull final Iterable<? extends AsyncStatement<? extends V>> statements) {
    return null;
  }

  @NotNull
  public <V> AsyncStatement<V> value(final V value) {
    return statement(new ValueObserver<V>(value));
  }

  @NotNull
  public <V> AsyncLoop<V> values(@NotNull final Iterable<V> values) {
    return null;
  }

  interface CombinationCompleter<S, A, R> {

    void accept(@NotNull List<A> statements, S stack, @NotNull R result) throws Exception;
  }

  interface CombinationSetter<S, A, R> {

    S apply(@NotNull List<A> statements, int index, S stack, @NotNull R result) throws Exception;
  }

  interface CombinationUpdater<S, A, V, R> {

    S apply(@NotNull List<A> statements, int index, S stack, V value, @NotNull R result) throws
        Exception;
  }

  interface Combiner<S, A, V, R> {

    void done(@NotNull List<A> statements, S stack, @NotNull R result) throws Exception;

    S failure(@NotNull List<A> statements, int index, S stack, Throwable failure,
        @NotNull R result) throws Exception;

    S init(@NotNull List<A> statements) throws Exception;

    S set(@NotNull List<A> statements, int index, S stack, @NotNull R result) throws Exception;

    S value(@NotNull List<A> statements, int index, S stack, V value, @NotNull R result) throws
        Exception;
  }

  private static class ExecutorObserver<V> implements Observer<AsyncResult<V>>, Serializable {

    private final ScheduledExecutor mExecutor;

    private final Observer<AsyncResult<V>> mObserver;

    private ExecutorObserver(@NotNull final Observer<AsyncResult<V>> observer,
        @NotNull final ScheduledExecutor executor) {
      mObserver = ConstantConditions.notNull("observer", observer);
      mExecutor = executor;
    }

    public void accept(final AsyncResult<V> result) {
      mExecutor.execute(new Runnable() {

        public void run() {
          try {
            mObserver.accept(result);

          } catch (final Throwable t) {
            // TODO: 16/01/2018 invert order of call?
            RuntimeInterruptedException.throwIfInterrupt(t);
            result.fail(t);
          }
        }
      });
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ObserverProxy<V>(mObserver, mExecutor);
    }

    private static class ObserverProxy<V> extends SerializableProxy {

      private ObserverProxy(Observer<AsyncResult<V>> observer, ScheduledExecutor executor) {
        super(proxy(observer), executor);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ExecutorObserver<V>((Observer<AsyncResult<V>>) args[0],
              (ScheduledExecutor) args[1]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }
  }

  private static class FailureObserver<V> implements Observer<AsyncResult<V>>, Serializable {

    private final Throwable mError;

    private FailureObserver(final Throwable error) {
      mError = error;
    }

    public void accept(final AsyncResult<V> result) {
      result.fail(mError);
    }
  }

  private static class ValueObserver<V> implements Observer<AsyncResult<V>>, Serializable {

    private final V mValue;

    private ValueObserver(final V value) {
      mValue = value;
    }

    public void accept(final AsyncResult<V> result) {
      result.set(mValue);
    }
  }
}

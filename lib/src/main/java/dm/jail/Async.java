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
import dm.jail.async.RuntimeInterruptedException;
import dm.jail.config.BuildConfig;
import dm.jail.executor.ScheduledExecutor;
import dm.jail.log.LogPrinter;
import dm.jail.log.LogPrinter.Level;
import dm.jail.util.ConstantConditions;
import dm.jail.util.SerializableProxy;
import dm.jail.util.Threads;

/**
 * Created by davide-maestroni on 01/12/2018.
 */
public class Async {

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

  @NotNull
  public <V> AsyncStatement<V> failure(@NotNull final Throwable failure) {
    return statement(new FailureObserver<V>(failure));
  }

  @NotNull
  public <V> AsyncLoop<V> failures(@NotNull final Iterable<? extends Throwable> failures) {
    return null;
  }

  @NotNull
  public Async log(@Nullable final Level level) {
    return new Async(mExecutor, mLogPrinter, level);
  }

  @NotNull
  public Async log(@Nullable final LogPrinter printer) {
    return new Async(mExecutor, printer, mLogLevel);
  }

  @NotNull
  public <V> AsyncLoop<V> loop(@NotNull final AsyncStatement<? extends Iterable<V>> statement) {
    return loop(new LoopObserver<V>(statement));
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
      @Nullable final CombinationCompleter<S, ? super AsyncStatement<V>, ? super
          AsyncResultCollection<? extends R>> done,
      @Nullable final CombinationSettler<S, ? super AsyncStatement<V>, ? super
          AsyncResultCollection<? extends R>> settle,
      @NotNull final Iterable<? extends AsyncStatement<? extends V>> statements) {
    return null;
  }

  @NotNull
  public Async on(@NotNull final ScheduledExecutor executor) {
    return new Async(ConstantConditions.notNull("executor", executor), mLogPrinter, mLogLevel);
  }

  @NotNull
  public <V> AsyncStatement<V> statement(@NotNull final Observer<AsyncResult<V>> observer) {
    return new DefaultAsyncStatement<V>(statementObserver(observer), true, mLogPrinter, mLogLevel);
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
      @Nullable final CombinationCompleter<S, ? super AsyncStatement<V>, ? super AsyncResult<?
          extends R>> done,
      @Nullable final CombinationSettler<S, ? super AsyncStatement<V>, ? super AsyncResult<?
          extends R>> settle,
      @NotNull final Iterable<? extends AsyncStatement<? extends V>> statements) {
    return null;
  }

  @NotNull
  public <V> AsyncStatement<V> unevaluatedFailure(@NotNull final Throwable failure) {
    return unevaluatedStatement(new FailureObserver<V>(failure));
  }

  @NotNull
  public <V> AsyncStatement<V> unevaluatedStatement(
      @NotNull final Observer<AsyncResult<V>> observer) {
    // TODO: 30/01/2018 value, failure, loop, etc.
    return new DefaultAsyncStatement<V>(new UnevaluatedObserver<V>(statementObserver(observer)),
        false, mLogPrinter, mLogLevel);
  }

  @NotNull
  public <V> AsyncStatement<V> unevaluatedValue(final V value) {
    return unevaluatedStatement(new ValueObserver<V>(value));
  }

  @NotNull
  public <V> AsyncStatement<V> value(final V value) {
    return statement(new ValueObserver<V>(value));
  }

  @NotNull
  public <V> AsyncLoop<V> values(@NotNull final Iterable<V> values) {
    return null;
  }

  @NotNull
  private <V> Observer<AsyncResult<V>> statementObserver(
      @NotNull final Observer<AsyncResult<V>> observer) {
    final ScheduledExecutor executor = mExecutor;
    if (executor != null) {
      return new ExecutorObserver<V>(observer, executor);
    }

    return observer;
  }

  interface CombinationCompleter<S, A, R> {

    S complete(@NotNull List<A> statements, int index, S stack, @NotNull R result) throws Exception;
  }

  interface CombinationSettler<S, A, R> {

    void settle(@NotNull List<A> statements, S stack, @NotNull R result) throws Exception;
  }

  interface CombinationUpdater<S, A, V, R> {

    S update(@NotNull List<A> statements, int index, S stack, V value, @NotNull R result) throws
        Exception;
  }

  interface Combiner<S, A, V, R> {

    S done(@NotNull List<A> statements, int index, S stack, @NotNull R result) throws Exception;

    S failure(@NotNull List<A> statements, int index, S stack, Throwable failure,
        @NotNull R result) throws Exception;

    S init(@NotNull List<A> statements) throws Exception;

    void settle(@NotNull List<A> statements, S stack, @NotNull R result) throws Exception;

    S value(@NotNull List<A> statements, int index, S stack, V value, @NotNull R result) throws
        Exception;
  }

  private static class ExecutorObserver<V>
      implements InterruptibleObserver<AsyncResult<V>>, Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final ScheduledExecutor mExecutor;

    private final Object mMutex = new Object();

    private final Observer<AsyncResult<V>> mObserver;

    private Thread mThread;

    private ExecutorObserver(@NotNull final Observer<AsyncResult<V>> observer,
        @NotNull final ScheduledExecutor executor) {
      mObserver = ConstantConditions.notNull("observer", observer);
      mExecutor = executor;
    }

    public void accept(final AsyncResult<V> result) {
      mExecutor.execute(new Runnable() {

        public void run() {
          synchronized (mMutex) {
            mThread = Thread.currentThread();
          }

          try {
            mObserver.accept(result);

          } catch (final Throwable t) {
            synchronized (mMutex) {
              mThread = null;
            }

            result.fail(RuntimeInterruptedException.wrapIfInterrupt(t));
          }
        }
      });
    }

    public void interrupt() {
      synchronized (mMutex) {
        final Thread thread = mThread;
        if (thread != null) {
          Threads.interruptIfWaiting(thread);
        }
      }
    }

    @NotNull
    private Object writeReplace() throws ObjectStreamException {
      return new ObserverProxy<V>(mObserver, mExecutor);
    }

    private static class ObserverProxy<V> extends SerializableProxy {

      private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

      private ObserverProxy(final Observer<AsyncResult<V>> observer,
          final ScheduledExecutor executor) {
        super(proxy(observer), executor);
      }

      @NotNull
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

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final Throwable mFailure;

    private FailureObserver(@NotNull final Throwable failure) {
      mFailure = ConstantConditions.notNull("failure", failure);
    }

    public void accept(final AsyncResult<V> result) {
      result.fail(mFailure);
    }
  }

  private static class LoopObserver<V>
      implements RenewableObserver<AsyncResultCollection<V>>, Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final AsyncStatement<? extends Iterable<V>> mStatement;

    private LoopObserver(@NotNull final AsyncStatement<? extends Iterable<V>> statement) {
      mStatement = ConstantConditions.notNull("statement", statement);
    }

    @NotNull
    public Observer<AsyncResultCollection<V>> renew() {
      return new LoopObserver<V>(mStatement.evaluate());
    }

    public void accept(final AsyncResultCollection<V> results) {
      mStatement.then(new Mapper<Iterable<V>, Void>() {

        public Void apply(final Iterable<V> values) {
          results.addValues(values).set();
          return null;
        }
      }).elseCatch(new Mapper<Throwable, Void>() {

        public Void apply(final Throwable failure) {
          results.addFailure(failure).set();
          return null;
        }
      });
    }
  }

  private static class UnevaluatedObserver<V>
      implements RenewableObserver<AsyncResult<V>>, Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final Observer<AsyncResult<V>> mObserver;

    UnevaluatedObserver(@NotNull final Observer<AsyncResult<V>> observer) {
      mObserver = ConstantConditions.notNull("observer", observer);
    }

    @NotNull
    private Object writeReplace() throws ObjectStreamException {
      return new ObserverProxy<V>(mObserver);
    }

    private static class ObserverProxy<V> extends SerializableProxy {

      private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

      private ObserverProxy(final Observer<AsyncResult<V>> observer) {
        super(proxy(observer));
      }

      @NotNull
      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new UnevaluatedObserver<V>((Observer<AsyncResult<V>>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void accept(final AsyncResult<V> result) {
    }

    @NotNull
    public Observer<AsyncResult<V>> renew() {
      return mObserver;
    }
  }

  private static class ValueObserver<V> implements Observer<AsyncResult<V>>, Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final V mValue;

    private ValueObserver(final V value) {
      mValue = value;
    }

    public void accept(final AsyncResult<V> result) {
      result.set(mValue);
    }
  }
}

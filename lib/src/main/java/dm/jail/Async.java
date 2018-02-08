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
import java.util.concurrent.Executor;

import dm.jail.async.AsyncEvaluation;
import dm.jail.async.AsyncEvaluations;
import dm.jail.async.AsyncLoop;
import dm.jail.async.AsyncStatement;
import dm.jail.async.Mapper;
import dm.jail.async.Observer;
import dm.jail.async.RuntimeInterruptedException;
import dm.jail.config.BuildConfig;
import dm.jail.log.LogLevel;
import dm.jail.log.LogPrinter;
import dm.jail.util.ConstantConditions;
import dm.jail.util.Iterables;
import dm.jail.util.SerializableProxy;
import dm.jail.util.Threads;

/**
 * Created by davide-maestroni on 01/12/2018.
 */
public class Async {

  private final Executor mExecutor;

  private final boolean mIsUnevaluated;

  private final LogLevel mLogLevel;

  private final LogPrinter mLogPrinter;

  public Async() {
    this(false, null, null, null);
  }

  private Async(final boolean isUnevaluated, @Nullable final Executor executor,
      @Nullable final LogPrinter printer, @Nullable final LogLevel level) {
    mIsUnevaluated = isUnevaluated;
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
    return loop(new FailuresObserver<V>(failures));
  }

  @NotNull
  public Async log(@Nullable final LogLevel level) {
    return new Async(mIsUnevaluated, mExecutor, mLogPrinter, level);
  }

  @NotNull
  public Async log(@Nullable final LogPrinter printer) {
    return new Async(mIsUnevaluated, mExecutor, printer, mLogLevel);
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public <V> AsyncLoop<V> loop(@NotNull final AsyncStatement<? extends Iterable<V>> statement) {
    if (!mIsUnevaluated && (statement instanceof AsyncLoop)) {
      return (AsyncLoop<V>) statement;
    }

    return loop(new LoopObserver<V>(statement));
  }

  @NotNull
  public <V> AsyncLoop<V> loop(@NotNull final Observer<AsyncEvaluations<V>> observer) {
    final boolean isUnevaluated = mIsUnevaluated;
    final Observer<AsyncEvaluations<V>> loopObserver = loopObserver(observer);
    return new DefaultAsyncLoop<V>(
        (isUnevaluated) ? new UnevaluatedObserver<V, AsyncEvaluations<V>>(loopObserver)
            : loopObserver, !isUnevaluated, mLogPrinter, mLogLevel);
  }

  @NotNull
  public <S, V, R> AsyncLoop<R> loopOf(
      @NotNull final Combiner<S, ? super AsyncLoop<V>, ? super V, ? super AsyncEvaluations<?
          extends R>> combiner,
      @NotNull final Iterable<? extends AsyncLoop<? extends V>> loops) {
    return null;
  }

  @NotNull
  public <S, V, R> AsyncLoop<R> loopOf(@Nullable final Mapper<? super List<AsyncLoop<V>>, S> init,
      @Nullable final CombinationUpdater<S, ? super AsyncLoop<V>, ? super V, ? super
          AsyncEvaluations<? extends R>> value,
      @Nullable final CombinationUpdater<S, ? super AsyncLoop<V>, ? super Throwable, ? super
          AsyncEvaluations<? extends R>> failure,
      @Nullable final CombinationCompleter<S, ? super AsyncLoop<V>, ? super AsyncEvaluations<?
          extends R>> done,
      @Nullable final CombinationSettler<S, ? super AsyncLoop<V>, ? super AsyncEvaluations<?
          extends R>> settle,
      @NotNull final Iterable<? extends AsyncLoop<? extends V>> loops) {
    return null;
  }

  @NotNull
  public <V> AsyncLoop<V> loopOnce(@NotNull final AsyncStatement<? extends V> statement) {
    return loop(new SingleLoopObserver<V>(statement));
  }

  @NotNull
  public Async on(@Nullable final Executor executor) {
    return new Async(mIsUnevaluated, executor, mLogPrinter, mLogLevel);
  }

  @NotNull
  public <V> AsyncStatement<V> statement(@NotNull final Observer<AsyncEvaluation<V>> observer) {
    final boolean isUnevaluated = mIsUnevaluated;
    final Observer<AsyncEvaluation<V>> statementObserver = statementObserver(observer);
    return new DefaultAsyncStatement<V>(
        (isUnevaluated) ? new UnevaluatedObserver<V, AsyncEvaluation<V>>(statementObserver)
            : statementObserver, !isUnevaluated, mLogPrinter, mLogLevel);
  }

  @NotNull
  public <S, V, R> AsyncStatement<R> statementOf(
      @NotNull final Combiner<S, ? super AsyncStatement<V>, ? super V, ? super AsyncEvaluation<?
          extends R>> combiner,
      @NotNull final Iterable<? extends AsyncStatement<? extends V>> statements) {
    return null;
  }

  @NotNull
  public <S, V, R> AsyncStatement<R> statementOf(
      @Nullable final Mapper<? super List<AsyncStatement<V>>, S> init,
      @Nullable final CombinationUpdater<S, ? super AsyncStatement<V>, ? super V, ? super
          AsyncEvaluation<? extends R>> value,
      @Nullable final CombinationUpdater<S, ? super AsyncStatement<V>, ? super Throwable, ? super
          AsyncEvaluation<? extends R>> failure,
      @Nullable final CombinationCompleter<S, ? super AsyncStatement<V>, ? super
          AsyncEvaluation<? extends R>> done,
      @Nullable final CombinationSettler<S, ? super AsyncStatement<V>, ? super AsyncEvaluation<?
          extends R>> settle,
      @NotNull final Iterable<? extends AsyncStatement<? extends V>> statements) {
    return null;
  }

  @NotNull
  public Async unevaluated() {
    return new Async(true, mExecutor, mLogPrinter, mLogLevel);
  }

  @NotNull
  public <V> AsyncStatement<V> value(final V value) {
    return statement(new ValueObserver<V>(value));
  }

  @NotNull
  public <V> AsyncLoop<V> values(@NotNull final Iterable<? extends V> values) {
    return loop(new ValuesObserver<V>(values));
  }

  @NotNull
  private <V> Observer<AsyncEvaluations<V>> loopObserver(
      @NotNull final Observer<AsyncEvaluations<V>> observer) {
    final Executor executor = mExecutor;
    if (executor != null) {
      return new LoopExecutorObserver<V>(observer, executor);
    }

    return observer;
  }

  @NotNull
  private <V> Observer<AsyncEvaluation<V>> statementObserver(
      @NotNull final Observer<AsyncEvaluation<V>> observer) {
    final Executor executor = mExecutor;
    if (executor != null) {
      return new StatementExecutorObserver<V>(observer, executor);
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

  private static class FailureObserver<V> implements Observer<AsyncEvaluation<V>>, Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final Throwable mFailure;

    private FailureObserver(@NotNull final Throwable failure) {
      mFailure = ConstantConditions.notNull("failure", failure);
    }

    public void accept(final AsyncEvaluation<V> evaluation) {
      evaluation.fail(mFailure);
    }
  }

  private static class FailuresObserver<V> implements Observer<AsyncEvaluations<V>>, Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final Iterable<? extends Throwable> mFailures;

    private FailuresObserver(@Nullable final Iterable<? extends Throwable> failures) {
      if ((failures != null) && Iterables.contains(failures, null)) {
        throw new NullPointerException("failures cannot contain null objects");
      }

      mFailures = failures;
    }

    public void accept(final AsyncEvaluations<V> evaluations) {
      evaluations.addFailures(mFailures).set();
    }
  }

  private static class LoopExecutorObserver<V>
      implements InterruptibleObserver<AsyncEvaluations<V>>, Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final Executor mExecutor;

    private final Object mMutex = new Object();

    private final Observer<AsyncEvaluations<V>> mObserver;

    private Thread mThread;

    private LoopExecutorObserver(@NotNull final Observer<AsyncEvaluations<V>> observer,
        @NotNull final Executor executor) {
      mObserver = ConstantConditions.notNull("observer", observer);
      mExecutor = executor;
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

      private ObserverProxy(final Observer<AsyncEvaluations<V>> observer, final Executor executor) {
        super(proxy(observer), executor);
      }

      @NotNull
      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new LoopExecutorObserver<V>((Observer<AsyncEvaluations<V>>) args[0],
              (Executor) args[1]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void accept(final AsyncEvaluations<V> evaluations) {
      mExecutor.execute(new Runnable() {

        public void run() {
          synchronized (mMutex) {
            mThread = Thread.currentThread();
          }

          try {
            mObserver.accept(evaluations);

          } catch (final Throwable t) {
            synchronized (mMutex) {
              mThread = null;
            }

            try {
              evaluations.addFailure(RuntimeInterruptedException.wrapIfInterrupt(t)).set();

            } catch (final Throwable ignored) {
              // cannot take any action
            }
          }
        }
      });
    }
  }

  private static class LoopObserver<V>
      implements RenewableObserver<AsyncEvaluations<V>>, Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final AsyncStatement<? extends Iterable<V>> mStatement;

    private LoopObserver(@NotNull final AsyncStatement<? extends Iterable<V>> statement) {
      mStatement = ConstantConditions.notNull("statement", statement);
    }

    @NotNull
    public Observer<AsyncEvaluations<V>> renew() {
      return new LoopObserver<V>(mStatement.evaluate());
    }

    public void accept(final AsyncEvaluations<V> evaluations) {
      mStatement.then(new Mapper<Iterable<V>, Void>() {

        public Void apply(final Iterable<V> values) {
          evaluations.addValues(values).set();
          return null;
        }
      }).elseCatch(new Mapper<Throwable, Void>() {

        public Void apply(final Throwable failure) {
          evaluations.addFailure(failure).set();
          return null;
        }
      });
    }
  }

  private static class SingleLoopObserver<V>
      implements RenewableObserver<AsyncEvaluations<V>>, Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final AsyncStatement<? extends V> mStatement;

    private SingleLoopObserver(@NotNull final AsyncStatement<? extends V> statement) {
      mStatement = ConstantConditions.notNull("statement", statement);
    }

    @NotNull
    public Observer<AsyncEvaluations<V>> renew() {
      return new SingleLoopObserver<V>(mStatement.evaluate());
    }

    public void accept(final AsyncEvaluations<V> evaluations) {
      mStatement.then(new Mapper<V, Void>() {

        public Void apply(final V value) {
          evaluations.addValue(value).set();
          return null;
        }
      }).elseCatch(new Mapper<Throwable, Void>() {

        public Void apply(final Throwable failure) {
          evaluations.addFailure(failure).set();
          return null;
        }
      });
    }
  }

  private static class StatementExecutorObserver<V>
      implements InterruptibleObserver<AsyncEvaluation<V>>, Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final Executor mExecutor;

    private final Object mMutex = new Object();

    private final Observer<AsyncEvaluation<V>> mObserver;

    private Thread mThread;

    private StatementExecutorObserver(@NotNull final Observer<AsyncEvaluation<V>> observer,
        @NotNull final Executor executor) {
      mObserver = ConstantConditions.notNull("observer", observer);
      mExecutor = executor;
    }

    @NotNull
    private Object writeReplace() throws ObjectStreamException {
      return new ObserverProxy<V>(mObserver, mExecutor);
    }

    private static class ObserverProxy<V> extends SerializableProxy {

      private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

      private ObserverProxy(final Observer<AsyncEvaluation<V>> observer, final Executor executor) {
        super(proxy(observer), executor);
      }

      @NotNull
      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new StatementExecutorObserver<V>((Observer<AsyncEvaluation<V>>) args[0],
              (Executor) args[1]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void interrupt() {
      synchronized (mMutex) {
        final Thread thread = mThread;
        if (thread != null) {
          Threads.interruptIfWaiting(thread);
        }
      }
    }

    public void accept(final AsyncEvaluation<V> evaluation) {
      mExecutor.execute(new Runnable() {

        public void run() {
          synchronized (mMutex) {
            mThread = Thread.currentThread();
          }

          try {
            mObserver.accept(evaluation);

          } catch (final Throwable t) {
            synchronized (mMutex) {
              mThread = null;
            }

            try {
              evaluation.fail(RuntimeInterruptedException.wrapIfInterrupt(t));

            } catch (final Throwable ignored) {
              // cannot take any action
            }
          }
        }
      });
    }
  }

  private static class UnevaluatedObserver<V, R> implements RenewableObserver<R>, Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final Observer<R> mObserver;

    UnevaluatedObserver(@NotNull final Observer<R> observer) {
      mObserver = ConstantConditions.notNull("observer", observer);
    }

    @NotNull
    private Object writeReplace() throws ObjectStreamException {
      return new ObserverProxy<V, R>(mObserver);
    }

    private static class ObserverProxy<V, R> extends SerializableProxy {

      private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

      private ObserverProxy(final Observer<R> observer) {
        super(proxy(observer));
      }

      @NotNull
      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new UnevaluatedObserver<V, R>((Observer<R>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void accept(final R evaluation) {
    }

    @NotNull
    public Observer<R> renew() {
      return mObserver;
    }
  }

  private static class ValueObserver<V> implements Observer<AsyncEvaluation<V>>, Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final V mValue;

    private ValueObserver(final V value) {
      mValue = value;
    }

    public void accept(final AsyncEvaluation<V> evaluation) {
      evaluation.set(mValue);
    }
  }

  private static class ValuesObserver<V> implements Observer<AsyncEvaluations<V>>, Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final Iterable<? extends V> mValues;

    private ValuesObserver(@Nullable final Iterable<? extends V> values) {
      mValues = values;
    }

    public void accept(final AsyncEvaluations<V> evaluations) {
      evaluations.addValues(mValues).set();
    }
  }
}

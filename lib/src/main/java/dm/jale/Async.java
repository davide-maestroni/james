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
import java.util.List;
import java.util.concurrent.Executor;

import dm.jale.async.AsyncEvaluation;
import dm.jale.async.AsyncEvaluations;
import dm.jale.async.AsyncLoop;
import dm.jale.async.AsyncStatement;
import dm.jale.async.AsyncStatement.Forker;
import dm.jale.async.CombinationCompleter;
import dm.jale.async.CombinationSettler;
import dm.jale.async.CombinationUpdater;
import dm.jale.async.Combiner;
import dm.jale.async.Completer;
import dm.jale.async.Mapper;
import dm.jale.async.Observer;
import dm.jale.async.RuntimeInterruptedException;
import dm.jale.async.Updater;
import dm.jale.config.BuildConfig;
import dm.jale.util.ConstantConditions;
import dm.jale.util.Iterables;
import dm.jale.util.SerializableProxy;
import dm.jale.util.Threads;

/**
 * Created by davide-maestroni on 01/12/2018.
 */
public class Async {

  // TODO: 14/02/2018 AsyncIO, AsyncMath, AsyncRange

  private final Executor mExecutor;

  private final boolean mIsUnevaluated;

  private final String mLoggerName;

  public Async() {
    this(false, null, null);
  }

  private Async(final boolean isUnevaluated, @Nullable final Executor executor,
      @Nullable final String loggerName) {
    mIsUnevaluated = isUnevaluated;
    mExecutor = executor;
    mLoggerName = loggerName;
  }

  @NotNull
  public static <S, V, R, A> Forker<?, V, R, A> buffered(@NotNull final Forker<S, V, R, A> forker) {
    return new BufferedForker<S, V, R, A>(forker);
  }

  @NotNull
  public static <S, V> Forker<?, V, AsyncEvaluations<V>, AsyncLoop<V>> bufferedLoop(
      @Nullable Mapper<? super AsyncLoop<V>, S> init,
      @Nullable Updater<S, ? super V, ? super AsyncLoop<V>> value,
      @Nullable Updater<S, ? super Throwable, ? super AsyncLoop<V>> failure,
      @Nullable Completer<S, ? super AsyncLoop<V>> done,
      @Nullable Updater<S, ? super AsyncEvaluations<V>, ? super AsyncLoop<V>> evaluation) {
    return new BufferedForker<S, V, AsyncEvaluations<V>, AsyncLoop<V>>(
        new ComposedLoopForker<S, V>(init, value, failure, done, evaluation));
  }

  @NotNull
  public static <S, V> Forker<?, V, AsyncEvaluation<V>, AsyncStatement<V>> bufferedStatement(
      @Nullable final Mapper<? super AsyncStatement<V>, S> init,
      @Nullable Updater<S, ? super V, ? super AsyncStatement<V>> value,
      @Nullable Updater<S, ? super Throwable, ? super AsyncStatement<V>> failure,
      @Nullable Completer<S, ? super AsyncStatement<V>> done,
      @Nullable Updater<S, ? super AsyncEvaluation<V>, ? super AsyncStatement<V>> evaluation) {
    return new BufferedForker<S, V, AsyncEvaluation<V>, AsyncStatement<V>>(
        new ComposedStatementForker<S, V>(init, value, failure, done, evaluation));
  }

  @NotNull
  public Async evaluateOn(@Nullable final Executor executor) {
    return new Async(mIsUnevaluated, executor, mLoggerName);
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
  public Async loggerName(@Nullable final String loggerName) {
    return new Async(mIsUnevaluated, mExecutor, loggerName);
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
            : loopObserver, !isUnevaluated, loopLoggerName());
  }

  @NotNull
  public <S, V, R> AsyncLoop<R> loopOf(
      @NotNull final Combiner<S, ? super V, ? super AsyncEvaluations<R>, AsyncLoop<V>> combiner,
      @NotNull final Iterable<? extends AsyncLoop<? extends V>> loops) {
    return loop(new CombinationLoopObserver<S, V, R>(combiner, loops, loopLoggerName()));
  }

  @NotNull
  public <S, V, R> AsyncLoop<R> loopOf(@Nullable final Mapper<? super List<AsyncLoop<V>>, S> init,
      @Nullable final CombinationUpdater<S, ? super V, ? super AsyncEvaluations<? extends R>,
          AsyncLoop<V>> value,
      @Nullable final CombinationUpdater<S, ? super Throwable, ? super AsyncEvaluations<? extends
          R>, AsyncLoop<V>> failure,
      @Nullable final CombinationCompleter<S, ? super AsyncEvaluations<? extends R>,
          AsyncLoop<V>> done,
      @Nullable final CombinationSettler<S, ? super AsyncEvaluations<? extends R>, AsyncLoop<V>>
          settle,
      @NotNull final Iterable<? extends AsyncLoop<? extends V>> loops) {
    return loopOf(new ComposedLoopCombiner<S, V, R>(init, value, failure, done, settle), loops);
  }

  @NotNull
  public <V> AsyncLoop<V> loopOnce(@NotNull final AsyncStatement<? extends V> statement) {
    return loop(new SingleLoopObserver<V>(statement));
  }

  @NotNull
  public <V> AsyncStatement<V> statement(@NotNull final Observer<AsyncEvaluation<V>> observer) {
    final boolean isUnevaluated = mIsUnevaluated;
    final Observer<AsyncEvaluation<V>> statementObserver = statementObserver(observer);
    return new DefaultAsyncStatement<V>(
        (isUnevaluated) ? new UnevaluatedObserver<V, AsyncEvaluation<V>>(statementObserver)
            : statementObserver, !isUnevaluated, statementLoggerName());
  }

  @NotNull
  public <S, V, R> AsyncStatement<R> statementOf(
      @NotNull final Combiner<S, ? super V, ? super AsyncEvaluation<R>, AsyncStatement<V>> combiner,
      @NotNull final Iterable<? extends AsyncStatement<? extends V>> statements) {
    return statement(
        new CombinationStatementObserver<S, V, R>(combiner, statements, statementLoggerName()));
  }

  @NotNull
  public <S, V, R> AsyncStatement<R> statementOf(
      @Nullable final Mapper<? super List<AsyncStatement<V>>, S> init,
      @Nullable final CombinationUpdater<S, ? super V, ? super AsyncEvaluation<? extends R>,
          AsyncStatement<V>> value,
      @Nullable final CombinationUpdater<S, ? super Throwable, ? super AsyncEvaluation<? extends
          R>, AsyncStatement<V>> failure,
      @Nullable final CombinationCompleter<S, ? super AsyncEvaluation<? extends R>,
          AsyncStatement<V>> done,
      @Nullable final CombinationSettler<S, ? super AsyncEvaluation<? extends R>,
          AsyncStatement<V>> settle,
      @NotNull final Iterable<? extends AsyncStatement<? extends V>> statements) {
    return statementOf(new ComposedStatementCombiner<S, V, R>(init, value, failure, done, settle),
        statements);
  }

  @NotNull
  public Async unevaluated() {
    return new Async(true, mExecutor, mLoggerName);
  }

  @NotNull
  public <V> AsyncStatement<V> value(final V value) {
    return statement(new ValueObserver<V>(value));
  }

  @NotNull
  public <V> AsyncLoop<V> values(@NotNull final Iterable<? extends V> values) {
    return loop(new ValuesObserver<V>(values));
  }

  private String loopLoggerName() {
    final String loggerName = mLoggerName;
    return (loggerName != null) ? loggerName : AsyncLoop.class.getName();
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

  private String statementLoggerName() {
    final String loggerName = mLoggerName;
    return (loggerName != null) ? loggerName : AsyncStatement.class.getName();
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

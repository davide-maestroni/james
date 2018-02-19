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

import dm.jale.async.Completer;
import dm.jale.async.Evaluation;
import dm.jale.async.EvaluationCollection;
import dm.jale.async.JoinCompleter;
import dm.jale.async.JoinSettler;
import dm.jale.async.JoinUpdater;
import dm.jale.async.Joiner;
import dm.jale.async.Loop;
import dm.jale.async.Mapper;
import dm.jale.async.Observer;
import dm.jale.async.Statement;
import dm.jale.async.Statement.Forker;
import dm.jale.async.Updater;
import dm.jale.config.BuildConfig;
import dm.jale.util.ConstantConditions;
import dm.jale.util.SerializableProxy;
import dm.jale.util.Threads;

/**
 * Created by davide-maestroni on 01/12/2018.
 */
public class Async {

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
  public static <S, V, R, C> Forker<?, V, R, C> buffered(@NotNull final Forker<S, V, R, C> forker) {
    return new BufferedForker<S, V, R, C>(forker);
  }

  @NotNull
  public static <S, V> Forker<?, V, EvaluationCollection<V>, Loop<V>> bufferedLoop(
      @Nullable Mapper<? super Loop<V>, S> init,
      @Nullable Updater<S, ? super V, ? super Loop<V>> value,
      @Nullable Updater<S, ? super Throwable, ? super Loop<V>> failure,
      @Nullable Completer<S, ? super Loop<V>> done,
      @Nullable Updater<S, ? super EvaluationCollection<V>, ? super Loop<V>> evaluation) {
    return new BufferedForker<S, V, EvaluationCollection<V>, Loop<V>>(
        new ComposedLoopForker<S, V>(init, value, failure, done, evaluation));
  }

  @NotNull
  public static <S, V> Forker<?, V, Evaluation<V>, Statement<V>> bufferedStatement(
      @Nullable final Mapper<? super Statement<V>, S> init,
      @Nullable Updater<S, ? super V, ? super Statement<V>> value,
      @Nullable Updater<S, ? super Throwable, ? super Statement<V>> failure,
      @Nullable Completer<S, ? super Statement<V>> done,
      @Nullable Updater<S, ? super Evaluation<V>, ? super Statement<V>> evaluation) {
    return new BufferedForker<S, V, Evaluation<V>, Statement<V>>(
        new ComposedStatementForker<S, V>(init, value, failure, done, evaluation));
  }

  @NotNull
  public Async evaluateOn(@Nullable final Executor executor) {
    return new Async(mIsUnevaluated, executor, mLoggerName);
  }

  @NotNull
  public <V> Statement<V> failure(@NotNull final Throwable failure) {
    return statement(new FailureObserver<V>(failure));
  }

  @NotNull
  public <V> Loop<V> failures(@NotNull final Iterable<? extends Throwable> failures) {
    return loop(new FailuresObserver<V>(failures));
  }

  @NotNull
  public Async loggerName(@Nullable final String loggerName) {
    return new Async(mIsUnevaluated, mExecutor, loggerName);
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public <V> Loop<V> loop(@NotNull final Statement<? extends Iterable<V>> statement) {
    if (!mIsUnevaluated && (statement instanceof Loop)) {
      return (Loop<V>) statement;
    }

    return loop(new LoopObserver<V>(statement));
  }

  @NotNull
  public <V> Loop<V> loop(@NotNull final Observer<EvaluationCollection<V>> observer) {
    final boolean isUnevaluated = mIsUnevaluated;
    final Observer<EvaluationCollection<V>> loopObserver = loopObserver(observer);
    return new DefaultLoop<V>(
        (isUnevaluated) ? new UnevaluatedObserver<V, EvaluationCollection<V>>(loopObserver)
            : loopObserver, !isUnevaluated, loopLoggerName());
  }

  @NotNull
  public <S, V, R> Loop<R> loopOf(
      @NotNull final Joiner<S, ? super V, ? super EvaluationCollection<R>, Loop<V>> joiner,
      @NotNull final Iterable<? extends Loop<? extends V>> loops) {
    return loop(new JoinLoopObserver<S, V, R>(joiner, loops, loopLoggerName()));
  }

  @NotNull
  public <S, V, R> Loop<R> loopOf(@Nullable final Mapper<? super List<Loop<V>>, S> init,
      @Nullable final JoinUpdater<S, ? super V, ? super EvaluationCollection<? extends R>,
          Loop<V>> value,
      @Nullable final JoinUpdater<S, ? super Throwable, ? super EvaluationCollection<? extends
          R>, Loop<V>> failure,
      @Nullable final JoinCompleter<S, ? super EvaluationCollection<? extends R>, Loop<V>> done,
      @Nullable final JoinSettler<S, ? super EvaluationCollection<? extends R>, Loop<V>> settle,
      @NotNull final Iterable<? extends Loop<? extends V>> loops) {
    return loopOf(new ComposedLoopJoiner<S, V, R>(init, value, failure, done, settle), loops);
  }

  @NotNull
  public <V> Loop<V> loopOnce(@NotNull final Statement<? extends V> statement) {
    return loop(new SingleLoopObserver<V>(statement));
  }

  @NotNull
  public <V> Statement<V> statement(@NotNull final Observer<Evaluation<V>> observer) {
    final boolean isUnevaluated = mIsUnevaluated;
    final Observer<Evaluation<V>> statementObserver = statementObserver(observer);
    return new DefaultStatement<V>(
        (isUnevaluated) ? new UnevaluatedObserver<V, Evaluation<V>>(statementObserver)
            : statementObserver, !isUnevaluated, statementLoggerName());
  }

  @NotNull
  public <S, V, R> Statement<R> statementOf(
      @NotNull final Joiner<S, ? super V, ? super Evaluation<R>, Statement<V>> joiner,
      @NotNull final Iterable<? extends Statement<? extends V>> statements) {
    return statement(new JoinStatementObserver<S, V, R>(joiner, statements, statementLoggerName()));
  }

  @NotNull
  public <S, V, R> Statement<R> statementOf(
      @Nullable final Mapper<? super List<Statement<V>>, S> init,
      @Nullable final JoinUpdater<S, ? super V, ? super Evaluation<? extends R>, Statement<V>>
          value,
      @Nullable final JoinUpdater<S, ? super Throwable, ? super Evaluation<? extends R>,
          Statement<V>> failure,
      @Nullable final JoinCompleter<S, ? super Evaluation<? extends R>, Statement<V>> done,
      @Nullable final JoinSettler<S, ? super Evaluation<? extends R>, Statement<V>> settle,
      @NotNull final Iterable<? extends Statement<? extends V>> statements) {
    return statementOf(new ComposedStatementJoiner<S, V, R>(init, value, failure, done, settle),
        statements);
  }

  @NotNull
  public Async unevaluated() {
    return new Async(true, mExecutor, mLoggerName);
  }

  @NotNull
  public <V> Statement<V> value(final V value) {
    return statement(new ValueObserver<V>(value));
  }

  @NotNull
  public <V> Loop<V> values(@NotNull final Iterable<? extends V> values) {
    return loop(new ValuesObserver<V>(values));
  }

  private String loopLoggerName() {
    final String loggerName = mLoggerName;
    return (loggerName != null) ? loggerName : Loop.class.getName();
  }

  @NotNull
  private <V> Observer<EvaluationCollection<V>> loopObserver(
      @NotNull final Observer<EvaluationCollection<V>> observer) {
    final Executor executor = mExecutor;
    if (executor != null) {
      return new LoopExecutorObserver<V>(observer, executor);
    }

    return observer;
  }

  private String statementLoggerName() {
    final String loggerName = mLoggerName;
    return (loggerName != null) ? loggerName : Statement.class.getName();
  }

  @NotNull
  private <V> Observer<Evaluation<V>> statementObserver(
      @NotNull final Observer<Evaluation<V>> observer) {
    final Executor executor = mExecutor;
    if (executor != null) {
      return new StatementExecutorObserver<V>(observer, executor);
    }

    return observer;
  }

  private static class FailureObserver<V> implements Observer<Evaluation<V>>, Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final Throwable mFailure;

    private FailureObserver(@NotNull final Throwable failure) {
      mFailure = ConstantConditions.notNull("failure", failure);
    }

    public void accept(final Evaluation<V> evaluation) {
      evaluation.fail(mFailure);
    }
  }

  private static class FailuresObserver<V>
      implements Observer<EvaluationCollection<V>>, Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final Iterable<? extends Throwable> mFailures;

    private FailuresObserver(@Nullable final Iterable<? extends Throwable> failures) {
      mFailures =
          (failures != null) ? ConstantConditions.notNullElements("failures", failures) : null;
    }

    public void accept(final EvaluationCollection<V> evaluation) {
      evaluation.addFailures(mFailures).set();
    }
  }

  private static class LoopExecutorObserver<V>
      implements InterruptibleObserver<EvaluationCollection<V>>, Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final Executor mExecutor;

    private final Object mMutex = new Object();

    private final Observer<EvaluationCollection<V>> mObserver;

    private Thread mThread;

    private LoopExecutorObserver(@NotNull final Observer<EvaluationCollection<V>> observer,
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

      private ObserverProxy(final Observer<EvaluationCollection<V>> observer,
          final Executor executor) {
        super(proxy(observer), executor);
      }

      @NotNull
      @SuppressWarnings("unchecked")
      private Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new LoopExecutorObserver<V>((Observer<EvaluationCollection<V>>) args[0],
              (Executor) args[1]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void accept(final EvaluationCollection<V> evaluation) {
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
              evaluation.addFailure(t).set();

            } catch (final Throwable ignored) {
              // cannot take any action
              // TODO: 17/02/2018 log?
            }
          }
        }
      });
    }
  }

  private static class LoopObserver<V>
      implements RenewableObserver<EvaluationCollection<V>>, Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final Statement<? extends Iterable<V>> mStatement;

    private LoopObserver(@NotNull final Statement<? extends Iterable<V>> statement) {
      mStatement = ConstantConditions.notNull("statement", statement);
    }

    @NotNull
    public Observer<EvaluationCollection<V>> renew() {
      return new LoopObserver<V>(mStatement.evaluate());
    }

    public void accept(final EvaluationCollection<V> evaluation) {
      mStatement.then(new Mapper<Iterable<V>, Void>() {

        public Void apply(final Iterable<V> values) {
          evaluation.addValues(values).set();
          return null;
        }
      }).elseCatch(new Mapper<Throwable, Void>() {

        public Void apply(final Throwable failure) {
          evaluation.addFailure(failure).set();
          return null;
        }
      });
    }
  }

  private static class SingleLoopObserver<V>
      implements RenewableObserver<EvaluationCollection<V>>, Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final Statement<? extends V> mStatement;

    private SingleLoopObserver(@NotNull final Statement<? extends V> statement) {
      mStatement = ConstantConditions.notNull("statement", statement);
    }

    @NotNull
    public Observer<EvaluationCollection<V>> renew() {
      return new SingleLoopObserver<V>(mStatement.evaluate());
    }

    public void accept(final EvaluationCollection<V> evaluation) {
      mStatement.then(new Mapper<V, Void>() {

        public Void apply(final V value) {
          evaluation.addValue(value).set();
          return null;
        }
      }).elseCatch(new Mapper<Throwable, Void>() {

        public Void apply(final Throwable failure) {
          evaluation.addFailure(failure).set();
          return null;
        }
      });
    }
  }

  private static class StatementExecutorObserver<V>
      implements InterruptibleObserver<Evaluation<V>>, Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final Executor mExecutor;

    private final Object mMutex = new Object();

    private final Observer<Evaluation<V>> mObserver;

    private Thread mThread;

    private StatementExecutorObserver(@NotNull final Observer<Evaluation<V>> observer,
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

      private ObserverProxy(final Observer<Evaluation<V>> observer, final Executor executor) {
        super(proxy(observer), executor);
      }

      @NotNull
      @SuppressWarnings("unchecked")
      private Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new StatementExecutorObserver<V>((Observer<Evaluation<V>>) args[0],
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

    public void accept(final Evaluation<V> evaluation) {
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
              evaluation.fail(t);

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
      private Object readResolve() throws ObjectStreamException {
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

  private static class ValueObserver<V> implements Observer<Evaluation<V>>, Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final V mValue;

    private ValueObserver(final V value) {
      mValue = value;
    }

    public void accept(final Evaluation<V> evaluation) {
      evaluation.set(mValue);
    }
  }

  private static class ValuesObserver<V>
      implements Observer<EvaluationCollection<V>>, Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final Iterable<? extends V> mValues;

    private ValuesObserver(@Nullable final Iterable<? extends V> values) {
      mValues = values;
    }

    public void accept(final EvaluationCollection<V> evaluation) {
      evaluation.addValues(mValues).set();
    }
  }
}

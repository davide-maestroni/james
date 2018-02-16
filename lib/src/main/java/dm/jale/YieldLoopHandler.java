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
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import dm.jale.async.Evaluation;
import dm.jale.async.EvaluationCollection;
import dm.jale.async.FailureException;
import dm.jale.async.Loop;
import dm.jale.async.Loop.YieldOutputs;
import dm.jale.async.Loop.Yielder;
import dm.jale.async.RuntimeInterruptedException;
import dm.jale.async.Statement;
import dm.jale.config.BuildConfig;
import dm.jale.log.Logger;
import dm.jale.util.ConstantConditions;
import dm.jale.util.SerializableProxy;

import static dm.jale.executor.ExecutorPool.immediateExecutor;
import static dm.jale.executor.ExecutorPool.withThrottling;

/**
 * Created by davide-maestroni on 02/05/2018.
 */
class YieldLoopHandler<S, V, R> extends AsyncLoopHandler<V, R> implements Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Executor mExecutor;

  private final Logger mLogger;

  private final Yielder<S, ? super V, R> mYielder;

  private final YielderOutputs<R> mYielderOutputs = new YielderOutputs<R>();

  private volatile Throwable mFailure;

  private boolean mIsInitialized;

  private S mStack;

  YieldLoopHandler(@NotNull final Yielder<S, ? super V, R> yielder,
      @Nullable final String loggerName) {
    mYielder = ConstantConditions.notNull("yielder", yielder);
    mExecutor = withThrottling(1, immediateExecutor());
    mLogger = Logger.newLogger(this, loggerName, Locale.ENGLISH);
  }

  @Override
  void addFailure(@NotNull final Throwable failure,
      @NotNull final EvaluationCollection<R> evaluation) {
    checkFailed();
    mExecutor.execute(new YielderRunnable(evaluation) {

      @Override
      protected void innerRun(@NotNull final YielderOutputs<R> outputs) throws Exception {
        mStack = mYielder.failure(mStack, failure, outputs);
      }
    });
  }

  @Override
  void addFailures(@Nullable final Iterable<? extends Throwable> failures,
      @NotNull final EvaluationCollection<R> evaluation) {
    checkFailed();
    if (failures == null) {
      return;
    }

    mExecutor.execute(new YielderRunnable(evaluation) {

      @Override
      protected void innerRun(@NotNull final YielderOutputs<R> outputs) throws Exception {
        @SuppressWarnings("UnnecessaryLocalVariable") final Yielder<S, ? super V, R> yielder =
            mYielder;
        for (final Throwable failure : failures) {
          mStack = yielder.failure(mStack, failure, outputs);
        }
      }
    });
  }

  @Override
  void addValue(final V value, @NotNull final EvaluationCollection<R> evaluation) {
    checkFailed();
    mExecutor.execute(new YielderRunnable(evaluation) {

      @Override
      protected void innerRun(@NotNull final YielderOutputs<R> outputs) throws Exception {
        mStack = mYielder.value(mStack, value, outputs);
      }
    });
  }

  @Override
  void addValues(@Nullable final Iterable<? extends V> values,
      @NotNull final EvaluationCollection<R> evaluation) {
    checkFailed();
    if (values == null) {
      return;
    }

    mExecutor.execute(new YielderRunnable(evaluation) {

      @Override
      protected void innerRun(@NotNull final YielderOutputs<R> outputs) throws Exception {
        @SuppressWarnings("UnnecessaryLocalVariable") final Yielder<S, ? super V, R> yielder =
            mYielder;
        for (final V value : values) {
          mStack = yielder.value(mStack, value, outputs);
        }
      }
    });
  }

  @NotNull
  @Override
  AsyncLoopHandler<V, R> renew() {
    return new YieldLoopHandler<S, V, R>(mYielder, mLogger.getName());
  }

  @Override
  void set(@NotNull final EvaluationCollection<R> evaluation) {
    checkFailed();
    mExecutor.execute(new YielderRunnable(evaluation) {

      @Override
      protected void innerRun(@NotNull final YielderOutputs<R> outputs) throws Exception {
        mYielder.done(mStack, outputs);
        outputs.set();
      }
    });
  }

  private void checkFailed() {
    if (mFailure != null) {
      throw FailureException.wrap(mFailure);
    }
  }

  private void failSafe(@NotNull final EvaluationCollection<R> evaluation,
      @NotNull final Throwable failure) {
    mYielderOutputs.withEvaluations(evaluation).set();
    try {
      evaluation.addFailure(failure).set();

    } catch (final Throwable ignored) {
      // cannot take any action
    }
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new HandlerProxy<S, V, R>(mYielder, mLogger.getName());
  }

  private static class HandlerProxy<S, V, R> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private HandlerProxy(final Yielder<S, ? super V, R> yielder, final String loggerName) {
      super(proxy(yielder), loggerName);
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new YieldLoopHandler<S, V, R>((Yielder<S, ? super V, R>) args[0], (String) args[1]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }

  private static class YielderOutputs<V> implements YieldOutputs<V> {

    private final AtomicBoolean mIsSet = new AtomicBoolean();

    private EvaluationCollection<V> mEvaluation;

    @NotNull
    public YieldOutputs<V> yieldFailure(@NotNull final Throwable failure) {
      checkSet();
      mEvaluation.addFailure(failure);
      return this;
    }

    @NotNull
    public YieldOutputs<V> yieldFailures(@Nullable final Iterable<Throwable> failures) {
      checkSet();
      mEvaluation.addFailures(failures);
      return this;
    }

    @NotNull
    public YieldOutputs<V> yieldIf(@NotNull final Statement<? extends V> statement) {
      checkSet();
      statement.to(new Evaluation<V>() {

        public void fail(@NotNull final Throwable failure) {
          mEvaluation.addFailure(failure);
        }

        public void set(final V value) {
          mEvaluation.addValue(value);
        }
      });
      return this;
    }

    @NotNull
    @SuppressWarnings("unchecked")
    public YieldOutputs<V> yieldLoop(@NotNull final Statement<? extends Iterable<V>> loop) {
      checkSet();
      if (loop instanceof Loop) {
        ((Loop<V>) loop).to(new EvaluationCollection<V>() {

          @NotNull
          public EvaluationCollection<V> addFailure(@NotNull final Throwable failure) {
            mEvaluation.addFailure(failure);
            return this;
          }

          @NotNull
          public EvaluationCollection<V> addFailures(
              @Nullable final Iterable<? extends Throwable> failures) {
            mEvaluation.addFailures(failures);
            return this;
          }

          @NotNull
          public EvaluationCollection<V> addValue(final V value) {
            mEvaluation.addValue(value);
            return this;
          }

          @NotNull
          public EvaluationCollection<V> addValues(@Nullable final Iterable<? extends V> values) {
            mEvaluation.addValues(values);
            return this;
          }

          public void set() {
          }
        });

      } else {
        loop.to(new Evaluation<Iterable<V>>() {

          public void fail(@NotNull final Throwable failure) {
            mEvaluation.addFailure(failure);
          }

          public void set(final Iterable<V> value) {
            mEvaluation.addValues(value);
          }
        });
      }

      return this;
    }

    @NotNull
    public YieldOutputs<V> yieldValue(final V value) {
      checkSet();
      mEvaluation.addValue(value);
      return this;
    }

    @NotNull
    public YieldOutputs<V> yieldValues(@Nullable final Iterable<V> value) {
      checkSet();
      mEvaluation.addValues(value);
      return this;
    }

    private void checkSet() {
      if (mIsSet.get()) {
        throw new IllegalStateException("loop has already complete");
      }
    }

    private void set() {
      if (mIsSet.getAndSet(true)) {
        checkSet();
      }
    }

    @NotNull
    private YielderOutputs<V> withEvaluations(@NotNull final EvaluationCollection<V> evaluation) {
      mEvaluation = evaluation;
      return this;
    }
  }

  private abstract class YielderRunnable implements Runnable {

    private final EvaluationCollection<R> mEvaluation;

    private YielderRunnable(@NotNull final EvaluationCollection<R> evaluation) {
      mEvaluation = evaluation;
    }

    public void run() {
      final EvaluationCollection<R> evaluation = mEvaluation;
      try {
        if (mFailure != null) {
          evaluation.set();
          return;
        }

        if (!mIsInitialized) {
          mIsInitialized = true;
          mStack = mYielder.init();
        }

        innerRun(mYielderOutputs.withEvaluations(evaluation));
        evaluation.set();

      } catch (final CancellationException e) {
        mLogger.wrn(e, "Loop has been cancelled");
        mFailure = e;
        failSafe(evaluation, e);

      } catch (final Throwable t) {
        mLogger.err(t, "Error while completing loop");
        mFailure = t;
        failSafe(evaluation, RuntimeInterruptedException.wrapIfInterrupt(t));
      }
    }

    protected abstract void innerRun(@NotNull YielderOutputs<R> outputs) throws Exception;
  }
}

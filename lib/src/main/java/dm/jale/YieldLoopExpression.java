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
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import dm.jale.config.BuildConfig;
import dm.jale.eventual.Evaluation;
import dm.jale.eventual.EvaluationCollection;
import dm.jale.eventual.FailureException;
import dm.jale.eventual.Loop;
import dm.jale.eventual.Loop.YieldOutputs;
import dm.jale.eventual.Loop.Yielder;
import dm.jale.eventual.Statement;
import dm.jale.util.ConstantConditions;
import dm.jale.util.SerializableProxy;

import static dm.jale.executor.ExecutorPool.immediateExecutor;
import static dm.jale.executor.ExecutorPool.ordered;
import static dm.jale.executor.ExecutorPool.withErrorBackPropagation;

/**
 * Created by davide-maestroni on 02/05/2018.
 */
class YieldLoopExpression<S, V, R> extends LoopExpression<V, R> implements Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Executor mExecutor;

  private final Yielder<S, ? super V, ? super YieldOutputs<R>> mYielder;

  private final YielderOutputs<R> mYielderOutputs = new YielderOutputs<R>();

  private boolean mIsInitialized;

  private volatile boolean mIsLoop = true;

  private S mStack;

  YieldLoopExpression(@NotNull final Yielder<S, ? super V, ? super YieldOutputs<R>> yielder) {
    mYielder = ConstantConditions.notNull("yielder", yielder);
    mExecutor = withErrorBackPropagation(ordered(immediateExecutor()));
  }

  @Override
  void addFailure(@NotNull final Throwable failure,
      @NotNull final EvaluationCollection<R> evaluation) {
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
    mExecutor.execute(new YielderRunnable(evaluation) {

      @Override
      protected void innerRun(@NotNull final YielderOutputs<R> outputs) throws Exception {
        if (failures != null) {
          @SuppressWarnings(
              "UnnecessaryLocalVariable") final Yielder<S, ? super V, ? super YieldOutputs<R>>
              yielder = mYielder;
          for (final Throwable failure : failures) {
            mStack = yielder.failure(mStack, failure, outputs);
          }
        }
      }
    });
  }

  @Override
  void addValue(final V value, @NotNull final EvaluationCollection<R> evaluation) {
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
    mExecutor.execute(new YielderRunnable(evaluation) {

      @Override
      protected void innerRun(@NotNull final YielderOutputs<R> outputs) throws Exception {
        if (values != null) {
          @SuppressWarnings(
              "UnnecessaryLocalVariable") final Yielder<S, ? super V, ? super YieldOutputs<R>>
              yielder = mYielder;
          for (final V value : values) {
            mStack = yielder.value(mStack, value, outputs);
          }
        }
      }
    });
  }

  @Override
  boolean isComplete() {
    return !mIsLoop;
  }

  @NotNull
  @Override
  LoopExpression<V, R> renew() {
    return new YieldLoopExpression<S, V, R>(mYielder);
  }

  @Override
  void set(@NotNull final EvaluationCollection<R> evaluation) {
    mExecutor.execute(new YielderRunnable(evaluation, true) {

      @Override
      protected void innerRun(@NotNull final YielderOutputs<R> outputs) throws Exception {
        mYielder.done(mStack, outputs);
        outputs.set();
      }
    });
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
    return new HandlerProxy<S, V, R>(mYielder);
  }

  private static class HandlerProxy<S, V, R> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private HandlerProxy(final Yielder<S, ? super V, ? super YieldOutputs<R>> yielder) {
      super(proxy(yielder));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new YieldLoopExpression<S, V, R>(
            (Yielder<S, ? super V, ? super YieldOutputs<R>>) args[0]);

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
    public YieldOutputs<V> yieldLoopIf(
        @NotNull final Statement<? extends Iterable<? extends V>> loop) {
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
        loop.to(new Evaluation<Iterable<? extends V>>() {

          public void fail(@NotNull final Throwable failure) {
            mEvaluation.addFailure(failure);
          }

          public void set(final Iterable<? extends V> value) {
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
        throw new IllegalStateException("loop has already completed");
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

    private final boolean mForceRun;

    private YielderRunnable(@NotNull final EvaluationCollection<R> evaluation) {
      this(evaluation, false);
    }

    private YielderRunnable(@NotNull final EvaluationCollection<R> evaluation,
        final boolean forceRun) {
      mEvaluation = evaluation;
      mForceRun = forceRun;
    }

    public void run() {
      final EvaluationCollection<R> evaluation = mEvaluation;
      try {
        final Yielder<S, ? super V, ? super YieldOutputs<R>> yielder = mYielder;
        if (!mIsInitialized) {
          mIsInitialized = true;
          mStack = yielder.init();
        }

        if (mIsLoop) {
          mIsLoop = yielder.loop(mStack);
        }

        if (mIsLoop || mForceRun) {
          innerRun(mYielderOutputs.withEvaluations(evaluation));
        }

        evaluation.set();

      } catch (final Throwable t) {
        failSafe(evaluation, t);
        throw FailureException.wrapIfNot(RuntimeException.class, t);
      }
    }

    protected abstract void innerRun(@NotNull YielderOutputs<R> outputs) throws Exception;
  }
}

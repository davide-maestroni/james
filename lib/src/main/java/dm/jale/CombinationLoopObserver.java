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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;

import dm.jale.async.Combiner;
import dm.jale.async.EvaluationCollection;
import dm.jale.async.FailureException;
import dm.jale.async.Loop;
import dm.jale.async.Observer;
import dm.jale.async.RuntimeInterruptedException;
import dm.jale.config.BuildConfig;
import dm.jale.log.Logger;
import dm.jale.util.ConstantConditions;
import dm.jale.util.Iterables;
import dm.jale.util.SerializableProxy;

import static dm.jale.executor.ExecutorPool.immediateExecutor;
import static dm.jale.executor.ExecutorPool.withThrottling;

/**
 * Created by davide-maestroni on 02/14/2018.
 */
class CombinationLoopObserver<S, V, R> implements Observer<EvaluationCollection<R>>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Combiner<S, ? super V, ? super EvaluationCollection<R>, Loop<V>> mCombiner;

  private final Executor mExecutor;

  private final Logger mLogger;

  private final List<Loop<? extends V>> mLoopList;

  CombinationLoopObserver(
      @NotNull final Combiner<S, ? super V, ? super EvaluationCollection<R>, Loop<V>> combiner,
      @NotNull final Iterable<? extends Loop<? extends V>> loops,
      @NotNull final String loggerName) {
    mCombiner = ConstantConditions.notNull("combiner", combiner);
    mLoopList = Collections.unmodifiableList(Iterables.toList(loops));
    mExecutor = withThrottling(1, immediateExecutor());
    mLogger = Logger.newLogger(this, loggerName);
  }

  @SuppressWarnings("unchecked")
  public void accept(final EvaluationCollection<R> evaluations) throws Exception {
    int i = 0;
    @SuppressWarnings("UnnecessaryLocalVariable") final Logger logger = mLogger;
    @SuppressWarnings("UnnecessaryLocalVariable") final Executor executor = mExecutor;
    final Combiner<S, ? super V, ? super EvaluationCollection<R>, Loop<V>> combiner = mCombiner;
    final List<? extends Loop<? extends V>> loops = mLoopList;
    final CombinationState<S> state = new CombinationState<S>(loops.size());
    try {
      state.set(combiner.init((List<Loop<V>>) loops));
      for (final Loop<? extends V> loop : loops) {
        final int index = i++;
        loop.to(new EvaluationCollectionCombination<S, V, R>(state, combiner, executor, evaluations,
            loops, index, logger));
      }

    } catch (final CancellationException e) {
      mLogger.wrn(e, "Loop has been cancelled");
      state.setFailed(e);
      Asyncs.failSafe(evaluations, e);

    } catch (final Throwable t) {
      mLogger.err(t, "Error while initializing statements combination");
      state.setFailed(t);
      Asyncs.failSafe(evaluations, RuntimeInterruptedException.wrapIfInterrupt(t));
    }
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new ObserverProxy<S, V, R>(mCombiner, mLoopList, mLogger.getName());
  }

  private static class EvaluationCollectionCombination<S, V, R> implements EvaluationCollection<V> {

    private final Combiner<S, ? super V, ? super EvaluationCollection<R>, Loop<V>> mCombiner;

    private final EvaluationCollection<R> mEvaluations;

    private final Executor mExecutor;

    private final int mIndex;

    private final Logger mLogger;

    private final List<? extends Loop<? extends V>> mLoops;

    private final CombinationState<S> mState;

    private EvaluationCollectionCombination(@NotNull final CombinationState<S> state,
        @NotNull final Combiner<S, ? super V, ? super EvaluationCollection<R>, Loop<V>> combiner,
        @NotNull final Executor executor, @NotNull final EvaluationCollection<R> evaluations,
        @NotNull final List<? extends Loop<? extends V>> loops, final int index,
        @NotNull final Logger logger) {
      mState = state;
      mCombiner = combiner;
      mExecutor = executor;
      mEvaluations = evaluations;
      mLoops = loops;
      mIndex = index;
      mLogger = logger;
    }

    @NotNull
    public EvaluationCollection<V> addFailure(@NotNull final Throwable failure) {
      checkFailed();
      mExecutor.execute(new Runnable() {

        public void run() {
          final CombinationState<S> state = mState;
          if (state.isFailed()) {
            mLogger.wrn("Ignoring failure: %s", failure);
            return;
          }

          final EvaluationCollection<R> evaluations = mEvaluations;
          try {
            @SuppressWarnings("unchecked") final List<Loop<V>> loops = (List<Loop<V>>) mLoops;
            state.set(mCombiner.failure(state.get(), failure, evaluations, loops, mIndex));

          } catch (final CancellationException e) {
            mLogger.wrn(e, "Loop has been cancelled");
            state.setFailed(e);
            Asyncs.failSafe(evaluations, e);

          } catch (final Throwable t) {
            mLogger.err(t, "Error while processing failure: %s", failure);
            state.setFailed(t);
            Asyncs.failSafe(evaluations, RuntimeInterruptedException.wrapIfInterrupt(t));
          }
        }
      });
      return this;
    }

    @NotNull
    public EvaluationCollection<V> addFailures(
        @Nullable final Iterable<? extends Throwable> failures) {
      checkFailed();
      mExecutor.execute(new Runnable() {

        public void run() {
          final CombinationState<S> state = mState;
          if (state.isFailed()) {
            mLogger.wrn("Ignoring failures: %s", failures);
            return;
          }

          if (failures != null) {
            final EvaluationCollection<R> evaluations = mEvaluations;
            try {
              @SuppressWarnings("UnnecessaryLocalVariable") final int index = mIndex;
              @SuppressWarnings(
                  "UnnecessaryLocalVariable") final Combiner<S, ? super V, ? super
                  EvaluationCollection<R>, Loop<V>>
                  combiner = mCombiner;
              @SuppressWarnings("unchecked") final List<Loop<V>> loops = (List<Loop<V>>) mLoops;
              for (final Throwable failure : failures) {
                state.set(combiner.failure(state.get(), failure, evaluations, loops, index));
              }

            } catch (final CancellationException e) {
              mLogger.wrn(e, "Loop has been cancelled");
              state.setFailed(e);
              Asyncs.failSafe(evaluations, e);

            } catch (final Throwable t) {
              mLogger.err(t, "Error while processing failures: %s", failures);
              state.setFailed(t);
              Asyncs.failSafe(evaluations, RuntimeInterruptedException.wrapIfInterrupt(t));
            }
          }
        }
      });
      return this;
    }

    @NotNull
    public EvaluationCollection<V> addValue(final V value) {
      checkFailed();
      mExecutor.execute(new Runnable() {

        public void run() {
          final CombinationState<S> state = mState;
          if (state.isFailed()) {
            mLogger.wrn("Ignoring value: %s", value);
            return;
          }

          final EvaluationCollection<R> evaluations = mEvaluations;
          try {
            @SuppressWarnings("unchecked") final List<Loop<V>> loops = (List<Loop<V>>) mLoops;
            state.set(mCombiner.value(state.get(), value, evaluations, loops, mIndex));

          } catch (final CancellationException e) {
            mLogger.wrn(e, "Loop has been cancelled");
            state.setFailed(e);
            Asyncs.failSafe(evaluations, e);

          } catch (final Throwable t) {
            mLogger.err(t, "Error while processing value: %s", value);
            state.setFailed(t);
            Asyncs.failSafe(evaluations, RuntimeInterruptedException.wrapIfInterrupt(t));
          }
        }
      });
      return this;
    }

    @NotNull
    public EvaluationCollection<V> addValues(@Nullable final Iterable<? extends V> values) {
      checkFailed();
      mExecutor.execute(new Runnable() {

        public void run() {
          final CombinationState<S> state = mState;
          if (state.isFailed()) {
            mLogger.wrn("Ignoring values: %s", values);
            return;
          }

          if (values != null) {
            final EvaluationCollection<R> evaluations = mEvaluations;
            try {
              @SuppressWarnings("UnnecessaryLocalVariable") final int index = mIndex;
              @SuppressWarnings(
                  "UnnecessaryLocalVariable") final Combiner<S, ? super V, ? super
                  EvaluationCollection<R>, Loop<V>>
                  combiner = mCombiner;
              @SuppressWarnings("unchecked") final List<Loop<V>> loops = (List<Loop<V>>) mLoops;
              for (final V value : values) {
                state.set(combiner.value(state.get(), value, evaluations, loops, index));
              }

            } catch (final CancellationException e) {
              mLogger.wrn(e, "Loop has been cancelled");
              state.setFailed(e);
              Asyncs.failSafe(evaluations, e);

            } catch (final Throwable t) {
              mLogger.err(t, "Error while processing values: %s", values);
              state.setFailed(t);
              Asyncs.failSafe(evaluations, RuntimeInterruptedException.wrapIfInterrupt(t));
            }
          }
        }
      });
      return this;
    }

    public void set() {
      checkFailed();
      mExecutor.execute(new Runnable() {

        public void run() {
          final CombinationState<S> state = mState;
          if (state.isFailed()) {
            mLogger.wrn("Ignoring completion");
            return;
          }

          final EvaluationCollection<R> evaluations = mEvaluations;
          try {
            @SuppressWarnings("UnnecessaryLocalVariable") final int index = mIndex;
            final Combiner<S, ? super V, ? super EvaluationCollection<R>, Loop<V>> combiner =
                mCombiner;
            @SuppressWarnings("unchecked") final List<Loop<V>> loops = (List<Loop<V>>) mLoops;
            state.set(combiner.done(state.get(), evaluations, loops, index));
            if (state.set()) {
              combiner.settle(state.get(), evaluations, loops);
            }

          } catch (final CancellationException e) {
            mLogger.wrn(e, "Statement has been cancelled");
            state.setFailed(e);
            Asyncs.failSafe(evaluations, e);

          } catch (final Throwable t) {
            mLogger.err(t, "Error while completing loop");
            state.setFailed(t);
            Asyncs.failSafe(evaluations, RuntimeInterruptedException.wrapIfInterrupt(t));
          }
        }
      });
    }

    private void checkFailed() {
      final CombinationState<S> state = mState;
      if (state.isFailed()) {
        throw FailureException.wrap(state.getFailure());
      }
    }
  }

  private static class ObserverProxy<S, V, R> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private ObserverProxy(
        final Combiner<S, ? super V, ? super EvaluationCollection<R>, Loop<V>> combiner,
        final Iterable<? extends Loop<? extends V>> loops, final String loggerName) {
      super(proxy(combiner), loops, loggerName);
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new CombinationLoopObserver<S, V, R>(
            (Combiner<S, ? super V, ? super EvaluationCollection<R>, Loop<V>>) args[0],
            (Iterable<? extends Loop<? extends V>>) args[1], (String) args[2]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

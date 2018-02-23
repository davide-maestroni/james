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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;

import dm.jale.config.BuildConfig;
import dm.jale.eventual.EvaluationCollection;
import dm.jale.eventual.FailureException;
import dm.jale.eventual.Joiner;
import dm.jale.eventual.Loop;
import dm.jale.log.Logger;
import dm.jale.util.ConstantConditions;
import dm.jale.util.Iterables;
import dm.jale.util.SerializableProxy;

import static dm.jale.executor.ExecutorPool.immediateExecutor;
import static dm.jale.executor.ExecutorPool.withThrottling;

/**
 * Created by davide-maestroni on 02/14/2018.
 */
class JoinLoopObserver<S, V, R>
    implements RenewableObserver<EvaluationCollection<R>>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Executor mExecutor;

  private final Joiner<S, ? super V, ? super EvaluationCollection<R>, Loop<V>> mJoiner;

  private final Logger mLogger;

  private final List<Loop<? extends V>> mLoopList;

  JoinLoopObserver(
      @NotNull final Joiner<S, ? super V, ? super EvaluationCollection<R>, Loop<V>> joiner,
      @NotNull final Iterable<? extends Loop<? extends V>> loops,
      @NotNull final String loggerName) {
    final List<? extends Loop<? extends V>> loopList =
        Iterables.toList(ConstantConditions.notNullElements("loops", loops));
    mJoiner = ConstantConditions.notNull("joiner", joiner);
    mLoopList = Collections.unmodifiableList(loopList);
    mExecutor = withThrottling(1, immediateExecutor());
    mLogger = Logger.newLogger(this, loggerName);
  }

  @SuppressWarnings("unchecked")
  public void accept(final EvaluationCollection<R> evaluation) throws Exception {
    int i = 0;
    @SuppressWarnings("UnnecessaryLocalVariable") final Logger logger = mLogger;
    @SuppressWarnings("UnnecessaryLocalVariable") final Executor executor = mExecutor;
    final Joiner<S, ? super V, ? super EvaluationCollection<R>, Loop<V>> joiner = mJoiner;
    final List<? extends Loop<? extends V>> loops = mLoopList;
    final JoinState<S> state = new JoinState<S>(loops.size());
    try {
      state.set(joiner.init((List<Loop<V>>) loops));
      for (final Loop<? extends V> loop : loops) {
        final int index = i++;
        loop.to(
            new JoinEvaluationCollection<S, V, R>(state, joiner, executor, evaluation, loops, index,
                logger));
      }

    } catch (final CancellationException e) {
      mLogger.wrn(e, "Loop has been cancelled");
      state.setFailed(e);
      Eventuals.failSafe(evaluation, e);

    } catch (final Throwable t) {
      mLogger.err(t, "Error while initializing statements joining");
      state.setFailed(t);
      Eventuals.failSafe(evaluation, t);
    }
  }

  @NotNull
  public JoinLoopObserver<S, V, R> renew() {
    final ArrayList<Loop<? extends V>> loops = new ArrayList<Loop<? extends V>>();
    for (final Loop<? extends V> loop : mLoopList) {
      loops.add(loop.evaluate());
    }

    return new JoinLoopObserver<S, V, R>(mJoiner, loops, mLogger.getName());
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new ObserverProxy<S, V, R>(mJoiner, mLoopList, mLogger.getName());
  }

  private static class JoinEvaluationCollection<S, V, R> implements EvaluationCollection<V> {

    private final EvaluationCollection<R> mEvaluation;

    private final Executor mExecutor;

    private final int mIndex;

    private final Joiner<S, ? super V, ? super EvaluationCollection<R>, Loop<V>> mJoiner;

    private final Logger mLogger;

    private final List<? extends Loop<? extends V>> mLoops;

    private final JoinState<S> mState;

    private JoinEvaluationCollection(@NotNull final JoinState<S> state,
        @NotNull final Joiner<S, ? super V, ? super EvaluationCollection<R>, Loop<V>> joiner,
        @NotNull final Executor executor, @NotNull final EvaluationCollection<R> evaluation,
        @NotNull final List<? extends Loop<? extends V>> loops, final int index,
        @NotNull final Logger logger) {
      mState = state;
      mJoiner = joiner;
      mExecutor = executor;
      mEvaluation = evaluation;
      mLoops = loops;
      mIndex = index;
      mLogger = logger;
    }

    @NotNull
    public EvaluationCollection<V> addFailure(@NotNull final Throwable failure) {
      checkFailed();
      mExecutor.execute(new Runnable() {

        public void run() {
          final JoinState<S> state = mState;
          if (state.isFailed()) {
            mLogger.wrn("Ignoring failure: %s", failure);
            return;
          }

          final EvaluationCollection<R> evaluation = mEvaluation;
          try {
            @SuppressWarnings("unchecked") final List<Loop<V>> loops = (List<Loop<V>>) mLoops;
            state.set(mJoiner.failure(state.get(), failure, evaluation, loops, mIndex));

          } catch (final CancellationException e) {
            mLogger.wrn(e, "Loop has been cancelled");
            state.setFailed(e);
            Eventuals.failSafe(evaluation, e);

          } catch (final Throwable t) {
            mLogger.err(t, "Error while processing failure: %s", failure);
            state.setFailed(t);
            Eventuals.failSafe(evaluation, t);
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
          final JoinState<S> state = mState;
          if (state.isFailed()) {
            mLogger.wrn("Ignoring failures: %s", Iterables.toString(failures));
            return;
          }

          if (failures != null) {
            final EvaluationCollection<R> evaluation = mEvaluation;
            try {
              @SuppressWarnings("UnnecessaryLocalVariable") final int index = mIndex;
              @SuppressWarnings(
                  "UnnecessaryLocalVariable") final Joiner<S, ? super V, ? super
                  EvaluationCollection<R>, Loop<V>>
                  joiner = mJoiner;
              @SuppressWarnings("unchecked") final List<Loop<V>> loops = (List<Loop<V>>) mLoops;
              for (final Throwable failure : failures) {
                state.set(joiner.failure(state.get(), failure, evaluation, loops, index));
              }

            } catch (final CancellationException e) {
              mLogger.wrn(e, "Loop has been cancelled");
              state.setFailed(e);
              Eventuals.failSafe(evaluation, e);

            } catch (final Throwable t) {
              mLogger.err(t, "Error while processing failures: %s", failures);
              state.setFailed(t);
              Eventuals.failSafe(evaluation, t);
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
          final JoinState<S> state = mState;
          if (state.isFailed()) {
            mLogger.wrn("Ignoring value: %s", value);
            return;
          }

          final EvaluationCollection<R> evaluation = mEvaluation;
          try {
            @SuppressWarnings("unchecked") final List<Loop<V>> loops = (List<Loop<V>>) mLoops;
            state.set(mJoiner.value(state.get(), value, evaluation, loops, mIndex));

          } catch (final CancellationException e) {
            mLogger.wrn(e, "Loop has been cancelled");
            state.setFailed(e);
            Eventuals.failSafe(evaluation, e);

          } catch (final Throwable t) {
            mLogger.err(t, "Error while processing value: %s", value);
            state.setFailed(t);
            Eventuals.failSafe(evaluation, t);
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
          final JoinState<S> state = mState;
          if (state.isFailed()) {
            mLogger.wrn("Ignoring values: %s", Iterables.toString(values));
            return;
          }

          if (values != null) {
            final EvaluationCollection<R> evaluation = mEvaluation;
            try {
              @SuppressWarnings("UnnecessaryLocalVariable") final int index = mIndex;
              @SuppressWarnings(
                  "UnnecessaryLocalVariable") final Joiner<S, ? super V, ? super
                  EvaluationCollection<R>, Loop<V>>
                  joiner = mJoiner;
              @SuppressWarnings("unchecked") final List<Loop<V>> loops = (List<Loop<V>>) mLoops;
              for (final V value : values) {
                state.set(joiner.value(state.get(), value, evaluation, loops, index));
              }

            } catch (final CancellationException e) {
              mLogger.wrn(e, "Loop has been cancelled");
              state.setFailed(e);
              Eventuals.failSafe(evaluation, e);

            } catch (final Throwable t) {
              mLogger.err(t, "Error while processing values: %s", values);
              state.setFailed(t);
              Eventuals.failSafe(evaluation, t);
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
          final JoinState<S> state = mState;
          if (state.isFailed()) {
            mLogger.wrn("Ignoring completion");
            return;
          }

          final EvaluationCollection<R> evaluation = mEvaluation;
          try {
            @SuppressWarnings("UnnecessaryLocalVariable") final int index = mIndex;
            final Joiner<S, ? super V, ? super EvaluationCollection<R>, Loop<V>> joiner = mJoiner;
            @SuppressWarnings("unchecked") final List<Loop<V>> loops = (List<Loop<V>>) mLoops;
            state.set(joiner.done(state.get(), evaluation, loops, index));
            if (state.set()) {
              joiner.settle(state.get(), evaluation, loops);
            }

          } catch (final CancellationException e) {
            mLogger.wrn(e, "Statement has been cancelled");
            state.setFailed(e);
            Eventuals.failSafe(evaluation, e);

          } catch (final Throwable t) {
            mLogger.err(t, "Error while completing loop");
            state.setFailed(t);
            Eventuals.failSafe(evaluation, t);
          }
        }
      });
    }

    private void checkFailed() {
      final JoinState<S> state = mState;
      if (state.isFailed()) {
        throw FailureException.wrap(state.getFailure());
      }
    }
  }

  private static class ObserverProxy<S, V, R> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private ObserverProxy(
        final Joiner<S, ? super V, ? super EvaluationCollection<R>, Loop<V>> joiner,
        final Iterable<? extends Loop<? extends V>> loops, final String loggerName) {
      super(proxy(joiner), loops, loggerName);
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new JoinLoopObserver<S, V, R>(
            (Joiner<S, ? super V, ? super EvaluationCollection<R>, Loop<V>>) args[0],
            (Iterable<? extends Loop<? extends V>>) args[1], (String) args[2]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

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

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;

import dm.jale.async.Evaluation;
import dm.jale.async.FailureException;
import dm.jale.async.Joiner;
import dm.jale.async.Observer;
import dm.jale.async.Statement;
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
class JoinStatementObserver<S, V, R> implements Observer<Evaluation<R>>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Executor mExecutor;

  private final Joiner<S, ? super V, ? super Evaluation<R>, Statement<V>> mJoiner;

  private final Logger mLogger;

  private final List<Statement<? extends V>> mStatementList;

  JoinStatementObserver(
      @NotNull final Joiner<S, ? super V, ? super Evaluation<R>, Statement<V>> joiner,
      @NotNull final Iterable<? extends Statement<? extends V>> statements,
      @NotNull final String loggerName) {
    final List<? extends Statement<? extends V>> statementList =
        Iterables.toList(ConstantConditions.notNullElements("statements", statements));
    mJoiner = ConstantConditions.notNull("joiner", joiner);
    mStatementList = Collections.unmodifiableList(statementList);
    mExecutor = withThrottling(1, immediateExecutor());
    mLogger = Logger.newLogger(this, loggerName);
  }

  @SuppressWarnings("unchecked")
  public void accept(final Evaluation<R> evaluation) throws Exception {
    int i = 0;
    @SuppressWarnings("UnnecessaryLocalVariable") final Logger logger = mLogger;
    @SuppressWarnings("UnnecessaryLocalVariable") final Executor executor = mExecutor;
    final Joiner<S, ? super V, ? super Evaluation<R>, Statement<V>> joiner = mJoiner;
    final List<? extends Statement<? extends V>> statements = mStatementList;
    final JoinState<S> state = new JoinState<S>(statements.size());
    try {
      state.set(joiner.init((List<Statement<V>>) statements));
      for (final Statement<? extends V> statement : statements) {
        final int index = i++;
        statement.to(
            new JoinEvaluation<S, V, R>(state, joiner, executor, evaluation, statements, index,
                logger));
      }

    } catch (final CancellationException e) {
      mLogger.wrn(e, "Statement has been cancelled");
      state.setFailed(e);
      Asyncs.failSafe(evaluation, e);

    } catch (final Throwable t) {
      mLogger.err(t, "Error while initializing statements joining");
      state.setFailed(t);
      Asyncs.failSafe(evaluation, t);
    }
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new ObserverProxy<S, V, R>(mJoiner, mStatementList, mLogger.getName());
  }

  private static class JoinEvaluation<S, V, R> implements Evaluation<V> {

    private final Evaluation<R> mEvaluation;

    private final Executor mExecutor;

    private final int mIndex;

    private final Joiner<S, ? super V, ? super Evaluation<R>, Statement<V>> mJoiner;

    private final Logger mLogger;

    private final JoinState<S> mState;

    private final List<? extends Statement<? extends V>> mStatements;

    private JoinEvaluation(@NotNull final JoinState<S> state,
        @NotNull final Joiner<S, ? super V, ? super Evaluation<R>, Statement<V>> joiner,
        @NotNull final Executor executor, final Evaluation<R> evaluation,
        @NotNull final List<? extends Statement<? extends V>> statements, final int index,
        @NotNull final Logger logger) {
      mState = state;
      mJoiner = joiner;
      mExecutor = executor;
      mEvaluation = evaluation;
      mStatements = statements;
      mIndex = index;
      mLogger = logger;
    }

    public void fail(@NotNull final Throwable failure) {
      checkFailed();
      mExecutor.execute(new Runnable() {

        public void run() {
          final JoinState<S> state = mState;
          if (state.isFailed()) {
            mLogger.wrn("Ignoring failure: %s", failure);
            return;
          }

          final Evaluation<R> evaluation = mEvaluation;
          try {
            final int index = mIndex;
            final Joiner<S, ? super V, ? super Evaluation<R>, Statement<V>> joiner = mJoiner;
            @SuppressWarnings("unchecked") final List<Statement<V>> statements =
                (List<Statement<V>>) mStatements;
            state.set(joiner.failure(state.get(), failure, evaluation, statements, index));
            state.set(joiner.done(state.get(), evaluation, statements, index));
            if (state.set()) {
              joiner.settle(state.get(), evaluation, statements);
            }

          } catch (final CancellationException e) {
            mLogger.wrn(e, "Statement has been cancelled");
            state.setFailed(e);
            Asyncs.failSafe(evaluation, e);

          } catch (final Throwable t) {
            mLogger.err(t, "Error while processing failure: %s", failure);
            state.setFailed(t);
            Asyncs.failSafe(evaluation, t);
          }
        }
      });
    }

    public void set(final V value) {
      checkFailed();
      mExecutor.execute(new Runnable() {

        public void run() {
          final JoinState<S> state = mState;
          if (state.isFailed()) {
            mLogger.wrn("Ignoring value: %s", value);
            return;
          }

          final Evaluation<R> evaluation = mEvaluation;
          try {
            final int index = mIndex;
            final Joiner<S, ? super V, ? super Evaluation<R>, Statement<V>> joiner = mJoiner;
            @SuppressWarnings("unchecked") final List<Statement<V>> statements =
                (List<Statement<V>>) mStatements;
            state.set(joiner.value(state.get(), value, evaluation, statements, index));
            state.set(joiner.done(state.get(), evaluation, statements, index));
            if (state.set()) {
              joiner.settle(state.get(), evaluation, statements);
            }

          } catch (final CancellationException e) {
            mLogger.wrn(e, "Statement has been cancelled");
            state.setFailed(e);
            Asyncs.failSafe(evaluation, e);

          } catch (final Throwable t) {
            mLogger.err(t, "Error while processing value: %s", value);
            state.setFailed(t);
            Asyncs.failSafe(evaluation, t);
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

    private ObserverProxy(final Joiner<S, ? super V, ? super Evaluation<R>, Statement<V>> joiner,
        final Iterable<? extends Statement<? extends V>> statements, final String loggerName) {
      super(proxy(joiner), statements, loggerName);
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new JoinStatementObserver<S, V, R>(
            (Joiner<S, ? super V, ? super Evaluation<R>, Statement<V>>) args[0],
            (Iterable<? extends Statement<? extends V>>) args[1], (String) args[2]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

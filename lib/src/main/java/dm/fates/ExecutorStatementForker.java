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

package dm.fates;

import org.jetbrains.annotations.NotNull;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.concurrent.Executor;

import dm.fates.ExecutorStatementForker.ForkerStack;
import dm.fates.config.BuildConfig;
import dm.fates.eventual.Evaluation;
import dm.fates.eventual.SimpleState;
import dm.fates.eventual.Statement;
import dm.fates.eventual.StatementForker;
import dm.fates.executor.EvaluationExecutor;
import dm.fates.executor.ExecutorPool;

/**
 * Created by davide-maestroni on 02/12/2018.
 */
class ExecutorStatementForker<V> implements StatementForker<ForkerStack<V>, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final EvaluationExecutor mExecutor;

  ExecutorStatementForker(@NotNull final Executor executor) {
    mExecutor = ExecutorPool.register(executor);
  }

  public ForkerStack<V> done(final ForkerStack<V> stack, @NotNull final Statement<V> context) {
    return stack;
  }

  public ForkerStack<V> evaluation(final ForkerStack<V> stack,
      @NotNull final Evaluation<V> evaluation, @NotNull final Statement<V> context) {
    if (stack.evaluation == null) {
      stack.evaluation = evaluation;
      final SimpleState<V> state = stack.state;
      if (state != null) {
        try {
          if (state.isSet()) {
            value(stack, state.value(), context);

          } else {
            failure(stack, state.failure(), context);
          }

        } finally {
          stack.state = null;
        }
      }

    } else {
      evaluation.fail(new IllegalStateException("the statement evaluation cannot be propagated"));
    }

    return stack;
  }

  public ForkerStack<V> failure(final ForkerStack<V> stack, @NotNull final Throwable failure,
      @NotNull final Statement<V> context) {
    final Evaluation<V> evaluation = stack.evaluation;
    if (evaluation != null) {
      mExecutor.execute(new Runnable() {

        public void run() {
          evaluation.fail(failure);
        }
      });

    } else {
      stack.state = SimpleState.ofFailure(failure);
    }

    return stack;
  }

  public ForkerStack<V> init(@NotNull final Statement<V> context) {
    return new ForkerStack<V>();
  }

  public ForkerStack<V> value(final ForkerStack<V> stack, final V value,
      @NotNull final Statement<V> context) {
    final Evaluation<V> evaluation = stack.evaluation;
    if (evaluation != null) {
      mExecutor.execute(new Runnable() {

        public void run() {
          evaluation.set(value);
        }
      });

    } else {
      stack.state = SimpleState.ofValue(value);
    }

    return stack;
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new ForkerProxy<V>(mExecutor);
  }

  static class ForkerStack<V> {

    private Evaluation<V> evaluation;

    private SimpleState<V> state;
  }

  private static class ForkerProxy<V> implements Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final Executor mExecutor;

    private ForkerProxy(final Executor executor) {
      mExecutor = executor;
    }

    @NotNull
    private Object readResolve() throws ObjectStreamException {
      try {
        return new ExecutorStatementForker<V>(mExecutor);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

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
import java.util.concurrent.Executor;

import dm.jale.config.BuildConfig;
import dm.jale.eventual.Evaluation;
import dm.jale.eventual.Statement;
import dm.jale.eventual.StatementForker;
import dm.jale.executor.EvaluationExecutor;
import dm.jale.executor.ExecutorPool;

/**
 * Created by davide-maestroni on 02/12/2018.
 */
class ExecutorStatementForker<V>
    extends BufferedForker<Evaluation<V>, V, Evaluation<V>, Statement<V>> {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  ExecutorStatementForker(@NotNull final Executor executor) {
    super(new InnerForker<V>(executor));
  }

  private static class InnerForker<V> implements StatementForker<Evaluation<V>, V>, Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final EvaluationExecutor mExecutor;

    private InnerForker(@NotNull final Executor executor) {
      mExecutor = ExecutorPool.register(executor);
    }

    public Evaluation<V> done(final Evaluation<V> stack, @NotNull final Statement<V> context) {
      return stack;
    }

    public Evaluation<V> evaluation(final Evaluation<V> stack,
        @NotNull final Evaluation<V> evaluation, @NotNull final Statement<V> context) {
      if (stack == null) {
        return evaluation;

      } else {
        evaluation.fail(new IllegalStateException("the statement evaluation cannot be propagated"));
      }

      return stack;
    }

    public Evaluation<V> failure(final Evaluation<V> stack, @NotNull final Throwable failure,
        @NotNull final Statement<V> context) {
      mExecutor.execute(new Runnable() {

        public void run() {
          stack.fail(failure);
        }
      });
      return stack;
    }

    public Evaluation<V> init(@NotNull final Statement<V> context) {
      return null;
    }

    public Evaluation<V> value(final Evaluation<V> stack, final V value,
        @NotNull final Statement<V> context) {
      mExecutor.execute(new Runnable() {

        public void run() {
          stack.set(value);
        }
      });
      return stack;
    }

    @NotNull
    private Object writeReplace() throws ObjectStreamException {
      return new ForkerProxy<V>(mExecutor);
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
          return new InnerForker<V>(mExecutor);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }
  }
}

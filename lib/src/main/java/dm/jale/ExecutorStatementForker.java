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

import dm.jale.async.AsyncEvaluation;
import dm.jale.async.AsyncStatement;
import dm.jale.async.StatementForker;
import dm.jale.config.BuildConfig;
import dm.jale.executor.ExecutorPool;
import dm.jale.executor.OwnerExecutor;

/**
 * Created by davide-maestroni on 02/12/2018.
 */
class ExecutorStatementForker<V>
    extends BufferedForker<AsyncEvaluation<V>, V, AsyncEvaluation<V>, AsyncStatement<V>> {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  ExecutorStatementForker(@NotNull final Executor executor) {
    super(new InnerForker<V>(executor));
  }

  private static class InnerForker<V>
      implements StatementForker<AsyncEvaluation<V>, V>, Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final OwnerExecutor mExecutor;

    private InnerForker(@NotNull final Executor executor) {
      mExecutor = ExecutorPool.register(executor);
    }

    public AsyncEvaluation<V> done(final AsyncEvaluation<V> stack,
        @NotNull final AsyncStatement<V> async) {
      return stack;
    }

    public AsyncEvaluation<V> evaluation(final AsyncEvaluation<V> stack,
        @NotNull final AsyncEvaluation<V> evaluation, @NotNull final AsyncStatement<V> async) {
      if (stack == null) {
        return evaluation;

      } else {
        evaluation.fail(new IllegalStateException());
      }

      return stack;
    }

    public AsyncEvaluation<V> failure(final AsyncEvaluation<V> stack,
        @NotNull final Throwable failure, @NotNull final AsyncStatement<V> async) {
      mExecutor.execute(new Runnable() {

        public void run() {
          stack.fail(failure);
        }
      });
      return stack;
    }

    public AsyncEvaluation<V> init(@NotNull final AsyncStatement<V> async) {
      return null;
    }

    public AsyncEvaluation<V> value(final AsyncEvaluation<V> stack, final V value,
        @NotNull final AsyncStatement<V> async) {
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
      Object readResolve() throws ObjectStreamException {
        try {
          return new InnerForker<V>(mExecutor);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }
  }
}

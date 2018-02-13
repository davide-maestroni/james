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
import java.util.concurrent.atomic.AtomicLong;

import dm.jale.ExecutorLoopForker.Stack;
import dm.jale.async.AsyncEvaluations;
import dm.jale.async.AsyncLoop;
import dm.jale.async.AsyncStatement.Forker;
import dm.jale.config.BuildConfig;
import dm.jale.executor.ExecutorPool;
import dm.jale.executor.OwnerExecutor;

/**
 * Created by davide-maestroni on 02/12/2018.
 */
class ExecutorLoopForker<V> extends BufferedLoopForker<Stack<V>, V> {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  ExecutorLoopForker(@NotNull final Executor executor) {
    super(new LoopForker<V>(executor));
  }

  static class Stack<V> {

    private final AtomicLong pendingCount = new AtomicLong();

    private AsyncEvaluations<V> evaluations;
  }

  private static class LoopForker<V>
      implements Forker<Stack<V>, V, AsyncEvaluations<V>, AsyncLoop<V>>, Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final OwnerExecutor mExecutor;

    private LoopForker(@NotNull final Executor executor) {
      mExecutor = ExecutorPool.register(executor);
    }

    public Stack<V> done(final Stack<V> stack, @NotNull final AsyncLoop<V> async) {
      mExecutor.execute(new Runnable() {

        public void run() {
          if (stack.pendingCount.decrementAndGet() == 0) {
            stack.evaluations.set();
          }
        }
      });

      return stack;
    }

    public Stack<V> evaluation(final Stack<V> stack, @NotNull final AsyncEvaluations<V> evaluations,
        @NotNull final AsyncLoop<V> async) {
      stack.evaluations = evaluations;
      return stack;
    }

    public Stack<V> failure(final Stack<V> stack, @NotNull final Throwable failure,
        @NotNull final AsyncLoop<V> async) {
      final AtomicLong pendingCount = stack.pendingCount;
      final AsyncEvaluations<V> evaluations = stack.evaluations;
      pendingCount.incrementAndGet();
      mExecutor.execute(new Runnable() {

        public void run() {
          evaluations.addFailure(failure);
          if (pendingCount.decrementAndGet() == 0) {
            evaluations.set();
          }
        }
      });

      return stack;
    }

    public Stack<V> init(@NotNull final AsyncLoop<V> async) throws Exception {
      return new Stack<V>();
    }

    public Stack<V> value(final Stack<V> stack, final V value,
        @NotNull final AsyncLoop<V> async) throws Exception {
      final AtomicLong pendingCount = stack.pendingCount;
      final AsyncEvaluations<V> evaluations = stack.evaluations;
      pendingCount.incrementAndGet();
      mExecutor.execute(new Runnable() {

        public void run() {
          evaluations.addValue(value);
          if (pendingCount.decrementAndGet() == 0) {
            evaluations.set();
          }
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
          return new ExecutorLoopForker<V>(mExecutor);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }
  }
}

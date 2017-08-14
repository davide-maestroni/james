/*
 * Copyright 2017 Davide Maestroni
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

package dm.james.handler;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

import dm.james.executor.ScheduledExecutor;
import dm.james.promise.PromiseIterable.CallbackIterable;
import dm.james.promise.PromiseIterable.StatefulHandler;
import dm.james.util.ConstantConditions;
import dm.james.util.InterruptedExecutionException;

/**
 * Created by davide-maestroni on 08/14/2017.
 */
class ScheduleHandler<I> implements StatefulHandler<I, I, Void>, Serializable {

  private final ScheduledExecutor mExecutor;

  ScheduleHandler(@NotNull final ScheduledExecutor executor) {
    mExecutor = ConstantConditions.notNull("executor", executor);
  }

  public Void create(@NotNull final CallbackIterable<I> callback) {
    return null;
  }

  public Void fulfill(final Void state, final I input,
      @NotNull final CallbackIterable<I> callback) {
    mExecutor.execute(new Runnable() {

      public void run() {
        try {
          callback.add(input);

        } catch (final Throwable t) {
          InterruptedExecutionException.throwIfInterrupt(t);
        }
      }
    });

    return null;
  }

  public Void reject(final Void state, final Throwable reason,
      @NotNull final CallbackIterable<I> callback) {
    mExecutor.execute(new Runnable() {

      public void run() {
        try {
          callback.addRejection(reason);

        } catch (final Throwable t) {
          InterruptedExecutionException.throwIfInterrupt(t);
        }
      }
    });

    return null;
  }

  public void resolve(final Void state, @NotNull final CallbackIterable<I> callback) {
    mExecutor.execute(new Runnable() {

      public void run() {
        try {
          callback.resolve();

        } catch (final Throwable t) {
          InterruptedExecutionException.throwIfInterrupt(t);
        }
      }
    });
  }
}

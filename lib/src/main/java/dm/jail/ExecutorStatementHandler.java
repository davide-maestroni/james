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

package dm.jail;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CancellationException;

import dm.jail.async.AsyncResult;
import dm.jail.config.BuildConfig;
import dm.jail.executor.ScheduledExecutor;
import dm.jail.util.ConstantConditions;

/**
 * Created by davide-maestroni on 02/01/2018.
 */
class ExecutorStatementHandler<V> extends AsyncStatementHandler<V, V> {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final ScheduledExecutor mExecutor;

  ExecutorStatementHandler(@NotNull final ScheduledExecutor executor) {
    mExecutor = ConstantConditions.notNull("executor", executor);
  }

  @Override
  void failure(@NotNull final Throwable failure, @NotNull final AsyncResult<V> result) throws
      Exception {
    if (failure instanceof CancellationException) {
      try {
        result.fail(failure);

      } catch (final Throwable ignored) {
        // cannot take any action
      }

    } else {
      mExecutor.execute(new Runnable() {

        public void run() {
          try {
            result.fail(failure);

          } catch (final Throwable ignored) {
            // cannot take any action
          }
        }
      });
    }
  }

  @Override
  void value(final V value, @NotNull final AsyncResult<V> result) throws Exception {
    mExecutor.execute(new Runnable() {

      public void run() {
        try {
          result.set(value);

        } catch (final Throwable ignored) {
          // cannot take any action
        }
      }
    });
  }
}

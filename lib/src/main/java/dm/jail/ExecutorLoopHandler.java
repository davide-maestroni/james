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
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CancellationException;

import dm.jail.async.AsyncResultCollection;
import dm.jail.config.BuildConfig;
import dm.jail.executor.ScheduledExecutor;
import dm.jail.util.ConstantConditions;

/**
 * Created by davide-maestroni on 02/01/2018.
 */
class ExecutorLoopHandler<V> extends AsyncLoopHandler<V, V> {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final ScheduledExecutor mExecutor;

  ExecutorLoopHandler(@NotNull final ScheduledExecutor executor) {
    mExecutor = ConstantConditions.notNull("executor", executor);
  }

  @Override
  void addFailure(@NotNull final Throwable failure,
      @NotNull final AsyncResultCollection<V> results) throws Exception {
    if (failure instanceof CancellationException) {
      try {
        results.addFailure(failure).set();

      } catch (final Throwable ignored) {
        // cannot take any action
      }

    } else {
      mExecutor.execute(new Runnable() {

        public void run() {
          try {
            results.addFailure(failure).set();

          } catch (final Throwable ignored) {
            // cannot take any action
          }
        }
      });
    }
  }

  @Override
  void addFailures(@Nullable final Iterable<Throwable> failures,
      @NotNull final AsyncResultCollection<V> results) throws Exception {
    mExecutor.execute(new Runnable() {

      public void run() {
        try {
          results.addFailures(failures).set();

        } catch (final Throwable ignored) {
          // cannot take any action
        }
      }
    });
  }

  @Override
  void addValue(final V value, @NotNull final AsyncResultCollection<V> results) throws Exception {
    mExecutor.execute(new Runnable() {

      public void run() {
        try {
          results.addValue(value).set();

        } catch (final Throwable ignored) {
          // cannot take any action
        }
      }
    });
  }

  @Override
  void addValues(@Nullable final Iterable<V> values,
      @NotNull final AsyncResultCollection<V> results) throws Exception {
    mExecutor.execute(new Runnable() {

      public void run() {
        try {
          results.addValues(values).set();

        } catch (final Throwable ignored) {
          // cannot take any action
        }
      }
    });
  }
}

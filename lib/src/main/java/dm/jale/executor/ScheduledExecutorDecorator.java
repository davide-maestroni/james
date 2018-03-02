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

package dm.jale.executor;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

import dm.jale.util.ConstantConditions;

/**
 * Implementation of a decorator of an executor object.
 * <p>
 * Created by davide-maestroni on 09/22/2014.
 */
class ScheduledExecutorDecorator implements ScheduledExecutor, ExecutorDecorator {

  private final ScheduledExecutor mExecutor;

  /**
   * Constructor.
   *
   * @param executor the wrapped instance.
   */
  public ScheduledExecutorDecorator(@NotNull final ScheduledExecutor executor) {
    mExecutor = ConstantConditions.notNull("executor", executor);
  }

  public void execute(@NotNull final Runnable command) {
    mExecutor.execute(command);
  }

  public void execute(@NotNull final Runnable command, final long delay,
      @NotNull final TimeUnit timeUnit) {
    mExecutor.execute(command, delay, timeUnit);
  }

  @NotNull
  public EvaluationExecutor getDecorated() {
    return mExecutor;
  }

  public boolean isOwnedThread() {
    return mExecutor.isOwnedThread();
  }

  public void stop() {
    mExecutor.stop();
  }
}

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

package dm.jail.executor;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

import dm.jail.config.BuildConfig;
import dm.jail.util.ConstantConditions;

/**
 * Created by davide-maestroni on 02/07/2018.
 */
class ScheduledExecutorWrapper implements ScheduledExecutor, ExecutorDecorator, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final OwnerExecutor mExecutor;

  ScheduledExecutorWrapper(@NotNull final OwnerExecutor executor) {
    mExecutor = ConstantConditions.notNull("executor", executor);
  }

  public void execute(@NotNull final Runnable runnable, final long delay,
      @NotNull final TimeUnit timeUnit) {
    ConstantConditions.unsupported();
  }

  public void execute(@NotNull final Runnable runnable) {
    mExecutor.execute(runnable);
  }

  @NotNull
  public OwnerExecutor getDecorated() {
    return mExecutor;
  }

  public boolean isOwnedThread() {
    return mExecutor.isOwnedThread();
  }

  public void stop() {
  }
}

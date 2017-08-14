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

package dm.james;

import org.jetbrains.annotations.NotNull;

import dm.james.executor.ScheduledExecutor;
import dm.james.executor.ScheduledExecutors;

/**
 * Created by davide-maestroni on 07/20/2017.
 */
public enum PropagationType {
  IMMEDIATE(ScheduledExecutors.immediateExecutor()),

  LOOP(ScheduledExecutors.loopExecutor());

  private final ScheduledExecutor mExecutor;

  PropagationType(@NotNull final ScheduledExecutor executor) {
    mExecutor = executor;
  }

  void execute(@NotNull final Runnable command) {
    mExecutor.execute(command);
  }

  @NotNull
  ScheduledExecutor executor() {
    return mExecutor;
  }
}

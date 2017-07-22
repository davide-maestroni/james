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

package dm.james.processor;

import org.jetbrains.annotations.NotNull;

import dm.james.executor.ScheduledExecutor;
import dm.james.util.ConstantConditions;

/**
 * Created by davide-maestroni on 07/21/2017.
 */
public class Processors {

  /**
   * Avoid explicit instantiation.
   */
  protected Processors() {
    ConstantConditions.avoid();
  }

  @NotNull
  public static <T> ScheduleProcessor<T> scheduleOn(@NotNull final ScheduledExecutor executor) {
    return new ScheduleProcessor<T>(executor);
  }
}

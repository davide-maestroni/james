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
import org.jetbrains.annotations.Nullable;

import dm.james.executor.ScheduledExecutor;
import dm.james.promise.ObserverHandler;
import dm.james.promise.Promise.Callback;
import dm.james.promise.Promise.Handler;
import dm.james.util.ConstantConditions;

/**
 * Created by davide-maestroni on 07/21/2017.
 */
public class Handlers {

  /**
   * Avoid explicit instantiation.
   */
  protected Handlers() {
    ConstantConditions.avoid();
  }

  @NotNull
  public static <I, O> Handler<I, O> handle(
      @Nullable final ObserverHandler<I, O, ? super Callback<O>> outputHandler,
      @Nullable final ObserverHandler<Throwable, O, ? super Callback<O>> errorHandler) {
    return new HandlesHandler<I, O>(outputHandler, errorHandler);
  }

  @NotNull
  public static <I> ScheduleHandler<I> scheduleOn(@NotNull final ScheduledExecutor executor) {
    return new ScheduleHandler<I>(executor);
  }
}

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

/**
 * The executor class defines an object responsible for executing routine invocations inside
 * specifically managed threads.
 * <p>
 * The implementation can both be synchronous or asynchronous, it can allocate specialized threads
 * or share a pool of them between different instances.
 * <br>
 * The only requirement is that the specified command is called each time a run method is invoked,
 * even if the same command instance is passed several times as input parameter.
 * <p>
 * Note that, a proper asynchronous executor implementation will never synchronously run a command,
 * unless the run method is called inside one of the managed thread. While, a proper synchronous
 * executor, will always run commands on the very same caller thread.
 * <br>
 * Note also that the executor methods might be called from different threads, so, it is up to the
 * implementing class to ensure synchronization when required.
 * <p>
 * The implementing class can optionally support the cancellation of commands not yet run
 * (waiting, for example, in a consuming queue).
 * <p>
 * The class {@link ExecutorPool ScheduledExecutor} provides a few
 * implementations employing concurrent Java classes.
 * <p>
 * Created by davide-maestroni on 09/07/2014.
 */
public interface ScheduledExecutor extends StoppableExecutor {

  /**
   * Executes the specified command (that is, it calls the {@link Runnable#run()} method inside
   * the executor thread) after the specified delay.
   *
   * @param command  the command.
   * @param delay    the command delay.
   * @param timeUnit the delay time unit.
   * @throws java.util.concurrent.RejectedExecutionException it the executor is currently unable to
   *                                                         fulfill the command (for instance,
   *                                                         after being stopped).
   */
  void execute(@NotNull Runnable command, long delay, @NotNull TimeUnit timeUnit);
}

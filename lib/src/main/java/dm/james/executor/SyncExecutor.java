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

package dm.james.executor;

/**
 * Base abstract implementation of a synchronous executor.
 * <br>
 * For a synchronous executor any thread is an execution thread while no one is managed.
 * <p>
 * Created by davide-maestroni on 06/06/2016.
 */
public abstract class SyncExecutor implements ScheduledExecutor {

  public boolean isExecutionThread() {
    return true;
  }

  public void stop() {
  }

  public boolean isOwnedThread() {
    return false;
  }
}

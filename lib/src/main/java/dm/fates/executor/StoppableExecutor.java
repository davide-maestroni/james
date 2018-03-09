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

package dm.fates.executor;

/**
 * Created by davide-maestroni on 02/07/2018.
 */
public interface StoppableExecutor extends EvaluationExecutor {

  /**
   * Stops the executor.
   * <br>
   * The method is meant to signal that the executor is no more needed. In fact, as a consequence of
   * the call, pending commands might get discarded and the executor itself may become unusable.
   * <br>
   * The specific implementation can leverage the method to eventually free allocated resources.
   */
  void stop();
}

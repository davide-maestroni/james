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

import org.jetbrains.annotations.Nullable;

/**
 * Exception wrapping a thread interrupted exception caught inside a method execution.
 * <p>
 * Created by davide-maestroni on 09/08/2014.
 */
public class InterruptedExecutionException extends RuntimeException {

  /**
   * Constructor.
   *
   * @param cause the wrapped exception.
   */
  public InterruptedExecutionException(@Nullable final InterruptedException cause) {
    super(cause);
    Thread.currentThread().interrupt();
  }

  /**
   * Checks if the specified throwable is not an interrupted exception.
   *
   * @param t the throwable.
   * @throws InterruptedExecutionException if the specified throwable is an instance of
   *                                       {@code InterruptedExecutionException} or
   *                                       {@code InterruptedException}.
   */
  public static void throwIfInterrupt(@Nullable final Throwable t) {
    if (t instanceof InterruptedExecutionException) {
      throw ((InterruptedExecutionException) t);
    }

    if (t instanceof InterruptedException) {
      throw new InterruptedExecutionException((InterruptedException) t);
    }
  }
}

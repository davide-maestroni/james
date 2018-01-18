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

package dm.jail.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Exception wrapping a thread interrupted exception caught inside a method execution.
 * <p>
 * Created by davide-maestroni on 09/08/2014.
 */
public class RuntimeInterruptedException extends RuntimeException {

  /**
   * Constructor.
   *
   * @param cause the wrapped exception.
   */
  public RuntimeInterruptedException(@Nullable final InterruptedException cause) {
    super(cause);
    Thread.currentThread().interrupt();
  }

  /**
   * Checks if the specified throwable is not an interrupted exception.
   *
   * @param t the throwable.
   * @throws RuntimeInterruptedException if the specified throwable is an instance of
   *                                     {@code RuntimeInterruptedException} or
   *                                     {@code InterruptedException}.
   */
  public static void throwIfInterrupt(@Nullable final Throwable t) {
    if (t instanceof RuntimeInterruptedException) {
      throw ((RuntimeInterruptedException) t);
    }

    if (t instanceof InterruptedException) {
      throw new RuntimeInterruptedException((InterruptedException) t);
    }
  }

  @NotNull
  public InterruptedException toInterruptedException() {
    final Throwable cause = getCause();
    if (cause instanceof InterruptedException) {
      return (InterruptedException) cause;
    }

    return new InterruptedException(getMessage());
  }
}

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

package dm.jail.async;

import org.jetbrains.annotations.NotNull;

/**
 * Exception wrapping a thread interrupted exception caught inside a method execution.
 * <p>
 * Created by davide-maestroni on 09/08/2014.
 */
public class RuntimeInterruptedException extends FailureException {

  /**
   * Constructor.
   *
   * @param cause the wrapped exception.
   */
  public RuntimeInterruptedException(@NotNull final InterruptedException cause) {
    super(cause);
    Thread.currentThread().interrupt();
  }

  public static Throwable wrapIfInterrupt(final Throwable t) {
    if (t instanceof InterruptedException) {
      return new RuntimeInterruptedException((InterruptedException) t);
    }

    return t;
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public InterruptedException toInterruptedException() {
    return (InterruptedException) getCause();
  }
}

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

package dm.jale.async;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeoutException;

/**
 * Created by davide-maestroni on 01/15/2018.
 */
public class RuntimeTimeoutException extends FailureException {

  public RuntimeTimeoutException() {
    super(new TimeoutException());
  }

  public RuntimeTimeoutException(final String message) {
    super(new TimeoutException(message));
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public TimeoutException toTimeoutException() {
    return (TimeoutException) getCause();
  }
}

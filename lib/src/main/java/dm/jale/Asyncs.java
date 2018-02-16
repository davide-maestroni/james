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

package dm.jale;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;

import dm.jale.async.Evaluation;
import dm.jale.async.EvaluationCollection;
import dm.jale.log.Logger;

/**
 * Created by davide-maestroni on 02/01/2018.
 */
class Asyncs {

  private static final Class<?>[] ANY_EXCEPTION = new Class<?>[]{Throwable.class};

  private static final Class<?>[] NO_EXCEPTION = new Class<?>[]{};

  private Asyncs() {
  }

  @NotNull
  static Class<?>[] cloneExceptionTypes(@Nullable final Class<?>... exceptionTypes) {
    return (exceptionTypes != null) ? (exceptionTypes.length > 0) ? exceptionTypes.clone()
        : ANY_EXCEPTION : NO_EXCEPTION;
  }

  static void close(@Nullable final Closeable closeable, @NotNull final Logger logger) throws
      IOException {
    if (closeable == null) {
      logger.wrn("Cannot close null closeable");
      return;
    }

    closeable.close();
  }

  static void failSafe(@NotNull final Evaluation<?> evaluation, @NotNull final Throwable failure) {
    try {
      evaluation.fail(failure);

    } catch (final Throwable ignored) {
      // Just ignore it
    }
  }

  static void failSafe(@NotNull final EvaluationCollection<?> evaluations,
      @NotNull final Throwable failure) {
    try {
      evaluations.addFailure(failure).set();

    } catch (final Throwable ignored) {
      // Just ignore it
    }
  }
}

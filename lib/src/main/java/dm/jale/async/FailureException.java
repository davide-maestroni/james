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

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.concurrent.CancellationException;

import dm.jale.log.Logger;
import dm.jale.util.ConstantConditions;

/**
 * Created by davide-maestroni on 01/08/2018.
 */
public class FailureException extends RuntimeException {

  public FailureException(@NotNull final Throwable throwable) {
    super(ConstantConditions.notNull("throwable", throwable));
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public static FailureException wrap(@NotNull final Throwable t) {
    return (FailureException) wrapIfNot(FailureException.class, t);
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public static RuntimeException wrapIfNot(@NotNull final Class<? extends RuntimeException> type,
      @NotNull final Throwable t) {
    if (!type.isInstance(t)) {
      return new FailureException(t);
    }

    return (RuntimeException) t;
  }

  @Override
  public String getMessage() {
    final Throwable cause = super.getCause();
    return (cause != null) ? cause.getMessage() : super.getMessage();
  }

  @Override
  public String getLocalizedMessage() {
    final Throwable cause = super.getCause();
    return (cause != null) ? cause.getLocalizedMessage() : super.getLocalizedMessage();
  }

  @NotNull
  @Override
  public final Throwable getCause() {
    return super.getCause();
  }

  @Override
  public void printStackTrace() {
    final Throwable cause = super.getCause();
    if (cause != null) {
      cause.printStackTrace();

    } else {
      super.printStackTrace();
    }
  }

  @Override
  public void printStackTrace(final PrintStream printStream) {
    final Throwable cause = super.getCause();
    if (cause != null) {
      cause.printStackTrace(printStream);

    } else {
      super.printStackTrace(printStream);
    }
  }

  @Override
  public void printStackTrace(final PrintWriter printWriter) {
    final Throwable cause = super.getCause();
    if (cause != null) {
      cause.printStackTrace(printWriter);

    } else {
      super.printStackTrace(printWriter);
    }
  }

  public boolean isCancelled() {
    return (getCause() instanceof CancellationException);
  }

  public void printFullStackTrace(final PrintStream printStream) {
    super.printStackTrace(printStream);
  }

  public void printFullStackTrace(final PrintWriter printWriter) {
    super.printStackTrace(printWriter);
  }

  public void printFullStackTrace() {
    super.printStackTrace();
  }

  @NotNull
  public String printFullStackTraceToString() {
    return Logger.printStackTrace(this);
  }

  @NotNull
  public String printStackTraceToString() {
    return Logger.printStackTrace(getCause());
  }
}

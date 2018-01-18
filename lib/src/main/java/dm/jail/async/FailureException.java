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
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.concurrent.CancellationException;

/**
 * Created by davide-maestroni on 01/08/2018.
 */
public class FailureException extends RuntimeException {

  public FailureException() {
  }

  public FailureException(final Throwable throwable) {
    super(throwable);
  }

  @NotNull
  public static FailureException wrap(@Nullable final Throwable t) {
    return (FailureException) wrapIfNot(FailureException.class, t);
  }

  @NotNull
  public static RuntimeException wrapIfNot(@NotNull final Class<? extends RuntimeException> type,
      @Nullable final Throwable t) {
    if ((t == null) || !type.isInstance(t)) {
      return new FailureException(t);
    }

    return (RuntimeException) t;
  }

  @Override
  public String getMessage() {
    final Throwable cause = getCause();
    return (cause != null) ? cause.getMessage() : super.getMessage();
  }

  @Override
  public String getLocalizedMessage() {
    final Throwable cause = getCause();
    return (cause != null) ? cause.getLocalizedMessage() : super.getLocalizedMessage();
  }

  @Override
  public void printStackTrace() {
    final Throwable cause = getCause();
    if (cause != null) {
      cause.printStackTrace();

    } else {
      super.printStackTrace();
    }
  }

  @Override
  public void printStackTrace(final PrintStream printStream) {
    final Throwable cause = getCause();
    if (cause != null) {
      cause.printStackTrace(printStream);

    } else {
      super.printStackTrace(printStream);
    }
  }

  @Override
  public void printStackTrace(final PrintWriter printWriter) {
    final Throwable cause = getCause();
    if (cause != null) {
      cause.printStackTrace(printWriter);

    } else {
      super.printStackTrace(printWriter);
    }
  }

  @Override
  public synchronized Throwable fillInStackTrace() {
    final Throwable cause = getCause();
    return (cause != null) ? cause.fillInStackTrace() : super.fillInStackTrace();
  }

  @Override
  public StackTraceElement[] getStackTrace() {
    final Throwable cause = getCause();
    return (cause != null) ? cause.getStackTrace() : super.getStackTrace();
  }

  @Override
  public void setStackTrace(final StackTraceElement[] stackTraceElements) {
    final Throwable cause = getCause();
    if (cause != null) {
      cause.setStackTrace(stackTraceElements);

    } else {
      super.setStackTrace(stackTraceElements);
    }
  }

  public boolean isCancelled() {
    return (getCause() instanceof CancellationException);
  }
}

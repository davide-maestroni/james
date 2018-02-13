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

package dm.jale.log;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by davide-maestroni on 02/11/2018.
 */
class JavaLoggingConnector implements LogConnector {

  public LogPrinter getPrinter(@NotNull final String loggerName,
      @NotNull final List<Object> contexts) {
    return new JavaLogPrinter(Logger.getLogger(loggerName));
  }

  private static class JavaLogPrinter implements LogPrinter {

    private final Logger mLogger;

    private JavaLogPrinter(@NotNull final java.util.logging.Logger logger) {
      mLogger = logger;
    }

    public boolean canLogDbg() {
      return mLogger.isLoggable(Level.FINE);
    }

    public boolean canLogErr() {
      return mLogger.isLoggable(Level.SEVERE);
    }

    public boolean canLogWrn() {
      return mLogger.isLoggable(Level.WARNING);
    }

    public void dbg(@Nullable final String message, @Nullable final Throwable throwable) {
      mLogger.log(Level.FINE, message, throwable);
    }

    public void err(@Nullable final String message, @Nullable final Throwable throwable) {
      mLogger.log(Level.SEVERE, message, throwable);
    }

    public void wrn(@Nullable final String message, @Nullable final Throwable throwable) {
      mLogger.log(Level.WARNING, message, throwable);
    }
  }
}

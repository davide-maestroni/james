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

package dm.jail.log;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.List;

import dm.jail.util.ConstantConditions;

/**
 * Abstract implementation of a log.
 * <p>
 * This class is useful to avoid the need of implementing all the methods defined in the interface.
 * <p>
 * A standard format is applied to the log messages.
 * <br>
 * The inheriting class may just implement the writing of the formatted message, or customize its
 * composition.
 * <p>
 * Created by davide-maestroni on 10/03/2014.
 */
@SuppressWarnings("WeakerAccess")
public abstract class TemplateLogPrinter implements LogPrinter, Serializable {

  private final LogFormatter mFormatter;

  public TemplateLogPrinter(@NotNull final LogFormatter formatter) {
    mFormatter = ConstantConditions.notNull("formatter", formatter);
  }

  public void dbg(@NotNull final List<Object> contexts, @Nullable final String message,
      @Nullable final Throwable throwable) {
    log(LogLevel.DEBUG, contexts, message, throwable);
  }

  public void err(@NotNull final List<Object> contexts, @Nullable final String message,
      @Nullable final Throwable throwable) {
    log(LogLevel.ERROR, contexts, message, throwable);
  }

  public void wrn(@NotNull final List<Object> contexts, @Nullable final String message,
      @Nullable final Throwable throwable) {
    log(LogLevel.WARNING, contexts, message, throwable);
  }

  /**
   * Formats and then write the specified log message.
   *
   * @param level     the log level.
   * @param contexts  the log context array.
   * @param message   the log message.
   * @param throwable the related exception.
   */
  protected void log(@NotNull final LogLevel level, @NotNull final List<Object> contexts,
      @Nullable final String message, @Nullable final Throwable throwable) {
    print(mFormatter.format(level, contexts, message, throwable));
  }

  /**
   * Writes the specified message after it's been formatted.
   *
   * @param message the message.
   */
  protected void print(@NotNull final String message) {
  }
}

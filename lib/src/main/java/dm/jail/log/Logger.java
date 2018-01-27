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

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import dm.jail.log.LogPrinter.Level;
import dm.jail.util.ConstantConditions;

import static dm.jail.util.Reflections.asArgs;

/**
 * Utility class used for logging messages.
 * <p>
 * Created by davide-maestroni on 10/03/2014.
 */
@SuppressWarnings("WeakerAccess")
public class Logger {

  private static final int DEBUG_LEVEL = Level.DEBUG.ordinal();

  private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;

  private static final int ERROR_LEVEL = Level.ERROR.ordinal();

  private static final int WARNING_LEVEL = Level.WARNING.ordinal();

  private static final AtomicReference<Level> sLogLevel = new AtomicReference<Level>(Level.ERROR);

  private static final AtomicReference<LogPrinter> sLogPrinter =
      new AtomicReference<LogPrinter>(LogPrinters.systemPrinter());

  private final List<Object> mContextList;

  private final Object[] mContexts;

  private final int mLevel;

  private final Level mLogLevel;

  private final LogPrinter mLogPrinter;

  /**
   * Constructor.
   *
   * @param contexts the array of contexts.
   * @param printer  the printer instance.
   * @param level    the log level.
   */
  private Logger(@NotNull final Object[] contexts, @Nullable final LogPrinter printer,
      @Nullable final Level level) {
    mContexts = contexts.clone();
    mLogPrinter = (printer == null) ? sLogPrinter.get() : printer;
    mLogLevel = (level == null) ? sLogLevel.get() : level;
    mLevel = mLogLevel.ordinal();
    mContextList = Arrays.asList(mContexts);
  }

  /**
   * Gets the default log level.
   *
   * @return the log level.
   */
  @NotNull
  public static Level getDefaultLevel() {
    return sLogLevel.get();
  }

  /**
   * Sets the default log level.
   *
   * @param level the log level.
   */
  public static void setDefaultLevel(@NotNull final Level level) {
    sLogLevel.set(ConstantConditions.notNull("level", level));
  }

  /**
   * Gets the default log printer instance.
   *
   * @return the log instance.
   */
  @NotNull
  public static LogPrinter getDefaultPrinter() {
    return sLogPrinter.get();
  }

  /**
   * Sets the default log printer instance.
   *
   * @param printer the printer instance.
   */
  public static void setDefaultPrinter(@NotNull final LogPrinter printer) {
    sLogPrinter.set(ConstantConditions.notNull("printer", printer));
  }

  /**
   * Creates a new logger.
   *
   * @param printer the printer instance.
   * @param level   the log level.
   * @param context the context.
   * @return the new logger.
   */
  @NotNull
  public static Logger newLogger(@Nullable final LogPrinter printer, @Nullable final Level level,
      @NotNull final Object context) {
    return new Logger(asArgs(ConstantConditions.notNull("context", context)), printer, level);
  }

  /**
   * Logs a debug message.
   *
   * @param message the message.
   */
  public void dbg(@Nullable final String message) {
    if (mLevel <= DEBUG_LEVEL) {
      mLogPrinter.dbg(mContextList, message, null);
    }
  }

  /**
   * Logs a debug message.
   *
   * @param format the message format.
   * @param arg1   the first format argument.
   */
  public void dbg(@NotNull final String format, @Nullable final Object arg1) {
    if (mLevel <= DEBUG_LEVEL) {
      mLogPrinter.dbg(mContextList, String.format(DEFAULT_LOCALE, format, arg1), null);
    }
  }

  /**
   * Logs a debug message.
   *
   * @param format the message format.
   * @param arg1   the first format argument.
   * @param arg2   the second format argument.
   */
  public void dbg(@NotNull final String format, @Nullable final Object arg1,
      @Nullable final Object arg2) {
    if (mLevel <= DEBUG_LEVEL) {
      mLogPrinter.dbg(mContextList, String.format(DEFAULT_LOCALE, format, arg1, arg2), null);
    }
  }

  /**
   * Logs a debug message.
   *
   * @param format the message format.
   * @param arg1   the first format argument.
   * @param arg2   the second format argument.
   * @param arg3   the third format argument.
   */
  public void dbg(@NotNull final String format, @Nullable final Object arg1,
      @Nullable final Object arg2, @Nullable final Object arg3) {
    if (mLevel <= DEBUG_LEVEL) {
      mLogPrinter.dbg(mContextList, String.format(DEFAULT_LOCALE, format, arg1, arg2, arg3), null);
    }
  }

  /**
   * Logs a debug message.
   *
   * @param format the message format.
   * @param arg1   the first format argument.
   * @param arg2   the second format argument.
   * @param arg3   the third format argument.
   * @param arg4   the fourth format argument.
   */
  public void dbg(@NotNull final String format, @Nullable final Object arg1,
      @Nullable final Object arg2, @Nullable final Object arg3, @Nullable final Object arg4) {
    if (mLevel <= DEBUG_LEVEL) {
      mLogPrinter.dbg(mContextList, String.format(DEFAULT_LOCALE, format, arg1, arg2, arg3, arg4),
          null);
    }
  }

  /**
   * Logs a debug message.
   *
   * @param format the message format.
   * @param args   the format arguments.
   */
  public void dbg(@NotNull final String format, @Nullable final Object... args) {
    if (mLevel <= DEBUG_LEVEL) {
      mLogPrinter.dbg(mContextList, String.format(DEFAULT_LOCALE, format, args), null);
    }
  }

  /**
   * Logs a debug exception.
   *
   * @param throwable the related throwable.
   */
  public void dbg(@Nullable final Throwable throwable) {
    if (mLevel <= DEBUG_LEVEL) {
      mLogPrinter.dbg(mContextList, "", throwable);
    }
  }

  /**
   * Logs a debug message.
   *
   * @param throwable the related throwable.
   * @param message   the message.
   */
  public void dbg(@Nullable final Throwable throwable, @Nullable final String message) {
    if (mLevel <= DEBUG_LEVEL) {
      mLogPrinter.dbg(mContextList, message, throwable);
    }
  }

  /**
   * Logs a debug message.
   *
   * @param throwable the related throwable.
   * @param format    the message format.
   * @param arg1      the first format argument.
   */
  public void dbg(@NotNull final Throwable throwable, @NotNull final String format,
      @Nullable final Object arg1) {
    if (mLevel <= DEBUG_LEVEL) {
      mLogPrinter.dbg(mContextList, String.format(DEFAULT_LOCALE, format, arg1), throwable);
    }
  }

  /**
   * Logs a debug message.
   *
   * @param throwable the related throwable.
   * @param format    the message format.
   * @param arg1      the first format argument.
   * @param arg2      the second format argument.
   */
  public void dbg(@NotNull final Throwable throwable, @NotNull final String format,
      @Nullable final Object arg1, @Nullable final Object arg2) {
    if (mLevel <= DEBUG_LEVEL) {
      mLogPrinter.dbg(mContextList, String.format(DEFAULT_LOCALE, format, arg1, arg2), throwable);
    }
  }

  /**
   * Logs a debug message.
   *
   * @param throwable the related throwable.
   * @param format    the message format.
   * @param arg1      the first format argument.
   * @param arg2      the second format argument.
   * @param arg3      the third format argument.
   */
  public void dbg(@NotNull final Throwable throwable, @NotNull final String format,
      @Nullable final Object arg1, @Nullable final Object arg2, @Nullable final Object arg3) {
    if (mLevel <= DEBUG_LEVEL) {
      mLogPrinter.dbg(mContextList, String.format(DEFAULT_LOCALE, format, arg1, arg2, arg3),
          throwable);
    }
  }

  /**
   * Logs a debug message.
   *
   * @param throwable the related throwable.
   * @param format    the message format.
   * @param arg1      the first format argument.
   * @param arg2      the second format argument.
   * @param arg3      the third format argument.
   * @param arg4      the fourth format argument.
   */
  public void dbg(@NotNull final Throwable throwable, @NotNull final String format,
      @Nullable final Object arg1, @Nullable final Object arg2, @Nullable final Object arg3,
      @Nullable final Object arg4) {
    if (mLevel <= DEBUG_LEVEL) {
      mLogPrinter.dbg(mContextList, String.format(DEFAULT_LOCALE, format, arg1, arg2, arg3, arg4),
          throwable);
    }
  }

  /**
   * Logs a debug message.
   *
   * @param throwable the related throwable.
   * @param format    the message format.
   * @param args      the format arguments.
   */
  public void dbg(@NotNull final Throwable throwable, @NotNull final String format,
      @Nullable final Object... args) {
    if (mLevel <= DEBUG_LEVEL) {
      mLogPrinter.dbg(mContextList, String.format(DEFAULT_LOCALE, format, args), throwable);
    }
  }

  /**
   * Logs an error message.
   *
   * @param message the message.
   */
  public void err(@Nullable final String message) {
    if (mLevel <= ERROR_LEVEL) {
      mLogPrinter.err(mContextList, message, null);
    }
  }

  /**
   * Logs an error message.
   *
   * @param format the message format.
   * @param arg1   the first format argument.
   */
  public void err(@NotNull final String format, @Nullable final Object arg1) {
    if (mLevel <= ERROR_LEVEL) {
      mLogPrinter.err(mContextList, String.format(DEFAULT_LOCALE, format, arg1), null);
    }
  }

  /**
   * Logs an error message.
   *
   * @param format the message format.
   * @param arg1   the first format argument.
   * @param arg2   the second format argument.
   */
  public void err(@NotNull final String format, @Nullable final Object arg1,
      @Nullable final Object arg2) {
    if (mLevel <= ERROR_LEVEL) {
      mLogPrinter.err(mContextList, String.format(DEFAULT_LOCALE, format, arg1, arg2), null);
    }
  }

  /**
   * Logs an error message.
   *
   * @param format the message format.
   * @param arg1   the first format argument.
   * @param arg2   the second format argument.
   * @param arg3   the third format argument.
   */
  public void err(@NotNull final String format, @Nullable final Object arg1,
      @Nullable final Object arg2, @Nullable final Object arg3) {
    if (mLevel <= ERROR_LEVEL) {
      mLogPrinter.err(mContextList, String.format(DEFAULT_LOCALE, format, arg1, arg2, arg3), null);
    }
  }

  /**
   * Logs an error message.
   *
   * @param format the message format.
   * @param arg1   the first format argument.
   * @param arg2   the second format argument.
   * @param arg3   the third format argument.
   * @param arg4   the fourth format argument.
   */
  public void err(@NotNull final String format, @Nullable final Object arg1,
      @Nullable final Object arg2, @Nullable final Object arg3, @Nullable final Object arg4) {
    if (mLevel <= ERROR_LEVEL) {
      mLogPrinter.err(mContextList, String.format(DEFAULT_LOCALE, format, arg1, arg2, arg3, arg4),
          null);
    }
  }

  /**
   * Logs an error message.
   *
   * @param format the message format.
   * @param args   the format arguments.
   */
  public void err(@NotNull final String format, @Nullable final Object... args) {
    if (mLevel <= ERROR_LEVEL) {
      mLogPrinter.err(mContextList, String.format(DEFAULT_LOCALE, format, args), null);
    }
  }

  /**
   * Logs an error exception.
   *
   * @param throwable the related throwable.
   */
  public void err(@NotNull final Throwable throwable) {
    if (mLevel <= ERROR_LEVEL) {
      mLogPrinter.err(mContextList, "", throwable);
    }
  }

  /**
   * Logs an error message.
   *
   * @param throwable the related throwable.
   * @param message   the message.
   */
  public void err(@NotNull final Throwable throwable, @Nullable final String message) {
    if (mLevel <= ERROR_LEVEL) {
      mLogPrinter.err(mContextList, message, throwable);
    }
  }

  /**
   * Logs an error message.
   *
   * @param throwable the related throwable.
   * @param format    the message format.
   * @param arg1      the first format argument.
   */
  public void err(@NotNull final Throwable throwable, @NotNull final String format,
      @Nullable final Object arg1) {
    if (mLevel <= ERROR_LEVEL) {
      mLogPrinter.err(mContextList, String.format(DEFAULT_LOCALE, format, arg1), throwable);
    }
  }

  /**
   * Logs an error message.
   *
   * @param throwable the related throwable.
   * @param format    the message format.
   * @param arg1      the first format argument.
   * @param arg2      the second format argument.
   */
  public void err(@NotNull final Throwable throwable, @NotNull final String format,
      @Nullable final Object arg1, @Nullable final Object arg2) {
    if (mLevel <= ERROR_LEVEL) {
      mLogPrinter.err(mContextList, String.format(DEFAULT_LOCALE, format, arg1, arg2), throwable);
    }
  }

  /**
   * Logs an error message.
   *
   * @param throwable the related throwable.
   * @param format    the message format.
   * @param arg1      the first format argument.
   * @param arg2      the second format argument.
   * @param arg3      the third format argument.
   */
  public void err(@NotNull final Throwable throwable, @NotNull final String format,
      @Nullable final Object arg1, @Nullable final Object arg2, @Nullable final Object arg3) {
    if (mLevel <= ERROR_LEVEL) {
      mLogPrinter.err(mContextList, String.format(DEFAULT_LOCALE, format, arg1, arg2, arg3),
          throwable);
    }
  }

  /**
   * Logs an error message.
   *
   * @param throwable the related throwable.
   * @param format    the message format.
   * @param arg1      the first format argument.
   * @param arg2      the second format argument.
   * @param arg3      the third format argument.
   * @param arg4      the fourth format argument.
   */
  public void err(@NotNull final Throwable throwable, @NotNull final String format,
      @Nullable final Object arg1, @Nullable final Object arg2, @Nullable final Object arg3,
      @Nullable final Object arg4) {
    if (mLevel <= ERROR_LEVEL) {
      mLogPrinter.err(mContextList, String.format(DEFAULT_LOCALE, format, arg1, arg2, arg3, arg4),
          throwable);
    }
  }

  /**
   * Logs an error message.
   *
   * @param throwable the related throwable.
   * @param format    the message format.
   * @param args      the format arguments.
   */
  public void err(@NotNull final Throwable throwable, @NotNull final String format,
      @Nullable final Object... args) {
    if (mLevel <= ERROR_LEVEL) {
      mLogPrinter.err(mContextList, String.format(DEFAULT_LOCALE, format, args), throwable);
    }
  }

  /**
   * Returns the list of contexts.
   *
   * @return the list of contexts.
   */
  @NotNull
  public List<Object> getContextList() {
    return mContextList;
  }

  /**
   * Returns the log level of this logger.
   *
   * @return the log level.
   */
  @NotNull
  public Level getLogLevel() {
    return mLogLevel;
  }

  /**
   * Returns the log instance of this logger.
   *
   * @return the log instance.
   */
  @NotNull
  public LogPrinter getLogPrinter() {
    return mLogPrinter;
  }

  /**
   * Creates a new logger with the same log instance and log level, but adding the specified
   * context to the list of contexts.
   *
   * @param context the context.
   * @return the new logger.
   */
  @NotNull
  public Logger subContextLogger(@NotNull final Object context) {
    ConstantConditions.notNull("context", context);
    final Object[] thisContexts = mContexts;
    final int thisLength = thisContexts.length;
    final Object[] newContexts = new Object[thisLength + 1];
    System.arraycopy(thisContexts, 0, newContexts, 0, thisLength);
    newContexts[thisLength] = context;
    return new Logger(newContexts, mLogPrinter, mLogLevel);
  }

  public boolean willPrint(@NotNull final Level level) {
    return (mLevel <= level.ordinal());
  }

  /**
   * Logs a warning message.
   *
   * @param message the message.
   */
  public void wrn(@Nullable final String message) {
    if (mLevel <= WARNING_LEVEL) {
      mLogPrinter.wrn(mContextList, message, null);
    }
  }

  /**
   * Logs a warning message.
   *
   * @param format the message format.
   * @param arg1   the first format argument.
   */
  public void wrn(@NotNull final String format, @Nullable final Object arg1) {
    if (mLevel <= WARNING_LEVEL) {
      mLogPrinter.wrn(mContextList, String.format(DEFAULT_LOCALE, format, arg1), null);
    }
  }

  /**
   * Logs a warning message.
   *
   * @param format the message format.
   * @param arg1   the first format argument.
   * @param arg2   the second format argument.
   */
  public void wrn(@NotNull final String format, @Nullable final Object arg1,
      @Nullable final Object arg2) {
    if (mLevel <= WARNING_LEVEL) {
      mLogPrinter.wrn(mContextList, String.format(DEFAULT_LOCALE, format, arg1, arg2), null);
    }
  }

  /**
   * Logs a warning message.
   *
   * @param format the message format.
   * @param arg1   the first format argument.
   * @param arg2   the second format argument.
   * @param arg3   the third format argument.
   */
  public void wrn(@NotNull final String format, @Nullable final Object arg1,
      @Nullable final Object arg2, @Nullable final Object arg3) {
    if (mLevel <= WARNING_LEVEL) {
      mLogPrinter.wrn(mContextList, String.format(DEFAULT_LOCALE, format, arg1, arg2, arg3), null);
    }
  }

  /**
   * Logs a warning message.
   *
   * @param format the message format.
   * @param arg1   the first format argument.
   * @param arg2   the second format argument.
   * @param arg3   the third format argument.
   * @param arg4   the fourth format argument.
   */
  public void wrn(@NotNull final String format, @Nullable final Object arg1,
      @Nullable final Object arg2, @Nullable final Object arg3, @Nullable final Object arg4) {
    if (mLevel <= WARNING_LEVEL) {
      mLogPrinter.wrn(mContextList, String.format(DEFAULT_LOCALE, format, arg1, arg2, arg3, arg4),
          null);
    }
  }

  /**
   * Logs a warning message.
   *
   * @param format the message format.
   * @param args   the format arguments.
   */
  public void wrn(@NotNull final String format, @Nullable final Object... args) {
    if (mLevel <= WARNING_LEVEL) {
      mLogPrinter.wrn(mContextList, String.format(DEFAULT_LOCALE, format, args), null);
    }
  }

  /**
   * Logs a warning exception.
   *
   * @param throwable the related throwable.
   */
  public void wrn(@NotNull final Throwable throwable) {
    if (mLevel <= WARNING_LEVEL) {
      mLogPrinter.wrn(mContextList, "", throwable);
    }
  }

  /**
   * Logs a warning message.
   *
   * @param throwable the related throwable.
   * @param message   the message.
   */
  public void wrn(@NotNull final Throwable throwable, @Nullable final String message) {
    if (mLevel <= WARNING_LEVEL) {
      mLogPrinter.wrn(mContextList, message, throwable);
    }
  }

  /**
   * Logs a warning message.
   *
   * @param throwable the related throwable.
   * @param format    the message format.
   * @param arg1      the first format argument.
   */
  public void wrn(@NotNull final Throwable throwable, @NotNull final String format,
      @Nullable final Object arg1) {
    if (mLevel <= WARNING_LEVEL) {
      mLogPrinter.wrn(mContextList, String.format(DEFAULT_LOCALE, format, arg1), throwable);
    }
  }

  /**
   * Logs a warning message.
   *
   * @param throwable the related throwable.
   * @param format    the message format.
   * @param arg1      the first format argument.
   * @param arg2      the second format argument.
   */
  public void wrn(@NotNull final Throwable throwable, @NotNull final String format,
      @Nullable final Object arg1, @Nullable final Object arg2) {
    if (mLevel <= WARNING_LEVEL) {
      mLogPrinter.wrn(mContextList, String.format(DEFAULT_LOCALE, format, arg1, arg2), throwable);
    }
  }

  /**
   * Logs a warning message.
   *
   * @param throwable the related throwable.
   * @param format    the message format.
   * @param arg1      the first format argument.
   * @param arg2      the second format argument.
   * @param arg3      the third format argument.
   */
  public void wrn(@NotNull final Throwable throwable, @NotNull final String format,
      @Nullable final Object arg1, @Nullable final Object arg2, @Nullable final Object arg3) {
    if (mLevel <= WARNING_LEVEL) {
      mLogPrinter.wrn(mContextList, String.format(DEFAULT_LOCALE, format, arg1, arg2, arg3),
          throwable);
    }
  }

  /**
   * Logs a warning message.
   *
   * @param throwable the related throwable.
   * @param format    the message format.
   * @param arg1      the first format argument.
   * @param arg2      the second format argument.
   * @param arg3      the third format argument.
   * @param arg4      the fourth format argument.
   */
  public void wrn(@NotNull final Throwable throwable, @NotNull final String format,
      @Nullable final Object arg1, @Nullable final Object arg2, @Nullable final Object arg3,
      @Nullable final Object arg4) {
    if (mLevel <= WARNING_LEVEL) {
      mLogPrinter.wrn(mContextList, String.format(DEFAULT_LOCALE, format, arg1, arg2, arg3, arg4),
          throwable);
    }
  }

  /**
   * Logs a warning message.
   *
   * @param throwable the related throwable.
   * @param format    the message format.
   * @param args      the format arguments.
   */
  public void wrn(@NotNull final Throwable throwable, @NotNull final String format,
      @Nullable final Object... args) {
    if (mLevel <= WARNING_LEVEL) {
      mLogPrinter.wrn(mContextList, String.format(DEFAULT_LOCALE, format, args), throwable);
    }
  }
}

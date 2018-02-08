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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import dm.jail.util.ConstantConditions;

import static dm.jail.util.Reflections.asArgs;

/**
 * Utility class used for logging messages.
 * <p>
 * Created by davide-maestroni on 10/03/2014.
 */
@SuppressWarnings("WeakerAccess")
public class Logger {

  private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;

  private static final LogLevel DEFAULT_LOG_LEVEL = LogLevel.ERROR;

  private static final LogPrinter DEFAULT_LOG_PRINTER = LogPrinters.systemPrinter();

  private static final int LEVEL_DEBUG = LogLevel.DEBUG.ordinal();

  private static final int LEVEL_ERROR = LogLevel.ERROR.ordinal();

  private static final int LEVEL_WARNING = LogLevel.WARNING.ordinal();

  private static final Object sMutex = new Object();

  private static LinkedHashMap<Class<?>, LogLevel> sLogLevels =
      new LinkedHashMap<Class<?>, LogLevel>();

  private static LinkedHashMap<Class<?>, LogPrinter> sLogPrinters =
      new LinkedHashMap<Class<?>, LogPrinter>();

  private final List<Object> mContextList;

  private final Object[] mContexts;

  private final int mLevel;

  private final LogLevel mLogLevel;

  private final LogPrinter mLogPrinter;

  /**
   * Constructor.
   *
   * @param contexts the array of contexts.
   * @param printer  the printer instance.
   * @param level    the log level.
   */
  private Logger(@NotNull final Object[] contexts, @NotNull final LogPrinter printer,
      @NotNull final LogLevel level) {
    mContexts = contexts;
    mLogPrinter = printer;
    mLogLevel = level;
    mLevel = level.ordinal();
    mContextList = Arrays.asList(contexts);
  }

  /**
   * Gets the default log level.
   *
   * @return the log level.
   */
  @NotNull
  public static LogLevel getDefaultLevel() {
    return sLogLevels.get(Object.class);
  }

  /**
   * Sets the default log level.
   *
   * @param level the log level.
   */
  public static void setDefaultLevel(@NotNull final LogLevel level) {
    setDefaultLevel(Object.class, level);
  }

  @NotNull
  public static LogLevel getDefaultLevel(@NotNull final Class<?> type) {
    synchronized (sMutex) {
      return getBestMatch(type, sLogLevels);
    }
  }

  @NotNull
  public static LogPrinter getDefaultPrinter(@NotNull final Class<?> type) {
    synchronized (sMutex) {
      return getBestMatch(type, sLogPrinters);
    }
  }

  /**
   * Gets the default log printer instance.
   *
   * @return the log instance.
   */
  @NotNull
  public static LogPrinter getDefaultPrinter() {
    return sLogPrinters.get(Object.class);
  }

  /**
   * Sets the default log printer instance.
   *
   * @param printer the printer instance.
   */
  public static void setDefaultPrinter(@NotNull final LogPrinter printer) {
    setDefaultPrinter(Object.class, printer);
  }

  /**
   * Creates a new logger.
   *
   * @param context the context.
   * @param printer the printer instance.
   * @param level   the log level.
   * @return the new logger.
   */
  @NotNull
  public static Logger newLogger(@NotNull final Object context, @Nullable final LogPrinter printer,
      @Nullable final LogLevel level) {
    return new Logger(asArgs(ConstantConditions.notNull("context", context)),
        (printer != null) ? printer : getDefaultPrinter(context.getClass()),
        (level != null) ? level : getDefaultLevel(context.getClass()));
  }

  @NotNull
  public static Logger newLogger(@NotNull final Object context) {
    return newLogger(context, null, null);
  }

  @NotNull
  public static Logger newLogger(@NotNull final Object context,
      @Nullable final LogPrinter printer) {
    return newLogger(context, printer, null);
  }

  @NotNull
  public static Logger newLogger(@NotNull final Object context, @Nullable final LogLevel level) {
    return newLogger(context, null, level);
  }

  public static void setDefaultLevel(@NotNull final Class<?> type, @NotNull final LogLevel level) {
    ConstantConditions.notNull("level", level);
    synchronized (sMutex) {
      final LinkedHashMap<Class<?>, LogLevel> logLevels =
          new LinkedHashMap<Class<?>, LogLevel>(sLogLevels);
      if (logLevels.put(type, level) != null) {
        sLogLevels = logLevels;
      }
    }
  }

  public static void setDefaultPrinter(@NotNull final Class<?> type,
      @NotNull final LogPrinter printer) {
    ConstantConditions.notNull("printer", printer);
    synchronized (sMutex) {
      final LinkedHashMap<Class<?>, LogPrinter> logPrinters =
          new LinkedHashMap<Class<?>, LogPrinter>(sLogPrinters);
      if (logPrinters.put(type, printer) != null) {
        sLogPrinters = logPrinters;
      }
    }
  }

  @NotNull
  private static <T> T getBestMatch(@NotNull final Class<?> type,
      @NotNull final Map<Class<?>, T> map) {
    Class<?> bestCandidate = null;
    T result = map.get(Object.class);
    for (final Entry<Class<?>, T> entry : map.entrySet()) {
      final Class<?> key = entry.getKey();
      if (key.equals(type)) {
        return entry.getValue();
      }

      if (key.isAssignableFrom(type)) {
        if ((bestCandidate == null) || bestCandidate.isAssignableFrom(key) || !key.isAssignableFrom(
            bestCandidate)) {
          bestCandidate = key;
          result = entry.getValue();
        }
      }
    }

    return result;
  }

  /**
   * Logs a debug message.
   *
   * @param message the message.
   */
  public void dbg(@Nullable final String message) {
    if (mLevel <= LEVEL_DEBUG) {
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
    if (mLevel <= LEVEL_DEBUG) {
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
    if (mLevel <= LEVEL_DEBUG) {
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
    if (mLevel <= LEVEL_DEBUG) {
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
    if (mLevel <= LEVEL_DEBUG) {
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
    if (mLevel <= LEVEL_DEBUG) {
      mLogPrinter.dbg(mContextList, String.format(DEFAULT_LOCALE, format, args), null);
    }
  }

  /**
   * Logs a debug exception.
   *
   * @param throwable the related throwable.
   */
  public void dbg(@Nullable final Throwable throwable) {
    if (mLevel <= LEVEL_DEBUG) {
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
    if (mLevel <= LEVEL_DEBUG) {
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
    if (mLevel <= LEVEL_DEBUG) {
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
    if (mLevel <= LEVEL_DEBUG) {
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
    if (mLevel <= LEVEL_DEBUG) {
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
    if (mLevel <= LEVEL_DEBUG) {
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
    if (mLevel <= LEVEL_DEBUG) {
      mLogPrinter.dbg(mContextList, String.format(DEFAULT_LOCALE, format, args), throwable);
    }
  }

  /**
   * Logs an error message.
   *
   * @param message the message.
   */
  public void err(@Nullable final String message) {
    if (mLevel <= LEVEL_ERROR) {
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
    if (mLevel <= LEVEL_ERROR) {
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
    if (mLevel <= LEVEL_ERROR) {
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
    if (mLevel <= LEVEL_ERROR) {
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
    if (mLevel <= LEVEL_ERROR) {
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
    if (mLevel <= LEVEL_ERROR) {
      mLogPrinter.err(mContextList, String.format(DEFAULT_LOCALE, format, args), null);
    }
  }

  /**
   * Logs an error exception.
   *
   * @param throwable the related throwable.
   */
  public void err(@NotNull final Throwable throwable) {
    if (mLevel <= LEVEL_ERROR) {
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
    if (mLevel <= LEVEL_ERROR) {
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
    if (mLevel <= LEVEL_ERROR) {
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
    if (mLevel <= LEVEL_ERROR) {
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
    if (mLevel <= LEVEL_ERROR) {
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
    if (mLevel <= LEVEL_ERROR) {
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
    if (mLevel <= LEVEL_ERROR) {
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
  public LogLevel getLogLevel() {
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
  public Logger newChildLogger(@NotNull final Object context) {
    return newChildLogger(context, null, null);
  }

  @NotNull
  public Logger newChildLogger(@NotNull final Object context, @Nullable final LogPrinter printer) {
    return newChildLogger(context, printer, null);
  }

  @NotNull
  public Logger newChildLogger(@NotNull final Object context, @Nullable final LogLevel level) {
    return newChildLogger(context, null, level);
  }

  @NotNull
  public Logger newChildLogger(@NotNull final Object context, @Nullable final LogPrinter printer,
      @Nullable final LogLevel level) {
    ConstantConditions.notNull("context", context);
    final Object[] thisContexts = mContexts;
    final int thisLength = thisContexts.length;
    final Object[] newContexts = new Object[thisLength + 1];
    System.arraycopy(thisContexts, 0, newContexts, 0, thisLength);
    newContexts[thisLength] = context;
    return new Logger(newContexts,
        (printer != null) ? printer : getDefaultPrinter(context.getClass()),
        (level != null) ? level : getDefaultLevel(context.getClass()));
  }

  public boolean willPrint(@NotNull final LogLevel level) {
    return (mLevel <= level.ordinal());
  }

  /**
   * Logs a warning message.
   *
   * @param message the message.
   */
  public void wrn(@Nullable final String message) {
    if (mLevel <= LEVEL_WARNING) {
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
    if (mLevel <= LEVEL_WARNING) {
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
    if (mLevel <= LEVEL_WARNING) {
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
    if (mLevel <= LEVEL_WARNING) {
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
    if (mLevel <= LEVEL_WARNING) {
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
    if (mLevel <= LEVEL_WARNING) {
      mLogPrinter.wrn(mContextList, String.format(DEFAULT_LOCALE, format, args), null);
    }
  }

  /**
   * Logs a warning exception.
   *
   * @param throwable the related throwable.
   */
  public void wrn(@NotNull final Throwable throwable) {
    if (mLevel <= LEVEL_WARNING) {
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
    if (mLevel <= LEVEL_WARNING) {
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
    if (mLevel <= LEVEL_WARNING) {
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
    if (mLevel <= LEVEL_WARNING) {
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
    if (mLevel <= LEVEL_WARNING) {
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
    if (mLevel <= LEVEL_WARNING) {
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
    if (mLevel <= LEVEL_WARNING) {
      mLogPrinter.wrn(mContextList, String.format(DEFAULT_LOCALE, format, args), throwable);
    }
  }

  static {
    sLogLevels.put(Object.class, DEFAULT_LOG_LEVEL);
    sLogPrinters.put(Object.class, DEFAULT_LOG_PRINTER);
  }
}

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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.WeakHashMap;

import dm.jale.util.ConstantConditions;

import static dm.jale.util.Reflections.asArgs;

/**
 * Utility class used for logging messages.
 * <p>
 * Created by davide-maestroni on 10/03/2014.
 */
@SuppressWarnings("WeakerAccess")
public class Logger {

  private static final Locale DEFAULT_LOCALE = Locale.getDefault();

  private static final LogConnector DEFAULT_LOG_CONNECTOR = new JavaLoggingConnector();

  private static final String NULL_IDENTITY_STRING = "null@" + Integer.toHexString(0);

  private static final WeakHashMap<Logger, Void> sLoggers = new WeakHashMap<Logger, Void>();

  private static final Object sMutex = new Object();

  private static LogConnector sConnector = DEFAULT_LOG_CONNECTOR;

  private final List<Object> mContextList;

  private final Object[] mContexts;

  private final Locale mLocale;

  private final String mLoggerName;

  private volatile LogPrinter mPrinter;

  /**
   * Constructor.
   *
   * @param contexts   the array of contexts.
   * @param loggerName the logger name.
   * @param locale     the locale instance.
   */
  private Logger(@NotNull final Object[] contexts, @NotNull final String loggerName,
      @NotNull final Locale locale) {
    mContexts = contexts;
    mLoggerName = loggerName;
    mLocale = locale;
    mContextList = Collections.unmodifiableList(Arrays.asList(contexts));
  }

  public static void addConnector(@NotNull final LogConnector connector) {
    ConstantConditions.notNull("connector", connector);
    synchronized (sMutex) {
      final LogConnector newConnector;
      final LogConnector currentConnector = sConnector;
      if (currentConnector == DEFAULT_LOG_CONNECTOR) {
        newConnector = connector;

      } else if (currentConnector instanceof MultiConnector) {
        newConnector = new MultiConnector((MultiConnector) currentConnector, connector);

      } else {
        newConnector = new MultiConnector(currentConnector, connector);
      }

      sConnector = newConnector;
      for (final Logger logger : sLoggers.keySet()) {
        logger.setPrinter(newConnector);
      }
    }
  }

  public static void clearConnectors() {
    synchronized (sMutex) {
      sConnector = DEFAULT_LOG_CONNECTOR;
      for (final Logger logger : sLoggers.keySet()) {
        logger.setPrinter(DEFAULT_LOG_CONNECTOR);
      }
    }
  }

  @NotNull
  public static String identityToString(@Nullable final Object object) {
    return (object != null) ? object.getClass().getName() + "@" + Integer.toHexString(
        System.identityHashCode(object)) : NULL_IDENTITY_STRING;
  }

  @NotNull
  public static Logger newLogger(@NotNull final Object context) {
    return newLogger(context, null, null);
  }

  @NotNull
  public static Logger newLogger(@NotNull final Object context, @Nullable final Locale locale) {
    return newLogger(context, null, locale);
  }

  @NotNull
  public static Logger newLogger(@NotNull final Object context, @Nullable final String loggerName) {
    return newLogger(context, loggerName, null);
  }

  /**
   * Creates a new logger.
   *
   * @param context    the context.
   * @param loggerName the logger name.
   * @param locale     the locale instance.
   * @return the new logger.
   */
  @NotNull
  public static Logger newLogger(@NotNull final Object context, @Nullable final String loggerName,
      @Nullable final Locale locale) {
    final Logger logger = new Logger(asArgs(ConstantConditions.notNull("context", context)),
        (loggerName != null) ? loggerName : context.getClass().getName(),
        (locale != null) ? locale : DEFAULT_LOCALE);
    register(logger);
    return logger;
  }

  /**
   * Prints the stack trace of the specified throwable into a string.
   *
   * @param throwable the throwable instance.
   * @return the printed stack trace.
   */
  @NotNull
  public static String printStackTrace(@NotNull final Throwable throwable) {
    final StringWriter writer = new StringWriter();
    final PrintWriter printWriter = new PrintWriter(writer);
    throwable.printStackTrace(printWriter);
    printWriter.close();
    return writer.toString();
  }

  @NotNull
  private static String contextToString(@NotNull final Object object) {
    return object.getClass().getSimpleName() + "@" + Integer.toHexString(
        System.identityHashCode(object));
  }

  @NotNull
  private static String printContexts(@NotNull final List<Object> contexts) {
    final Iterator<Object> iterator = contexts.iterator();
    if (iterator.hasNext()) {
      final StringBuilder builder = new StringBuilder();
      builder.append('[');
      while (true) {
        builder.append(contextToString(iterator.next()));
        if (!iterator.hasNext()) {
          return builder.append(']').append(' ').toString();
        }

        builder.append(" => ");
      }
    }

    return "";
  }

  private static void register(@NotNull final Logger logger) {
    synchronized (sMutex) {
      sLoggers.put(logger, null);
      logger.setPrinter(sConnector);
    }
  }

  /**
   * Logs a debug message.
   *
   * @param message the message.
   */
  public void dbg(@Nullable final String message) {
    final LogPrinter printer = mPrinter;
    if (printer.canLogDbg()) {
      printer.dbg(printContexts(mContextList) + message, null);
    }
  }

  /**
   * Logs a debug message.
   *
   * @param format the message format.
   * @param arg1   the first format argument.
   */
  public void dbg(@NotNull final String format, @Nullable final Object arg1) {
    final LogPrinter printer = mPrinter;
    if (printer.canLogDbg()) {
      printer.dbg(printContexts(mContextList) + String.format(mLocale, format, arg1), null);
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
    final LogPrinter printer = mPrinter;
    if (printer.canLogDbg()) {
      printer.dbg(printContexts(mContextList) + String.format(mLocale, format, arg1, arg2), null);
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
    final LogPrinter printer = mPrinter;
    if (printer.canLogDbg()) {
      printer.dbg(printContexts(mContextList) + String.format(mLocale, format, arg1, arg2, arg3),
          null);
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
    final LogPrinter printer = mPrinter;
    if (printer.canLogDbg()) {
      printer.dbg(
          printContexts(mContextList) + String.format(mLocale, format, arg1, arg2, arg3, arg4),
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
    final LogPrinter printer = mPrinter;
    if (printer.canLogDbg()) {
      printer.dbg(printContexts(mContextList) + String.format(mLocale, format, args), null);
    }
  }

  /**
   * Logs a debug exception.
   *
   * @param throwable the related throwable.
   */
  public void dbg(@Nullable final Throwable throwable) {
    final LogPrinter printer = mPrinter;
    if (printer.canLogDbg()) {
      printer.dbg(printContexts(mContextList), throwable);
    }
  }

  /**
   * Logs a debug message.
   *
   * @param throwable the related throwable.
   * @param message   the message.
   */
  public void dbg(@Nullable final Throwable throwable, @Nullable final String message) {
    final LogPrinter printer = mPrinter;
    if (printer.canLogDbg()) {
      printer.dbg(printContexts(mContextList) + message, throwable);
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
    final LogPrinter printer = mPrinter;
    if (printer.canLogDbg()) {
      printer.dbg(printContexts(mContextList) + String.format(mLocale, format, arg1), throwable);
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
    final LogPrinter printer = mPrinter;
    if (printer.canLogDbg()) {
      printer.dbg(printContexts(mContextList) + String.format(mLocale, format, arg1, arg2),
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
   */
  public void dbg(@NotNull final Throwable throwable, @NotNull final String format,
      @Nullable final Object arg1, @Nullable final Object arg2, @Nullable final Object arg3) {
    final LogPrinter printer = mPrinter;
    if (printer.canLogDbg()) {
      printer.dbg(printContexts(mContextList) + String.format(mLocale, format, arg1, arg2, arg3),
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
    final LogPrinter printer = mPrinter;
    if (printer.canLogDbg()) {
      printer.dbg(
          printContexts(mContextList) + String.format(mLocale, format, arg1, arg2, arg3, arg4),
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
    final LogPrinter printer = mPrinter;
    if (printer.canLogDbg()) {
      printer.dbg(printContexts(mContextList) + String.format(mLocale, format, args), throwable);
    }
  }

  /**
   * Logs an error message.
   *
   * @param message the message.
   */
  public void err(@Nullable final String message) {
    final LogPrinter printer = mPrinter;
    if (printer.canLogErr()) {
      printer.err(printContexts(mContextList) + message, null);
    }
  }

  /**
   * Logs an error message.
   *
   * @param format the message format.
   * @param arg1   the first format argument.
   */
  public void err(@NotNull final String format, @Nullable final Object arg1) {
    final LogPrinter printer = mPrinter;
    if (printer.canLogErr()) {
      printer.err(printContexts(mContextList) + String.format(mLocale, format, arg1), null);
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
    final LogPrinter printer = mPrinter;
    if (printer.canLogErr()) {
      printer.err(printContexts(mContextList) + String.format(mLocale, format, arg1, arg2), null);
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
    final LogPrinter printer = mPrinter;
    if (printer.canLogErr()) {
      printer.err(printContexts(mContextList) + String.format(mLocale, format, arg1, arg2, arg3),
          null);
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
    final LogPrinter printer = mPrinter;
    if (printer.canLogErr()) {
      printer.err(
          printContexts(mContextList) + String.format(mLocale, format, arg1, arg2, arg3, arg4),
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
    final LogPrinter printer = mPrinter;
    if (printer.canLogErr()) {
      printer.err(printContexts(mContextList) + String.format(mLocale, format, args), null);
    }
  }

  /**
   * Logs an error exception.
   *
   * @param throwable the related throwable.
   */
  public void err(@NotNull final Throwable throwable) {
    final LogPrinter printer = mPrinter;
    if (printer.canLogErr()) {
      printer.err(printContexts(mContextList), throwable);
    }
  }

  /**
   * Logs an error message.
   *
   * @param throwable the related throwable.
   * @param message   the message.
   */
  public void err(@NotNull final Throwable throwable, @Nullable final String message) {
    final LogPrinter printer = mPrinter;
    if (printer.canLogErr()) {
      printer.err(printContexts(mContextList) + message, throwable);
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
    final LogPrinter printer = mPrinter;
    if (printer.canLogErr()) {
      printer.err(printContexts(mContextList) + String.format(mLocale, format, arg1), throwable);
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
    final LogPrinter printer = mPrinter;
    if (printer.canLogErr()) {
      printer.err(printContexts(mContextList) + String.format(mLocale, format, arg1, arg2),
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
   */
  public void err(@NotNull final Throwable throwable, @NotNull final String format,
      @Nullable final Object arg1, @Nullable final Object arg2, @Nullable final Object arg3) {
    final LogPrinter printer = mPrinter;
    if (printer.canLogErr()) {
      printer.err(printContexts(mContextList) + String.format(mLocale, format, arg1, arg2, arg3),
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
    final LogPrinter printer = mPrinter;
    if (printer.canLogErr()) {
      printer.err(
          printContexts(mContextList) + String.format(mLocale, format, arg1, arg2, arg3, arg4),
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
    final LogPrinter printer = mPrinter;
    if (printer.canLogErr()) {
      printer.err(printContexts(mContextList) + String.format(mLocale, format, args), throwable);
    }
  }

  @NotNull
  public Locale getLocale() {
    return mLocale;
  }

  @NotNull
  public String getName() {
    return mLoggerName;
  }

  /**
   * Returns the log instance of this logger.
   *
   * @return the log instance.
   */
  @NotNull
  public LogPrinter getPrinter() {
    return mPrinter;
  }

  private void setPrinter(@NotNull final LogConnector connector) {
    mPrinter = connector.getPrinter(mLoggerName, mContextList);
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
    ConstantConditions.notNull("context", context);
    final Object[] thisContexts = mContexts;
    final int thisLength = thisContexts.length;
    final Object[] newContexts = new Object[thisLength + 1];
    System.arraycopy(thisContexts, 0, newContexts, 0, thisLength);
    newContexts[thisLength] = context;
    final Logger logger = new Logger(newContexts, mLoggerName, mLocale);
    register(logger);
    return logger;
  }

  /**
   * Logs a warning message.
   *
   * @param message the message.
   */
  public void wrn(@Nullable final String message) {
    final LogPrinter printer = mPrinter;
    if (printer.canLogWrn()) {
      printer.wrn(printContexts(mContextList) + message, null);
    }
  }

  /**
   * Logs a warning message.
   *
   * @param format the message format.
   * @param arg1   the first format argument.
   */
  public void wrn(@NotNull final String format, @Nullable final Object arg1) {
    final LogPrinter printer = mPrinter;
    if (printer.canLogWrn()) {
      printer.wrn(printContexts(mContextList) + String.format(mLocale, format, arg1), null);
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
    final LogPrinter printer = mPrinter;
    if (printer.canLogWrn()) {
      printer.wrn(printContexts(mContextList) + String.format(mLocale, format, arg1, arg2), null);
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
    final LogPrinter printer = mPrinter;
    if (printer.canLogWrn()) {
      printer.wrn(printContexts(mContextList) + String.format(mLocale, format, arg1, arg2, arg3),
          null);
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
    final LogPrinter printer = mPrinter;
    if (printer.canLogWrn()) {
      printer.wrn(
          printContexts(mContextList) + String.format(mLocale, format, arg1, arg2, arg3, arg4),
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
    final LogPrinter printer = mPrinter;
    if (printer.canLogWrn()) {
      printer.wrn(printContexts(mContextList) + String.format(mLocale, format, args), null);
    }
  }

  /**
   * Logs a warning exception.
   *
   * @param throwable the related throwable.
   */
  public void wrn(@NotNull final Throwable throwable) {
    final LogPrinter printer = mPrinter;
    if (printer.canLogWrn()) {
      printer.wrn(printContexts(mContextList), throwable);
    }
  }

  /**
   * Logs a warning message.
   *
   * @param throwable the related throwable.
   * @param message   the message.
   */
  public void wrn(@NotNull final Throwable throwable, @Nullable final String message) {
    final LogPrinter printer = mPrinter;
    if (printer.canLogWrn()) {
      printer.wrn(printContexts(mContextList) + message, throwable);
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
    final LogPrinter printer = mPrinter;
    if (printer.canLogWrn()) {
      printer.wrn(printContexts(mContextList) + String.format(mLocale, format, arg1), throwable);
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
    final LogPrinter printer = mPrinter;
    if (printer.canLogWrn()) {
      printer.wrn(printContexts(mContextList) + String.format(mLocale, format, arg1, arg2),
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
   */
  public void wrn(@NotNull final Throwable throwable, @NotNull final String format,
      @Nullable final Object arg1, @Nullable final Object arg2, @Nullable final Object arg3) {
    final LogPrinter printer = mPrinter;
    if (printer.canLogWrn()) {
      printer.wrn(printContexts(mContextList) + String.format(mLocale, format, arg1, arg2, arg3),
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
    final LogPrinter printer = mPrinter;
    if (printer.canLogWrn()) {
      printer.wrn(
          printContexts(mContextList) + String.format(mLocale, format, arg1, arg2, arg3, arg4),
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
    final LogPrinter printer = mPrinter;
    if (printer.canLogWrn()) {
      printer.wrn(printContexts(mContextList) + String.format(mLocale, format, args), throwable);
    }
  }

  private static class MultiConnector implements LogConnector {

    private final LogConnector[] mConnectors;

    private MultiConnector(@NotNull final LogConnector currentConnector,
        @NotNull final LogConnector newConnector) {
      mConnectors = new LogConnector[]{currentConnector, newConnector};
    }

    private MultiConnector(@NotNull final MultiConnector currentConnector,
        @NotNull final LogConnector newConnector) {
      final LogConnector[] currentConnectors = currentConnector.mConnectors;
      final int length = currentConnectors.length;
      final LogConnector[] connectors = new LogConnector[length];
      System.arraycopy(currentConnectors, 0, connectors, 0, length);
      connectors[length] = newConnector;
      mConnectors = connectors;
    }

    public LogPrinter getPrinter(@NotNull final String loggerName,
        @NotNull final List<Object> contexts) {
      final LogConnector[] connectors = mConnectors;
      final int length = connectors.length;
      final LogPrinter[] printers = new LogPrinter[length];
      for (int i = 0; i < length; ++i) {
        printers[i] = connectors[i].getPrinter(loggerName, contexts);
      }

      return new MultiPrinter(printers);
    }
  }

  private static class MultiPrinter implements LogPrinter {

    private final LogPrinter[] mPrinters;

    private MultiPrinter(@NotNull final LogPrinter[] printers) {
      mPrinters = printers;
    }

    public boolean canLogDbg() {
      for (final LogPrinter printer : mPrinters) {
        if (printer.canLogDbg()) {
          return true;
        }
      }

      return false;
    }

    public boolean canLogErr() {
      for (final LogPrinter printer : mPrinters) {
        if (printer.canLogErr()) {
          return true;
        }
      }

      return false;
    }

    public boolean canLogWrn() {
      for (final LogPrinter printer : mPrinters) {
        if (printer.canLogWrn()) {
          return true;
        }
      }

      return false;
    }

    public void dbg(@Nullable final String message, @Nullable final Throwable throwable) {
      for (final LogPrinter printer : mPrinters) {
        printer.dbg(message, throwable);
      }
    }

    public void err(@Nullable final String message, @Nullable final Throwable throwable) {
      for (final LogPrinter printer : mPrinters) {
        printer.err(message, throwable);
      }
    }

    public void wrn(@Nullable final String message, @Nullable final Throwable throwable) {
      for (final LogPrinter printer : mPrinters) {
        printer.wrn(message, throwable);
      }
    }
  }
}

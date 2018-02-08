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

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import dm.jail.config.BuildConfig;
import dm.jail.util.ConstantConditions;

/**
 * Utility class for creating and sharing log instances.
 * <p>
 * Created by davide-maestroni on 12/22/2014.
 */
public class LogPrinters {

  private static final String NULL_IDENTITY_STRING = "null@" + Integer.toHexString(0);

  private static final DefaultFormatter sDefaultFormatter = new DefaultFormatter();

  private static final SystemPrinter sDefaultSystemPrinter = new SystemPrinter(sDefaultFormatter);

  private static final NullPrinter sNullPrinter = new NullPrinter();

  /**
   * Avoid explicit instantiation.
   */
  protected LogPrinters() {
    ConstantConditions.avoid();
  }

  @NotNull
  public static LogFormatter defaultFormatter() {
    return sDefaultFormatter;
  }

  @NotNull
  public static LogFormatter defaultFormatter(@Nullable final String logFormat,
      @Nullable final String exceptionFormat) {
    return new DefaultFormatter(logFormat, exceptionFormat);
  }

  @NotNull
  public static String identityToString(@Nullable final Object object) {
    return (object != null) ? object.getClass().getName() + "@" + Integer.toHexString(
        System.identityHashCode(object)) : NULL_IDENTITY_STRING;
  }

  /**
   * Returns the null log shared instance.
   *
   * @return the shared instance.
   */
  @NotNull
  public static LogPrinter nullPrinter() {
    return sNullPrinter;
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
  public static LogPrinter systemPrinter(@NotNull final LogFormatter formatter) {
    return new SystemPrinter(formatter);
  }

  /**
   * Returns the system output log shared instance.
   *
   * @return the shared instance.
   */
  @NotNull
  public static LogPrinter systemPrinter() {
    return sDefaultSystemPrinter;
  }

  private static class DefaultFormatter implements LogFormatter, Serializable {

    private static final String DEFAULT_EXCEPTION_FORMAT = " caused by:%n%s";

    private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;

    private static final String DEFAULT_LOG_FORMAT = "%s\t%s\t%s\t%s<%s>";

    private static final String FORMAT_DATE = "MM/dd HH:mm:ss.SSS z";

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private static ThreadLocal<SimpleDateFormat> sDateFormatter =
        new ThreadLocal<SimpleDateFormat>() {

          @Override
          protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat(FORMAT_DATE, Locale.getDefault());
          }
        };

    private final String mExceptionFormat;

    private final String mLogFormat;

    private DefaultFormatter() {
      mLogFormat = DEFAULT_LOG_FORMAT;
      mExceptionFormat = DEFAULT_EXCEPTION_FORMAT;
    }

    private DefaultFormatter(@Nullable final String logFormat,
        @Nullable final String exceptionFormat) {
      mLogFormat = (logFormat != null) ? logFormat : DEFAULT_LOG_FORMAT;
      mExceptionFormat = (exceptionFormat != null) ? exceptionFormat : DEFAULT_EXCEPTION_FORMAT;
    }

    @NotNull
    private static String printContexts(@NotNull final List<Object> contexts) {
      final Iterator<Object> iterator = contexts.iterator();
      if (iterator.hasNext()) {
        final StringBuilder builder = new StringBuilder();
        builder.append('[');
        while (true) {
          builder.append(identityToString(iterator.next()));
          if (!iterator.hasNext()) {
            return builder.append(']').toString();
          }

          builder.append(" => ");
        }
      }

      return "[]";
    }

    @NotNull
    public String format(@NotNull final LogLevel level, @NotNull final List<Object> contexts,
        @Nullable final String message, @Nullable final Throwable throwable) {
      final String formatted =
          String.format(DEFAULT_LOCALE, mLogFormat, sDateFormatter.get().format(new Date()),
              Thread.currentThread().getName(), printContexts(contexts), level, message);
      if (throwable != null) {
        return formatted + String.format(DEFAULT_LOCALE, mExceptionFormat,
            LogPrinters.printStackTrace(throwable));
      }

      return formatted;
    }
  }

  private static class NullPrinter extends TemplateLogPrinter {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private NullPrinter() {
      super(sDefaultFormatter);
    }

    @Override
    protected void log(@NotNull final LogLevel level, @NotNull final List<Object> contexts,
        @Nullable final String message, @Nullable final Throwable throwable) {
    }
  }

  private static class SystemPrinter extends TemplateLogPrinter {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private SystemPrinter(@NotNull final LogFormatter formatter) {
      super(formatter);
    }

    @Override
    protected void print(@NotNull final String message) {
      System.out.println(message);
    }
  }
}

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
import java.io.StringWriter;

import dm.jail.util.ConstantConditions;

/**
 * Utility class for creating and sharing log instances.
 * <p>
 * Created by davide-maestroni on 12/22/2014.
 */
public class LogPrinters {

  /**
   * Avoid explicit instantiation.
   */
  protected LogPrinters() {
    ConstantConditions.avoid();
  }

  /**
   * Returns the null log shared instance.
   *
   * @return the shared instance.
   */
  @NotNull
  public static LogPrinter nullPrinter() {
    return NullLogPrinter.instance();
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
    throwable.printStackTrace(new PrintWriter(writer));
    return writer.toString();
  }

  /**
   * Returns the system output log shared instance.
   *
   * @return the shared instance.
   */
  @NotNull
  public static LogPrinter systemPrinter() {
    return SystemLogPrinter.instance();
  }

  @NotNull
  public static LogPrinter withFilter(@NotNull final LogFilter filter,
      @NotNull final LogPrinter printer) {
    // TODO: 05/02/2018 implement
    return null;
  }

  public interface LogFilter {

    @Nullable
    String apply(@NotNull Object context, @Nullable String message, @Nullable Throwable throwable);
  }
}

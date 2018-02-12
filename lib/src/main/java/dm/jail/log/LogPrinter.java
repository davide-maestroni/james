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

import org.jetbrains.annotations.Nullable;

/**
 * Interface defining a log printer object responsible for formatting and writing the log messages.
 * <p>
 * A default printer instance can be set by invoking the proper logger methods. Note, however, that
 * the instance employed cannot be dynamically changed after the logger instantiation.
 * <br>
 * Note also that a printer instance is typically accessed from different threads, so, it is
 * responsibility of the implementing class to avoid concurrency issues by synchronizing mutable
 * fields when required.
 * <p>
 * To avoid an excessive number of log messages, it is sufficient to set an higher log level.
 * Though, it is also possible to completely remove the log source code (and related strings) from
 * the released code by using Proguard and adding, for example, the following rule to the
 * configuration file:
 * <pre><code>
 * -assumenosideeffects class dm.jail.log.Logger {
 *   public void dbg(...);
 * }
 * </code></pre>
 * <p>
 * Created by davide-maestroni on 10/03/2014.
 *
 * @see Logger Logger
 */
public interface LogPrinter {

  boolean canLogDbg();

  boolean canLogErr();

  boolean canLogWrn();

  /**
   * Logs a debug message.
   *
   * @param message   the message.
   * @param throwable the optional throwable or null.
   */
  void dbg(@Nullable String message, @Nullable Throwable throwable);

  /**
   * Logs an error message.
   *
   * @param message   the message.
   * @param throwable the optional throwable or null.
   */
  void err(@Nullable String message, @Nullable Throwable throwable);

  /**
   * Logs a warning message.
   *
   * @param message   the message.
   * @param throwable the optional throwable or null.
   */
  void wrn(@Nullable String message, @Nullable Throwable throwable);
}

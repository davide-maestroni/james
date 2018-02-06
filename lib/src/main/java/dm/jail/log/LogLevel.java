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

/**
 * Log levels enumeration from more to less verbose.
 */
public enum LogLevel {

  /**
   * The most verbose log level.
   * <br>
   * Debug logs are meant to describe in detail what's happening inside the routine.
   */
  DEBUG,

  /**
   * The medium log level.
   * <br>
   * Warning logs are meant to notify events that are not completely unexpected, but might be a
   * clue that something wrong is happening.
   */
  WARNING,

  /**
   * The least verbose level.
   * <br>
   * Error logs notify unexpected events that are clearly an exception in the normal code
   * execution.
   */
  ERROR,

  /**
   * Silences all the logs.
   */
  SILENT
}

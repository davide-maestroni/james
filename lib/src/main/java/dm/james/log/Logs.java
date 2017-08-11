/*
 * Copyright 2017 Davide Maestroni
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

package dm.james.log;

import org.jetbrains.annotations.NotNull;

import dm.james.util.ConstantConditions;

/**
 * Utility class for creating and sharing log instances.
 * <p>
 * Created by davide-maestroni on 12/22/2014.
 */
public class Logs {

  /**
   * Avoid explicit instantiation.
   */
  protected Logs() {
    ConstantConditions.avoid();
  }

  /**
   * Returns the null log shared instance.
   *
   * @return the shared instance.
   */
  @NotNull
  public static Log nullLog() {
    return NullLog.instance();
  }

  /**
   * Returns the system output log shared instance.
   *
   * @return the shared instance.
   */
  @NotNull
  public static Log systemLog() {
    return SystemLog.instance();
  }
}

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

package dm.fates.util;

import org.jetbrains.annotations.NotNull;

import java.lang.Thread.State;

/**
 * Created by davide-maestroni on 08/05/2017.
 */
public class Threads {

  private Threads() {
  }

  public static boolean interruptIfWaiting(@NotNull final Thread thread) {
    final State state = thread.getState();
    if ((state == State.WAITING) || (state == State.TIMED_WAITING)) {
      thread.interrupt();
      return true;
    }

    return false;
  }
}

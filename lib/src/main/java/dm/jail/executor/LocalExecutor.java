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

package dm.jail.executor;

import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

import dm.jail.log.Logger;
import dm.jail.util.DoubleQueue;

/**
 * Class maintaining a queue of commands which is local to the calling thread.
 * <p>
 * The implementation ensures that recursive commands are broken into commands handled inside a
 * consuming loop, running in the same thread.
 * <p>
 * Created by davide-maestroni on 09/18/2014.
 */
class LocalExecutor {

  private static final int INITIAL_CAPACITY = 1 << 3;

  private static final LocalExecutorThreadLocal sExecutor = new LocalExecutorThreadLocal();

  private final DoubleQueue<Runnable> mCommands = new DoubleQueue<Runnable>(INITIAL_CAPACITY);

  private final Logger mLogger;

  private boolean mIsRunning;

  /**
   * Constructor.
   */
  private LocalExecutor() {
    mLogger = Logger.newLogger(this);
  }

  /**
   * Cancels the specified command if not already run.
   *
   * @param command the command.
   */
  public static void cancel(@NotNull final Runnable command) {
    sExecutor.get().removeCommand(command);
  }

  /**
   * Runs the specified command.
   *
   * @param command the command.
   */
  public static void run(@NotNull final Runnable command) {
    sExecutor.get().addCommand(command);
  }

  private void addCommand(@NotNull final Runnable command) {
    if (!mIsRunning) {
      mIsRunning = true;
      try {
        try {
          command.run();

        } catch (final Throwable t) {
          mLogger.wrn(t, "Suppressed exception");
        }

        run();

      } finally {
        mIsRunning = false;
      }

    } else {
      mCommands.add(command);
    }
  }

  private void removeCommand(@NotNull final Runnable command) {
    final Iterator<Runnable> iterator = mCommands.iterator();
    while (iterator.hasNext()) {
      if (iterator.next() == command) {
        iterator.remove();
      }
    }
  }

  private void run() {
    mIsRunning = true;
    final DoubleQueue<Runnable> commands = mCommands;
    try {
      while (!commands.isEmpty()) {
        try {
          commands.removeFirst().run();

        } catch (final Throwable t) {
          mLogger.wrn(t, "Suppressed exception");
        }
      }

    } finally {
      mIsRunning = false;
    }
  }

  /**
   * Thread local initializing the queue instance.
   */
  private static class LocalExecutorThreadLocal extends ThreadLocal<LocalExecutor> {

    @Override
    protected LocalExecutor initialValue() {
      return new LocalExecutor();
    }
  }
}

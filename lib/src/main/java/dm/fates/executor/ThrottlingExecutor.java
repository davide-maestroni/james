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

package dm.fates.executor;

import org.jetbrains.annotations.NotNull;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;

import dm.fates.config.BuildConfig;
import dm.fates.util.ConstantConditions;
import dm.fates.util.DoubleQueue;
import dm.fates.util.WeakIdentityHashMap;

/**
 * Executor implementation throttling the number of running commands so to keep it under a
 * specified limit.
 * <p>
 * Note that, in case the executor is backed by a synchronous one, it is possible that commands
 * are run on threads different from the calling one, so that the results will be not immediately
 * available.
 * <p>
 * Created by davide-maestroni on 07/18/2015.
 */
class ThrottlingExecutor extends ScheduledExecutorDecorator implements Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final WeakIdentityHashMap<Runnable, WeakReference<ThrottlingCommand>> mCommands =
      new WeakIdentityHashMap<Runnable, WeakReference<ThrottlingCommand>>();

  private final ScheduledExecutor mExecutor;

  private final int mMaxRunning;

  private final Object mMutex = new Object();

  private final DoubleQueue<PendingCommand> mQueue = new DoubleQueue<PendingCommand>();

  private int mRunningCount;

  /**
   * Constructor.
   *
   * @param executor    the wrapped instance.
   * @param maxCommands the maximum number of running commands.
   * @throws IllegalArgumentException if the specified max number is less than 1.
   */
  private ThrottlingExecutor(@NotNull final ScheduledExecutor executor, final int maxCommands) {
    super(executor);
    mExecutor = executor;
    mMaxRunning = ConstantConditions.positive("maxCommands", maxCommands);
  }

  /**
   * Returns a new throttling executor instance.
   *
   * @param executor    the wrapped instance.
   * @param maxCommands the maximum number of running commands.
   * @return the executor instance.
   * @throws IllegalArgumentException if the specified max number is less than 1.
   */
  @NotNull
  static ThrottlingExecutor of(@NotNull final ScheduledExecutor executor, final int maxCommands) {
    return new ThrottlingExecutor(executor, maxCommands);
  }

  @Override
  public void execute(@NotNull final Runnable command) {
    final ThrottlingCommand throttlingCommand;
    synchronized (mMutex) {
      final DoubleQueue<PendingCommand> queue = mQueue;
      if ((mRunningCount + queue.size()) >= mMaxRunning) {
        queue.add(new PendingCommand(command, 0, TimeUnit.MILLISECONDS));
        return;
      }

      throttlingCommand = getThrottlingCommand(command);
    }

    super.execute(throttlingCommand);
  }

  @Override
  public void execute(@NotNull final Runnable command, final long delay,
      @NotNull final TimeUnit timeUnit) {
    final ThrottlingCommand throttlingCommand;
    synchronized (mMutex) {
      final DoubleQueue<PendingCommand> queue = mQueue;
      if ((mRunningCount + queue.size()) >= mMaxRunning) {
        queue.add(new PendingCommand(command, delay, timeUnit));
        return;
      }

      throttlingCommand = getThrottlingCommand(command);
    }

    super.execute(throttlingCommand, delay, timeUnit);
  }

  @NotNull
  private ThrottlingCommand getThrottlingCommand(@NotNull final Runnable command) {
    final WeakIdentityHashMap<Runnable, WeakReference<ThrottlingCommand>> commands = mCommands;
    final WeakReference<ThrottlingCommand> commandReference = commands.get(command);
    ThrottlingCommand throttlingCommand =
        (commandReference != null) ? commandReference.get() : null;
    if (throttlingCommand == null) {
      throttlingCommand = new ThrottlingCommand(command);
      commands.put(command, new WeakReference<ThrottlingCommand>(throttlingCommand));
    }

    return throttlingCommand;
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new ExecutorProxy(mExecutor, mMaxRunning);
  }

  private static class ExecutorProxy implements Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final ScheduledExecutor mExecutor;

    private final int mMaxCommands;

    private ExecutorProxy(@NotNull final ScheduledExecutor executor, final int maxCommands) {
      mExecutor = executor;
      mMaxCommands = maxCommands;
    }

    @NotNull
    private Object readResolve() throws ObjectStreamException {
      try {
        return new ThrottlingExecutor(mExecutor, mMaxCommands);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }

  /**
   * Pending command implementation.
   */
  private class PendingCommand implements Runnable {

    private final Runnable mCommand;

    private final long mDelay;

    private final long mStartTimeMillis;

    private final TimeUnit mTimeUnit;

    /**
     * Constructor.
     *
     * @param command  the command.
     * @param delay    the command delay.
     * @param timeUnit the delay time unit.
     */
    private PendingCommand(@NotNull final Runnable command, final long delay,
        @NotNull final TimeUnit timeUnit) {
      mCommand = command;
      mDelay = delay;
      mTimeUnit = timeUnit;
      mStartTimeMillis = System.currentTimeMillis();
    }

    public void run() {
      final ThrottlingCommand throttlingCommand;
      synchronized (mMutex) {
        throttlingCommand = getThrottlingCommand(mCommand);
      }

      final long delay = mDelay;
      final long currentDelay = (delay == 0) ? 0
          : Math.max(mTimeUnit.toMillis(delay) + mStartTimeMillis - System.currentTimeMillis(), 0);
      if (currentDelay == 0) {
        ThrottlingExecutor.super.execute(throttlingCommand);

      } else {
        ThrottlingExecutor.super.execute(throttlingCommand, currentDelay, TimeUnit.MILLISECONDS);
      }
    }
  }

  /**
   * Runnable used to dequeue and run pending commands, when the maximum running count allows
   * it.
   */
  private class ThrottlingCommand implements Runnable {

    private final Runnable mCommand;

    /**
     * Constructor.
     *
     * @param command the command.
     */
    private ThrottlingCommand(@NotNull final Runnable command) {
      mCommand = command;
    }

    public void run() {
      final Runnable command = mCommand;
      final DoubleQueue<PendingCommand> queue = mQueue;
      synchronized (mMutex) {
        if (mRunningCount >= mMaxRunning) {
          queue.addFirst(new PendingCommand(command, 0, TimeUnit.MILLISECONDS));
          return;
        }

        ++mRunningCount;
      }

      try {
        command.run();

      } finally {
        PendingCommand pendingCommand = null;
        synchronized (mMutex) {
          --mRunningCount;
          if (!queue.isEmpty()) {
            pendingCommand = queue.removeFirst();
          }
        }

        if (pendingCommand != null) {
          pendingCommand.run();
        }
      }
    }
  }
}

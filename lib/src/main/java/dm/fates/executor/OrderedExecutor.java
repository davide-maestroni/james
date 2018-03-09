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

import java.io.Serializable;
import java.lang.ref.WeakReference;

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
class OrderedExecutor implements EvaluationExecutor, ExecutorDecorator, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final WeakIdentityHashMap<Runnable, WeakReference<OrderedCommand>> mCommands =
      new WeakIdentityHashMap<Runnable, WeakReference<OrderedCommand>>();

  private final EvaluationExecutor mExecutor;

  private final Object mMutex = new Object();

  private final DoubleQueue<Runnable> mQueue = new DoubleQueue<Runnable>();

  private boolean mIsPending;

  /**
   * Constructor.
   *
   * @param executor the wrapped instance.
   */
  private OrderedExecutor(@NotNull final EvaluationExecutor executor) {
    mExecutor = ConstantConditions.notNull("executor", executor);
  }

  /**
   * Returns a new throttling executor instance.
   *
   * @param executor the wrapped instance.
   * @return the executor instance.
   */
  @NotNull
  static OrderedExecutor of(@NotNull final EvaluationExecutor executor) {
    return new OrderedExecutor(executor);
  }

  public void execute(@NotNull final Runnable command) {
    final OrderedCommand orderedCommand;
    synchronized (mMutex) {
      if (mIsPending) {
        mQueue.add(command);
        return;
      }

      mIsPending = true;
      orderedCommand = getOrderedCommand(command);
    }

    mExecutor.execute(orderedCommand);
  }

  @NotNull
  public EvaluationExecutor getDecorated() {
    return mExecutor;
  }

  public boolean isOwnedThread() {
    return mExecutor.isOwnedThread();
  }

  @NotNull
  private OrderedCommand getOrderedCommand(@NotNull final Runnable command) {
    final WeakIdentityHashMap<Runnable, WeakReference<OrderedCommand>> commands = mCommands;
    final WeakReference<OrderedCommand> commandReference = commands.get(command);
    OrderedCommand orderedCommand = (commandReference != null) ? commandReference.get() : null;
    if (orderedCommand == null) {
      orderedCommand = new OrderedCommand(command);
      commands.put(command, new WeakReference<OrderedCommand>(orderedCommand));
    }

    return orderedCommand;
  }

  /**
   * Runnable used to dequeue and run pending commands, when the maximum running count allows
   * it.
   */
  private class OrderedCommand implements Runnable {

    private final Runnable mCommand;

    /**
     * Constructor.
     *
     * @param command the command.
     */
    private OrderedCommand(@NotNull final Runnable command) {
      mCommand = command;
    }

    public void run() {
      try {
        mCommand.run();

      } finally {
        OrderedCommand orderedCommand = null;
        synchronized (mMutex) {
          final DoubleQueue<Runnable> queue = mQueue;
          if (!queue.isEmpty()) {
            orderedCommand = getOrderedCommand(queue.removeFirst());

          } else {
            mIsPending = false;
          }
        }

        if (orderedCommand != null) {
          mExecutor.execute(orderedCommand);
        }
      }
    }
  }
}

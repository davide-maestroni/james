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

package dm.jale.executor;

import org.jetbrains.annotations.NotNull;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import dm.jale.config.BuildConfig;
import dm.jale.util.SerializableProxy;

/**
 * Scheduled thread pool executor implementing a dynamic allocation of threads.
 * <br>
 * When the number of running threads reaches the maximum pool size, further commands are queued
 * for later execution.
 * <p>
 * Created by davide-maestroni on 01/23/2015.
 */
class DynamicScheduledThreadPoolExecutorService extends ScheduledThreadPoolExecutorService {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final int mCorePoolSize;

  private final long mKeepAliveTime;

  private final TimeUnit mKeepAliveUnit;

  private final int mMaxPoolSize;

  private final ThreadFactory mThreadFactory;

  /**
   * Constructor.
   *
   * @param corePoolSize    the number of threads to keep in the pool, even if they are idle.
   * @param maximumPoolSize the maximum number of threads to allow in the pool.
   * @param keepAliveTime   when the number of threads is greater than the core, this is the
   *                        maximum time that excess idle threads will wait for new tasks before
   *                        terminating.
   * @param keepAliveUnit   the time unit for the keep alive time.
   * @throws IllegalArgumentException if one of the following holds:<ul>
   *                                  <li>{@code corePoolSize < 0}</li>
   *                                  <li>{@code maximumPoolSize <= 0}</li>
   *                                  <li>{@code keepAliveTime < 0}</li></ul>
   */
  DynamicScheduledThreadPoolExecutorService(final int corePoolSize, final int maximumPoolSize,
      final long keepAliveTime, @NotNull final TimeUnit keepAliveUnit) {
    super(new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, keepAliveUnit,
        new NonRejectingQueue()));
    mCorePoolSize = corePoolSize;
    mMaxPoolSize = maximumPoolSize;
    mKeepAliveTime = keepAliveTime;
    mKeepAliveUnit = keepAliveUnit;
    mThreadFactory = null;
  }

  /**
   * Constructor.
   *
   * @param corePoolSize    the number of threads to keep in the pool, even if they are idle.
   * @param maximumPoolSize the maximum number of threads to allow in the pool.
   * @param keepAliveTime   when the number of threads is greater than the core, this is the
   *                        maximum time that excess idle threads will wait for new tasks before
   *                        terminating.
   * @param keepAliveUnit   the time unit for the keep alive time.
   * @param threadFactory   the thread factory.
   * @throws IllegalArgumentException if one of the following holds:<ul>
   *                                  <li>{@code corePoolSize < 0}</li>
   *                                  <li>{@code maximumPoolSize <= 0}</li>
   *                                  <li>{@code keepAliveTime < 0}</li></ul>
   */
  DynamicScheduledThreadPoolExecutorService(final int corePoolSize, final int maximumPoolSize,
      final long keepAliveTime, @NotNull final TimeUnit keepAliveUnit,
      @NotNull final ThreadFactory threadFactory) {
    super(new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, keepAliveUnit,
        new NonRejectingQueue(), threadFactory));
    mCorePoolSize = corePoolSize;
    mMaxPoolSize = maximumPoolSize;
    mKeepAliveTime = keepAliveTime;
    mKeepAliveUnit = keepAliveUnit;
    mThreadFactory = threadFactory;
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new ExecutorProxy(mCorePoolSize, mMaxPoolSize, mKeepAliveTime, mKeepAliveUnit,
        mThreadFactory);
  }

  private static class ExecutorProxy extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private ExecutorProxy(final int corePoolSize, final int maximumPoolSize,
        final long keepAliveTime, final TimeUnit keepAliveUnit, final ThreadFactory threadFactory) {
      super(corePoolSize, maximumPoolSize, keepAliveTime, keepAliveUnit, proxy(threadFactory));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new DynamicScheduledThreadPoolExecutorService((Integer) args[0], (Integer) args[1],
            (Long) args[2], (TimeUnit) args[3], (ThreadFactory) args[4]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }

  /**
   * Implementation of a synchronous queue, which avoids rejection of tasks by forcedly waiting
   * for available threads.
   */
  private static class NonRejectingQueue extends SynchronousQueue<Runnable> {

    // Just don't care...
    private static final long serialVersionUID = -1;

    @Override
    public boolean offer(@NotNull final Runnable runnable) {
      try {
        put(runnable);

      } catch (final InterruptedException ignored) {
        return false;
      }

      return true;
    }
  }
}

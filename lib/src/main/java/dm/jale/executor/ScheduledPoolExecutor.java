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

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import dm.jale.config.BuildConfig;

/**
 * Created by davide-maestroni on 02/08/2018.
 */
class ScheduledPoolExecutor extends ScheduledExecutorDecorator implements Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final int mPoolSize;

  private final ThreadFactory mThreadFactory;

  ScheduledPoolExecutor() {
    super(OwnerScheduledExecutorServiceWrapper.of(Executors.newScheduledThreadPool(getPoolSize())));
    mPoolSize = Integer.MIN_VALUE;
    mThreadFactory = null;
  }

  ScheduledPoolExecutor(final int corePoolSize) {
    super(OwnerScheduledExecutorServiceWrapper.of(Executors.newScheduledThreadPool(corePoolSize)));
    mPoolSize = corePoolSize;
    mThreadFactory = null;
  }

  ScheduledPoolExecutor(@NotNull final ThreadFactory threadFactory) {
    super(OwnerScheduledExecutorServiceWrapper.of(
        Executors.newScheduledThreadPool(getPoolSize(), threadFactory)));
    mPoolSize = Integer.MIN_VALUE;
    mThreadFactory = threadFactory;
  }

  ScheduledPoolExecutor(final int corePoolSize, @NotNull final ThreadFactory threadFactory) {
    super(OwnerScheduledExecutorServiceWrapper.of(
        Executors.newScheduledThreadPool(corePoolSize, threadFactory)));
    mPoolSize = corePoolSize;
    mThreadFactory = threadFactory;
  }

  private static int getPoolSize() {
    return (Runtime.getRuntime().availableProcessors() << 1) - 1;
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new ExecutorProxy(mPoolSize, mThreadFactory);
  }

  private static class ExecutorProxy implements Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final int mPoolSize;

    private final ThreadFactory mThreadFactory;

    private ExecutorProxy(final int poolSize, final ThreadFactory threadFactory) {
      mPoolSize = poolSize;
      mThreadFactory = threadFactory;
    }

    @NotNull
    Object readResolve() throws ObjectStreamException {
      final int poolSize = mPoolSize;
      final ThreadFactory threadFactory = mThreadFactory;
      if (poolSize == Integer.MIN_VALUE) {
        if (threadFactory == null) {
          return new ScheduledPoolExecutor();
        }

        return new ScheduledPoolExecutor(threadFactory);

      } else if (threadFactory == null) {
        return new ScheduledPoolExecutor(poolSize);
      }

      return new ScheduledPoolExecutor(poolSize, threadFactory);
    }
  }
}

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

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import dm.jail.config.BuildConfig;

/**
 * Created by davide-maestroni on 02/08/2018.
 */
class PoolExecutor implements StoppableExecutor, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final OwnerExecutorServiceWrapper mExecutor;

  private final int mPoolSize;

  private final ThreadFactory mThreadFactory;

  PoolExecutor() {
    mExecutor = OwnerExecutorServiceWrapper.of(Executors.newCachedThreadPool());
    mPoolSize = Integer.MIN_VALUE;
    mThreadFactory = null;
  }

  PoolExecutor(final int corePoolSize) {
    mExecutor = OwnerExecutorServiceWrapper.of(Executors.newFixedThreadPool(corePoolSize));
    mPoolSize = corePoolSize;
    mThreadFactory = null;
  }

  PoolExecutor(@NotNull final ThreadFactory threadFactory) {
    mExecutor = OwnerExecutorServiceWrapper.of(Executors.newCachedThreadPool(threadFactory));
    mPoolSize = Integer.MIN_VALUE;
    mThreadFactory = threadFactory;
  }

  PoolExecutor(final int corePoolSize, @NotNull final ThreadFactory threadFactory) {
    mExecutor =
        OwnerExecutorServiceWrapper.of(Executors.newFixedThreadPool(corePoolSize, threadFactory));
    mPoolSize = corePoolSize;
    mThreadFactory = threadFactory;
  }

  public void execute(@NotNull final Runnable runnable) {
    mExecutor.execute(runnable);
  }

  public boolean isOwnedThread() {
    return mExecutor.isOwnedThread();
  }

  public void stop() {
    mExecutor.stop();
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
          return new PoolExecutor();
        }

        return new PoolExecutor(threadFactory);

      } else if (threadFactory == null) {
        return new PoolExecutor(poolSize);
      }

      return new PoolExecutor(poolSize, threadFactory);
    }
  }
}

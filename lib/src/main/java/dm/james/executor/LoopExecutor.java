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

package dm.james.executor;

import org.jetbrains.annotations.NotNull;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.concurrent.TimeUnit;

/**
 * Class implementing a synchronous loop executor.
 * <p>
 * The executor maintains an internal buffer of commands that are consumed only when the last one
 * completes, thus avoiding overflowing the call stack because of nested calls to other routines.
 * <p>
 * Created by davide-maestroni on 09/18/2014.
 */
class LoopExecutor extends SyncExecutor implements Serializable {

  private static final LoopExecutor sInstance = new LoopExecutor();

  /**
   * Avoid explicit instantiation.
   */
  private LoopExecutor() {
  }

  @NotNull
  static LoopExecutor instance() {
    return sInstance;
  }

  @Override
  public void cancel(@NotNull final Runnable command) {
    LocalExecutor.cancel(command);
  }

  @Override
  public void execute(@NotNull final Runnable command) {
    LocalExecutor.run(command);
  }

  @Override
  public void execute(@NotNull final Runnable command, final long delay,
      @NotNull final TimeUnit timeUnit) {
    LocalExecutor.run(command, delay, timeUnit);
  }

  Object readResolve() throws ObjectStreamException {
    return sInstance;
  }
}

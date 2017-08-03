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

package dm.james.handler;

import org.jetbrains.annotations.NotNull;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;

import dm.james.executor.ScheduledExecutor;
import dm.james.promise.Promise.Callback;
import dm.james.promise.Promise.Handler;
import dm.james.util.ConstantConditions;
import dm.james.util.SerializableProxy;

/**
 * Created by davide-maestroni on 07/21/2017.
 */
public class ScheduleHandler<I> implements Handler<I, I>, Serializable {

  // TODO: 21/07/2017 delay, backoff to ScheduledExecutors

  private final ScheduledExecutor mExecutor;

  ScheduleHandler(@NotNull final ScheduledExecutor executor) {
    mExecutor = ConstantConditions.notNull("executor", executor);
  }

  public void reject(final Throwable reason, @NotNull final Callback<I> callback) {
    mExecutor.execute(new Runnable() {

      public void run() {
        callback.reject(reason);
      }
    });
  }

  public void resolve(final I input, @NotNull final Callback<I> callback) {
    mExecutor.execute(new Runnable() {

      public void run() {
        callback.resolve(input);
      }
    });
  }

  private Object writeReplace() throws ObjectStreamException {
    return new HandlerProxy(mExecutor);
  }

  private static class HandlerProxy<I> extends SerializableProxy {

    private HandlerProxy(final ScheduledExecutor executor) {
      super(executor);
    }

    @SuppressWarnings("unchecked")
    Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new ScheduleHandler<I>((ScheduledExecutor) args[0]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

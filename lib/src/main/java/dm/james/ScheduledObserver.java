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

package dm.james;

import org.jetbrains.annotations.NotNull;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;

import dm.james.executor.ScheduledExecutor;
import dm.james.promise.Observer;
import dm.james.promise.Promise.Callback;
import dm.james.util.ConstantConditions;
import dm.james.util.InterruptedExecutionException;
import dm.james.util.SerializableProxy;

/**
 * Created by davide-maestroni on 08/06/2017.
 */
class ScheduledObserver<O> implements Observer<Callback<O>>, Serializable {

  private final ScheduledExecutor mExecutor;

  private final Observer<? super Callback<O>> mObserver;

  ScheduledObserver(@NotNull final ScheduledExecutor executor,
      @NotNull final Observer<? super Callback<O>> observer) {
    mExecutor = ConstantConditions.notNull("executor", executor);
    mObserver = ConstantConditions.notNull("observer", observer);
  }

  public void accept(final Callback<O> callback) throws Exception {
    mExecutor.execute(new Runnable() {

      public void run() {
        try {
          mObserver.accept(callback);

        } catch (final Throwable t) {
          InterruptedExecutionException.throwIfInterrupt(t);
          callback.reject(t);
        }
      }
    });
  }

  private Object writeReplace() throws ObjectStreamException {
    return new ObserverProxy<O>(mExecutor, mObserver);
  }

  private static class ObserverProxy<O> extends SerializableProxy {

    private ObserverProxy(final ScheduledExecutor executor,
        final Observer<? super Callback<O>> observer) {
      super(executor, proxy(observer));
    }

    @SuppressWarnings("unchecked")
    Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new ScheduledObserver<O>((ScheduledExecutor) args[0],
            (Observer<? super Callback<O>>) args[1]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

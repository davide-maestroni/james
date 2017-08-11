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
import java.util.concurrent.Callable;

import dm.james.promise.Observer;
import dm.james.promise.Promise.Callback;
import dm.james.util.ConstantConditions;
import dm.james.util.SerializableProxy;

/**
 * Created by davide-maestroni on 08/07/2017.
 */
class CallableObserver<O> implements Observer<Callback<O>>, Serializable {

  private final Callable<O> mCallable;

  CallableObserver(@NotNull final Callable<O> callable) {
    mCallable = ConstantConditions.notNull("callable", callable);
  }

  public void accept(final Callback<O> callback) throws Exception {
    callback.resolve(mCallable.call());
  }

  private Object writeReplace() throws ObjectStreamException {
    return new ObserverProxy<O>(mCallable);
  }

  private static class ObserverProxy<O> extends SerializableProxy {

    private ObserverProxy(final Callable<O> callable) {
      super(proxy(callable));
    }

    @SuppressWarnings("unchecked")
    Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new CallableObserver<O>((Callable<O>) args[0]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

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

package dm.jail;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;

import dm.jail.async.AsyncResultCollection;
import dm.jail.async.Observer;
import dm.jail.config.BuildConfig;
import dm.jail.util.ConstantConditions;
import dm.jail.util.Iterables;
import dm.jail.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/01/2018.
 */
class ThenDoLoopHandler<V, R> extends AsyncLoopHandler<V, R> implements Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Observer<? super V> mObserver;

  ThenDoLoopHandler(@NotNull final Observer<? super V> observer) {
    mObserver = ConstantConditions.notNull("observer", observer);
  }

  @Override
  void addValue(final V value, @NotNull final AsyncResultCollection<R> results) throws Exception {
    mObserver.accept(value);
    super.addValue(value, results);
  }

  @Override
  @SuppressWarnings("unchecked")
  void addValues(@Nullable final Iterable<? extends V> values,
      @NotNull final AsyncResultCollection<R> results) throws Exception {
    if (values == null) {
      return;
    }

    int index = 0;
    try {
      final Observer<? super V> observer = mObserver;
      for (final V value : values) {
        observer.accept(value);
        ++index;
      }

    } finally {
      results.addValues((Iterable<R>) Iterables.asList(values).subList(0, index)).set();
    }
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new HandlerProxy<V, R>(mObserver);
  }

  private static class HandlerProxy<V, R> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private HandlerProxy(final Observer<? super V> observer) {
      super(proxy(observer));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new ThenDoLoopHandler<V, R>((Observer<? super V>) args[0]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

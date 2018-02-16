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

package dm.jale;

import org.jetbrains.annotations.NotNull;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;

import dm.jale.async.Loop.YieldOutputs;
import dm.jale.async.Observer;
import dm.jale.config.BuildConfig;
import dm.jale.util.ConstantConditions;
import dm.jale.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/05/2018.
 */
class ElseDoYielder<V> extends CollectionYielder<V> implements Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Observer<? super Throwable> mObserver;

  private final Class<?>[] mTypes;

  ElseDoYielder(@NotNull final Observer<? super Throwable> observer,
      @NotNull final Class<?>[] exceptionTypes) {
    mObserver = ConstantConditions.notNull("observer", observer);
    mTypes = ConstantConditions.notNull("exception types", exceptionTypes);
    if (Arrays.asList(mTypes).contains(null)) {
      throw new NullPointerException("exception type array contains null values");
    }
  }

  @Override
  public ArrayList<V> failure(final ArrayList<V> stack, @NotNull final Throwable failure,
      @NotNull final YieldOutputs<V> outputs) throws Exception {
    for (final Class<?> type : mTypes) {
      if (type.isInstance(failure)) {
        mObserver.accept(failure);
        break;
      }
    }

    return super.failure(stack, failure, outputs);
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new HandlerProxy<V>(mObserver, mTypes);
  }

  private static class HandlerProxy<V> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private HandlerProxy(final Observer<? super Throwable> mapper,
        final Class<?>[] exceptionTypes) {
      super(proxy(mapper), exceptionTypes);
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new ElseDoYielder<V>((Observer<? super Throwable>) args[0], (Class<?>[]) args[1]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

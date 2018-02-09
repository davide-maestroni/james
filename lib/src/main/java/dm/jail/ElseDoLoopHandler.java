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
import java.util.Arrays;

import dm.jail.async.AsyncEvaluations;
import dm.jail.async.Observer;
import dm.jail.config.BuildConfig;
import dm.jail.util.ConstantConditions;
import dm.jail.util.Iterables;
import dm.jail.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/01/2018.
 */
class ElseDoLoopHandler<V> extends AsyncLoopHandler<V, V> implements Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Observer<? super Throwable> mObserver;

  private final Class<?>[] mTypes;

  ElseDoLoopHandler(@NotNull final Observer<? super Throwable> observer,
      @NotNull final Class<?>[] exceptionTypes) {
    mObserver = ConstantConditions.notNull("observer", observer);
    mTypes = ConstantConditions.notNull("exception types", exceptionTypes);
    if (Arrays.asList(mTypes).contains(null)) {
      throw new NullPointerException("exception type array contains null values");
    }
  }

  @Override
  void addFailure(@NotNull final Throwable failure,
      @NotNull final AsyncEvaluations<V> evaluations) throws Exception {
    for (final Class<?> type : mTypes) {
      if (type.isInstance(failure)) {
        mObserver.accept(failure);
        break;
      }
    }

    super.addFailure(failure, evaluations);
  }

  @Override
  void addFailures(@Nullable final Iterable<? extends Throwable> failures,
      @NotNull final AsyncEvaluations<V> evaluations) throws Exception {
    if (failures == null) {
      return;
    }

    int index = 0;
    try {
      @SuppressWarnings("UnnecessaryLocalVariable") final Observer<? super Throwable> observer =
          mObserver;
      for (final Throwable failure : failures) {
        for (final Class<?> type : mTypes) {
          if (type.isInstance(failure)) {
            observer.accept(failure);
            break;
          }
        }

        ++index;
      }

    } finally {
      evaluations.addFailures(Iterables.asList(failures).subList(0, index)).set();
    }
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new HandlerProxy<V>(mObserver, mTypes);
  }

  private static class HandlerProxy<V> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private HandlerProxy(final Observer<? super Throwable> observer,
        final Class<?>[] exceptionTypes) {
      super(proxy(observer), exceptionTypes);
    }

    @NotNull
    @SuppressWarnings("unchecked")
    Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new ElseDoLoopHandler<V>((Observer<? super Throwable>) args[0],
            (Class<?>[]) args[1]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

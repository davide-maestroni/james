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

import dm.jale.config.BuildConfig;
import dm.jale.eventual.Evaluation;
import dm.jale.eventual.Observer;
import dm.jale.util.ConstantConditions;
import dm.jale.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/01/2018.
 */
class ElseDoStatementExpression<V> extends StatementExpression<V, V> implements Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Observer<? super Throwable> mObserver;

  private final Class<?>[] mTypes;

  ElseDoStatementExpression(@NotNull final Observer<? super Throwable> observer,
      @NotNull final Class<?>[] exceptionTypes) {
    mObserver = ConstantConditions.notNull("observer", observer);
    mTypes = ConstantConditions.notNullElements("exception types", exceptionTypes);
  }

  @Override
  void failure(@NotNull final Throwable failure, @NotNull final Evaluation<V> evaluation) throws
      Exception {
    for (final Class<?> type : mTypes) {
      if (type.isInstance(failure)) {
        mObserver.accept(failure);
        break;
      }
    }

    super.failure(failure, evaluation);
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
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new ElseDoStatementExpression<V>((Observer<? super Throwable>) args[0],
            (Class<?>[]) args[1]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

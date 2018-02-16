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
import org.jetbrains.annotations.Nullable;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.Arrays;

import dm.jale.async.AsyncEvaluations;
import dm.jale.async.AsyncLoop;
import dm.jale.async.Mapper;
import dm.jale.config.BuildConfig;
import dm.jale.util.ConstantConditions;
import dm.jale.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/01/2018.
 */
class ElseLoopIfStatementHandler<V> extends AsyncStatementLoopHandler<V, V>
    implements Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Mapper<? super Throwable, ? extends AsyncLoop<? extends V>> mMapper;

  private final Class<?>[] mTypes;

  ElseLoopIfStatementHandler(
      @NotNull final Mapper<? super Throwable, ? extends AsyncLoop<? extends V>> mapper,
      @Nullable final Class<?>[] exceptionTypes) {
    mMapper = ConstantConditions.notNull("mapper", mapper);
    mTypes = ConstantConditions.notNull("exception types", exceptionTypes);
    if (Arrays.asList(mTypes).contains(null)) {
      throw new NullPointerException("exception type array contains null values");
    }
  }

  @Override
  void failure(@NotNull final Throwable failure,
      @NotNull final AsyncEvaluations<V> evaluations) throws Exception {
    for (final Class<?> type : mTypes) {
      if (type.isInstance(failure)) {
        mMapper.apply(failure).to(evaluations);
        return;
      }
    }

    super.failure(failure, evaluations);
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new HandlerProxy<V>(mMapper, mTypes);
  }

  private static class HandlerProxy<V> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private HandlerProxy(final Mapper<? super Throwable, ? extends AsyncLoop<? extends V>> mapper,
        final Class<?>[] exceptionTypes) {
      super(proxy(mapper), exceptionTypes);
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new ElseLoopIfStatementHandler<V>(
            (Mapper<? super Throwable, ? extends AsyncLoop<? extends V>>) args[0],
            (Class<?>[]) args[1]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

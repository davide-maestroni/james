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

import dm.jale.async.Evaluation;
import dm.jale.async.Mapper;
import dm.jale.async.Statement;
import dm.jale.config.BuildConfig;
import dm.jale.util.ConstantConditions;
import dm.jale.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/01/2018.
 */
class ThenIfStatementHandler<V, R> extends AsyncStatementHandler<V, R> implements Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Mapper<? super V, ? extends Statement<R>> mMapper;

  ThenIfStatementHandler(@NotNull final Mapper<? super V, ? extends Statement<R>> mapper) {
    mMapper = ConstantConditions.notNull("mapper", mapper);
  }

  @Override
  void value(final V value, @NotNull final Evaluation<R> evaluation) throws Exception {
    mMapper.apply(value).to(evaluation);
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new HandlerProxy<V, R>(mMapper);
  }

  private static class HandlerProxy<V, R> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private HandlerProxy(final Mapper<? super V, ? extends Statement<R>> mapper) {
      super(proxy(mapper));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new ThenIfStatementHandler<V, R>(
            (Mapper<? super V, ? extends Statement<R>>) args[0]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

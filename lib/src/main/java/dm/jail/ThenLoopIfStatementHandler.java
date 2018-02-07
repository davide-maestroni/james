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

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;

import dm.jail.async.AsyncLoop;
import dm.jail.async.AsyncResults;
import dm.jail.async.Mapper;
import dm.jail.config.BuildConfig;
import dm.jail.util.ConstantConditions;
import dm.jail.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/01/2018.
 */
class ThenLoopIfStatementHandler<V, R> extends AsyncStatementLoopHandler<V, R>
    implements Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Mapper<? super V, ? extends AsyncLoop<R>> mMapper;

  ThenLoopIfStatementHandler(@NotNull final Mapper<? super V, ? extends AsyncLoop<R>> mapper) {
    mMapper = ConstantConditions.notNull("mapper", mapper);
  }

  @Override
  void value(final V value, @NotNull final AsyncResults<R> results) throws Exception {
    mMapper.apply(value).to(results);
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new HandlerProxy<V, R>(mMapper);
  }

  private static class HandlerProxy<V, R> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private HandlerProxy(final Mapper<? super V, ? extends AsyncLoop<R>> mapper) {
      super(proxy(mapper));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new ThenLoopIfStatementHandler<V, R>(
            (Mapper<? super V, ? extends AsyncLoop<R>>) args[0]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

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

package dm.jale.ext.yield;

import org.jetbrains.annotations.NotNull;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;

import dm.jale.eventual.Loop.YieldOutputs;
import dm.jale.eventual.LoopYielder;
import dm.jale.ext.config.BuildConfig;
import dm.jale.ext.eventual.BiMapper;
import dm.jale.util.ConstantConditions;
import dm.jale.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/27/2018.
 */
class OnValueYielder<V, R> implements LoopYielder<Boolean, V, R>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final BiMapper<? super V, ? super YieldOutputs<R>, Boolean> mMapper;

  OnValueYielder(@NotNull final BiMapper<? super V, ? super YieldOutputs<R>, Boolean> mapper) {
    mMapper = ConstantConditions.notNull("mapper", mapper);
  }

  public void done(final Boolean stack, @NotNull final YieldOutputs<R> outputs) {
  }

  public Boolean failure(final Boolean stack, @NotNull final Throwable failure,
      @NotNull final YieldOutputs<R> outputs) {
    outputs.yieldFailure(failure);
    return stack;
  }

  public Boolean init() {
    return Boolean.TRUE;
  }

  public boolean loop(final Boolean stack) {
    return stack;
  }

  public Boolean value(final Boolean stack, final V value,
      @NotNull final YieldOutputs<R> outputs) throws Exception {
    return mMapper.apply(value, outputs);
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new YielderProxy<V, R>(mMapper);
  }

  private static class YielderProxy<V, R> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private YielderProxy(final BiMapper<? super V, ? super YieldOutputs<R>, Boolean> mapper) {
      super(proxy(mapper));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new OnValueYielder<V, R>(
            (BiMapper<? super V, ? super YieldOutputs<R>, Boolean>) args[0]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

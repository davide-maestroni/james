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

package dm.jale.ext;

import org.jetbrains.annotations.NotNull;

import java.io.ObjectStreamException;
import java.io.Serializable;

import dm.jale.eventual.Loop.YieldOutputs;
import dm.jale.eventual.Loop.Yielder;
import dm.jale.ext.config.BuildConfig;
import dm.jale.log.Logger;

/**
 * Created by davide-maestroni on 02/27/2018.
 */
class StopErrorBackPropagationYielder<V> implements Yielder<Boolean, V, V>, Serializable {

  private static final StopErrorBackPropagationYielder<?> sInstance =
      new StopErrorBackPropagationYielder<Object>();

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Logger mLogger;

  private StopErrorBackPropagationYielder() {
    mLogger = Logger.newLogger(this);
  }

  @NotNull
  @SuppressWarnings("unchecked")
  static <V> StopErrorBackPropagationYielder<V> instance() {
    return (StopErrorBackPropagationYielder<V>) sInstance;
  }

  public void done(final Boolean stack, @NotNull final YieldOutputs<V> outputs) {
  }

  public Boolean failure(final Boolean stack, @NotNull final Throwable failure,
      @NotNull final YieldOutputs<V> outputs) {
    try {
      outputs.yieldFailure(failure);

    } catch (final Throwable t) {
      mLogger.dbg(t, "Suppressed back propagated failure");
      return Boolean.FALSE;
    }

    return stack;
  }

  public Boolean init() {
    return Boolean.TRUE;
  }

  public boolean loop(final Boolean stack) throws Exception {
    return stack;
  }

  public Boolean value(final Boolean stack, final V value, @NotNull final YieldOutputs<V> outputs) {
    try {
      outputs.yieldValue(value);

    } catch (final Throwable t) {
      mLogger.dbg(t, "Suppressed back propagated failure");
      return Boolean.FALSE;
    }

    return stack;
  }

  @NotNull
  private Object readResolve() throws ObjectStreamException {
    return sInstance;
  }
}

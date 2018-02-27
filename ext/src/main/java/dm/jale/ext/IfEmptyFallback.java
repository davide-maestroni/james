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

import java.io.Serializable;

import dm.jale.eventual.Loop;
import dm.jale.eventual.Loop.YieldOutputs;
import dm.jale.eventual.Loop.Yielder;
import dm.jale.util.ConstantConditions;

/**
 * Created by davide-maestroni on 02/27/2018.
 */
class IfEmptyFallback<V> implements Yielder<Boolean, V, V>, Serializable {

  private final Loop<? extends V> mLoop;

  IfEmptyFallback(@NotNull final Loop<? extends V> fallbackLoop) {
    mLoop = ConstantConditions.notNull("fallbackLoop", fallbackLoop);
  }

  public void done(final Boolean stack, @NotNull final YieldOutputs<V> outputs) {
    if (stack) {
      outputs.yieldLoopIf(mLoop);
    }
  }

  public Boolean failure(final Boolean stack, @NotNull final Throwable failure,
      @NotNull final YieldOutputs<V> outputs) {
    outputs.yieldFailure(failure);
    return Boolean.FALSE;
  }

  public Boolean init() {
    return Boolean.TRUE;
  }

  public boolean loop(final Boolean stack) {
    return true;
  }

  public Boolean value(final Boolean stack, final V value, @NotNull final YieldOutputs<V> outputs) {
    outputs.yieldValue(value);
    return Boolean.FALSE;
  }
}

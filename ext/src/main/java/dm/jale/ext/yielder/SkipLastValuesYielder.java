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

package dm.jale.ext.yielder;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

import dm.jale.eventual.Loop.YieldOutputs;
import dm.jale.eventual.LoopYielder;
import dm.jale.ext.config.BuildConfig;
import dm.jale.util.ConstantConditions;
import dm.jale.util.DoubleQueue;

/**
 * Created by davide-maestroni on 02/27/2018.
 */
class SkipLastValuesYielder<V> implements LoopYielder<DoubleQueue<V>, V, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final int mMaxCount;

  SkipLastValuesYielder(final int maxCount) {
    mMaxCount = ConstantConditions.notNegative("maxCount", maxCount);
  }

  public void done(final DoubleQueue<V> stack, @NotNull final YieldOutputs<V> outputs) {
  }

  public DoubleQueue<V> failure(final DoubleQueue<V> stack, @NotNull final Throwable failure,
      @NotNull final YieldOutputs<V> outputs) {
    outputs.yieldFailure(failure);
    return stack;
  }

  public DoubleQueue<V> init() {
    return new DoubleQueue<V>();
  }

  public boolean loop(final DoubleQueue<V> stack) {
    return true;
  }

  public DoubleQueue<V> value(final DoubleQueue<V> stack, final V value,
      @NotNull final YieldOutputs<V> outputs) {
    stack.add(value);
    return flush(stack, outputs);
  }

  @NotNull
  private DoubleQueue<V> flush(@NotNull final DoubleQueue<V> stack,
      final @NotNull YieldOutputs<V> outputs) {
    while (stack.size() > mMaxCount) {
      outputs.yieldValue(stack.removeFirst());
    }

    return stack;
  }
}

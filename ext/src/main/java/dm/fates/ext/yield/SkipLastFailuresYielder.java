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

package dm.fates.ext.yield;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

import dm.fates.eventual.Loop.YieldOutputs;
import dm.fates.eventual.LoopYielder;
import dm.fates.ext.config.BuildConfig;
import dm.fates.util.ConstantConditions;
import dm.fates.util.DoubleQueue;

/**
 * Created by davide-maestroni on 02/27/2018.
 */
class SkipLastFailuresYielder<V>
    implements LoopYielder<DoubleQueue<Throwable>, V, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final int mMaxCount;

  SkipLastFailuresYielder(final int maxCount) {
    mMaxCount = ConstantConditions.notNegative("maxCount", maxCount);
  }

  public void done(final DoubleQueue<Throwable> stack, @NotNull final YieldOutputs<V> outputs) {
  }

  public DoubleQueue<Throwable> failure(final DoubleQueue<Throwable> stack,
      @NotNull final Throwable failure, @NotNull final YieldOutputs<V> outputs) {
    stack.add(failure);
    return flush(stack, outputs);
  }

  public DoubleQueue<Throwable> init() {
    return new DoubleQueue<Throwable>();
  }

  public boolean loop(final DoubleQueue<Throwable> stack) {
    return true;
  }

  public DoubleQueue<Throwable> value(final DoubleQueue<Throwable> stack, final V value,
      @NotNull final YieldOutputs<V> outputs) {
    outputs.yieldValue(value);
    return stack;
  }

  @NotNull
  private DoubleQueue<Throwable> flush(@NotNull final DoubleQueue<Throwable> stack,
      final @NotNull YieldOutputs<V> outputs) {
    while (stack.size() > mMaxCount) {
      outputs.yieldFailure(stack.removeFirst());
    }

    return stack;
  }
}

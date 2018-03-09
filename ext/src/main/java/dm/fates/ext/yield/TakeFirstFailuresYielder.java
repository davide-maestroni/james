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
import dm.fates.ext.yield.TakeFirstFailuresYielder.YielderStack;
import dm.fates.util.ConstantConditions;

/**
 * Created by davide-maestroni on 02/27/2018.
 */
class TakeFirstFailuresYielder<V> implements LoopYielder<YielderStack, V, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final int mMaxCount;

  TakeFirstFailuresYielder(final int maxCount) {
    mMaxCount = ConstantConditions.notNegative("maxCount", maxCount);
  }

  public void done(final YielderStack stack, @NotNull final YieldOutputs<V> outputs) {
  }

  public YielderStack failure(final YielderStack stack, @NotNull final Throwable failure,
      @NotNull final YieldOutputs<V> outputs) {
    if (stack.count < mMaxCount) {
      outputs.yieldFailure(failure);
      ++stack.count;
    }

    return stack;
  }

  public YielderStack init() {
    return new YielderStack();
  }

  public boolean loop(final YielderStack stack) {
    return true;
  }

  public YielderStack value(final YielderStack stack, final V value,
      @NotNull final YieldOutputs<V> outputs) {
    outputs.yieldValue(value);
    return stack;
  }

  static class YielderStack {

    private int count;
  }
}

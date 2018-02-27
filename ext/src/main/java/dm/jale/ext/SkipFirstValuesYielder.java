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

import dm.jale.eventual.Loop.YieldOutputs;
import dm.jale.eventual.Loop.Yielder;
import dm.jale.ext.SkipFirstValuesYielder.YielderStack;
import dm.jale.ext.config.BuildConfig;
import dm.jale.util.ConstantConditions;

/**
 * Created by davide-maestroni on 02/27/2018.
 */
class SkipFirstValuesYielder<V> implements Yielder<YielderStack, V, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final int mCount;

  SkipFirstValuesYielder(final int count) {
    mCount = ConstantConditions.notNegative("count", count);
  }

  public void done(final YielderStack stack, @NotNull final YieldOutputs<V> outputs) {
  }

  public YielderStack failure(final YielderStack stack, @NotNull final Throwable failure,
      @NotNull final YieldOutputs<V> outputs) {
    outputs.yieldFailure(failure);
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
    if (stack.count < mCount) {
      outputs.yieldValue(value);
      ++stack.count;
    }

    return stack;
  }

  static class YielderStack {

    private int count;
  }
}

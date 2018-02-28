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
import dm.jale.eventual.Loop.Yielder;
import dm.jale.ext.config.BuildConfig;
import dm.jale.ext.yielder.ResizeValuesYielder.YielderStack;
import dm.jale.util.ConstantConditions;

/**
 * Created by davide-maestroni on 02/26/2018.
 */
class ResizeValuesYielder<V> implements Yielder<YielderStack, V, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final V mPadding;

  private final long mSize;

  ResizeValuesYielder(final long size, final V padding) {
    mSize = ConstantConditions.notNegative("size", size);
    mPadding = padding;
  }

  public void done(final YielderStack stack, @NotNull final YieldOutputs<V> outputs) {
    if (stack != null) {
      @SuppressWarnings("UnnecessaryLocalVariable") final V padding = mPadding;
      @SuppressWarnings("UnnecessaryLocalVariable") final long size = mSize;
      for (long i = stack.count; i < size; ++i) {
        outputs.yieldValue(padding);
      }
    }
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
    if (stack.count < mSize) {
      outputs.yieldValue(value);
      ++stack.count;
    }

    return stack;
  }

  static class YielderStack {

    private long count;
  }
}

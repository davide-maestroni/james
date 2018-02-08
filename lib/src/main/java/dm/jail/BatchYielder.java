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

import java.io.Serializable;
import java.util.ArrayList;

import dm.jail.async.AsyncLoop.YieldOutputs;
import dm.jail.async.AsyncLoop.Yielder;
import dm.jail.config.BuildConfig;
import dm.jail.util.ConstantConditions;

/**
 * Created by davide-maestroni on 02/04/2018.
 */
class BatchYielder<V> implements Yielder<ArrayList<V>, V, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final int mMaxBatch;

  private final int mMinBatch;

  BatchYielder(final int minBatch, final int maxBatch) {
    mMinBatch = ConstantConditions.positive("minBatch", minBatch);
    mMaxBatch = ConstantConditions.positive("maxBatch", maxBatch);
  }

  public void done(final ArrayList<V> stack, @NotNull final YieldOutputs<V> outputs) {
    outputs.yieldValues(stack);
  }

  public ArrayList<V> failure(final ArrayList<V> stack, @NotNull final Throwable failure,
      @NotNull final YieldOutputs<V> outputs) {
    flushValues(stack, outputs).yieldFailure(failure);
    return stack;
  }

  public ArrayList<V> init() {
    return new ArrayList<V>();
  }

  public boolean loop(final ArrayList<V> stack) {
    return true;
  }

  public ArrayList<V> value(final ArrayList<V> stack, final V value,
      @NotNull final YieldOutputs<V> outputs) {
    stack.add(value);
    if (stack.size() >= mMinBatch) {
      flushValues(stack, outputs);
    }

    return stack;
  }

  @NotNull
  private YieldOutputs<V> flushValues(@NotNull final ArrayList<V> values,
      @NotNull final YieldOutputs<V> outputs) {
    try {
      final int maxBatch = mMaxBatch;
      if (maxBatch == 1) {
        for (final V value : values) {
          outputs.yieldValue(value);
        }

      } else {
        int startOffset = 0;
        int endOffset = Math.min(values.size(), maxBatch);
        while (endOffset > startOffset) {
          outputs.yieldValues(new ArrayList<V>(values.subList(startOffset, endOffset)));
          startOffset = endOffset;
          endOffset = Math.min(values.size(), startOffset + maxBatch);
        }
      }

    } finally {
      values.clear();
    }

    return outputs;
  }
}

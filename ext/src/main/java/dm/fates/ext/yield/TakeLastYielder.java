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
import dm.fates.eventual.SimpleState;
import dm.fates.ext.config.BuildConfig;
import dm.fates.util.ConstantConditions;
import dm.fates.util.DoubleQueue;

/**
 * Created by davide-maestroni on 02/27/2018.
 */
class TakeLastYielder<V> implements LoopYielder<DoubleQueue<SimpleState<V>>, V, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final int mMaxCount;

  TakeLastYielder(final int maxCount) {
    mMaxCount = ConstantConditions.notNegative("maxCount", maxCount);
  }

  public void done(final DoubleQueue<SimpleState<V>> stack,
      @NotNull final YieldOutputs<V> outputs) {
    for (final SimpleState<V> state : stack) {
      if (state.isSet()) {
        outputs.yieldValue(state.value());

      } else {
        outputs.yieldFailure(state.failure());
      }
    }
  }

  public DoubleQueue<SimpleState<V>> failure(final DoubleQueue<SimpleState<V>> stack,
      @NotNull final Throwable failure, @NotNull final YieldOutputs<V> outputs) {
    stack.add(SimpleState.<V>ofFailure(failure));
    return flush(stack);
  }

  public DoubleQueue<SimpleState<V>> init() {
    return new DoubleQueue<SimpleState<V>>();
  }

  public boolean loop(final DoubleQueue<SimpleState<V>> stack) {
    return true;
  }

  public DoubleQueue<SimpleState<V>> value(final DoubleQueue<SimpleState<V>> stack, final V value,
      @NotNull final YieldOutputs<V> outputs) {
    stack.add(SimpleState.ofValue(value));
    return flush(stack);
  }

  @NotNull
  private DoubleQueue<SimpleState<V>> flush(@NotNull final DoubleQueue<SimpleState<V>> stack) {
    while (stack.size() > mMaxCount) {
      stack.removeFirst();
    }

    return stack;
  }
}

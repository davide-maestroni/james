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
import dm.jale.eventual.SimpleState;
import dm.jale.ext.config.BuildConfig;
import dm.jale.util.ConstantConditions;
import dm.jale.util.DoubleQueue;

/**
 * Created by davide-maestroni on 02/27/2018.
 */
class SkipLastYielder<V> implements Yielder<DoubleQueue<SimpleState<V>>, V, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final int mMaxCount;

  SkipLastYielder(final int maxCount) {
    mMaxCount = ConstantConditions.notNegative("maxCount", maxCount);
  }

  public void done(final DoubleQueue<SimpleState<V>> stack,
      @NotNull final YieldOutputs<V> outputs) {
  }

  public DoubleQueue<SimpleState<V>> failure(final DoubleQueue<SimpleState<V>> stack,
      @NotNull final Throwable failure, @NotNull final YieldOutputs<V> outputs) {
    stack.add(SimpleState.<V>ofFailure(failure));
    return flush(stack, outputs);
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
    return flush(stack, outputs);
  }

  @NotNull
  private DoubleQueue<SimpleState<V>> flush(@NotNull final DoubleQueue<SimpleState<V>> stack,
      final @NotNull YieldOutputs<V> outputs) {
    while (stack.size() > mMaxCount) {
      final SimpleState<V> state = stack.removeFirst();
      if (state.isSet()) {
        outputs.yieldValue(state.value());

      } else {
        outputs.yieldFailure(state.failure());
      }
    }

    return stack;
  }
}

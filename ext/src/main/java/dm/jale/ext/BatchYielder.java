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
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dm.jale.eventual.Loop.YieldOutputs;
import dm.jale.eventual.Loop.Yielder;
import dm.jale.ext.BatchYielder.YielderStack;
import dm.jale.ext.config.BuildConfig;
import dm.jale.util.ConstantConditions;

/**
 * Created by davide-maestroni on 02/19/2018.
 */
class BatchYielder<V> implements Yielder<YielderStack<V>, V, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final int mMaxFailures;

  private final int mMaxValues;

  BatchYielder(final int maxValues, final int maxFailures) {
    mMaxValues = ConstantConditions.positive("maxValues", maxValues);
    mMaxFailures = ConstantConditions.positive("maxFailures", maxFailures);
  }

  @Nullable
  private static <E> List<E> copyOrNull(@NotNull final List<E> list) {
    final int size = list.size();
    return (size == 0) ? null
        : (size == 1) ? Collections.singletonList(list.get(0)) : new ArrayList<E>(list);
  }

  public void done(final YielderStack<V> stack, @NotNull final YieldOutputs<V> outputs) {
    outputs.yieldFailures(stack.failures).yieldValues(stack.values);
  }

  public YielderStack<V> failure(final YielderStack<V> stack, @NotNull final Throwable failure,
      @NotNull final YieldOutputs<V> outputs) {
    final ArrayList<V> values = stack.values;
    final List<V> valueList = copyOrNull(values);
    values.clear();
    final ArrayList<Throwable> failures = stack.failures;
    failures.add(failure);
    final List<Throwable> failureList;
    if (failures.size() >= mMaxFailures) {
      failureList = copyOrNull(failures);
      failures.clear();

    } else {
      failureList = null;
    }

    outputs.yieldValues(valueList).yieldFailures(failureList);
    return stack;
  }

  public YielderStack<V> init() {
    return new YielderStack<V>();
  }

  public boolean loop(final YielderStack<V> stack) {
    return (stack != null);
  }

  public YielderStack<V> value(final YielderStack<V> stack, final V value,
      @NotNull final YieldOutputs<V> outputs) throws Exception {
    final ArrayList<Throwable> failures = stack.failures;
    final List<Throwable> failureList = copyOrNull(failures);
    failures.clear();
    final ArrayList<V> values = stack.values;
    values.add(value);
    final List<V> valueList;
    if (values.size() >= mMaxValues) {
      valueList = copyOrNull(values);
      values.clear();

    } else {
      valueList = null;
    }

    outputs.yieldFailures(failureList).yieldValues(valueList);
    return stack;
  }

  static class YielderStack<V> {

    private final ArrayList<Throwable> failures = new ArrayList<Throwable>();

    private final ArrayList<V> values = new ArrayList<V>();
  }
}

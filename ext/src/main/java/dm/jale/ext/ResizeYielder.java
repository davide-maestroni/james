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

import dm.jale.eventual.Evaluation;
import dm.jale.eventual.EvaluationState;
import dm.jale.eventual.Loop.YieldOutputs;
import dm.jale.eventual.Loop.Yielder;
import dm.jale.ext.ResizeYielder.YielderStack;
import dm.jale.ext.config.BuildConfig;
import dm.jale.util.ConstantConditions;

/**
 * Created by davide-maestroni on 02/26/2018.
 */
class ResizeYielder<V> implements Yielder<YielderStack, V, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final EvaluationState<V> mPadding;

  private final long mSize;

  ResizeYielder(final long size, @NotNull final EvaluationState<V> padding) {
    mSize = ConstantConditions.notNegative("size", size);
    mPadding = ConstantConditions.notNull("padding", padding);
  }

  public void done(final YielderStack stack, @NotNull final YieldOutputs<V> outputs) {
    if (stack != null) {
      @SuppressWarnings("UnnecessaryLocalVariable") final long size = mSize;
      @SuppressWarnings("UnnecessaryLocalVariable") final EvaluationState<V> padding = mPadding;
      final YieldEvaluation<V> evaluation = new YieldEvaluation<V>(outputs);
      for (long i = stack.count; i < size; ++i) {
        padding.to(evaluation);
      }
    }
  }

  public YielderStack failure(final YielderStack stack, @NotNull final Throwable failure,
      @NotNull final YieldOutputs<V> outputs) {
    final long size = mSize;
    if (stack.count < size) {
      outputs.yieldFailure(failure);
    }

    return (++stack.count < size) ? stack : null;
  }

  public YielderStack init() {
    return new YielderStack();
  }

  public boolean loop(final YielderStack stack) {
    return (stack != null);
  }

  public YielderStack value(final YielderStack stack, final V value,
      @NotNull final YieldOutputs<V> outputs) {
    final long size = mSize;
    if (stack.count < size) {
      outputs.yieldValue(value);
    }

    return (++stack.count < size) ? stack : null;
  }

  static class YielderStack {

    private long count;
  }

  private static class YieldEvaluation<V> implements Evaluation<V> {

    private final YieldOutputs<V> mOutputs;

    private YieldEvaluation(@NotNull final YieldOutputs<V> outputs) {
      mOutputs = outputs;
    }

    public void fail(@NotNull final Throwable failure) {
      mOutputs.yieldFailure(failure);
    }

    public void set(final V value) {
      mOutputs.yieldValue(value);
    }
  }
}

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

package dm.jale.ext.join;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.List;

import dm.jale.eventual.EvaluationCollection;
import dm.jale.eventual.Loop;
import dm.jale.eventual.LoopJoiner;
import dm.jale.ext.config.BuildConfig;

/**
 * Created by davide-maestroni on 02/16/2018.
 */
class FirstOnlyJoiner<V> implements LoopJoiner<Integer, V, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final boolean mMayInterruptIfRunning;

  FirstOnlyJoiner(final boolean mayInterruptIfRunning) {
    mMayInterruptIfRunning = mayInterruptIfRunning;
  }

  public Integer done(final Integer stack, @NotNull final EvaluationCollection<V> evaluation,
      @NotNull final List<Loop<V>> contexts, final int index) {
    if ((stack != null) && (stack == index)) {
      evaluation.set();
      return index;
    }

    return stack;
  }

  public Integer failure(final Integer stack, final Throwable failure,
      @NotNull final EvaluationCollection<V> evaluation, @NotNull final List<Loop<V>> contexts,
      final int index) {
    if (stack == null) {
      cancelOthers(contexts, index);
      evaluation.addFailure(failure);
      return index;

    } else if (stack == index) {
      evaluation.addFailure(failure);
      return stack;
    }

    return stack;
  }

  public Integer init(@NotNull final List<Loop<V>> contexts) {
    return null;
  }

  public void settle(final Integer stack, @NotNull final EvaluationCollection<V> evaluation,
      @NotNull final List<Loop<V>> contexts) {
    if (stack == null) {
      evaluation.set();
    }
  }

  public Integer value(final Integer stack, final V value,
      @NotNull final EvaluationCollection<V> evaluation, @NotNull final List<Loop<V>> contexts,
      final int index) {
    if (stack == null) {
      cancelOthers(contexts, index);
      evaluation.addValue(value);
      return index;

    } else if (stack == index) {
      evaluation.addValue(value);
      return stack;
    }

    return stack;
  }

  private void cancelOthers(final @NotNull List<Loop<V>> contexts, final int index) {
    final int size = contexts.size();
    for (int i = 0; i < size; ++i) {
      if (i != index) {
        contexts.get(i).cancel(mMayInterruptIfRunning);
      }
    }
  }
}

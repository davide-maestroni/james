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

package dm.jale.ext.forker;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

import dm.jale.Eventual;
import dm.jale.eventual.EvaluationCollection;
import dm.jale.eventual.Loop;
import dm.jale.eventual.LoopForker;
import dm.jale.ext.config.BuildConfig;
import dm.jale.ext.forker.RepeatAllForker.ForkerStack;
import dm.jale.util.ConstantConditions;

/**
 * Created by davide-maestroni on 02/19/2018.
 */
class RepeatAllForker<V> implements LoopForker<ForkerStack<V>, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final int mMaxTimes;

  private RepeatAllForker() {
    mMaxTimes = -1;
  }

  private RepeatAllForker(final int maxTimes) {
    mMaxTimes = ConstantConditions.positive("maxTimes", maxTimes);
  }

  @NotNull
  static <V> LoopForker<?, V> newForker() {
    return Eventual.bufferedLoop(new RepeatAllForker<V>());
  }

  @NotNull
  static <V> LoopForker<?, V> newForker(final int maxTimes) {
    return Eventual.bufferedLoop(new RepeatAllForker<V>(maxTimes));
  }

  public ForkerStack<V> done(final ForkerStack<V> stack, @NotNull final Loop<V> context) {
    stack.evaluation.set();
    stack.evaluation = null;
    return stack;
  }

  public ForkerStack<V> evaluation(final ForkerStack<V> stack,
      @NotNull final EvaluationCollection<V> evaluation, @NotNull final Loop<V> context) {

    final int maxTimes = mMaxTimes;
    if ((maxTimes < 0) || (stack.count < maxTimes)) {
      if (stack.evaluation == null) {
        stack.evaluation = evaluation;

      } else {
        context.evaluate().to(evaluation);
      }

      ++stack.count;

    } else {
      evaluation.addFailure(new IllegalStateException("the loop evaluation cannot be propagated"))
          .set();
    }

    return stack;
  }

  public ForkerStack<V> failure(final ForkerStack<V> stack, @NotNull final Throwable failure,
      @NotNull final Loop<V> context) {
    stack.evaluation.addFailure(failure);
    return stack;
  }

  public ForkerStack<V> init(@NotNull final Loop<V> context) {
    return new ForkerStack<V>();
  }

  public ForkerStack<V> value(final ForkerStack<V> stack, final V value,
      @NotNull final Loop<V> context) {
    stack.evaluation.addValue(value);
    return stack;
  }

  static class ForkerStack<V> {

    private int count;

    private EvaluationCollection<V> evaluation;
  }
}

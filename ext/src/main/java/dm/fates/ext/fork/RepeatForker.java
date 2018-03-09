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

package dm.fates.ext.fork;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

import dm.fates.Eventual;
import dm.fates.eventual.Evaluation;
import dm.fates.eventual.Statement;
import dm.fates.eventual.StatementForker;
import dm.fates.ext.config.BuildConfig;
import dm.fates.ext.fork.RepeatForker.ForkerStack;
import dm.fates.util.ConstantConditions;

/**
 * Created by davide-maestroni on 02/19/2018.
 */
class RepeatForker<V> implements StatementForker<ForkerStack<V>, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final int mMaxTimes;

  private RepeatForker() {
    mMaxTimes = -1;
  }

  private RepeatForker(final int maxTimes) {
    mMaxTimes = ConstantConditions.positive("maxTimes", maxTimes);
  }

  @NotNull
  static <V> StatementForker<?, V> newForker() {
    return Eventual.bufferedStatementForker(Eventual.safeStatementForker(new RepeatForker<V>()));
  }

  @NotNull
  static <V> StatementForker<?, V> newForker(final int maxTimes) {
    return Eventual.bufferedStatementForker(
        Eventual.safeStatementForker(new RepeatForker<V>(maxTimes)));
  }

  public ForkerStack<V> done(final ForkerStack<V> stack, @NotNull final Statement<V> context) {
    stack.evaluation = null;
    return stack;
  }

  public ForkerStack<V> evaluation(final ForkerStack<V> stack,
      @NotNull final Evaluation<V> evaluation, @NotNull final Statement<V> context) {
    final int maxTimes = mMaxTimes;
    if ((maxTimes < 0) || (stack.count < maxTimes)) {
      if (stack.evaluation == null) {
        stack.evaluation = evaluation;

      } else {
        context.evaluate().to(evaluation);
      }

      ++stack.count;

    } else {
      evaluation.fail(new IllegalStateException("the statement evaluation cannot be propagated"));
    }

    return stack;
  }

  public ForkerStack<V> failure(final ForkerStack<V> stack, @NotNull final Throwable failure,
      @NotNull final Statement<V> context) {
    stack.evaluation.fail(failure);
    return stack;
  }

  public ForkerStack<V> init(@NotNull final Statement<V> context) {
    return new ForkerStack<V>();
  }

  public ForkerStack<V> value(final ForkerStack<V> stack, final V value,
      @NotNull final Statement<V> context) {
    stack.evaluation.set(value);
    return stack;
  }

  static class ForkerStack<V> {

    private int count;

    private Evaluation<V> evaluation;
  }
}

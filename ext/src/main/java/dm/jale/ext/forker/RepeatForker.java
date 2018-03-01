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

import dm.jale.eventual.Evaluation;
import dm.jale.eventual.Statement;
import dm.jale.eventual.StatementForker;
import dm.jale.ext.config.BuildConfig;
import dm.jale.ext.forker.RepeatForker.ForkerStack;
import dm.jale.util.ConstantConditions;

/**
 * Created by davide-maestroni on 02/19/2018.
 */
class RepeatForker<V> implements StatementForker<ForkerStack, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final int mMaxTimes;

  RepeatForker() {
    mMaxTimes = -1;
  }

  RepeatForker(final int maxTimes) {
    mMaxTimes = ConstantConditions.positive("maxTimes", maxTimes);
  }

  public ForkerStack done(final ForkerStack stack, @NotNull final Statement<V> context) {
    return stack;
  }

  public ForkerStack evaluation(final ForkerStack stack, @NotNull final Evaluation<V> evaluation,
      @NotNull final Statement<V> context) {
    final int maxTimes = mMaxTimes;
    if ((maxTimes < 0) || (stack.count < maxTimes)) {
      context.evaluate().to(evaluation);
      ++stack.count;

    } else {
      evaluation.fail(new IllegalStateException("the statement evaluation cannot be propagated"));
    }

    return null;
  }

  public ForkerStack failure(final ForkerStack stack, @NotNull final Throwable failure,
      @NotNull final Statement<V> context) {
    return stack;
  }

  public ForkerStack init(@NotNull final Statement<V> context) {
    return new ForkerStack();
  }

  public ForkerStack value(final ForkerStack stack, final V value,
      @NotNull final Statement<V> context) {
    return stack;
  }

  static class ForkerStack {

    private int count;
  }
}

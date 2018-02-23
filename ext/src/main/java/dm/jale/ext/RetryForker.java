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

import dm.jale.Eventual;
import dm.jale.eventual.Evaluation;
import dm.jale.eventual.Statement;
import dm.jale.eventual.Statement.Forker;
import dm.jale.eventual.StatementForker;
import dm.jale.ext.config.BuildConfig;
import dm.jale.util.ConstantConditions;

/**
 * Created by davide-maestroni on 02/19/2018.
 */
class RetryForker<V> implements StatementForker<Evaluation<V>, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final int mMaxCount;

  private RetryForker(final int maxCount) {
    mMaxCount = ConstantConditions.notNegative("maxCount", maxCount);
  }

  @NotNull
  static <V> Forker<?, V, Evaluation<V>, Statement<V>> newForker(final int maxCount) {
    return Eventual.buffered(new RetryForker<V>(maxCount));
  }

  public Evaluation<V> done(final Evaluation<V> stack, @NotNull final Statement<V> context) {
    return stack;
  }

  public Evaluation<V> evaluation(final Evaluation<V> stack,
      @NotNull final Evaluation<V> evaluation, @NotNull final Statement<V> context) {
    if (stack != null) {
      evaluation.fail(new IllegalStateException("the statement evaluation cannot be propagated"));
      return stack;
    }

    return evaluation;
  }

  public Evaluation<V> failure(final Evaluation<V> stack, @NotNull final Throwable failure,
      @NotNull final Statement<V> context) {
    final int maxCount = mMaxCount;
    if (maxCount > 0) {
      context.evaluate().fork(RetryForker.<V>newForker(maxCount - 1)).to(stack);

    } else {
      stack.fail(failure);
    }

    return stack;
  }

  public Evaluation<V> init(@NotNull final Statement<V> context) {
    return null;
  }

  public Evaluation<V> value(final Evaluation<V> stack, final V value,
      @NotNull final Statement<V> context) {
    stack.set(value);
    return stack;
  }
}

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

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.ArrayList;

import dm.jale.eventual.Evaluation;
import dm.jale.eventual.SimpleState;
import dm.jale.eventual.Statement;
import dm.jale.eventual.StatementForker;
import dm.jale.ext.RepeatForker.ForkerStack;
import dm.jale.ext.config.BuildConfig;

/**
 * Created by davide-maestroni on 02/19/2018.
 */
class RepeatForker<V> implements StatementForker<ForkerStack<V>, V>, Serializable {

  private static final RepeatForker<?> sInstance = new RepeatForker<Object>();

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private RepeatForker() {
  }

  @NotNull
  @SuppressWarnings("unchecked")
  static <V> StatementForker<?, V> instance() {
    return (RepeatForker<V>) sInstance;
  }

  public ForkerStack<V> done(final ForkerStack<V> stack, @NotNull final Statement<V> context) {
    return stack;
  }

  public ForkerStack<V> evaluation(final ForkerStack<V> stack,
      @NotNull final Evaluation<V> evaluation, @NotNull final Statement<V> context) {
    final SimpleState<V> state = stack.state;
    if (state != null) {
      state.to(evaluation);

    } else {
      stack.evaluations.add(evaluation);
    }

    return stack;
  }

  public ForkerStack<V> failure(final ForkerStack<V> stack, @NotNull final Throwable failure,
      @NotNull final Statement<V> context) {
    final SimpleState<V> state = (stack.state = SimpleState.ofFailure(failure));
    final ArrayList<Evaluation<V>> evaluations = stack.evaluations;
    try {
      for (final Evaluation<V> evaluation : evaluations) {
        state.to(evaluation);
      }

    } finally {
      evaluations.clear();
    }

    return stack;
  }

  public ForkerStack<V> init(@NotNull final Statement<V> context) {
    return new ForkerStack<V>();
  }

  public ForkerStack<V> value(final ForkerStack<V> stack, final V value,
      @NotNull final Statement<V> context) {
    final SimpleState<V> state = (stack.state = SimpleState.ofValue(value));
    final ArrayList<Evaluation<V>> evaluations = stack.evaluations;
    try {
      for (final Evaluation<V> evaluation : evaluations) {
        state.to(evaluation);
      }

    } finally {
      evaluations.clear();
    }

    return stack;
  }

  @NotNull
  private Object readResolve() throws ObjectStreamException {
    return sInstance;
  }

  static class ForkerStack<V> {

    private final ArrayList<Evaluation<V>> evaluations = new ArrayList<Evaluation<V>>();

    private SimpleState<V> state;
  }
}

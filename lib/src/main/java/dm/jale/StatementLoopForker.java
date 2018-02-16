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

package dm.jale;

import org.jetbrains.annotations.NotNull;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import dm.jale.StatementLoopForker.ForkerStack;
import dm.jale.async.Evaluation;
import dm.jale.async.EvaluationCollection;
import dm.jale.async.Loop;
import dm.jale.async.Statement;
import dm.jale.async.Statement.Forker;
import dm.jale.config.BuildConfig;
import dm.jale.util.ConstantConditions;
import dm.jale.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/05/2018.
 */
class StatementLoopForker<S, V>
    implements Forker<ForkerStack<S, V>, V, EvaluationCollection<V>, Loop<V>>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Forker<S, Iterable<V>, Evaluation<Iterable<V>>, Statement<Iterable<V>>> mForker;

  @SuppressWarnings("unchecked")
  StatementLoopForker(
      @NotNull final Forker<S, ? super Iterable<V>, ? super Evaluation<Iterable<V>>, ? super
          Statement<Iterable<V>>> forker) {
    mForker =
        (Forker<S, Iterable<V>, Evaluation<Iterable<V>>, Statement<Iterable<V>>>) ConstantConditions
            .notNull("forker", forker);
  }

  public ForkerStack<S, V> done(final ForkerStack<S, V> stack, @NotNull final Loop<V> async) throws
      Exception {
    final Forker<S, Iterable<V>, Evaluation<Iterable<V>>, Statement<Iterable<V>>> forker = mForker;
    final List<V> values = stack.getValues();
    if (values != null) {
      stack.setStack(forker.value(stack.getStack(), stack.getValues(), async));
    }

    return stack.withStack(forker.done(stack.getStack(), async));
  }

  public ForkerStack<S, V> evaluation(final ForkerStack<S, V> stack,
      @NotNull final EvaluationCollection<V> evaluation, @NotNull final Loop<V> async) throws
      Exception {
    return stack.withStack(mForker.evaluation(stack.getStack(), new Evaluation<Iterable<V>>() {

      public void fail(@NotNull final Throwable failure) {
        evaluation.addFailure(failure).set();
      }

      public void set(final Iterable<V> value) {
        evaluation.addValues(value).set();
      }
    }, async));
  }

  public ForkerStack<S, V> failure(final ForkerStack<S, V> stack, @NotNull final Throwable failure,
      @NotNull final Loop<V> async) throws Exception {
    if (stack.getValues() == null) {
      return stack;
    }

    return stack.withStack(mForker.failure(stack.getStack(), failure, async)).withValues(null);
  }

  public ForkerStack<S, V> init(@NotNull final Loop<V> async) throws Exception {
    return new ForkerStack<S, V>().withStack(mForker.init(async)).withValues(new ArrayList<V>());
  }

  public ForkerStack<S, V> value(final ForkerStack<S, V> stack, final V value,
      @NotNull final Loop<V> async) throws Exception {
    final List<V> values = stack.getValues();
    if (values != null) {
      values.add(value);
    }

    return stack;
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new ForkerProxy<S, V>(mForker);
  }

  static class ForkerStack<S, V> {

    private S mStack;

    private List<V> mValues;

    S getStack() {
      return mStack;
    }

    void setStack(final S stack) {
      mStack = stack;
    }

    List<V> getValues() {
      return mValues;
    }

    void setValues(final List<V> values) {
      mValues = values;
    }

    @NotNull
    ForkerStack<S, V> withStack(final S stack) {
      mStack = stack;
      return this;
    }

    @NotNull
    ForkerStack<S, V> withValues(final List<V> values) {
      mValues = values;
      return this;
    }
  }

  private static class ForkerProxy<S, V> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private ForkerProxy(
        final Forker<S, Iterable<V>, Evaluation<Iterable<V>>, Statement<Iterable<V>>> forker) {
      super(proxy(forker));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new StatementLoopForker<S, V>(
            (Forker<S, Iterable<V>, Evaluation<Iterable<V>>, Statement<Iterable<V>>>) args[0]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

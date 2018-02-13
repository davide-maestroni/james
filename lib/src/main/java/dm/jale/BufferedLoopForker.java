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

import dm.jale.BufferedLoopForker.ForkerStack;
import dm.jale.async.AsyncEvaluations;
import dm.jale.async.AsyncLoop;
import dm.jale.async.AsyncStatement.Forker;
import dm.jale.async.SimpleState;
import dm.jale.config.BuildConfig;
import dm.jale.util.ConstantConditions;
import dm.jale.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/13/2018.
 */
class BufferedLoopForker<S, V>
    implements Forker<ForkerStack<S, V>, V, AsyncEvaluations<V>, AsyncLoop<V>>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Forker<S, ? super V, ? super AsyncEvaluations<V>, ? super AsyncLoop<V>> mForker;

  BufferedLoopForker(
      @NotNull final Forker<S, ? super V, ? super AsyncEvaluations<V>, ? super AsyncLoop<V>>
          forker) {
    mForker = ConstantConditions.notNull("forker", forker);
  }

  public ForkerStack<S, V> done(final ForkerStack<S, V> stack,
      @NotNull final AsyncLoop<V> async) throws Exception {
    return stack.settle(async);
  }

  public ForkerStack<S, V> evaluation(final ForkerStack<S, V> stack,
      @NotNull final AsyncEvaluations<V> evaluation, @NotNull final AsyncLoop<V> async) throws
      Exception {
    return stack.addEvaluations(evaluation, async);
  }

  public ForkerStack<S, V> failure(final ForkerStack<S, V> stack, @NotNull final Throwable failure,
      @NotNull final AsyncLoop<V> async) throws Exception {
    return stack.addFailure(failure, async);
  }

  public ForkerStack<S, V> init(@NotNull final AsyncLoop<V> async) throws Exception {
    return new ForkerStack<S, V>(mForker, async);
  }

  public ForkerStack<S, V> value(final ForkerStack<S, V> stack, final V value,
      @NotNull final AsyncLoop<V> async) throws Exception {
    return stack.addValue(value, async);
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new ForkerProxy<S, V>(mForker);
  }

  static class ForkerStack<S, V> {

    // TODO: 13/02/2018 boolean
    private final ArrayList<AsyncEvaluations<V>> mEvaluations =
        new ArrayList<AsyncEvaluations<V>>();

    private final Forker<S, ? super V, ? super AsyncEvaluations<V>, ? super AsyncLoop<V>> mForker;

    private final ArrayList<SimpleState<V>> mStates = new ArrayList<SimpleState<V>>();

    private S mStack;

    private ForkerStack(
        @NotNull final Forker<S, ? super V, ? super AsyncEvaluations<V>, ? super AsyncLoop<V>>
            forker,
        @NotNull final AsyncLoop<V> async) throws Exception {
      mForker = forker;
      mStack = forker.init(async);
    }

    @NotNull
    private ForkerStack<S, V> addEvaluations(@NotNull final AsyncEvaluations<V> evaluations,
        @NotNull final AsyncLoop<V> async) throws Exception {
      final ArrayList<AsyncEvaluations<V>> asyncEvaluations = mEvaluations;
      final boolean isFirst = asyncEvaluations.isEmpty();
      asyncEvaluations.add(evaluations);
      if (isFirst) {
        mStack = mForker.evaluation(mStack, evaluations, async);
        final ArrayList<SimpleState<V>> states = mStates;
        try {
          for (final SimpleState<V> state : states) {
            if (state.isSet()) {
              mStack = mForker.value(mStack, state.value(), async);

            } else if (state.isFailed()) {
              mStack = mForker.failure(mStack, state.failure(), async);

            } else {
              mStack = mForker.done(mStack, async);
            }
          }

        } finally {
          states.clear();
        }
      }

      return this;
    }

    @NotNull
    private ForkerStack<S, V> addFailure(@NotNull final Throwable failure,
        @NotNull final AsyncLoop<V> async) throws Exception {
      if (!mEvaluations.isEmpty()) {
        mStack = mForker.failure(mStack, failure, async);

      } else {
        mStates.add(SimpleState.<V>ofFailure(failure));
      }

      return this;
    }

    @NotNull
    private ForkerStack<S, V> addValue(final V value, @NotNull final AsyncLoop<V> async) throws
        Exception {
      if (!mEvaluations.isEmpty()) {
        mStack = mForker.value(mStack, value, async);

      } else {
        mStates.add(SimpleState.ofValue(value));
      }

      return this;
    }

    @NotNull
    private ForkerStack<S, V> settle(@NotNull final AsyncLoop<V> async) throws Exception {
      if (!mEvaluations.isEmpty()) {
        mStack = mForker.done(mStack, async);

      } else {
        mStates.add(SimpleState.<V>settled());
      }

      return this;
    }
  }

  private static class ForkerProxy<S, V> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private ForkerProxy(
        final Forker<S, ? super V, ? super AsyncEvaluations<V>, ? super AsyncLoop<V>> forker) {
      super(proxy(forker));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new BufferedLoopForker<S, V>(
            (Forker<S, ? super V, ? super AsyncEvaluations<V>, ? super AsyncLoop<V>>) args[0]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

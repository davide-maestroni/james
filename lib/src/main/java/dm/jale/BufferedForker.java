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

import dm.jale.BufferedForker.ForkerStack;
import dm.jale.async.AsyncStatement.Forker;
import dm.jale.async.SimpleState;
import dm.jale.config.BuildConfig;
import dm.jale.util.ConstantConditions;
import dm.jale.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/13/2018.
 */
class BufferedForker<S, V, R, A> implements Forker<ForkerStack<S, V, R, A>, V, R, A>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Forker<S, ? super V, ? super R, ? super A> mForker;

  BufferedForker(@NotNull final Forker<S, ? super V, ? super R, ? super A> forker) {
    mForker = ConstantConditions.notNull("forker", forker);
  }

  public ForkerStack<S, V, R, A> done(final ForkerStack<S, V, R, A> stack,
      @NotNull final A async) throws Exception {
    return stack.settle(async);
  }

  public ForkerStack<S, V, R, A> evaluation(final ForkerStack<S, V, R, A> stack,
      @NotNull final R evaluation, @NotNull final A async) throws Exception {
    return stack.addEvaluations(evaluation, async);
  }

  public ForkerStack<S, V, R, A> failure(final ForkerStack<S, V, R, A> stack,
      @NotNull final Throwable failure, @NotNull final A async) throws Exception {
    return stack.addFailure(failure, async);
  }

  public ForkerStack<S, V, R, A> init(@NotNull final A async) throws Exception {
    return new ForkerStack<S, V, R, A>(mForker, async);
  }

  public ForkerStack<S, V, R, A> value(final ForkerStack<S, V, R, A> stack, final V value,
      @NotNull final A async) throws Exception {
    return stack.addValue(value, async);
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new ForkerProxy<S, V, R, A>(mForker);
  }

  static class ForkerStack<S, V, R, A> {

    private final Forker<S, ? super V, ? super R, ? super A> mForker;

    private final ArrayList<SimpleState<V>> mStates = new ArrayList<SimpleState<V>>();

    private boolean mHasEvaluation;

    private S mStack;

    private ForkerStack(@NotNull final Forker<S, ? super V, ? super R, ? super A> forker,
        @NotNull final A async) throws Exception {
      mForker = forker;
      mStack = forker.init(async);
    }

    @NotNull
    private ForkerStack<S, V, R, A> addEvaluations(@NotNull final R evaluations,
        @NotNull final A async) throws Exception {
      final boolean isFirst = !mHasEvaluation;
      mHasEvaluation = true;
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
    private ForkerStack<S, V, R, A> addFailure(@NotNull final Throwable failure,
        @NotNull final A async) throws Exception {
      if (mHasEvaluation) {
        mStack = mForker.failure(mStack, failure, async);

      } else {
        mStates.add(SimpleState.<V>ofFailure(failure));
      }

      return this;
    }

    @NotNull
    private ForkerStack<S, V, R, A> addValue(final V value, @NotNull final A async) throws
        Exception {
      if (mHasEvaluation) {
        mStack = mForker.value(mStack, value, async);

      } else {
        mStates.add(SimpleState.ofValue(value));
      }

      return this;
    }

    @NotNull
    private ForkerStack<S, V, R, A> settle(@NotNull final A async) throws Exception {
      if (mHasEvaluation) {
        mStack = mForker.done(mStack, async);

      } else {
        mStates.add(SimpleState.<V>settled());
      }

      return this;
    }
  }

  private static class ForkerProxy<S, V, R, A> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private ForkerProxy(final Forker<S, ? super V, ? super R, ? super A> forker) {
      super(proxy(forker));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new BufferedForker<S, V, R, A>((Forker<S, ? super V, ? super R, ? super A>) args[0]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

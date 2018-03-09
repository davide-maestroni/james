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

package dm.fates;

import org.jetbrains.annotations.NotNull;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.ArrayList;

import dm.fates.BufferedForker.ForkerStack;
import dm.fates.config.BuildConfig;
import dm.fates.eventual.SimpleState;
import dm.fates.eventual.Statement.Forker;
import dm.fates.util.ConstantConditions;
import dm.fates.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/13/2018.
 */
class BufferedForker<S, V, R, C> implements Forker<ForkerStack<S, V, R, C>, V, R, C>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Forker<S, ? super V, ? super R, ? super C> mForker;

  BufferedForker(@NotNull final Forker<S, ? super V, ? super R, ? super C> forker) {
    mForker = ConstantConditions.notNull("forker", forker);
  }

  public ForkerStack<S, V, R, C> done(final ForkerStack<S, V, R, C> stack,
      @NotNull final C context) throws Exception {
    return stack.settle(context);
  }

  public ForkerStack<S, V, R, C> evaluation(final ForkerStack<S, V, R, C> stack,
      @NotNull final R evaluation, @NotNull final C context) throws Exception {
    return stack.addEvaluations(evaluation, context);
  }

  public ForkerStack<S, V, R, C> failure(final ForkerStack<S, V, R, C> stack,
      @NotNull final Throwable failure, @NotNull final C context) throws Exception {
    return stack.addFailure(failure, context);
  }

  public ForkerStack<S, V, R, C> init(@NotNull final C context) throws Exception {
    return new ForkerStack<S, V, R, C>(mForker, context);
  }

  public ForkerStack<S, V, R, C> value(final ForkerStack<S, V, R, C> stack, final V value,
      @NotNull final C context) throws Exception {
    return stack.addValue(value, context);
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new ForkerProxy<S, V, R, C>(mForker);
  }

  static class ForkerStack<S, V, R, C> {

    private final Forker<S, ? super V, ? super R, ? super C> mForker;

    private boolean mHasEvaluation;

    private S mStack;

    private ArrayList<SimpleState<V>> mStates = new ArrayList<SimpleState<V>>();

    private ForkerStack(@NotNull final Forker<S, ? super V, ? super R, ? super C> forker,
        @NotNull final C context) throws Exception {
      mForker = forker;
      mStack = forker.init(context);
    }

    @NotNull
    private ForkerStack<S, V, R, C> addEvaluations(@NotNull final R evaluation,
        @NotNull final C context) throws Exception {
      final boolean isFirst = !mHasEvaluation;
      mHasEvaluation = true;
      if (isFirst) {
        mStack = mForker.evaluation(mStack, evaluation, context);
        final ArrayList<SimpleState<V>> states = mStates;
        try {
          for (final SimpleState<V> state : states) {
            if (state.isSet()) {
              mStack = mForker.value(mStack, state.value(), context);

            } else if (state.isFailed()) {
              mStack = mForker.failure(mStack, state.failure(), context);

            } else {
              mStack = mForker.done(mStack, context);
            }
          }

        } finally {
          mStates = null;
        }
      }

      return this;
    }

    @NotNull
    private ForkerStack<S, V, R, C> addFailure(@NotNull final Throwable failure,
        @NotNull final C context) throws Exception {
      if (mHasEvaluation) {
        mStack = mForker.failure(mStack, failure, context);

      } else {
        mStates.add(SimpleState.<V>ofFailure(failure));
      }

      return this;
    }

    @NotNull
    private ForkerStack<S, V, R, C> addValue(final V value, @NotNull final C context) throws
        Exception {
      if (mHasEvaluation) {
        mStack = mForker.value(mStack, value, context);

      } else {
        mStates.add(SimpleState.ofValue(value));
      }

      return this;
    }

    @NotNull
    private ForkerStack<S, V, R, C> settle(@NotNull final C context) throws Exception {
      if (mHasEvaluation) {
        mStack = mForker.done(mStack, context);

      } else {
        mStates.add(SimpleState.<V>settled());
      }

      return this;
    }
  }

  private static class ForkerProxy<S, V, R, C> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private ForkerProxy(final Forker<S, ? super V, ? super R, ? super C> forker) {
      super(proxy(forker));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new BufferedForker<S, V, R, C>((Forker<S, ? super V, ? super R, ? super C>) args[0]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

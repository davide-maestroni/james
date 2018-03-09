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
import org.jetbrains.annotations.Nullable;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;

import dm.fates.config.BuildConfig;
import dm.fates.eventual.EvaluationCollection;
import dm.fates.eventual.Loop;
import dm.fates.eventual.LoopForker;
import dm.fates.eventual.Statement.Forker;
import dm.fates.util.ConstantConditions;
import dm.fates.util.SerializableProxy;

/**
 * Created by davide-maestroni on 03/09/2018.
 */
class SafeLoopForker<S, V> implements LoopForker<S, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Forker<S, ? super V, ? super EvaluationCollection<V>, ? super Loop<V>> mForker;

  SafeLoopForker(
      @NotNull final Forker<S, ? super V, ? super EvaluationCollection<V>, ? super Loop<V>>
          forker) {
    mForker = ConstantConditions.notNull("forker", forker);
  }

  public S done(final S stack, @NotNull final Loop<V> context) throws Exception {
    return mForker.done(stack, context);
  }

  public S evaluation(final S stack, @NotNull final EvaluationCollection<V> evaluation,
      @NotNull final Loop<V> context) throws Exception {
    return mForker.evaluation(stack, new SafeEvaluationCollection<V>(evaluation), context);
  }

  public S failure(final S stack, @NotNull final Throwable failure,
      @NotNull final Loop<V> context) throws Exception {
    return mForker.failure(stack, failure, context);
  }

  public S init(@NotNull final Loop<V> context) throws Exception {
    return mForker.init(context);
  }

  public S value(final S stack, final V value, @NotNull final Loop<V> context) throws Exception {
    return mForker.value(stack, value, context);
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new ForkerProxy<S, V>(mForker);
  }

  private static class ForkerProxy<S, V> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private ForkerProxy(
        final Forker<S, ? super V, ? super EvaluationCollection<V>, ? super Loop<V>> forker) {
      super(proxy(forker));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new SafeLoopForker<S, V>(
            (Forker<S, ? super V, ? super EvaluationCollection<V>, ? super Loop<V>>) args[0]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }

  private static class SafeEvaluationCollection<V> implements EvaluationCollection<V> {

    private final EvaluationCollection<V> mEvaluation;

    private volatile boolean mIsFailed;

    private SafeEvaluationCollection(@NotNull final EvaluationCollection<V> evaluation) {
      mEvaluation = evaluation;
    }

    @NotNull
    public EvaluationCollection<V> addFailure(@NotNull final Throwable failure) {
      if (!mIsFailed) {
        try {
          mEvaluation.addFailure(failure);

        } catch (final Throwable t) {
          mIsFailed = true;
          // TODO: 09/03/2018 suppressed
        }
      }

      return this;
    }

    @NotNull
    public EvaluationCollection<V> addFailures(
        @Nullable final Iterable<? extends Throwable> failures) {
      if (!mIsFailed) {
        try {
          mEvaluation.addFailures(failures);

        } catch (final Throwable t) {
          mIsFailed = true;
          // TODO: 09/03/2018 suppressed
        }
      }

      return this;
    }

    @NotNull
    public EvaluationCollection<V> addValue(final V value) {
      if (!mIsFailed) {
        try {
          mEvaluation.addValue(value);

        } catch (final Throwable t) {
          mIsFailed = true;
          // TODO: 09/03/2018 suppressed
        }
      }

      return this;
    }

    @NotNull
    public EvaluationCollection<V> addValues(@Nullable final Iterable<? extends V> values) {
      if (!mIsFailed) {
        try {
          mEvaluation.addValues(values);

        } catch (final Throwable t) {
          mIsFailed = true;
          // TODO: 09/03/2018 suppressed
        }
      }

      return this;
    }

    public void set() {
      if (!mIsFailed) {
        try {
          mEvaluation.set();

        } catch (final Throwable t) {
          mIsFailed = true;
          // TODO: 09/03/2018 suppressed
        }
      }
    }
  }
}

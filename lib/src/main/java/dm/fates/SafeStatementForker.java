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

import dm.fates.config.BuildConfig;
import dm.fates.eventual.Evaluation;
import dm.fates.eventual.Statement;
import dm.fates.eventual.Statement.Forker;
import dm.fates.eventual.StatementForker;
import dm.fates.util.ConstantConditions;
import dm.fates.util.SerializableProxy;

/**
 * Created by davide-maestroni on 03/09/2018.
 */
class SafeStatementForker<S, V> implements StatementForker<S, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Forker<S, ? super V, ? super Evaluation<V>, ? super Statement<V>> mForker;

  SafeStatementForker(
      @NotNull final Forker<S, ? super V, ? super Evaluation<V>, ? super Statement<V>> forker) {
    mForker = ConstantConditions.notNull("forker", forker);
  }

  public S done(final S stack, @NotNull final Statement<V> context) throws Exception {
    return mForker.done(stack, context);
  }

  public S evaluation(final S stack, @NotNull final Evaluation<V> evaluation,
      @NotNull final Statement<V> context) throws Exception {
    return mForker.evaluation(stack, new SafeEvaluation<V>(evaluation), context);
  }

  public S failure(final S stack, @NotNull final Throwable failure,
      @NotNull final Statement<V> context) throws Exception {
    return mForker.failure(stack, failure, context);
  }

  public S init(@NotNull final Statement<V> context) throws Exception {
    return mForker.init(context);
  }

  public S value(final S stack, final V value, @NotNull final Statement<V> context) throws
      Exception {
    return mForker.value(stack, value, context);
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new ForkerProxy<S, V>(mForker);
  }

  private static class ForkerProxy<S, V> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private ForkerProxy(
        final Forker<S, ? super V, ? super Evaluation<V>, ? super Statement<V>> forker) {
      super(proxy(forker));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new SafeStatementForker<S, V>(
            (Forker<S, ? super V, ? super Evaluation<V>, ? super Statement<V>>) args[0]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }

  private static class SafeEvaluation<V> implements Evaluation<V> {

    private final Evaluation<V> mEvaluation;

    private volatile boolean mIsFailed;

    private SafeEvaluation(@NotNull final Evaluation<V> evaluation) {
      mEvaluation = evaluation;
    }

    public void fail(@NotNull final Throwable failure) {
      if (!mIsFailed) {
        try {
          mEvaluation.fail(failure);

        } catch (final Throwable t) {
          mIsFailed = true;
          // TODO: 09/03/2018 suppressed
        }
      }
    }

    public void set(final V value) {
      if (!mIsFailed) {
        try {
          mEvaluation.set(value);

        } catch (final Throwable t) {
          mIsFailed = true;
          // TODO: 09/03/2018 suppressed
        }
      }
    }
  }
}

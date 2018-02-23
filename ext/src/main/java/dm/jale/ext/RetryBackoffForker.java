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

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.concurrent.TimeUnit;

import dm.jale.Eventual;
import dm.jale.eventual.Evaluation;
import dm.jale.eventual.Statement;
import dm.jale.eventual.Statement.Forker;
import dm.jale.eventual.StatementForker;
import dm.jale.executor.ScheduledExecutor;
import dm.jale.ext.RetryBackoffForker.ForkerStack;
import dm.jale.ext.backoff.BackoffUpdater;
import dm.jale.ext.backoff.BackoffUpdater.RetryEvaluation;
import dm.jale.ext.config.BuildConfig;
import dm.jale.util.ConstantConditions;
import dm.jale.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/19/2018.
 */
class RetryBackoffForker<S, V> implements StatementForker<ForkerStack<S, V>, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final BackoffUpdater<S> mBackoff;

  private final ScheduledExecutor mExecutor;

  private final int mRetryCount;

  private final S mStack;

  private RetryBackoffForker(@NotNull final ScheduledExecutor executor,
      @NotNull final BackoffUpdater<S> backoff, final S stack, final int retryCount) {
    mExecutor = ConstantConditions.notNull("executor", executor);
    mBackoff = ConstantConditions.notNull("backoff", backoff);
    mStack = stack;
    mRetryCount = retryCount;
  }

  @NotNull
  static <S, V> Forker<?, V, Evaluation<V>, Statement<V>> newForker(
      @NotNull final ScheduledExecutor executor, @NotNull final BackoffUpdater<S> backoff) {
    return Eventual.buffered(new RetryBackoffForker<S, V>(executor, backoff, null, 0));
  }

  @NotNull
  private static <S, V> Forker<?, V, Evaluation<V>, Statement<V>> newForker(
      @NotNull final ScheduledExecutor executor, @NotNull final BackoffUpdater<S> backoff,
      final S stack, final int retryCount) {
    return Eventual.buffered(new RetryBackoffForker<S, V>(executor, backoff, stack, retryCount));
  }

  public ForkerStack<S, V> done(final ForkerStack<S, V> stack,
      @NotNull final Statement<V> context) {
    return stack;
  }

  public ForkerStack<S, V> evaluation(final ForkerStack<S, V> stack,
      @NotNull final Evaluation<V> evaluation, @NotNull final Statement<V> context) {
    if (stack.evaluation() != null) {
      evaluation.fail(new IllegalStateException("the statement evaluation cannot be propagated"));
      return stack;
    }

    return stack.withEvaluation(evaluation);
  }

  public ForkerStack<S, V> failure(final ForkerStack<S, V> stack, @NotNull final Throwable failure,
      @NotNull final Statement<V> context) throws Exception {
    mBackoff.update(mStack, failure, stack.withStatement(context));
    if (!stack.isCalled()) {
      stack.evaluation().fail(failure);
    }

    return stack;
  }

  public ForkerStack<S, V> init(@NotNull final Statement<V> context) {
    return new ForkerStack<S, V>(mExecutor, mBackoff, mRetryCount);
  }

  public ForkerStack<S, V> value(final ForkerStack<S, V> stack, final V value,
      @NotNull final Statement<V> context) {
    stack.evaluation().set(value);
    return stack;
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new ForkerProxy<S, V>(mExecutor, mBackoff);
  }

  static class ForkerStack<S, V> implements RetryEvaluation<S> {

    private final BackoffUpdater<S> mBackoff;

    private final ScheduledExecutor mExecutor;

    private final int mRetryCount;

    private Evaluation<V> mEvaluation;

    private boolean mIsCalled;

    private Statement<V> mStatement;

    private ForkerStack(@NotNull final ScheduledExecutor executor,
        @NotNull final BackoffUpdater<S> backoff, final int retryCount) {
      mExecutor = executor;
      mBackoff = backoff;
      mRetryCount = retryCount;
    }

    public int count() {
      return mRetryCount;
    }

    public void retry(final S stack) {
      mIsCalled = true;
      final Statement<V> statement = mStatement;
      final Evaluation<V> evaluation = mEvaluation;
      final ScheduledExecutor executor = mExecutor;
      executor.execute(new Runnable() {

        public void run() {
          statement.evaluate()
              .fork(RetryBackoffForker.<S, V>newForker(executor, mBackoff, stack, mRetryCount + 1))
              .to(evaluation);
        }
      });
    }

    public void retryAfter(final long delay, @NotNull final TimeUnit timeUnit, final S stack) {
      mIsCalled = true;
      final Statement<V> statement = mStatement;
      final Evaluation<V> evaluation = mEvaluation;
      final ScheduledExecutor executor = mExecutor;
      executor.execute(new Runnable() {

        public void run() {
          statement.evaluate()
              .fork(RetryBackoffForker.<S, V>newForker(executor, mBackoff, stack, mRetryCount + 1))
              .to(evaluation);
        }
      }, delay, timeUnit);
    }

    private Evaluation<V> evaluation() {
      return mEvaluation;
    }

    private boolean isCalled() {
      return mIsCalled;
    }

    @NotNull
    private ForkerStack<S, V> withEvaluation(final Evaluation<V> evaluation) {
      mEvaluation = evaluation;
      return this;
    }

    @NotNull
    private ForkerStack<S, V> withStatement(@NotNull final Statement<V> statement) {
      mStatement = statement;
      return this;
    }
  }

  private static class ForkerProxy<S, V> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private ForkerProxy(final ScheduledExecutor executor, final BackoffUpdater<S> backoff) {
      super(executor, proxy(backoff));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new RetryBackoffForker<S, V>((ScheduledExecutor) args[0],
            (BackoffUpdater<S>) args[1], null, 0);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

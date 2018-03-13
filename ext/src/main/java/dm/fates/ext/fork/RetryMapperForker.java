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

package dm.fates.ext.fork;

import org.jetbrains.annotations.NotNull;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;

import dm.fates.eventual.Evaluation;
import dm.fates.eventual.Observer;
import dm.fates.eventual.SimpleState;
import dm.fates.eventual.Statement;
import dm.fates.eventual.StatementForker;
import dm.fates.ext.config.BuildConfig;
import dm.fates.ext.eventual.BiMapper;
import dm.fates.ext.fork.RetryMapperForker.ForkerStack;
import dm.fates.util.ConstantConditions;
import dm.fates.util.SerializableProxy;

/**
 * Created by davide-maestroni on 03/01/2018.
 */
class RetryMapperForker<S, V> implements StatementForker<ForkerStack<S, V>, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final BiMapper<S, ? super Throwable, ? extends Statement<S>> mMapper;

  private final S mStack;

  RetryMapperForker(@NotNull final BiMapper<S, ? super Throwable, ? extends Statement<S>> mapper,
      final S stack) {
    mMapper = ConstantConditions.notNull("mapper", mapper);
    mStack = stack;
  }

  public ForkerStack<S, V> done(final ForkerStack<S, V> stack,
      @NotNull final Statement<V> context) {
    return stack;
  }

  public ForkerStack<S, V> evaluation(final ForkerStack<S, V> stack,
      @NotNull final Evaluation<V> evaluation, @NotNull final Statement<V> context) throws
      Exception {
    if (stack.evaluation == null) {
      stack.evaluation = evaluation;
      final SimpleState<V> state = stack.state;
      if (state != null) {
        if (state.isFailed()) {
          final BiMapper<S, ? super Throwable, ? extends Statement<S>> mapper = mMapper;
          mapper.apply(stack.stack, state.failure())
              .eventuallyDo(new ValueObserver<S, V>(mapper, evaluation, context))
              .elseDo(new FailureObserver(evaluation))
              .consume();
          return stack;
        }

        try {
          state.to(evaluation);

        } finally {
          stack.state = null;
        }
      }

    } else {
      evaluation.fail(new IllegalStateException("the statement evaluation cannot be propagated"));
    }

    return stack;
  }

  public ForkerStack<S, V> failure(final ForkerStack<S, V> stack, @NotNull final Throwable failure,
      @NotNull final Statement<V> context) throws Exception {
    final Evaluation<V> evaluation = stack.evaluation;
    if (evaluation != null) {
      final BiMapper<S, ? super Throwable, ? extends Statement<S>> mapper = mMapper;
      mapper.apply(stack.stack, failure)
          .eventuallyDo(new ValueObserver<S, V>(mapper, evaluation, context))
          .elseDo(new FailureObserver(evaluation))
          .consume();

    } else {
      stack.state = SimpleState.ofFailure(failure);
    }

    return stack;
  }

  public ForkerStack<S, V> init(@NotNull final Statement<V> context) {
    final ForkerStack<S, V> stack = new ForkerStack<S, V>();
    stack.stack = mStack;
    return stack;
  }

  public ForkerStack<S, V> value(final ForkerStack<S, V> stack, final V value,
      @NotNull final Statement<V> context) {
    final Evaluation<V> evaluation = stack.evaluation;
    if (evaluation != null) {
      evaluation.set(value);

    } else {
      stack.state = SimpleState.ofValue(value);
    }

    return stack;
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new ForkerProxy<S, V>(mMapper);
  }

  static class ForkerStack<S, V> {

    private Evaluation<V> evaluation;

    private S stack;

    private SimpleState<V> state;
  }

  private static class FailureObserver implements Observer<Throwable> {

    private final Evaluation<?> mEvaluation;

    private FailureObserver(@NotNull final Evaluation<?> evaluation) {
      mEvaluation = evaluation;
    }

    public void accept(final Throwable failure) {
      mEvaluation.fail(failure);
    }
  }

  private static class ForkerProxy<S, V> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private ForkerProxy(final BiMapper<S, ? super Throwable, ? extends Statement<S>> mapper) {
      super(proxy(mapper));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new RetryMapperForker<S, V>(
            (BiMapper<S, ? super Throwable, ? extends Statement<S>>) args[0], null);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }

  private static class ValueObserver<S, V> implements Observer<S> {

    private final Statement<V> mContext;

    private final Evaluation<V> mEvaluation;

    private final BiMapper<S, ? super Throwable, ? extends Statement<S>> mMapper;

    private ValueObserver(
        @NotNull final BiMapper<S, ? super Throwable, ? extends Statement<S>> mapper,
        @NotNull final Evaluation<V> evaluation, @NotNull final Statement<V> context) {
      mMapper = mapper;
      mEvaluation = evaluation;
      mContext = context;
    }

    public void accept(final S stack) {
      mContext.evaluate().fork(new RetryMapperForker<S, V>(mMapper, stack)).to(mEvaluation);
    }
  }
}

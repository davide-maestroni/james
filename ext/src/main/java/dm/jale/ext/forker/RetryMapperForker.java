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

package dm.jale.ext.forker;

import org.jetbrains.annotations.NotNull;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;

import dm.jale.Eventual;
import dm.jale.eventual.Evaluation;
import dm.jale.eventual.Observer;
import dm.jale.eventual.Statement;
import dm.jale.eventual.StatementForker;
import dm.jale.ext.config.BuildConfig;
import dm.jale.ext.eventual.BiMapper;
import dm.jale.ext.forker.RetryMapperForker.ForkerStack;
import dm.jale.util.ConstantConditions;
import dm.jale.util.SerializableProxy;

/**
 * Created by davide-maestroni on 03/01/2018.
 */
class RetryMapperForker<S, V> implements StatementForker<ForkerStack<S, V>, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final BiMapper<S, ? super Throwable, ? extends Statement<S>> mMapper;

  private final S mStack;

  private RetryMapperForker(
      @NotNull final BiMapper<S, ? super Throwable, ? extends Statement<S>> mapper, final S stack) {
    mMapper = ConstantConditions.notNull("mapper", mapper);
    mStack = stack;
  }

  @NotNull
  static <S, V> StatementForker<?, V> newForker(
      @NotNull final BiMapper<S, ? super Throwable, ? extends Statement<S>> mapper) {
    return Eventual.bufferedStatement(new RetryMapperForker<S, V>(mapper, null));
  }

  public ForkerStack<S, V> done(final ForkerStack<S, V> stack,
      @NotNull final Statement<V> context) {
    return stack;
  }

  public ForkerStack<S, V> evaluation(final ForkerStack<S, V> stack,
      @NotNull final Evaluation<V> evaluation, @NotNull final Statement<V> context) {
    if (stack.evaluation != null) {
      evaluation.fail(new IllegalStateException("the statement evaluation cannot be propagated"));
      return stack;
    }

    stack.evaluation = evaluation;
    return stack;
  }

  public ForkerStack<S, V> failure(final ForkerStack<S, V> stack, @NotNull final Throwable failure,
      @NotNull final Statement<V> context) throws Exception {
    final Evaluation<V> evaluation = stack.evaluation;
    final BiMapper<S, ? super Throwable, ? extends Statement<S>> mapper = mMapper;
    mapper.apply(stack.stack, failure)
        .eventuallyDo(new ValueObserver<S, V>(mapper, evaluation, context))
        .elseDo(new FailureObserver(evaluation))
        .consume();
    return stack;
  }

  public ForkerStack<S, V> init(@NotNull final Statement<V> context) {
    final ForkerStack<S, V> stack = new ForkerStack<S, V>();
    stack.stack = mStack;
    return stack;
  }

  public ForkerStack<S, V> value(final ForkerStack<S, V> stack, final V value,
      @NotNull final Statement<V> context) {
    stack.evaluation.set(value);
    return stack;
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new ForkerProxy<S, V>(mMapper);
  }

  static class ForkerStack<S, V> {

    private Evaluation<V> evaluation;

    private S stack;
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

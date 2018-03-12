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

package dm.fates.eventual;

import org.jetbrains.annotations.NotNull;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.concurrent.CancellationException;

import dm.fates.config.BuildConfig;
import dm.fates.eventual.Loop.YieldOutputs;
import dm.fates.util.ConstantConditions;

/**
 * Created by davide-maestroni on 01/11/2018.
 */
public abstract class SimpleState<V> implements EvaluationState<V>, Serializable {

  private final String mStateName;

  private SimpleState(@NotNull final String stateName) {
    mStateName = stateName;
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public static <V> SimpleState<V> cancelled() {
    return new FailureState<V>(new CancellationException());
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public static <V> SimpleState<V> evaluating() {
    return (SimpleState<V>) EvaluatingState.sInstance;
  }

  @NotNull
  public static <V> SimpleState<V> ofFailure(@NotNull final Throwable reason) {
    return new FailureState<V>(reason);
  }

  @NotNull
  public static <V> SimpleState<V> ofValue(final V value) {
    return new ValueState<V>(value);
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public static <V> SimpleState<V> settled() {
    return (SimpleState<V>) SettledState.sInstance;
  }

  public abstract void addTo(@NotNull EvaluationCollection<? super V> evaluation);

  @Override
  public String toString() {
    return SimpleState.class.getSimpleName() + ": {state=" + mStateName + "}";
  }

  public abstract void yieldTo(@NotNull YieldOutputs<? super V> outputs);

  private static class EvaluatingState<V> extends SimpleState<V> {

    private static final EvaluatingState<?> sInstance = new EvaluatingState<Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private EvaluatingState() {
      super("Evaluating");
    }

    public void addTo(@NotNull final EvaluationCollection<? super V> evaluation) {
      ConstantConditions.unsupported();
    }

    @NotNull
    public Throwable failure() {
      throw new IllegalStateException("invalid state: Evaluating");
    }

    @NotNull
    private Object readResolve() throws ObjectStreamException {
      return sInstance;
    }

    public void yieldTo(@NotNull final YieldOutputs<? super V> outputs) {
      ConstantConditions.unsupported();
    }

    public boolean isCancelled() {
      return false;
    }

    public boolean isFailed() {
      return false;
    }

    public boolean isEvaluating() {
      return true;
    }

    public boolean isSet() {
      return false;
    }

    public void to(@NotNull final Evaluation<? super V> evaluation) {
      ConstantConditions.unsupported();
    }

    public V value() {
      throw new IllegalStateException("invalid state: Evaluating");
    }
  }

  private static class FailureState<V> extends SimpleState<V> {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final Throwable mFailure;

    private FailureState(@NotNull final Throwable failure) {
      super("Failed, failure=" + failure);
      mFailure = ConstantConditions.notNull("failure", failure);
    }

    public void yieldTo(@NotNull final YieldOutputs<? super V> outputs) {
      outputs.yieldFailure(mFailure);
    }

    public void addTo(@NotNull final EvaluationCollection<? super V> evaluation) {
      evaluation.addFailure(mFailure);
    }

    @NotNull
    public Throwable failure() {
      return mFailure;
    }

    public boolean isCancelled() {
      return (mFailure instanceof CancellationException);
    }

    public boolean isFailed() {
      return true;
    }

    public boolean isEvaluating() {
      return false;
    }

    public boolean isSet() {
      return false;
    }

    public void to(@NotNull final Evaluation<? super V> evaluation) {
      evaluation.fail(mFailure);
    }

    public V value() {
      throw new IllegalStateException("invalid state: Failed");
    }
  }

  private static class SettledState<V> extends SimpleState<V> {

    private static final SettledState<?> sInstance = new SettledState<Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private SettledState() {
      super("Settled");
    }

    @NotNull
    private Object readResolve() throws ObjectStreamException {
      return sInstance;
    }

    public void yieldTo(@NotNull final YieldOutputs<? super V> outputs) {
      ConstantConditions.unsupported();
    }

    public void addTo(@NotNull final EvaluationCollection<? super V> evaluation) {
      evaluation.set();
    }

    @NotNull
    public Throwable failure() {
      throw new IllegalStateException("invalid state: Settled");
    }

    public boolean isCancelled() {
      return false;
    }

    public boolean isFailed() {
      return false;
    }

    public boolean isEvaluating() {
      return false;
    }

    public boolean isSet() {
      return false;
    }

    public void to(@NotNull final Evaluation<? super V> evaluation) {
      ConstantConditions.unsupported();
    }

    public V value() {
      throw new IllegalStateException("invalid state: Settled");
    }
  }

  private static class ValueState<V> extends SimpleState<V> {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final V mValue;

    private ValueState(final V value) {
      super("Set, value=" + value);
      mValue = value;
    }

    public void yieldTo(@NotNull final YieldOutputs<? super V> outputs) {
      outputs.yieldValue(mValue);
    }

    public void addTo(@NotNull final EvaluationCollection<? super V> evaluation) {
      evaluation.addValue(mValue);
    }

    @NotNull
    public Throwable failure() {
      throw new IllegalStateException("invalid state: Set");
    }

    public boolean isCancelled() {
      return false;
    }

    public boolean isFailed() {
      return false;
    }

    public boolean isEvaluating() {
      return false;
    }

    public boolean isSet() {
      return true;
    }

    public void to(@NotNull final Evaluation<? super V> evaluation) {
      evaluation.set(mValue);
    }

    public V value() {
      return mValue;
    }
  }
}

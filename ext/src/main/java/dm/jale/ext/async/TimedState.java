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

package dm.jale.ext.async;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.concurrent.CancellationException;

import dm.jale.async.Evaluation;
import dm.jale.async.EvaluationCollection;
import dm.jale.async.EvaluationState;
import dm.jale.config.BuildConfig;
import dm.jale.util.ConstantConditions;

/**
 * Created by davide-maestroni on 01/11/2018.
 */
public abstract class TimedState<V> implements EvaluationState<V>, Serializable {

  private TimedState() {
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public static <V> TimedState<V> cancelled() {
    return new FailureState<V>(new CancellationException("cancelled state"));
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public static <V> TimedState<V> evaluating() {
    return new EvaluatingState<V>();
  }

  @NotNull
  public static <V> TimedState<V> ofFailure(@NotNull final Throwable reason) {
    return new FailureState<V>(reason);
  }

  @NotNull
  public static <V> TimedState<V> ofValue(final V value) {
    return new ValueState<V>(value);
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public static <V> TimedState<V> settled() {
    return new SettledState<V>();
  }

  public abstract void addTo(@NotNull EvaluationCollection<? super V> evaluation);

  public abstract long timestamp();

  private static class EvaluatingState<V> extends TimedState<V> {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final long mTimestamp;

    private EvaluatingState() {
      mTimestamp = System.currentTimeMillis();
    }

    public void addTo(@NotNull final EvaluationCollection<? super V> evaluation) {
      ConstantConditions.unsupported();
    }

    @NotNull
    public Throwable failure() {
      throw new IllegalStateException("invalid state: Evaluating");
    }

    public long timestamp() {
      return mTimestamp;
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

  private static class FailureState<V> extends TimedState<V> {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final Throwable mFailure;

    private final long mTimestamp;

    private FailureState(@NotNull final Throwable failure) {
      mFailure = ConstantConditions.notNull("failure", failure);
      mTimestamp = System.currentTimeMillis();
    }

    public void addTo(@NotNull final EvaluationCollection<? super V> evaluation) {
      evaluation.addFailure(mFailure);
    }

    public long timestamp() {
      return mTimestamp;
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

  private static class SettledState<V> extends TimedState<V> {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final long mTimestamp;

    private SettledState() {
      mTimestamp = System.currentTimeMillis();
    }

    public void addTo(@NotNull final EvaluationCollection<? super V> evaluation) {
      evaluation.set();
    }

    public long timestamp() {
      return mTimestamp;
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

  private static class ValueState<V> extends TimedState<V> {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final long mTimestamp;

    private final V mValue;

    private ValueState(final V value) {
      mValue = value;
      mTimestamp = System.currentTimeMillis();
    }

    public void addTo(@NotNull final EvaluationCollection<? super V> evaluation) {
      evaluation.addValue(mValue);
    }

    public long timestamp() {
      return mTimestamp;
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

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

package dm.james.async;

import org.jetbrains.annotations.NotNull;

import java.io.ObjectStreamException;
import java.io.Serializable;

import dm.james.promise.CancellationException;

/**
 * Created by davide-maestroni on 01/11/2018.
 */
public abstract class SimpleState<V> implements AsyncState<V> {

  @NotNull
  public static <V> SimpleState<V> ofFailure(final Throwable reason) {
    return new FailureState<V>(reason);
  }

  @NotNull
  public static <V> SimpleState<V> ofValue(final V value) {
    return new ValueState<V>(value);
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public static <V> SimpleState<V> pending() {
    return (SimpleState<V>) PendingState.sInstance;
  }

  private static class FailureState<V> extends SimpleState<V> implements Serializable {

    private final Throwable mReason;

    private FailureState(final Throwable reason) {
      mReason = reason;
    }

    public Throwable failure() {
      return mReason;
    }

    public boolean isCancelled() {
      return (mReason instanceof CancellationException);
    }

    public boolean isFailed() {
      return !isCancelled();
    }

    public boolean isPending() {
      return false;
    }

    public boolean isValued() {
      return false;
    }

    public V value() {
      throw new IllegalStateException();
    }
  }

  private static class PendingState<V> extends SimpleState<V> implements Serializable {

    private static final PendingState<?> sInstance = new PendingState<Object>();

    Object readResolve() throws ObjectStreamException {
      return sInstance;
    }

    public Throwable failure() {
      throw new IllegalStateException();
    }

    public boolean isCancelled() {
      return false;
    }

    public boolean isFailed() {
      return false;
    }

    public boolean isPending() {
      return true;
    }

    public boolean isValued() {
      return false;
    }

    public V value() {
      throw new IllegalStateException();
    }
  }

  private static class ValueState<V> extends SimpleState<V> implements Serializable {

    private final V mValue;

    private ValueState(final V value) {
      mValue = value;
    }

    public Throwable failure() {
      throw new IllegalStateException();
    }

    public boolean isCancelled() {
      return false;
    }

    public boolean isFailed() {
      return false;
    }

    public boolean isPending() {
      return false;
    }

    public boolean isValued() {
      return true;
    }

    public V value() {
      return mValue;
    }
  }
}

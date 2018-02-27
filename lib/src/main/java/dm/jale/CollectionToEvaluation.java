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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

import dm.jale.eventual.Evaluation;
import dm.jale.eventual.EvaluationCollection;
import dm.jale.log.Logger;
import dm.jale.util.ConstantConditions;
import dm.jale.util.Iterables;

/**
 * Created by davide-maestroni on 02/24/2018.
 */
class CollectionToEvaluation<V> implements EvaluationCollection<V> {

  private final Evaluation<? super Iterable<V>> mEvaluation;

  private final Logger mLogger;

  private final Object mMutex = new Object();

  private final ArrayList<V> mValues = new ArrayList<V>();

  private Throwable mFailure;

  private boolean mIsSet;

  CollectionToEvaluation(@NotNull final Evaluation<? super Iterable<V>> evaluation,
      @NotNull final Logger logger) {
    mEvaluation = ConstantConditions.notNull("evaluation", evaluation);
    mLogger = logger;
  }

  @NotNull
  public EvaluationCollection<V> addFailure(@NotNull final Throwable failure) {
    synchronized (mMutex) {
      checkSet();
      if (mFailure == null) {
        mFailure = failure;
        mValues.clear();

      } else {
        mLogger.wrn("Suppressed failure: %s", failure);
      }
    }

    return this;
  }

  @NotNull
  public EvaluationCollection<V> addFailures(
      @Nullable final Iterable<? extends Throwable> failures) {
    synchronized (mMutex) {
      checkSet();
      if (failures != null) {
        for (final Throwable failure : failures) {
          addFailure(failure);
        }
      }
    }

    return this;
  }

  @NotNull
  public EvaluationCollection<V> addValue(final V value) {
    synchronized (mMutex) {
      checkSet();
      if (mFailure == null) {
        mValues.add(value);
      }
    }

    return this;
  }

  @NotNull
  public EvaluationCollection<V> addValues(@Nullable final Iterable<? extends V> values) {
    synchronized (mMutex) {
      checkSet();
      if ((mFailure == null) && (values != null)) {
        Iterables.addAll(values, mValues);
      }
    }

    return this;
  }

  public void set() {
    synchronized (mMutex) {
      checkSet();
      mIsSet = true;
      if (mFailure == null) {
        mEvaluation.set(mValues);

      } else {
        mEvaluation.fail(mFailure);
      }
    }
  }

  private void checkSet() {
    if (mIsSet) {
      throw new IllegalStateException("loop has already completed");
    }
  }
}

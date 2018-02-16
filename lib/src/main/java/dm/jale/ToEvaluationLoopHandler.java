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

import java.io.NotSerializableException;
import java.io.ObjectStreamException;
import java.io.Serializable;

import dm.jale.async.EvaluationCollection;
import dm.jale.config.BuildConfig;
import dm.jale.util.ConstantConditions;

/**
 * Created by davide-maestroni on 02/05/2018.
 */
class ToEvaluationLoopHandler<V> extends AsyncLoopHandler<V, Void> implements Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final EvaluationCollection<V> mEvaluations;

  @SuppressWarnings("unchecked")
  ToEvaluationLoopHandler(@NotNull final EvaluationCollection<? super V> evaluations) {
    mEvaluations = (EvaluationCollection<V>) ConstantConditions.notNull("evaluations", evaluations);
  }

  @Override
  void addFailure(@NotNull final Throwable failure,
      @NotNull final EvaluationCollection<Void> evaluations) {
    mEvaluations.addFailure(failure);
  }

  @Override
  void addFailures(@Nullable final Iterable<? extends Throwable> failures,
      @NotNull final EvaluationCollection<Void> evaluations) {
    mEvaluations.addFailures(failures);
  }

  @Override
  void addValue(final V value, @NotNull final EvaluationCollection<Void> evaluations) {
    mEvaluations.addValue(value);
  }

  @Override
  void addValues(@Nullable final Iterable<? extends V> values,
      @NotNull final EvaluationCollection<Void> evaluations) {
    mEvaluations.addValues(values);
  }

  @Override
  void set(@NotNull final EvaluationCollection<Void> evaluations) {
    mEvaluations.set();
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    throw new NotSerializableException();
  }
}

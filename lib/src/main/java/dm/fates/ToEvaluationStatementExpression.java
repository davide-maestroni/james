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

import java.io.NotSerializableException;
import java.io.ObjectStreamException;
import java.io.Serializable;

import dm.fates.config.BuildConfig;
import dm.fates.eventual.Evaluation;
import dm.fates.util.ConstantConditions;

/**
 * Created by davide-maestroni on 02/05/2018.
 */
class ToEvaluationStatementExpression<V> extends StatementExpression<V, Void>
    implements Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Evaluation<V> mEvaluation;

  @SuppressWarnings("unchecked")
  ToEvaluationStatementExpression(@NotNull final Evaluation<? super V> evaluation) {
    mEvaluation = (Evaluation<V>) ConstantConditions.notNull("evaluation", evaluation);
  }

  @Override
  void failure(@NotNull final Throwable failure, @NotNull final Evaluation<Void> evaluation) {
    try {
      mEvaluation.fail(failure);

    } finally {
      evaluation.set(null);
    }
  }

  @NotNull
  @Override
  StatementExpression<V, Void> renew() {
    return ConstantConditions.unsupported();
  }

  @Override
  void value(final V value, @NotNull final Evaluation<Void> evaluation) {
    try {
      mEvaluation.set(value);

    } finally {
      evaluation.set(null);
    }
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    throw new NotSerializableException("this object is not serializable");
  }
}

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
import org.jetbrains.annotations.Nullable;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.ArrayList;

import dm.fates.config.BuildConfig;
import dm.fates.eventual.EvaluationCollection;
import dm.fates.eventual.Mapper;
import dm.fates.util.ConstantConditions;
import dm.fates.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/01/2018.
 */
class ElseCatchLoopExpression<V> extends LoopExpression<V, V> implements Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Mapper<? super Throwable, ? extends V> mMapper;

  private final Class<?>[] mTypes;

  ElseCatchLoopExpression(@NotNull final Mapper<? super Throwable, ? extends V> mapper,
      @NotNull final Class<?>[] exceptionTypes) {
    mMapper = ConstantConditions.notNull("mapper", mapper);
    mTypes = ConstantConditions.notNullElements("exception types", exceptionTypes);
  }

  @Override
  void addFailure(@NotNull final Throwable failure,
      @NotNull final EvaluationCollection<V> evaluation) throws Exception {
    for (final Class<?> type : mTypes) {
      if (type.isInstance(failure)) {
        evaluation.addValue(mMapper.apply(failure)).set();
        return;
      }
    }

    super.addFailure(failure, evaluation);
  }

  @Override
  void addFailures(@Nullable final Iterable<? extends Throwable> failures,
      @NotNull final EvaluationCollection<V> evaluation) throws Exception {
    if (failures == null) {
      return;
    }

    final ArrayList<V> outputs = new ArrayList<V>();
    @SuppressWarnings("UnnecessaryLocalVariable") final Class<?>[] types = mTypes;
    @SuppressWarnings("UnnecessaryLocalVariable") final Mapper<? super Throwable, ? extends V>
        mapper = mMapper;
    try {
      for (final Throwable failure : failures) {
        boolean found = false;
        for (final Class<?> type : types) {
          if (type.isInstance(failure)) {
            outputs.add(mapper.apply(failure));
            found = true;
            break;
          }
        }

        if (!found) {
          if (outputs.isEmpty()) {
            evaluation.addFailure(failure);

          } else {
            final ArrayList<V> values = new ArrayList<V>(outputs);
            outputs.clear();
            evaluation.addValues(values).addFailure(failure);
          }
        }
      }

    } finally {
      evaluation.addValues(outputs).set();
    }
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new HandlerProxy<V>(mMapper, mTypes);
  }

  private static class HandlerProxy<V> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private HandlerProxy(final Mapper<? super Throwable, ? extends V> mapper,
        final Class<?>[] exceptionTypes) {
      super(proxy(mapper), exceptionTypes);
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new ElseCatchLoopExpression<V>((Mapper<? super Throwable, ? extends V>) args[0],
            (Class<?>[]) args[1]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

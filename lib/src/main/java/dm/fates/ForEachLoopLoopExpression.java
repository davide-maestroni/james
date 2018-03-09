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
import dm.fates.util.Iterables;
import dm.fates.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/01/2018.
 */
class ForEachLoopLoopExpression<V, R> extends LoopExpression<V, R> implements Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Mapper<? super V, ? extends Iterable<? extends R>> mMapper;

  ForEachLoopLoopExpression(
      @NotNull final Mapper<? super V, ? extends Iterable<? extends R>> mapper) {
    mMapper = ConstantConditions.notNull("mapper", mapper);
  }

  @Override
  @SuppressWarnings("unchecked")
  void addValue(final V value, @NotNull final EvaluationCollection<R> evaluation) throws Exception {
    evaluation.addValues(mMapper.apply(value)).set();
  }

  @Override
  void addValues(@Nullable final Iterable<? extends V> values,
      @NotNull final EvaluationCollection<R> evaluation) throws Exception {
    if (values == null) {
      return;
    }

    final ArrayList<R> outputs = new ArrayList<R>();
    @SuppressWarnings(
        "UnnecessaryLocalVariable") final Mapper<? super V, ? extends Iterable<? extends R>>
        mapper = mMapper;
    try {
      for (final V value : values) {
        Iterables.addAll(mapper.apply(value), outputs);
      }

    } finally {
      evaluation.addValues(outputs).set();
    }
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new HandlerProxy<V, R>(mMapper);
  }

  private static class HandlerProxy<V, R> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private HandlerProxy(final Mapper<? super V, ? extends Iterable<? extends R>> mapper) {
      super(proxy(mapper));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new ForEachLoopLoopExpression<V, R>(
            (Mapper<? super V, ? extends Iterable<? extends R>>) args[0]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

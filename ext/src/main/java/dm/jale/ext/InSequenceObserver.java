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

package dm.jale.ext;

import org.jetbrains.annotations.NotNull;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;

import dm.jale.eventual.EvaluationCollection;
import dm.jale.eventual.Mapper;
import dm.jale.eventual.Observer;
import dm.jale.ext.config.BuildConfig;
import dm.jale.util.ConstantConditions;
import dm.jale.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/16/2018.
 */
class InSequenceObserver<V> implements Observer<EvaluationCollection<V>>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final long mCount;

  private final Mapper<? super V, ? extends V> mIncrement;

  private final V mStart;

  InSequenceObserver(final V start, final long count,
      @NotNull final Mapper<? super V, ? extends V> increment) {
    mCount = ConstantConditions.notNegative("count", count);
    mIncrement = ConstantConditions.notNull("increment", increment);
    mStart = start;
  }

  public void accept(final EvaluationCollection<V> evaluation) throws Exception {
    V value = mStart;
    @SuppressWarnings("UnnecessaryLocalVariable") final long count = mCount;
    @SuppressWarnings("UnnecessaryLocalVariable") final Mapper<? super V, ? extends V> increment =
        mIncrement;
    for (int i = 0; i < count; ++i) {
      evaluation.addValue(value);
      value = increment.apply(value);
    }

    evaluation.set();
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new ObserverProxy<V>(mStart, mCount, mIncrement);
  }

  private static class ObserverProxy<V> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private ObserverProxy(final V start, final long count,
        final Mapper<? super V, ? extends V> increment) {
      super(start, count, proxy(increment));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new InSequenceObserver<V>((V) args[0], (Long) args[2],
            (Mapper<? super V, ? extends V>) args[3]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

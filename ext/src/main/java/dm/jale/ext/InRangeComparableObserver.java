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

import dm.jale.async.AsyncEvaluations;
import dm.jale.async.Mapper;
import dm.jale.async.Observer;
import dm.jale.ext.config.BuildConfig;
import dm.jale.util.ConstantConditions;
import dm.jale.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/16/2018.
 */
class InRangeComparableObserver<V extends Comparable<V>>
    implements Observer<AsyncEvaluations<V>>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final V mEnd;

  private final Mapper<? super V, ? extends V> mIncrement;

  private final boolean mIsInclusive;

  private final V mStart;

  InRangeComparableObserver(@NotNull final V start, @NotNull final V end,
      @NotNull final Mapper<? super V, ? extends V> increment, final boolean isInclusive) {
    mStart = ConstantConditions.notNull("start", start);
    mEnd = ConstantConditions.notNull("end", end);
    mIncrement = ConstantConditions.notNull("increment", increment);
    mIsInclusive = isInclusive;
  }

  public void accept(final AsyncEvaluations<V> evaluations) throws Exception {
    V value = mStart;
    final V end = mEnd;
    final Mapper<? super V, ? extends V> increment = mIncrement;
    if (mIsInclusive) {
      while (value.compareTo(end) < 0) {
        evaluations.addValue(value);
        value = increment.apply(value);
      }

    } else {
      while (value.compareTo(end) <= 0) {
        evaluations.addValue(value);
        value = increment.apply(value);
      }
    }

    evaluations.set();
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new ObserverProxy<V>(mStart, mEnd, mIncrement, mIsInclusive);
  }

  private static class ObserverProxy<V extends Comparable<V>> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private ObserverProxy(final V start, final V end,
        final Mapper<? super V, ? extends V> increment, final boolean isInclusive) {
      super(start, end, proxy(increment), isInclusive);
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new InRangeComparableObserver<V>((V) args[0], (V) args[2],
            (Mapper<? super V, ? extends V>) args[3], (Boolean) args[4]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

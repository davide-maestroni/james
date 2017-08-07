/*
 * Copyright 2017 Davide Maestroni
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

package dm.james;

import org.jetbrains.annotations.NotNull;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;

import dm.james.promise.Mapper;
import dm.james.promise.Observer;
import dm.james.promise.PromiseIterable.CallbackIterable;
import dm.james.util.ConstantConditions;
import dm.james.util.SerializableProxy;

/**
 * Observer generating a range of data.
 * <p>
 * Created by davide-maestroni on 08/07/2017.
 *
 * @param <O> the output data type.
 */
class RangeObserver<O extends Comparable<? super O>>
    implements Observer<CallbackIterable<O>>, Serializable {

  private final O mEnd;

  private final EndpointsType mEndpoints;

  private final Mapper<O, O> mIncrement;

  private final O mStart;

  /**
   * Constructor.
   *
   * @param endpoints the endpoints type.
   * @param start     the first element in the range.
   * @param end       the last element in the range.
   * @param increment the mapper incrementing the current element.
   */
  RangeObserver(@NotNull final EndpointsType endpoints, @NotNull final O start,
      @NotNull final O end, @NotNull final Mapper<O, O> increment) {
    mEndpoints = ConstantConditions.notNull("endpoints type", endpoints);
    mStart = ConstantConditions.notNull("start element", start);
    mEnd = ConstantConditions.notNull("end element", end);
    mIncrement = ConstantConditions.notNull("increment", increment);
  }

  public void accept(final CallbackIterable<O> callback) throws Exception {
    final O start = mStart;
    final O end = mEnd;
    final Mapper<O, O> increment = mIncrement;
    final EndpointsType endpoints = mEndpoints;
    O current = endpoints.isStartInclusive() ? start : increment.apply(start);
    if (start.compareTo(end) <= 0) {
      while (current.compareTo(end) < 0) {
        callback.add(current);
        current = increment.apply(current);
      }

    } else {
      while (current.compareTo(end) > 0) {
        callback.add(current);
        current = increment.apply(current);
      }
    }

    if (endpoints.isEndInclusive() && (current.compareTo(end) == 0)) {
      callback.add(current);
    }

    callback.resolve();
  }

  private Object writeReplace() throws ObjectStreamException {
    return new ObserverProxy<O>(mEndpoints, mStart, mEnd, mIncrement);
  }

  private static class ObserverProxy<O extends Comparable<? super O>> extends SerializableProxy {

    private ObserverProxy(final EndpointsType endpoints, final O start, final O end,
        final Mapper<O, O> increment) {
      super(endpoints, start, end, increment);
    }

    @SuppressWarnings("unchecked")
    Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new RangeObserver<O>((EndpointsType) args[0], (O) args[1], (O) args[2],
            (Mapper<O, O>) args[3]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

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

package dm.james.handler;

import org.jetbrains.annotations.NotNull;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;

import dm.james.math.Operation;
import dm.james.promise.Observer;
import dm.james.promise.PromiseIterable.CallbackIterable;
import dm.james.range.EndpointsType;
import dm.james.util.ConstantConditions;
import dm.james.util.SerializableProxy;

import static dm.james.math.Numbers.getHigherPrecisionOperation;

/**
 * Consumer implementation generating a range of numbers.
 * <p>
 * Created by davide-maestroni on 08/07/2017.
 *
 * @param <N> the number type.
 */
class NumberRangeObserver<N extends Number> implements Observer<CallbackIterable<N>>, Serializable {

  private final N mEnd;

  private final EndpointsType mEndpoints;

  private final N mIncrement;

  private final Operation<? extends Number> mOperation;

  private final N mStart;

  /**
   * Constructor.
   *
   * @param endpoints the endpoints type.
   * @param start     the first number in the range.
   * @param end       the last number in the range.
   * @param increment the increment.
   */
  NumberRangeObserver(@NotNull final EndpointsType endpoints, @NotNull final N start,
      @NotNull final N end, @NotNull final N increment) {
    mEndpoints = ConstantConditions.notNull("endpoints type", endpoints);
    mStart = ConstantConditions.notNull("start element", start);
    mEnd = ConstantConditions.notNull("end element", end);
    mIncrement = ConstantConditions.notNull("increment", increment);
    mOperation = getHigherPrecisionOperation(
        getHigherPrecisionOperation(start.getClass(), increment.getClass()).convert(0).getClass(),
        end.getClass());
  }

  @SuppressWarnings("unchecked")
  public void accept(final CallbackIterable<N> callback) {
    final N start = mStart;
    final N end = mEnd;
    final N increment = mIncrement;
    final EndpointsType endpoints = mEndpoints;
    final Operation<? extends Number> operation = mOperation;
    N current = (N) (endpoints.isStartInclusive() ? operation.convert(start)
        : operation.add(start, increment));
    if (operation.compare(start, end) <= 0) {
      while (operation.compare(current, end) < 0) {
        callback.add(current);
        current = (N) operation.add(current, increment);
      }

    } else {
      while (operation.compare(current, end) > 0) {
        callback.add(current);
        current = (N) operation.add(current, increment);
      }
    }

    if (endpoints.isEndInclusive() && (operation.compare(current, end) == 0)) {
      callback.add((N) operation.convert(end));
    }

    callback.resolve();
  }

  private Object writeReplace() throws ObjectStreamException {
    return new ObserverProxy<N>(mEndpoints, mStart, mEnd, mIncrement);
  }

  private static class ObserverProxy<N extends Number> extends SerializableProxy {

    private ObserverProxy(final EndpointsType endpoints, final N start, final N end,
        final N increment) {
      super(endpoints, start, end, increment);
    }

    @SuppressWarnings("unchecked")
    Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new NumberRangeObserver<N>((EndpointsType) args[0], (N) args[1], (N) args[2],
            (N) args[3]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

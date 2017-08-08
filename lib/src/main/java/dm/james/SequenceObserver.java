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

import dm.james.promise.Observer;
import dm.james.promise.PromiseIterable.CallbackIterable;
import dm.james.range.SequenceIncrement;
import dm.james.util.ConstantConditions;
import dm.james.util.SerializableProxy;

/**
 * Consumer implementation generating a sequence of data.
 * <p>
 * Created by davide-maestroni on 08/07/2017.
 *
 * @param <O> the output data type.
 */
class SequenceObserver<O> implements Observer<CallbackIterable<O>>, Serializable {

  private final SequenceIncrement<O> mNext;

  private final long mSize;

  private final O mStart;

  /**
   * Constructor.
   *
   * @param start the first element of the sequence.
   * @param size  the size of the sequence.
   * @param next  the function computing the next element.
   */
  SequenceObserver(@NotNull final O start, final long size,
      @NotNull final SequenceIncrement<O> next) {
    mStart = ConstantConditions.notNull("start element", start);
    mSize = ConstantConditions.positive("sequence size", size);
    mNext = ConstantConditions.notNull("next function", next);
  }

  public void accept(final CallbackIterable<O> callback) throws Exception {
    O current = mStart;
    final long last = mSize - 1;
    if (last >= 0) {
      callback.add(current);
    }

    @SuppressWarnings("UnnecessaryLocalVariable") final SequenceIncrement<O> next = mNext;
    for (long i = 0; i < last; ++i) {
      current = next.getNext(current, i);
      callback.add(current);
    }

    callback.resolve();
  }

  private Object writeReplace() throws ObjectStreamException {
    return new ObserverProxy<O>(mStart, mSize, mNext);
  }

  private static class ObserverProxy<O> extends SerializableProxy {

    private ObserverProxy(final O start, final long size, final SequenceIncrement<O> next) {
      super(start, size, next);
    }

    @SuppressWarnings("unchecked")
    Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new SequenceObserver<O>((O) args[0], (Long) args[1], (SequenceIncrement<O>) args[2]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

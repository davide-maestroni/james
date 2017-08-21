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

import dm.james.math.Operation;
import dm.james.promise.Mapper;
import dm.james.promise.Observer;
import dm.james.promise.ChainableIterable.CallbackIterable;
import dm.james.range.EndpointsType;
import dm.james.range.SequenceIncrement;
import dm.james.util.ConstantConditions;

import static dm.james.math.Numbers.getHigherPrecisionOperation;
import static dm.james.math.Numbers.getOperation;

/**
 * Created by davide-maestroni on 07/21/2017.
 */
public class Handlers {

  private static final EndpointsType DEFAULT_ENDPOINTS = EndpointsType.INCLUSIVE;

  /**
   * Avoid explicit instantiation.
   */
  protected Handlers() {
    ConstantConditions.avoid();
  }

  /**
   * Returns a consumer generating the specified range of numbers.
   * <br>
   * The stream will generate a range of numbers up to and including the {@code end} number, by
   * applying a default increment of {@code +1} or {@code -1} depending on the comparison between
   * the first and the last number. That is, if the first number is less than the last, the
   * increment will be {@code +1}. On the contrary, if the former is greater than the latter, the
   * increment will be {@code -1}.
   * <br>
   * The endpoint values will be included or not in the range based on the specified type.
   * <br>
   * Note that the {@code end} number will be returned only if the incremented value will exactly
   * match it.
   *
   * @param endpoints the type of endpoints inclusion.
   * @param start     the first number in the range.
   * @param end       the last number in the range.
   * @param <N>       the number type.
   * @return the consumer instance.
   */
  @NotNull
  @SuppressWarnings("unchecked")
  public static <N extends Number> Observer<CallbackIterable<N>> range(
      @NotNull final EndpointsType endpoints, @NotNull final N start, @NotNull final N end) {
    final Operation<?> operation = getHigherPrecisionOperation(start.getClass(), end.getClass());
    return range(endpoints, start, end,
        (N) getOperation(start.getClass()).convert((operation.compare(start, end) <= 0) ? 1 : -1));
  }

  /**
   * Returns a consumer generating the specified range of numbers.
   * <br>
   * The stream will generate a range of numbers by applying the specified increment up to the
   * {@code end} number.
   * <br>
   * The endpoint values will be included or not in the range based on the specified type.
   * <br>
   * Note that the {@code end} number will be returned only if the incremented value will exactly
   * match it.
   *
   * @param endpoints the type of endpoints inclusion.
   * @param start     the first number in the range.
   * @param end       the last number in the range.
   * @param increment the increment to apply to the current number.
   * @param <N>       the number type.
   * @return the consumer instance.
   */
  @NotNull
  public static <N extends Number> Observer<CallbackIterable<N>> range(
      @NotNull final EndpointsType endpoints, @NotNull final N start, @NotNull final N end,
      @NotNull final N increment) {
    return new NumberRangeObserver<N>(endpoints, start, end, increment);
  }

  /**
   * Returns a consumer generating the specified range of data.
   * <br>
   * The generated data will start from the specified first one up to the specified last one, by
   * computing each next element through the specified function.
   * <br>
   * The endpoint values will be included or not in the range based on the specified type.
   *
   * @param endpoints the type of endpoints inclusion.
   * @param start     the first element in the range.
   * @param end       the last element in the range.
   * @param increment the mapper incrementing the current element.
   * @param <O>       the output data type.
   * @return the consumer instance.
   */
  @NotNull
  public static <O extends Comparable<? super O>> Observer<CallbackIterable<O>> range(
      @NotNull final EndpointsType endpoints, @NotNull final O start, @NotNull final O end,
      @NotNull final Mapper<O, O> increment) {
    return new RangeObserver<O>(endpoints, start, end, increment);
  }

  @NotNull
  public static <N extends Number> Observer<CallbackIterable<N>> range(@NotNull final N start,
      @NotNull final N end) {
    return range(DEFAULT_ENDPOINTS, start, end);
  }

  @NotNull
  public static <N extends Number> Observer<CallbackIterable<N>> range(@NotNull final N start,
      @NotNull final N end, @NotNull final N increment) {
    return range(DEFAULT_ENDPOINTS, start, end, increment);
  }

  @NotNull
  public static <O extends Comparable<? super O>> Observer<CallbackIterable<O>> range(
      @NotNull final O start, @NotNull final O end, @NotNull final Mapper<O, O> increment) {
    return range(DEFAULT_ENDPOINTS, start, end, increment);
  }

  /**
   * Returns a consumer generating the specified sequence of data.
   * <br>
   * The generated data will start from the specified first and will produce the specified number
   * of elements, by computing each next one through the specified function.
   *
   * @param start the first element of the sequence.
   * @param size  the size of the sequence.
   * @param next  the function computing the next element.
   * @param <O>   the data type.
   * @return the consumer instance.
   * @throws java.lang.IllegalArgumentException if the size is not positive.
   */
  @NotNull
  public static <O> Observer<CallbackIterable<O>> sequence(@NotNull final O start, final long size,
      @NotNull final SequenceIncrement<O> next) {
    return new SequenceObserver<O>(start, size, next);
  }
}

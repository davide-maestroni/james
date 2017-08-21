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

package dm.james.util;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A builder of backoff instances.
 * <br>
 * This class is useful to build a backoff policy, returning a delay in milliseconds to apply when
 * a counter exceeds a specified limit.
 * <p>
 * Created by davide-maestroni on 05/10/2016.
 */
public class Backoffs {

  private static final Backoff<?> sNoDelay = new Backoff<Object>() {

    public long getDelay(@NotNull final Object o) {
      return -1;
    }
  };

  /**
   * Constructor.
   */
  protected Backoffs() {
    ConstantConditions.avoid();
  }

  /**
   * Returns a constant backoff.
   * <br>
   * The backoff will always return the specified delay.
   *
   * @param delay    the delay value.
   * @param timeUnit the delay unit.
   * @return the backoff instance.
   * @throws IllegalArgumentException if the delay is negative.
   */
  @NotNull
  public static <T> Backoff<T> constantDelay(@NotNull final IntConverter<T> converter,
      final int offset, final long delay, @NotNull final TimeUnit timeUnit) {
    return new ConstantBackoff<T>(converter, offset, timeUnit.toMillis(delay));
  }

  /**
   * Returns an exponentially increasing backoff.
   * <br>
   * The backoff will return a delay computed as: {@code delay * 2^(count - 1)}.
   *
   * @param delay    the delay value.
   * @param timeUnit the delay unit.
   * @return the backoff instance.
   * @throws IllegalArgumentException if the delay is negative.
   */
  @NotNull
  public static <T> Backoff<T> exponentialDelay(@NotNull final IntConverter<T> converter,
      final int offset, final long delay, @NotNull final TimeUnit timeUnit) {
    return new ExponentialBackoff<T>(converter, offset, timeUnit.toMillis(delay));
  }

  /**
   * Returns a de-correlated jitter backoff.
   * <br>
   * The backoff will return a delay computed by taking in consideration the previous jitter delay.
   * <p>
   * Note that this particular implementation tries to scale the maximum jitter on the count value.
   *
   * @param delay    the delay value.
   * @param timeUnit the delay unit.
   * @return the backoff instance.
   * @throws IllegalArgumentException if the delay is negative.
   */
  @NotNull
  public static <T> Backoff<T> jitterDelay(@NotNull final IntConverter<T> converter,
      final int offset, final long delay, @NotNull final TimeUnit timeUnit) {
    return new DecorrelatedJitterBackoff<T>(converter, offset, timeUnit.toMillis(delay));
  }

  /**
   * Returns an linearly increasing backoff.
   * <br>
   * The backoff will return a delay computed as: {@code delay * count}.
   *
   * @param delay    the delay value.
   * @param timeUnit the delay unit.
   * @return the backoff instance.
   * @throws IllegalArgumentException if the delay is negative.
   */
  @NotNull
  public static <T> Backoff<T> linearDelay(@NotNull final IntConverter<T> converter,
      final int offset, final long delay, @NotNull final TimeUnit timeUnit) {
    return new LinearBackoff<T>(converter, offset, timeUnit.toMillis(delay));
  }

  /**
   * Returns the no delay backoff instance.
   * <br>
   * The backoff will always return {@code NO_DELAY}.
   *
   * @return the backoff instance.
   */
  @NotNull
  @SuppressWarnings("unchecked")
  public static <T> Backoff<T> noDelay() {
    return (Backoff<T>) sNoDelay;
  }

  /**
   * Sums the specified backoff to this one.
   * <br>
   * For each input count, if at least one of the returned delays is different than
   * {@code NO_DELAY}, its value is returned. If both are different, the sum of the two values
   * is returned.
   *
   * @return the summed backoff policy.
   */
  @NotNull
  public static <T> Backoff<T> sum(@NotNull final Backoff<T> first,
      @NotNull final Backoff<T> second) {
    return new SummedBackoff<T>(first, second);
  }

  /**
   * Caps this backoff policy to the specified maximum delay.
   *
   * @param value the delay value.
   * @param unit  the delay unit.
   * @return the capped backoff policy.
   * @throws IllegalArgumentException if the delay is negative.
   */
  @NotNull
  public static <T> Backoff<T> withCap(@NotNull final Backoff<T> backoff, final long value,
      @NotNull final TimeUnit unit) {
    return new CappedBackoff<T>(backoff, unit.toMillis(value));
  }

  /**
   * Adds jitter to this backoff policy.
   *
   * @param percentage a floating number between 0 and 1 indicating the percentage of delay to
   *                   randomize.
   * @return the backoff policy with jitter.
   * @throws IllegalArgumentException if the percentage is outside the [0, 1] range.
   */
  @NotNull
  public static <T> Backoff<T> withJitter(@NotNull final Backoff<T> backoff,
      final float percentage) {
    return new JitterBackoff<T>(backoff, percentage);
  }

  public interface IntConverter<T> {

    int toInt(T input);
  }

  /**
   * Capped delay backoff policy.
   */
  private static class CappedBackoff<T> implements Backoff<T>, Serializable {

    private final Backoff<T> mBackoff;

    private final long mDelay;

    /**
     * Constructor.
     *
     * @param backoff     the wrapped backoff instance.
     * @param delayMillis the maximum delay in milliseconds.
     */
    private CappedBackoff(@NotNull final Backoff<T> backoff, final long delayMillis) {
      mBackoff = backoff;
      mDelay = ConstantConditions.notNegative("delayMillis", delayMillis);
    }

    public long getDelay(@NotNull final T t) throws Exception {
      return Math.min(mBackoff.getDelay(t), mDelay);
    }
  }

  /**
   * Constant backoff policy.
   */
  private static class ConstantBackoff<T> implements Backoff<T>, Serializable {

    private final IntConverter<T> mConverter;

    private final long mDelay;

    private final int mOffset;

    /**
     * Constructor.
     *
     * @param offset      the offset count;
     * @param delayMillis the delay in milliseconds.
     * @throws IllegalArgumentException if the delay is negative.
     */
    private ConstantBackoff(@NotNull final IntConverter<T> converter, final int offset,
        final long delayMillis) {
      mConverter = ConstantConditions.notNull("converter", converter);
      mDelay = ConstantConditions.notNegative("delayMillis", delayMillis);
      mOffset = offset;
    }

    public long getDelay(@NotNull final T t) throws Exception {
      if (mConverter.toInt(t) <= mOffset) {
        return -1;
      }

      return mDelay;
    }
  }

  /**
   * De-correlated jitter backoff.
   */
  private static class DecorrelatedJitterBackoff<T> implements Backoff<T>, Serializable {

    private final IntConverter<T> mConverter;

    private final long mDelay;

    private final AtomicLong mLast = new AtomicLong();

    private final int mOffset;

    private final Random mRandom = new Random();

    /**
     * Constructor.
     *
     * @param offset      the offset count;
     * @param delayMillis the delay in milliseconds.
     */
    private DecorrelatedJitterBackoff(@NotNull final IntConverter<T> converter, final int offset,
        final long delayMillis) {
      mConverter = ConstantConditions.notNull("converter", converter);
      mDelay = ConstantConditions.notNegative("delayMillis", delayMillis);
      mOffset = offset;
      mLast.set(delayMillis);
    }

    public long getDelay(@NotNull final T t) throws Exception {
      final int excess = mConverter.toInt(t) - mOffset;
      if (excess <= 0) {
        return -1;
      }

      final long delay = mDelay;
      final double last = Math.IEEEremainder(mLast.get(), delay) + (delay * (1 << (excess - 1)));
      final long newLast = delay + Math.round(((last * 3) - delay) * mRandom.nextDouble());
      mLast.set(newLast);
      return newLast;
    }
  }

  /**
   * Exponentially increasing backoff policy.
   */
  private static class ExponentialBackoff<T> implements Backoff<T>, Serializable {

    private final IntConverter<T> mConverter;

    private final long mDelay;

    private final int mOffset;

    /**
     * Constructor.
     *
     * @param offset      the offset count;
     * @param delayMillis the delay in milliseconds.
     */
    private ExponentialBackoff(@NotNull final IntConverter<T> converter, final int offset,
        final long delayMillis) {
      mConverter = ConstantConditions.notNull("converter", converter);
      mDelay = ConstantConditions.notNegative("delayMillis", delayMillis);
      mOffset = offset;
    }

    public long getDelay(@NotNull final T t) throws Exception {
      final int excess = mConverter.toInt(t) - mOffset;
      if (excess <= 0) {
        return -1;
      }

      return mDelay << (excess - 1);
    }
  }

  /**
   * Backoff policy with jitter addition.
   */
  private static class JitterBackoff<T> implements Backoff<T>, Serializable {

    private final Backoff<T> mBackoff;

    private final float mPercent;

    private final Random mRandom = new Random();

    /**
     * Constructor.
     *
     * @param backoff    the wrapped backoff instance.
     * @param percentage the percentage of delay to randomize.
     * @throws IllegalArgumentException if the percentage is outside the [0, 1] range.
     */
    private JitterBackoff(@NotNull final Backoff<T> backoff, final float percentage) {
      if ((percentage < 0) || (percentage > 1)) {
        throw new IllegalArgumentException(
            "the jitter percentage must be between 0 and 1, but is: " + percentage);
      }

      mBackoff = backoff;
      mPercent = percentage;
    }

    public long getDelay(@NotNull final T t) throws Exception {
      final long delay = mBackoff.getDelay(t);
      if (delay < 0) {
        return delay;
      }

      final float percent = mPercent;
      return Math.round((delay * percent * mRandom.nextDouble()) + (delay * (1 - percent)));
    }
  }

  /**
   * Linearly increasing backoff policy.
   */
  private static class LinearBackoff<T> implements Backoff<T>, Serializable {

    private final IntConverter<T> mConverter;

    private final long mDelay;

    private final int mOffset;

    /**
     * Constructor.
     *
     * @param offset      the offset count;
     * @param delayMillis the delay in milliseconds.
     */
    private LinearBackoff(@NotNull final IntConverter<T> converter, final int offset,
        final long delayMillis) {
      mConverter = ConstantConditions.notNull("converter", converter);
      mDelay = ConstantConditions.notNegative("delayMillis", delayMillis);
      mOffset = offset;
    }

    public long getDelay(@NotNull final T t) throws Exception {
      final int excess = mConverter.toInt(t) - mOffset;
      if (excess <= 0) {
        return -1;
      }

      return mDelay * excess;
    }
  }

  /**
   * Summed backoff policy.
   */
  private static class SummedBackoff<T> implements Backoff<T>, Serializable {

    private final Backoff<T> mFirst;

    private final Backoff<T> mOther;

    /**
     * Constructor.
     *
     * @param first  the first backoff instance.
     * @param second the second backoff instance.
     */
    private SummedBackoff(@NotNull final Backoff<T> first, @NotNull final Backoff<T> second) {
      mFirst = ConstantConditions.notNull("first", first);
      mOther = ConstantConditions.notNull("second", second);
    }

    public long getDelay(@NotNull final T t) throws Exception {
      final long delay1 = mFirst.getDelay(t);
      final long delay2 = mOther.getDelay(t);
      if (delay1 < 0) {
        if (delay2 < 0) {
          return -1;
        }

        return delay2;

      } else if (delay2 < 0) {
        return delay1;
      }

      return delay1 + delay2;
    }
  }
}

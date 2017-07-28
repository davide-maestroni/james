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

import java.util.concurrent.TimeUnit;

/**
 * Created by davide-maestroni on 07/18/2017.
 */
public class TimeUtils {

  private static final long ONE_MILLI_NANOS = TimeUnit.MILLISECONDS.toNanos(1);

  public static long currentTimeIn(@NotNull final TimeUnit timeUnit) {
    if (timeUnit == TimeUnit.NANOSECONDS) {
      return System.nanoTime();
    }

    return timeUnit.convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS);
  }

  /**
   * Performs a {@link java.lang.Thread#sleep(long, int)} using the specified duration as timeout,
   * ensuring that the sleep time is respected even if spurious wake-ups happen in the while.
   *
   * @param time the time value.
   * @param unit the time unit.
   * @throws java.lang.InterruptedException if the current thread is interrupted.
   */
  public static void sleepAtLeast(final long time, @NotNull final TimeUnit unit) throws
      InterruptedException {
    if (time == 0) {
      return;
    }

    if ((unit.compareTo(TimeUnit.MILLISECONDS) >= 0) || ((unit.toNanos(time) % ONE_MILLI_NANOS)
        == 0)) {
      final long startMillis = System.currentTimeMillis();
      while (true) {
        if (!sleepSinceMillis(time, unit, startMillis)) {
          return;
        }
      }
    }

    final long startNanos = System.nanoTime();
    while (true) {
      if (!sleepSinceNanos(time, unit, startNanos)) {
        return;
      }
    }
  }

  /**
   * Performs a {@link java.lang.Thread#sleep(long, int)} as if started from the specified system
   * time in milliseconds, by using the specified time as timeout.
   *
   * @param time      the time value.
   * @param unit      the time unit.
   * @param milliTime the starting system time in milliseconds.
   * @return whether the sleep happened at all.
   * @throws java.lang.IllegalStateException if this duration overflows the maximum sleep time.
   * @throws java.lang.InterruptedException  if the current thread is interrupted.
   * @see System#currentTimeMillis()
   */
  public static boolean sleepSinceMillis(final long time, @NotNull final TimeUnit unit,
      final long milliTime) throws InterruptedException {
    if (time == 0) {
      return false;
    }

    final long millisToSleep = milliTime - System.currentTimeMillis() + unit.toMillis(time);
    if (millisToSleep <= 0) {
      return false;
    }

    TimeUnit.MILLISECONDS.sleep(millisToSleep);
    return true;
  }

  /**
   * Performs a {@link java.lang.Thread#sleep(long, int)} as if started from the specified high
   * precision system time in nanoseconds, by using the specified time as timeout.
   *
   * @param time     the time value.
   * @param unit     the time unit.
   * @param nanoTime the starting system time in nanoseconds.
   * @return whether the sleep happened at all.
   * @throws java.lang.IllegalStateException if this duration overflows the maximum sleep time.
   * @throws java.lang.InterruptedException  if the current thread is interrupted.
   * @see System#nanoTime()
   */
  public static boolean sleepSinceNanos(final long time, @NotNull final TimeUnit unit,
      final long nanoTime) throws InterruptedException {
    if (time == 0) {
      return false;
    }

    final long nanosToSleep = nanoTime - System.nanoTime() + unit.toNanos(time);
    if (nanosToSleep <= 0) {
      return false;
    }

    TimeUnit.NANOSECONDS.sleep(nanosToSleep);
    return true;
  }

  /**
   * Performs an {@link Object#wait()} as if started from the specified system time in
   * milliseconds, by using the specified time.
   * <br>
   * If the specified time is negative, the method will wait indefinitely.
   *
   * @param target    the target object.
   * @param milliTime the starting system time in milliseconds.
   * @param time      the time value.
   * @param unit      the time unit.
   * @return whether the wait happened at all.
   * @throws InterruptedException if the current thread is interrupted.
   * @see System#currentTimeMillis()
   */
  public static boolean waitSinceMillis(@NotNull final Object target, final long milliTime,
      final long time, @NotNull final TimeUnit unit) throws InterruptedException {
    if (time == 0) {
      return false;
    }

    if (time < 0) {
      target.wait();
      return true;
    }

    final long millisToWait = milliTime - System.currentTimeMillis() + unit.toMillis(time);
    if (millisToWait <= 0) {
      return false;
    }

    TimeUnit.MILLISECONDS.timedWait(target, millisToWait);
    return true;
  }

  /**
   * Performs an {@link Object#wait()} as if started from the specified high precision
   * system time in nanoseconds, by using the specified time.
   * <br>
   * If the specified time is negative, the method will wait indefinitely.
   *
   * @param target   the target object.
   * @param nanoTime the starting system time in nanoseconds.
   * @param time     the time value.
   * @param unit     the time unit.
   * @return whether the wait happened at all.
   * @throws InterruptedException if the current thread is interrupted.
   * @see System#nanoTime()
   */
  public static boolean waitSinceNanos(@NotNull final Object target, final long nanoTime,
      final long time, @NotNull final TimeUnit unit) throws InterruptedException {
    if (time == 0) {
      return false;
    }

    if (time < 0) {
      target.wait();
      return true;
    }

    final long nanosToWait = nanoTime - System.nanoTime() + unit.toNanos(time);
    if (nanosToWait <= 0) {
      return false;
    }

    TimeUnit.NANOSECONDS.timedWait(target, nanosToWait);
    return true;
  }

  /**
   * Waits for the specified condition to be true by performing an {@link Object#wait()}
   * and using the specified time.
   * <br>
   * If the specified time is negative, the method will wait indefinitely.
   *
   * @param target    the target object.
   * @param condition the condition to verify.
   * @param time      the time value.
   * @param unit      the time unit.
   * @return whether the check became true before the timeout elapsed.
   * @throws InterruptedException if the current thread is interrupted.
   */
  public static boolean waitUntil(@NotNull final Object target, @NotNull final Condition condition,
      final long time, @NotNull final TimeUnit unit) throws InterruptedException {
    if (time == 0) {
      return condition.isTrue();
    }

    if (time < 0) {
      while (!condition.isTrue()) {
        target.wait();
      }

      return true;
    }

    if ((unit.toNanos(time) % ONE_MILLI_NANOS) == 0) {
      final long startMillis = System.currentTimeMillis();
      while (!condition.isTrue()) {
        if (!waitSinceMillis(target, startMillis, time, unit)) {
          return false;
        }
      }

    } else {
      final long startNanos = System.nanoTime();
      while (!condition.isTrue()) {
        if (!waitSinceNanos(target, startNanos, time, unit)) {
          return false;
        }
      }
    }

    return true;
  }

  /**
   * Interface defining a condition to check.
   */
  public interface Condition {

    /**
     * Checks if true.
     *
     * @return whether the condition is verified.
     */
    boolean isTrue();
  }
}

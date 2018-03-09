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

package dm.fates.ext.backoff;

import org.jetbrains.annotations.NotNull;

import java.io.ObjectStreamException;
import java.util.concurrent.TimeUnit;

import dm.fates.ext.config.BuildConfig;
import dm.fates.util.ConstantConditions;

/**
 * Created by davide-maestroni on 03/01/2018.
 */
public class BackoffPool {

  private BackoffPool() {
    ConstantConditions.avoid();
  }

  @NotNull
  public static Backoff constant(final long delay, @NotNull final TimeUnit timeUnit) {
    return new ConstantBackoff(delay, timeUnit);
  }

  @NotNull
  public static Backoff decorrelatedJitter(final long baseDelay, @NotNull final TimeUnit timeUnit) {
    return new DecorrelatedJitterBackoff(baseDelay, timeUnit);
  }

  @NotNull
  public static Backoff exponential(final long baseDelay, @NotNull final TimeUnit timeUnit) {
    return new ExponentialBackoff(baseDelay, timeUnit);
  }

  @NotNull
  public static Backoff linear(final long baseDelay, @NotNull final TimeUnit timeUnit) {
    return new LinearBackoff(baseDelay, timeUnit);
  }

  @NotNull
  public static Backoff none() {
    return NoBackoff.sInstance;
  }

  @NotNull
  public static Backoff sum(@NotNull final Backoff first, @NotNull final Backoff second) {
    return new SumBackoff(first, second);
  }

  @NotNull
  public static Backoff withCap(final long cap, @NotNull final TimeUnit timeUnit,
      @NotNull final Backoff backoff) {
    return new CappedBackoff(cap, timeUnit, backoff);
  }

  @NotNull
  public static Backoff withCountOffset(final int offset, @NotNull final Backoff backoff) {
    return new CountOffsetBackoff(offset, backoff);
  }

  @NotNull
  public static Backoff withJitter(final float percentage, @NotNull final Backoff backoff) {
    return new JitteredBackoff(percentage, backoff);
  }

  @NotNull
  public static Backoff withTimeOffset(final long offset, @NotNull final TimeUnit timeUnit,
      @NotNull final Backoff backoff) {
    return new TimeOffsetBackoff(offset, timeUnit, backoff);
  }

  private static class NoBackoff extends ConstantBackoff {

    private static final NoBackoff sInstance = new NoBackoff();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    NoBackoff() {
      super(0, TimeUnit.MILLISECONDS);
    }

    @NotNull
    private Object readResolve() throws ObjectStreamException {
      return sInstance;
    }
  }
}

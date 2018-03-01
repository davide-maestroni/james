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

package dm.jale.ext.backoff;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

import dm.jale.util.ConstantConditions;

/**
 * Created by davide-maestroni on 03/01/2018.
 */
public class BackoffPool {

  private BackoffPool() {
    ConstantConditions.avoid();
  }

  @NotNull
  public static Backoff constant(final long delay, @NotNull final TimeUnit timeUnit) {
    return null;
  }

  @NotNull
  public static Backoff decorrelatedJitter(final long delay, @NotNull final TimeUnit timeUnit) {
    return null;
  }

  @NotNull
  public static Backoff exponential(final long delay, @NotNull final TimeUnit timeUnit) {
    return null;
  }

  @NotNull
  public static Backoff linear(final long delay, @NotNull final TimeUnit timeUnit) {
    return null;
  }

  @NotNull
  public static Backoff none() {
    return null;
  }

  @NotNull
  public static Backoff sum(@NotNull final Backoff first, @NotNull final Backoff second) {
    return null;
  }

  @NotNull
  public static Backoff withCap(final long offset, @NotNull final TimeUnit timeUnit,
      @NotNull final Backoff backoff) {
    return null;
  }

  @NotNull
  public static Backoff withCountOffset(final int offset, @NotNull final Backoff backoff) {
    return null;
  }

  @NotNull
  public static Backoff withJitter(final float percentage, @NotNull final Backoff backoff) {
    return null;
  }

  @NotNull
  public static Backoff withTimeOffset(final long offset, @NotNull final TimeUnit timeUnit,
      @NotNull final Backoff backoff) {
    return null;
  }
}

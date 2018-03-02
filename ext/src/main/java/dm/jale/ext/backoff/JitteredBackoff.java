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

import java.io.Serializable;
import java.util.Random;

import dm.jale.util.ConstantConditions;

/**
 * Created by davide-maestroni on 03/02/2018.
 */
class JitteredBackoff implements Backoff, Serializable {

  private final Backoff mBackoff;

  private final float mPercentage;

  private final Random mRandom = new Random();

  JitteredBackoff(final float percentage, @NotNull final Backoff backoff) {
    mBackoff = ConstantConditions.notNull("backoff", backoff);
    if ((percentage < 0) || (percentage > 1)) {
      throw new IllegalArgumentException("the percentage must be in the range [0, 1]");
    }

    mPercentage = percentage;
  }

  public long apply(final int count, final long lastDelay) {
    final float percentage = mPercentage;
    final long delay = mBackoff.apply(count, lastDelay);
    return Math.round((delay * (1 - percentage)) + (delay * percentage * mRandom.nextDouble()));
  }
}

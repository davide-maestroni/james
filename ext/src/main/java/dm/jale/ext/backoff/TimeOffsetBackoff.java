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
import java.util.concurrent.TimeUnit;

import dm.jale.ext.config.BuildConfig;
import dm.jale.util.ConstantConditions;

/**
 * Created by davide-maestroni on 03/02/2018.
 */
class TimeOffsetBackoff implements Backoff, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Backoff mBackoff;

  private final long mOffsetMillis;

  TimeOffsetBackoff(final long offset, @NotNull final TimeUnit timeUnit,
      @NotNull final Backoff backoff) {
    mBackoff = ConstantConditions.notNull("backoff", backoff);
    mOffsetMillis = timeUnit.toMillis(offset);
  }

  public long apply(final int count, final long lastDelay) {
    final long delay = mBackoff.apply(count, lastDelay);
    return (delay > 0) ? Math.max(0, mOffsetMillis + delay) : delay;
  }
}

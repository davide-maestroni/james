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

import java.io.Serializable;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import dm.fates.ext.config.BuildConfig;
import dm.fates.util.ConstantConditions;

/**
 * Created by davide-maestroni on 03/02/2018.
 */
class DecorrelatedJitterBackoff implements Backoff, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final long mDelayMillis;

  private final Random mRandom = new Random();

  DecorrelatedJitterBackoff(final long baseDelay, @NotNull final TimeUnit timeUnit) {
    mDelayMillis = timeUnit.toMillis(ConstantConditions.notNegative("baseDelay", baseDelay));
  }

  public long apply(final int count, final long lastDelay) {
    return mDelayMillis + Math.round(mRandom.nextDouble() * lastDelay * 3);
  }
}

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

import dm.jale.util.ConstantConditions;

/**
 * Created by davide-maestroni on 03/02/2018.
 */
class CountOffsetBackoff implements Backoff, Serializable {

  private final Backoff mBackoff;

  private final int mOffset;

  CountOffsetBackoff(final int offset, @NotNull final Backoff backoff) {
    mBackoff = ConstantConditions.notNull("backoff", backoff);
    mOffset = offset;
  }

  public long apply(final int count, final long lastDelay) {
    final int offset = count + mOffset;
    return (offset >= 0) ? mBackoff.apply(offset, lastDelay) : 0;
  }
}

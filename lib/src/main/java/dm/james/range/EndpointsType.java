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

package dm.james.range;

/**
 * Enumeration defining whether the endpoints are included or not in the range.
 * <p>
 * Created by davide-maestroni on 08/07/2017.
 */
public enum EndpointsType {

  /**
   * Inclusive endpoints.
   * <br>
   * Both starting and ending endpoints are included in the range.
   */
  INCLUSIVE(true, true),

  /**
   * Exclusive starting endpoint.
   * <br>
   * Only the ending endpoint is included in the range.
   */
  START_EXCLUSIVE(false, true),

  /**
   * Exclusive ending endpoint.
   * <br>
   * Only the starting endpoint is included in the range.
   */
  END_EXCLUSIVE(true, false),

  /**
   * Exclusive endpoints.
   * <br>
   * Both starting and ending endpoints are excluded from the range.
   */
  EXCLUSIVE(false, false);

  private final boolean mIsEndInclusive;

  private final boolean mIsStartInclusive;

  /**
   * Constructor.
   *
   * @param isStartInclusive if the starting endpoint is included in the range.
   * @param isEndInclusive   if the ending endpoint is included in the range.
   */
  EndpointsType(final boolean isStartInclusive, final boolean isEndInclusive) {
    mIsStartInclusive = isStartInclusive;
    mIsEndInclusive = isEndInclusive;
  }

  /**
   * Returns whether the ending endpoint is included in the range.
   *
   * @return if the ending endpoint is included in the range.
   */
  public boolean isEndInclusive() {
    return mIsEndInclusive;
  }

  /**
   * Returns whether the starting endpoint is included in the range.
   *
   * @return if the starting endpoint is included in the range.
   */
  public boolean isStartInclusive() {
    return mIsStartInclusive;
  }
}

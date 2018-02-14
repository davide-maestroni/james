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

package dm.jale;

/**
 * Created by davide-maestroni on 02/14/2018.
 */
class CombinationState<S> {

  private final int mCount;

  private boolean mIsRejected;

  private int mResolved;

  private S mState;

  CombinationState(final int count) {
    mCount = count;
  }

  S get() {
    return mState;
  }

  boolean isFailed() {
    return mIsRejected;
  }

  boolean set() {
    return (++mResolved >= mCount);
  }

  void set(final S state) {
    mState = state;
  }

  void setFailed() {
    mIsRejected = true;
  }
}

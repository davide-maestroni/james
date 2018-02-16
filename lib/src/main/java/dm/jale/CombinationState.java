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

import org.jetbrains.annotations.NotNull;

import dm.jale.util.ConstantConditions;

/**
 * Created by davide-maestroni on 02/14/2018.
 */
class CombinationState<S> {

  private final int mCount;

  private volatile Throwable mFailure;

  private int mSet;

  private S mState;

  CombinationState(final int count) {
    mCount = count;
  }

  S get() {
    return mState;
  }

  Throwable getFailure() {
    return mFailure;
  }

  boolean isFailed() {
    return (mFailure != null);
  }

  void setFailed(@NotNull final Throwable failure) {
    mFailure = ConstantConditions.notNull("failure", failure);
  }

  boolean set() {
    return (++mSet >= mCount);
  }

  void set(final S state) {
    mState = state;
  }
}

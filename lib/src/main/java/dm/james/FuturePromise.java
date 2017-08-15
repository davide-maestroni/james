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

package dm.james;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Future;

import dm.james.promise.Promise;
import dm.james.util.ConstantConditions;

/**
 * Created by davide-maestroni on 08/15/2017.
 */
class FuturePromise<O> extends PromiseWrapper<O> {

  private final Future<?> mFuture;

  private final boolean mMayInterruptIfRunning;

  FuturePromise(@NotNull final Promise<O> promise, @NotNull final Future<?> future,
      final boolean mayInterruptIfRunning) {
    super(promise);
    mFuture = ConstantConditions.notNull("future", future);
    mMayInterruptIfRunning = mayInterruptIfRunning;
  }

  @Override
  public boolean cancel() {
    final boolean cancelled = super.cancel();
    mFuture.cancel(mMayInterruptIfRunning);
    return cancelled;
  }

  @NotNull
  protected <R> Promise<R> newInstance(@NotNull final Promise<R> promise) {
    return new FuturePromise<R>(promise, mFuture, mMayInterruptIfRunning);
  }
}

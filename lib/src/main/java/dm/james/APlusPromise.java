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
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

import dm.james.promise.Mapper;
import dm.james.promise.Observer;
import dm.james.promise.Promise;
import dm.james.promise.Provider;
import dm.james.promise.RejectionException;
import dm.james.util.ConstantConditions;

/**
 * Created by davide-maestroni on 07/21/2017.
 */
class APlusPromise<O> implements Promise<O> {

  private final Bond mBond;

  private final Promise<O> mPromise;

  APlusPromise(@NotNull final Bond bond, @NotNull final Promise<O> promise) {
    mBond = ConstantConditions.notNull("bond", bond);
    mPromise = promise.apply(bond.<O>cache());
  }

  @NotNull
  public <R> Promise<R> apply(@NotNull final Mapper<Promise<O>, Promise<R>> mapper) {
    return new APlusPromise<R>(mBond, mPromise.apply(mapper));
  }

  public O get() {
    return mPromise.get();
  }

  public O get(final long timeout, @NotNull final TimeUnit timeUnit) {
    return mPromise.get(timeout, timeUnit);
  }

  @Nullable
  public RejectionException getError() {
    return mPromise.getError();
  }

  @Nullable
  public RejectionException getError(final long timeout, @NotNull final TimeUnit timeUnit) {
    return mPromise.getError(timeout, timeUnit);
  }

  public RejectionException getErrorOr(final RejectionException other, final long timeout,
      @NotNull final TimeUnit timeUnit) {
    return mPromise.getErrorOr(other, timeout, timeUnit);
  }

  public O getOr(final O other, final long timeout, @NotNull final TimeUnit timeUnit) {
    return mPromise.getOr(other, timeout, timeUnit);
  }

  public boolean isBound() {
    return mPromise.isBound();
  }

  public boolean isFulfilled() {
    return mPromise.isFulfilled();
  }

  public boolean isPending() {
    return mPromise.isPending();
  }

  public boolean isRejected() {
    return mPromise.isRejected();
  }

  public boolean isResolved() {
    return mPromise.isResolved();
  }

  @NotNull
  public <R> Promise<R> then(@NotNull final StatelessProcessor<O, R> processor) {
    return new APlusPromise<R>(mBond, mPromise.then(processor));
  }

  @NotNull
  public <R> Promise<R> then(@Nullable final Handler<O, R, Callback<R>> outputHandler,
      @Nullable final Handler<Throwable, R, Callback<R>> errorHandler,
      @Nullable final Observer<Callback<R>> emptyHandler) {
    return new APlusPromise<R>(mBond, mPromise.then(outputHandler, errorHandler, emptyHandler));
  }

  @NotNull
  public Promise<O> thenCatch(@NotNull final Mapper<Throwable, O> mapper) {
    return new APlusPromise<O>(mBond, mPromise.thenCatch(mapper));
  }

  @NotNull
  public Promise<O> thenFill(@NotNull final Provider<O> provider) {
    return new APlusPromise<O>(mBond, mPromise.thenFill(provider));
  }

  @NotNull
  public <R> Promise<R> thenMap(@NotNull final Mapper<O, R> mapper) {
    return new APlusPromise<R>(mBond, mPromise.thenMap(mapper));
  }

  public boolean waitFulfilled(final long timeout, @NotNull final TimeUnit timeUnit) {
    return mPromise.waitFulfilled(timeout, timeUnit);
  }

  public boolean waitPending(final long timeout, @NotNull final TimeUnit timeUnit) {
    return mPromise.waitPending(timeout, timeUnit);
  }

  public boolean waitRejected(final long timeout, @NotNull final TimeUnit timeUnit) {
    return mPromise.waitRejected(timeout, timeUnit);
  }

  public boolean waitResolved(final long timeout, @NotNull final TimeUnit timeUnit) {
    return mPromise.waitResolved(timeout, timeUnit);
  }
}

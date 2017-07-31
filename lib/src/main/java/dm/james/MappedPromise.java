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

import dm.james.promise.Action;
import dm.james.promise.Mapper;
import dm.james.promise.Observer;
import dm.james.promise.Promise;
import dm.james.promise.RejectionException;
import dm.james.util.ConstantConditions;

/**
 * Created by davide-maestroni on 07/21/2017.
 */
class MappedPromise<O> implements Promise<O> {

  private final Mapper<Promise<?>, Promise<?>> mMapper;

  private final Promise<O> mPromise;

  @SuppressWarnings("unchecked")
  MappedPromise(@NotNull final Mapper<Promise<?>, Promise<?>> mapper,
      @NotNull final Promise<O> promise) {
    mMapper = ConstantConditions.notNull("mapper", mapper);
    try {
      mPromise = (Promise<O>) mapper.apply(promise);

    } catch (final Exception e) {
      throw RejectionException.wrapIfNotRejectionException(e);
    }
  }

  @NotNull
  public <R> Promise<R> apply(@NotNull final Mapper<Promise<O>, Promise<R>> mapper) {
    return new MappedPromise<R>(mMapper, mPromise.apply(mapper));
  }

  @NotNull
  public Promise<O> catchAny(@NotNull final Mapper<Throwable, O> mapper) {
    return new MappedPromise<O>(mMapper, mPromise.catchAny(mapper));
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
  public <R> Promise<R> then(@Nullable final Handler<O, R, ? super Callback<R>> outputHandler,
      @Nullable final Handler<Throwable, R, ? super Callback<R>> errorHandler) {
    return new MappedPromise<R>(mMapper, mPromise.then(outputHandler, errorHandler));
  }

  @NotNull
  public <R> Promise<R> then(@NotNull final Mapper<O, R> mapper) {
    return new MappedPromise<R>(mMapper, mPromise.then(mapper));
  }

  @NotNull
  public <R> Promise<R> then(@NotNull final Processor<O, R> processor) {
    return new MappedPromise<R>(mMapper, mPromise.then(processor));
  }

  public void waitResolved() {
    mPromise.waitResolved();
  }

  public boolean waitResolved(final long timeout, @NotNull final TimeUnit timeUnit) {
    return mPromise.waitResolved(timeout, timeUnit);
  }

  @NotNull
  public Promise<O> whenFulfilled(@NotNull final Observer<O> observer) {
    return new MappedPromise<O>(mMapper, mPromise.whenFulfilled(observer));
  }

  @NotNull
  public Promise<O> whenRejected(@NotNull final Observer<Throwable> observer) {
    return new MappedPromise<O>(mMapper, mPromise.whenRejected(observer));
  }

  @NotNull
  public Promise<O> whenResolved(@NotNull final Action action) {
    return new MappedPromise<O>(mMapper, mPromise.whenResolved(action));
  }

  @NotNull
  Mapper<Promise<?>, Promise<?>> getMapper() {
    return mMapper;
  }
}

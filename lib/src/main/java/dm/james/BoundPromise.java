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

import java.io.ObjectStreamException;
import java.io.Serializable;

import dm.james.promise.DeferredPromise;
import dm.james.promise.Promise;
import dm.james.util.ConstantConditions;

/**
 * Created by davide-maestroni on 07/21/2017.
 */
class BoundPromise<I, O> extends PromiseWrapper<O> implements Serializable {

  private final Promise<I> mPromise;

  private BoundPromise(@NotNull final Promise<I> promise,
      @NotNull final DeferredPromise<I, O> deferred) {
    super(deferred);
    mPromise = promise;
  }

  @NotNull
  static <I, O> BoundPromise<I, O> create(@NotNull final Promise<I> promise,
      @NotNull final DeferredPromise<I, O> deferred) {
    final BoundPromise<I, O> boundPromise =
        new BoundPromise<I, O>(ConstantConditions.notNull("promise", promise), deferred);
    promise.then(new DeferredHandlerFulfill<I>(deferred), new DeferredHandlerReject<I>(deferred));
    return boundPromise;
  }

  @Override
  public boolean cancel() {
    return mPromise.cancel();
  }

  @NotNull
  @SuppressWarnings("unchecked")
  protected <R> Promise<R> newInstance(@NotNull final Promise<R> promise) {
    return new BoundPromise<I, R>(mPromise, (DeferredPromise<I, R>) promise);
  }

  @SuppressWarnings("unchecked")
  private Object writeReplace() throws ObjectStreamException {
    return new PromiseProxy<I, O>(mPromise, (DeferredPromise<I, O>) wrapped());
  }

  private static class DeferredHandlerFulfill<O> implements Handler<O, Callback<O>>, Serializable {

    private final DeferredPromise<O, ?> mDeferred;

    private DeferredHandlerFulfill(@NotNull final DeferredPromise<O, ?> deferred) {
      mDeferred = deferred;
    }

    public void accept(final O input, final Callback<O> callback) {
      mDeferred.resolve(input);
    }
  }

  private static class DeferredHandlerReject<O>
      implements Handler<Throwable, Callback<O>>, Serializable {

    private final DeferredPromise<O, ?> mDeferred;

    private DeferredHandlerReject(@NotNull final DeferredPromise<O, ?> deferred) {
      mDeferred = deferred;
    }

    public void accept(final Throwable reason, final Callback<O> callback) throws Exception {
      mDeferred.reject(reason);
    }
  }

  private static class PromiseProxy<I, O> implements Serializable {

    private final DeferredPromise<I, O> mDeferred;

    private final Promise<I> mPromise;

    private PromiseProxy(@NotNull final Promise<I> promise,
        @NotNull final DeferredPromise<I, O> deferred) {
      mPromise = promise;
      mDeferred = deferred;
    }

    Object readResolve() throws ObjectStreamException {
      return BoundPromise.create(mPromise, mDeferred);
    }
  }
}

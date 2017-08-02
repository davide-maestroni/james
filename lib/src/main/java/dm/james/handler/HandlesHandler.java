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

package dm.james.handler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;

import dm.james.promise.ObserverHandler;
import dm.james.promise.Promise.Callback;
import dm.james.promise.Promise.Handler;

/**
 * Created by davide-maestroni on 08/02/2017.
 */
class HandlesHandler<I, O> implements Handler<I, O> {

  private final ObserverHandler<Throwable, O, Callback<O>> mErrorHandler;

  private final ObserverHandler<I, O, Callback<O>> mOutputHandler;

  @SuppressWarnings("unchecked")
  HandlesHandler(@Nullable final ObserverHandler<I, O, ? super Callback<O>> outputHandler,
      @Nullable final ObserverHandler<Throwable, O, ? super Callback<O>> errorHandler) {
    mOutputHandler = (outputHandler != null) ? (ObserverHandler<I, O, Callback<O>>) outputHandler
        : new PassThroughOutputHandler<I, O>();
    mErrorHandler =
        (errorHandler != null) ? (ObserverHandler<Throwable, O, Callback<O>>) errorHandler
            : new PassThroughErrorHandler<O>();
  }

  public void reject(final Throwable reason, @NotNull final Callback<O> callback) throws Exception {
    mErrorHandler.accept(reason, callback);
  }

  public void resolve(final I input, @NotNull final Callback<O> callback) throws Exception {
    mOutputHandler.accept(input, callback);
  }

  private static class PassThroughErrorHandler<O>
      implements ObserverHandler<Throwable, O, Callback<O>>, Serializable {

    public void accept(final Throwable input, @NotNull final Callback<O> callback) {
      callback.reject(input);
    }
  }

  private static class PassThroughOutputHandler<O, R>
      implements ObserverHandler<O, R, Callback<R>>, Serializable {

    @SuppressWarnings("unchecked")
    public void accept(final O input, @NotNull final Callback<R> callback) {
      callback.resolve((R) input);
    }
  }
}

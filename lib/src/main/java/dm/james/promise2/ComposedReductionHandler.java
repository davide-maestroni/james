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

package dm.james.promise2;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;

import dm.james.promise.Mapper;
import dm.james.promise2.Promise.ReductionFulfill;
import dm.james.promise2.Promise.ReductionHandler;
import dm.james.promise2.Promise.ReductionReject;
import dm.james.promise2.Promise.ReductionResolve;
import dm.james.promise2.Thenable.Callback;
import dm.james.util.SerializableProxy;

/**
 * Created by davide-maestroni on 12/11/2017.
 */
class ComposedReductionHandler<E, R, S, C extends Callback<R>>
    implements ReductionHandler<E, R, S, C>, Serializable {

  // TODO: 11/12/2017 move to valuepromise

  // TODO: 11/12/2017 serializable + singleton
  private static final Mapper<?, ?> sDefaultCreate = new Mapper<Object, Object>() {

    public Object apply(final Object input) {
      return null;
    }
  };

  private static final ReductionFulfill<?, ?, ?, ?> sDefaultFulfill =
      new ReductionFulfill<Object, Object, Object, Callback<Object>>() {

        public Object fulfill(final Object state, final Object value,
            @NotNull final Callback<Object> callback) {
          return null;
        }
      };

  private static final ReductionReject<?, ?, ?> sDefaultReject =
      new ReductionReject<Object, Object, Callback<Object>>() {

        public Object reject(final Object state, @NotNull final Throwable reason,
            @NotNull final Callback<Object> callback) {
          return null;
        }
      };

  private static final ReductionResolve<?, ?, ?> sDefaultResolve =
      new ReductionResolve<Object, Object, Callback<Object>>() {

        public void resolve(final Object state, @NotNull final Callback<Object> callback) {

        }
      };

  private final Mapper<? super Callback<R>, ? extends S> mCreate;

  private final ReductionFulfill<E, R, ? super S, ? super Callback<R>> mFulfill;

  private final ReductionReject<R, ? super S, ? super Callback<R>> mReject;

  private final ReductionResolve<R, ? super S, ? super Callback<R>> mResolve;

  @SuppressWarnings("unchecked")
  ComposedReductionHandler(@Nullable final Mapper<? super Callback<R>, ? extends S> create,
      @Nullable final ReductionFulfill<E, R, ? super S, ? super Callback<R>> fulfill,
      @Nullable final ReductionReject<R, ? super S, ? super Callback<R>> reject,
      @Nullable final ReductionResolve<R, ? super S, ? super Callback<R>> resolve) {
    mCreate = (Mapper<? super Callback<R>, ? extends S>) ((create != null) ? create
        : (Mapper<? super Callback<R>, ? extends S>) defaultCreate());
    mFulfill = (ReductionFulfill<E, R, ? super S, ? super Callback<R>>) ((fulfill != null) ? fulfill
        : defaultFulfill());
    mReject = (ReductionReject<R, ? super S, ? super Callback<R>>) ((reject != null) ? reject
        : defaultReject());
    mResolve = (ReductionResolve<R, ? super S, ? super Callback<R>>) ((resolve != null) ? resolve
        : defaultResolve());
  }

  @NotNull
  @SuppressWarnings("unchecked")
  static <R, S> Mapper<? super Callback<R>, ? extends S> defaultCreate() {
    return (Mapper<? super Callback<R>, ? extends S>) sDefaultCreate;
  }

  @NotNull
  @SuppressWarnings("unchecked")
  static <E, R, S> ReductionFulfill<E, R, ? super S, ? super Callback<R>> defaultFulfill() {
    return (ReductionFulfill<E, R, ? super S, ? super Callback<R>>) sDefaultFulfill;
  }

  @NotNull
  @SuppressWarnings("unchecked")
  static <R, S> ReductionReject<R, ? super S, ? super Callback<R>> defaultReject() {
    return (ReductionReject<R, ? super S, ? super Callback<R>>) sDefaultReject;
  }

  @NotNull
  @SuppressWarnings("unchecked")
  static <R, S> ReductionResolve<R, ? super S, ? super Callback<R>> defaultResolve() {
    return (ReductionResolve<R, ? super S, ? super Callback<R>>) sDefaultResolve;
  }

  public S create(@NotNull final C callback) throws Exception {
    return mCreate.apply(callback);
  }

  @SuppressWarnings("unchecked")
  public S fulfill(final S state, final E value, @NotNull final C callback) throws Exception {
    return (S) mFulfill.fulfill(state, value, callback);
  }

  @SuppressWarnings("unchecked")
  public S reject(final S state, @NotNull final Throwable reason, @NotNull final C callback) throws
      Exception {
    return (S) mReject.reject(state, reason, callback);
  }

  public void resolve(final S state, @NotNull final C callback) throws Exception {
    mResolve.resolve(state, callback);
  }

  private Object writeReplace() throws ObjectStreamException {
    return new HandlerProxy<E, R, S, C>(mCreate, mFulfill, mReject, mResolve);
  }

  private static class HandlerProxy<E, R, S, C extends Callback<R>> extends SerializableProxy {

    private HandlerProxy(final Mapper<? super Callback<R>, ? extends S> create,
        final ReductionFulfill<E, R, ? super S, ? super Callback<R>> fulfill,
        final ReductionReject<R, ? super S, ? super Callback<R>> reject,
        final ReductionResolve<R, ? super S, ? super Callback<R>> resolve) {
      super(proxy(create), proxy(fulfill), proxy(reject), proxy(resolve));
    }

    @SuppressWarnings("unchecked")
    Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new ComposedReductionHandler<E, R, S, C>(
            (Mapper<? super Callback<R>, ? extends S>) args[0],
            (ReductionFulfill<E, R, ? super S, ? super Callback<R>>) args[1],
            (ReductionReject<R, ? super S, ? super Callback<R>>) args[2],
            (ReductionResolve<R, ? super S, ? super Callback<R>>) args[3]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

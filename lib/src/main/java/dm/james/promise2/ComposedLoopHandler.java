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
import dm.james.promise2.Promise.LoopFulfill;
import dm.james.promise2.Promise.LoopHandler;
import dm.james.promise2.Promise.LoopReject;
import dm.james.promise2.Promise.LoopResolve;
import dm.james.promise2.Thenable.Callback;
import dm.james.util.SerializableProxy;

/**
 * Created by davide-maestroni on 12/11/2017.
 */
class ComposedLoopHandler<E, R, S, C extends Callback<R>>
    implements LoopHandler<E, R, S, C>, Serializable {

  // TODO: 11/12/2017 move to valuepromise

  // TODO: 11/12/2017 serializable + singleton
  private static final Mapper<?, ?> sDefaultCreate = new Mapper<Object, Object>() {

    public Object apply(final Object input) {
      return null;
    }
  };

  private static final LoopFulfill<?, ?, ?, ?> sDefaultFulfill =
      new LoopFulfill<Object, Object, Object, Callback<Object>>() {

        public Object fulfill(final Object state, final Object value,
            @NotNull final Callback<Object> callback) {
          return null;
        }
      };

  private static final LoopReject<?, ?, ?> sDefaultReject =
      new LoopReject<Object, Object, Callback<Object>>() {

        public Object reject(final Object state, @NotNull final Throwable reason,
            @NotNull final Callback<Object> callback) {
          return null;
        }
      };

  private static final LoopResolve<?, ?, ?> sDefaultResolve =
      new LoopResolve<Object, Object, Callback<Object>>() {

        public void resolve(final Object state, @NotNull final Callback<Object> callback) {

        }
      };

  private final Mapper<? super Callback<R>, ? extends S> mCreate;

  private final LoopFulfill<E, R, ? super S, ? super Callback<R>> mFulfill;

  private final LoopReject<R, ? super S, ? super Callback<R>> mReject;

  private final LoopResolve<R, ? super S, ? super Callback<R>> mResolve;

  @SuppressWarnings("unchecked")
  ComposedLoopHandler(@Nullable final Mapper<? super Callback<R>, ? extends S> create,
      @Nullable final LoopFulfill<E, R, ? super S, ? super Callback<R>> fulfill,
      @Nullable final LoopReject<R, ? super S, ? super Callback<R>> reject,
      @Nullable final LoopResolve<R, ? super S, ? super Callback<R>> resolve) {
    mCreate = (Mapper<? super Callback<R>, ? extends S>) ((create != null) ? create
        : (Mapper<? super Callback<R>, ? extends S>) defaultCreate());
    mFulfill = (LoopFulfill<E, R, ? super S, ? super Callback<R>>) ((fulfill != null) ? fulfill
        : defaultFulfill());
    mReject = (LoopReject<R, ? super S, ? super Callback<R>>) ((reject != null) ? reject
        : defaultReject());
    mResolve = (LoopResolve<R, ? super S, ? super Callback<R>>) ((resolve != null) ? resolve
        : defaultResolve());
  }

  @NotNull
  @SuppressWarnings("unchecked")
  static <R, S> Mapper<? super Callback<R>, ? extends S> defaultCreate() {
    return (Mapper<? super Callback<R>, ? extends S>) sDefaultCreate;
  }

  @NotNull
  @SuppressWarnings("unchecked")
  static <E, R, S> LoopFulfill<E, R, ? super S, ? super Callback<R>> defaultFulfill() {
    return (LoopFulfill<E, R, ? super S, ? super Callback<R>>) sDefaultFulfill;
  }

  @NotNull
  @SuppressWarnings("unchecked")
  static <R, S> LoopReject<R, ? super S, ? super Callback<R>> defaultReject() {
    return (LoopReject<R, ? super S, ? super Callback<R>>) sDefaultReject;
  }

  @NotNull
  @SuppressWarnings("unchecked")
  static <R, S> LoopResolve<R, ? super S, ? super Callback<R>> defaultResolve() {
    return (LoopResolve<R, ? super S, ? super Callback<R>>) sDefaultResolve;
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
        final LoopFulfill<E, R, ? super S, ? super Callback<R>> fulfill,
        final LoopReject<R, ? super S, ? super Callback<R>> reject,
        final LoopResolve<R, ? super S, ? super Callback<R>> resolve) {
      super(proxy(create), proxy(fulfill), proxy(reject), proxy(resolve));
    }

    @SuppressWarnings("unchecked")
    Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new ComposedLoopHandler<E, R, S, C>(
            (Mapper<? super Callback<R>, ? extends S>) args[0],
            (LoopFulfill<E, R, ? super S, ? super Callback<R>>) args[1],
            (LoopReject<R, ? super S, ? super Callback<R>>) args[2],
            (LoopResolve<R, ? super S, ? super Callback<R>>) args[3]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

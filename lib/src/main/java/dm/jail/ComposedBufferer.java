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

package dm.jail;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;

import dm.jail.async.AsyncResult;
import dm.jail.async.AsyncResultCollection;
import dm.jail.async.AsyncStatement;
import dm.jail.async.AsyncStatement.BufferUpdater;
import dm.jail.async.AsyncStatement.Bufferer;
import dm.jail.async.Mapper;
import dm.james.util.SerializableProxy;

/**
 * Created by davide-maestroni on 01/19/2018.
 */
class ComposedBufferer<S, V> implements Bufferer<S, AsyncStatement<V>, V, V>, Serializable {

  private final BufferUpdater<S, ? super AsyncStatement<V>, ? super Throwable> mFailure;

  private final Mapper<? super AsyncStatement<V>, S> mInit;

  private final BufferUpdater<S, ? super AsyncStatement<V>, ? super AsyncResultCollection<?
      extends V>>
      mLoop;

  private final BufferUpdater<S, ? super AsyncStatement<V>, ? super AsyncResult<? extends V>>
      mStatement;

  private final BufferUpdater<S, ? super AsyncStatement<V>, ? super V> mValue;

  @SuppressWarnings("unchecked")
  ComposedBufferer(@Nullable final Mapper<? super AsyncStatement<V>, S> init,
      @Nullable final BufferUpdater<S, ? super AsyncStatement<V>, ? super V> value,
      @Nullable final BufferUpdater<S, ? super AsyncStatement<V>, ? super Throwable> failure,
      @Nullable final BufferUpdater<S, ? super AsyncStatement<V>, ? super AsyncResult<? extends
          V>> statement,
      @Nullable final BufferUpdater<S, ? super AsyncStatement<V>, ? super AsyncResultCollection<?
          extends V>> loop) {
    mInit = (Mapper<? super AsyncStatement<V>, S>) ((init != null) ? init : DefaultInit.sInstance);
    mValue = (BufferUpdater<S, ? super AsyncStatement<V>, ? super V>) ((value != null) ? value
        : DefaultUpdateValue.sInstance);
    mFailure = (BufferUpdater<S, ? super AsyncStatement<V>, ? super Throwable>) ((failure != null)
        ? failure : DefaultUpdateValue.sInstance);
    mStatement = (BufferUpdater<S, ? super AsyncStatement<V>, ? super AsyncResult<? extends V>>) (
        (statement != null) ? statement : DefaultUpdateResult.sInstance);
    mLoop =
        (BufferUpdater<S, ? super AsyncStatement<V>, ? super AsyncResultCollection<? extends V>>) (
            (loop != null) ? loop : DefaultUpdateCollection.sInstance);
  }

  public S failure(@NotNull final AsyncStatement<V> statement, final S stack,
      final Throwable failure) throws Exception {
    return null;
  }

  public S init(@NotNull final AsyncStatement<V> statement) throws Exception {
    return mInit.apply(statement);
  }

  public S loop(@NotNull final AsyncStatement<V> statement, final S stack,
      @NotNull final AsyncResultCollection<V> results) throws Exception {
    return mLoop.update(statement, stack, results);
  }

  public S statement(@NotNull final AsyncStatement<V> statement, final S stack,
      @NotNull final AsyncResult<V> result) throws Exception {
    return mStatement.update(statement, stack, result);
  }

  public S value(@NotNull final AsyncStatement<V> statement, final S stack, final V value) throws
      Exception {
    return mValue.update(statement, stack, value);
  }

  private Object writeReplace() throws ObjectStreamException {
    return new BuffererProxy<S, V>(mInit, mValue, mFailure, mStatement, mLoop);
  }

  private static class BuffererProxy<S, V> extends SerializableProxy {

    private BuffererProxy(final Mapper<? super AsyncStatement<V>, S> init,
        final BufferUpdater<S, ? super AsyncStatement<V>, ? super V> value,
        final BufferUpdater<S, ? super AsyncStatement<V>, ? super Throwable> failure,
        final BufferUpdater<S, ? super AsyncStatement<V>, ? super AsyncResult<? extends V>>
            statement,
        final BufferUpdater<S, ? super AsyncStatement<V>, ? super AsyncResultCollection<? extends
            V>> loop) {
      super(proxy(init), proxy(value), proxy(failure), proxy(statement), proxy(loop));
    }

    @SuppressWarnings("unchecked")
    Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new ComposedBufferer<S, V>((Mapper<? super AsyncStatement<V>, S>) args[0],
            (BufferUpdater<S, ? super AsyncStatement<V>, ? super V>) args[1],
            (BufferUpdater<S, ? super AsyncStatement<V>, ? super Throwable>) args[2],
            (BufferUpdater<S, ? super AsyncStatement<V>, ? super AsyncResult<? extends V>>) args[3],
            (BufferUpdater<S, ? super AsyncStatement<V>, ? super AsyncResultCollection<? extends
                V>>) args[4]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }

  private static class DefaultInit<S, V> implements Mapper<AsyncStatement<V>, S>, Serializable {

    private static final DefaultInit<?, ?> sInstance = new DefaultInit<Object, Object>();

    public S apply(final AsyncStatement<V> statement) {
      return null;
    }

    Object readResolve() throws ObjectStreamException {
      return sInstance;
    }
  }

  private static class DefaultUpdateCollection<S, V>
      implements BufferUpdater<S, AsyncStatement<V>, AsyncResultCollection<V>>, Serializable {

    private static final DefaultUpdateCollection<?, ?> sInstance =
        new DefaultUpdateCollection<Object, Object>();

    public S update(@NotNull final AsyncStatement<V> statement, final S stack,
        final AsyncResultCollection<V> result) {
      result.set();
      return stack;
    }

    Object readResolve() throws ObjectStreamException {
      return sInstance;
    }
  }

  private static class DefaultUpdateResult<S, V>
      implements BufferUpdater<S, AsyncStatement<V>, AsyncResult<V>>, Serializable {

    private static final DefaultUpdateResult<?, ?> sInstance =
        new DefaultUpdateResult<Object, Object>();

    Object readResolve() throws ObjectStreamException {
      return sInstance;
    }

    public S update(@NotNull final AsyncStatement<V> statement, final S stack,
        final AsyncResult<V> result) {
      result.set(null);
      return stack;
    }
  }

  private static class DefaultUpdateValue<S, V, I>
      implements BufferUpdater<S, AsyncStatement<V>, I>, Serializable {

    private static final DefaultUpdateValue<?, ?, ?> sInstance =
        new DefaultUpdateValue<Object, Object, Object>();

    Object readResolve() throws ObjectStreamException {
      return sInstance;
    }

    public S update(@NotNull final AsyncStatement<V> statement, final S stack, final I input) {
      return stack;
    }
  }
}

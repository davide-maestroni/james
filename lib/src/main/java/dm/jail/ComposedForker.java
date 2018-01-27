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
import dm.jail.async.AsyncStatement;
import dm.jail.async.AsyncStatement.ForkCompleter;
import dm.jail.async.AsyncStatement.ForkUpdater;
import dm.jail.async.AsyncStatement.Forker;
import dm.jail.async.Mapper;
import dm.jail.util.SerializableProxy;

/**
 * Created by davide-maestroni on 01/19/2018.
 */
class ComposedForker<S, V>
    implements Forker<S, AsyncStatement<V>, V, AsyncResult<V>>, Serializable {

  private final ForkCompleter<S, ? super AsyncStatement<V>> mDone;

  private final ForkUpdater<S, ? super AsyncStatement<V>, ? super Throwable> mFailure;

  private final Mapper<? super AsyncStatement<V>, S> mInit;

  private final ForkUpdater<S, ? super AsyncStatement<V>, ? super AsyncResult<? extends V>>
      mStatement;

  private final ForkUpdater<S, ? super AsyncStatement<V>, ? super V> mValue;

  @SuppressWarnings("unchecked")
  ComposedForker(@Nullable final Mapper<? super AsyncStatement<V>, S> init,
      @Nullable final ForkUpdater<S, ? super AsyncStatement<V>, ? super V> value,
      @Nullable final ForkUpdater<S, ? super AsyncStatement<V>, ? super Throwable> failure,
      @Nullable final ForkCompleter<S, ? super AsyncStatement<V>> done,
      @Nullable final ForkUpdater<S, ? super AsyncStatement<V>, ? super AsyncResult<V>> statement) {
    mInit = (Mapper<? super AsyncStatement<V>, S>) ((init != null) ? init : DefaultInit.sInstance);
    mValue = (ForkUpdater<S, ? super AsyncStatement<V>, ? super V>) ((value != null) ? value
        : DefaultUpdateValue.sInstance);
    mFailure =
        (ForkUpdater<S, ? super AsyncStatement<V>, ? super Throwable>) ((failure != null) ? failure
            : DefaultUpdateValue.sInstance);
    mDone = (ForkCompleter<S, ? super AsyncStatement<V>>) ((done != null) ? done
        : DefaultComplete.sInstance);
    mStatement = (ForkUpdater<S, ? super AsyncStatement<V>, ? super AsyncResult<? extends V>>) (
        (statement != null) ? statement : DefaultUpdateResult.sInstance);
  }

  public S done(@NotNull final AsyncStatement<V> statement, final S stack) throws Exception {
    return mDone.complete(statement, stack);
  }

  public S failure(@NotNull final AsyncStatement<V> statement, final S stack,
      @NotNull final Throwable failure) throws Exception {
    return mFailure.update(statement, stack, failure);
  }

  public S init(@NotNull final AsyncStatement<V> statement) throws Exception {
    return mInit.apply(statement);
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
    return new BuffererProxy<S, V>(mInit, mValue, mFailure, mStatement, mDone);
  }

  private static class BuffererProxy<S, V> extends SerializableProxy {

    private BuffererProxy(final Mapper<? super AsyncStatement<V>, S> init,
        final ForkUpdater<S, ? super AsyncStatement<V>, ? super V> value,
        final ForkUpdater<S, ? super AsyncStatement<V>, ? super Throwable> failure,
        final ForkUpdater<S, ? super AsyncStatement<V>, ? super AsyncResult<? extends V>> statement,
        final ForkCompleter<S, ? super AsyncStatement<V>> done) {
      super(proxy(init), proxy(value), proxy(failure), proxy(statement), proxy(done));
    }

    @SuppressWarnings("unchecked")
    Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new ComposedForker<S, V>((Mapper<? super AsyncStatement<V>, S>) args[0],
            (ForkUpdater<S, ? super AsyncStatement<V>, ? super V>) args[1],
            (ForkUpdater<S, ? super AsyncStatement<V>, ? super Throwable>) args[2],
            (ForkCompleter<S, ? super AsyncStatement<V>>) args[4],
            (ForkUpdater<S, ? super AsyncStatement<V>, ? super AsyncResult<? extends V>>) args[3]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }

  private static class DefaultComplete<S, V>
      implements ForkCompleter<S, AsyncStatement<V>>, Serializable {

    private static final DefaultComplete<?, ?> sInstance = new DefaultComplete<Object, Object>();

    public S complete(@NotNull final AsyncStatement<V> statement, final S stack) {
      return stack;
    }

    Object readResolve() throws ObjectStreamException {
      return sInstance;
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

  private static class DefaultUpdateResult<S, V>
      implements ForkUpdater<S, AsyncStatement<V>, AsyncResult<V>>, Serializable {

    private static final DefaultUpdateResult<?, ?> sInstance =
        new DefaultUpdateResult<Object, Object>();

    public S update(@NotNull final AsyncStatement<V> statement, final S stack,
        final AsyncResult<V> result) {
      result.fail(new UnsupportedOperationException());
      return stack;
    }

    Object readResolve() throws ObjectStreamException {
      return sInstance;
    }
  }

  private static class DefaultUpdateValue<S, V, I>
      implements ForkUpdater<S, AsyncStatement<V>, I>, Serializable {

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

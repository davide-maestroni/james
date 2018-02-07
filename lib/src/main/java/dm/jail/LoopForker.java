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

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import dm.jail.LoopForker.ForkerStack;
import dm.jail.async.AsyncLoop;
import dm.jail.async.AsyncResult;
import dm.jail.async.AsyncResults;
import dm.jail.async.AsyncStatement;
import dm.jail.async.AsyncStatement.Forker;
import dm.jail.config.BuildConfig;
import dm.jail.util.ConstantConditions;
import dm.jail.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/05/2018.
 */
class LoopForker<S, V>
    implements Forker<ForkerStack<S, V>, AsyncLoop<V>, V, AsyncResults<V>>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Forker<S, AsyncStatement<Iterable<V>>, Iterable<V>, AsyncResult<Iterable<V>>>
      mForker;

  @SuppressWarnings("unchecked")
  LoopForker(
      @NotNull final Forker<S, ? super AsyncStatement<Iterable<V>>, ? super Iterable<V>, ? super
          AsyncResult<Iterable<V>>> forker) {
    mForker =
        (Forker<S, AsyncStatement<Iterable<V>>, Iterable<V>, AsyncResult<Iterable<V>>>)
            ConstantConditions
            .notNull("forker", forker);
  }

  public ForkerStack<S, V> done(@NotNull final AsyncLoop<V> statement,
      final ForkerStack<S, V> stack) throws Exception {
    final Forker<S, AsyncStatement<Iterable<V>>, Iterable<V>, AsyncResult<Iterable<V>>> forker =
        mForker;
    final List<V> values = stack.getValues();
    if (values != null) {
      stack.setStack(forker.value(statement, stack.getStack(), stack.getValues()));
    }

    return stack.withStack(forker.done(statement, stack.getStack()));
  }

  public ForkerStack<S, V> failure(@NotNull final AsyncLoop<V> statement,
      final ForkerStack<S, V> stack, @NotNull final Throwable failure) throws Exception {
    if (stack.getValues() == null) {
      return stack;
    }

    return stack.withStack(mForker.failure(statement, stack.getStack(), failure)).withValues(null);
  }

  public ForkerStack<S, V> init(@NotNull final AsyncLoop<V> statement) throws Exception {
    return new ForkerStack<S, V>().withStack(mForker.init(statement))
                                  .withValues(new ArrayList<V>());
  }

  public ForkerStack<S, V> statement(@NotNull final AsyncLoop<V> statement,
      final ForkerStack<S, V> stack, @NotNull final AsyncResults<V> result) throws Exception {
    return stack.withStack(
        mForker.statement(statement, stack.getStack(), new AsyncResult<Iterable<V>>() {

          public void fail(@NotNull final Throwable failure) {
            result.addFailure(failure).set();
          }

          public void set(final Iterable<V> value) {
            result.addValues(value).set();
          }
        }));
  }

  public ForkerStack<S, V> value(@NotNull final AsyncLoop<V> statement,
      final ForkerStack<S, V> stack, final V value) throws Exception {
    final List<V> values = stack.getValues();
    if (values != null) {
      values.add(value);
    }

    return stack;
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new ForkerProxy<S, V>(mForker);
  }

  static class ForkerStack<S, V> {

    private S mStack;

    private List<V> mValues;

    S getStack() {
      return mStack;
    }

    void setStack(final S stack) {
      mStack = stack;
    }

    List<V> getValues() {
      return mValues;
    }

    void setValues(final List<V> values) {
      mValues = values;
    }

    @NotNull
    ForkerStack<S, V> withStack(final S stack) {
      mStack = stack;
      return this;
    }

    @NotNull
    ForkerStack<S, V> withValues(final List<V> values) {
      mValues = values;
      return this;
    }
  }

  private static class ForkerProxy<S, V> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private ForkerProxy(
        final Forker<S, AsyncStatement<Iterable<V>>, Iterable<V>, AsyncResult<Iterable<V>>>
            forker) {
      super(proxy(forker));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new LoopForker<S, V>(
            (Forker<S, AsyncStatement<Iterable<V>>, Iterable<V>, AsyncResult<Iterable<V>>>)
                args[0]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

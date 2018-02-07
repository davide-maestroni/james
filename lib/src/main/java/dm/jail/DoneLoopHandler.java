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
import java.util.Iterator;

import dm.jail.async.Action;
import dm.jail.async.AsyncResults;
import dm.jail.config.BuildConfig;
import dm.jail.util.ConstantConditions;
import dm.jail.util.Iterables;
import dm.jail.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/01/2018.
 */
class DoneLoopHandler<V> extends AsyncLoopHandler<V, V> implements Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Action mAction;

  DoneLoopHandler(@NotNull final Action action) {
    mAction = ConstantConditions.notNull("action", action);
  }

  @Override
  void addFailure(@NotNull final Throwable failure, @NotNull final AsyncResults<V> results) throws
      Exception {
    mAction.perform();
    super.addFailure(failure, results);
  }

  @Override
  @SuppressWarnings("WhileLoopReplaceableByForEach")
  void addFailures(@Nullable final Iterable<? extends Throwable> failures,
      @NotNull final AsyncResults<V> results) throws Exception {
    if (failures == null) {
      return;
    }

    int index = 0;
    try {
      final Action action = mAction;
      final Iterator<? extends Throwable> iterator = failures.iterator();
      while (iterator.hasNext()) {
        iterator.next();
        action.perform();
        ++index;
      }

    } finally {
      results.addFailures(Iterables.asList(failures).subList(0, index)).set();
    }
  }

  @Override
  void addValue(final V value, @NotNull final AsyncResults<V> results) throws Exception {
    mAction.perform();
    super.addValue(value, results);
  }

  @Override
  @SuppressWarnings("WhileLoopReplaceableByForEach")
  void addValues(@Nullable final Iterable<? extends V> values,
      @NotNull final AsyncResults<V> results) throws Exception {
    if (values == null) {
      return;
    }

    int index = 0;
    try {
      final Action action = mAction;
      final Iterator<? extends V> iterator = values.iterator();
      while (iterator.hasNext()) {
        iterator.next();
        action.perform();
        ++index;
      }

    } finally {
      results.addValues(Iterables.asList(values).subList(0, index)).set();
    }
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new HandlerProxy<V>(mAction);
  }

  private static class HandlerProxy<V> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private HandlerProxy(final Action action) {
      super(proxy(action));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new DoneLoopHandler<V>((Action) args[0]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

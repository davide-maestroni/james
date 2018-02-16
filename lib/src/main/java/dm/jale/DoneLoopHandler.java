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

package dm.jale;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.Iterator;

import dm.jale.async.Action;
import dm.jale.async.AsyncEvaluations;
import dm.jale.config.BuildConfig;
import dm.jale.util.ConstantConditions;
import dm.jale.util.Iterables;
import dm.jale.util.SerializableProxy;

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
  void addFailure(@NotNull final Throwable failure,
      @NotNull final AsyncEvaluations<V> evaluations) throws Exception {
    mAction.perform();
    super.addFailure(failure, evaluations);
  }

  @Override
  @SuppressWarnings("WhileLoopReplaceableByForEach")
  void addFailures(@Nullable final Iterable<? extends Throwable> failures,
      @NotNull final AsyncEvaluations<V> evaluations) throws Exception {
    if (failures == null) {
      return;
    }

    int index = 0;
    try {
      @SuppressWarnings("UnnecessaryLocalVariable") final Action action = mAction;
      final Iterator<? extends Throwable> iterator = failures.iterator();
      while (iterator.hasNext()) {
        iterator.next();
        action.perform();
        ++index;
      }

    } finally {
      evaluations.addFailures(Iterables.asList(failures).subList(0, index)).set();
    }
  }

  @Override
  void addValue(final V value, @NotNull final AsyncEvaluations<V> evaluations) throws Exception {
    mAction.perform();
    super.addValue(value, evaluations);
  }

  @Override
  @SuppressWarnings("WhileLoopReplaceableByForEach")
  void addValues(@Nullable final Iterable<? extends V> values,
      @NotNull final AsyncEvaluations<V> evaluations) throws Exception {
    if (values == null) {
      return;
    }

    int index = 0;
    try {
      @SuppressWarnings("UnnecessaryLocalVariable") final Action action = mAction;
      final Iterator<? extends V> iterator = values.iterator();
      while (iterator.hasNext()) {
        iterator.next();
        action.perform();
        ++index;
      }

    } finally {
      evaluations.addValues(Iterables.asList(values).subList(0, index)).set();
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
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new DoneLoopHandler<V>((Action) args[0]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

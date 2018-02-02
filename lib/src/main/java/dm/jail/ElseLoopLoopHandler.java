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
import java.util.ArrayList;
import java.util.Arrays;

import dm.jail.async.AsyncResultCollection;
import dm.jail.async.Mapper;
import dm.jail.config.BuildConfig;
import dm.jail.util.ConstantConditions;
import dm.jail.util.Iterables;
import dm.jail.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/01/2018.
 */
class ElseLoopLoopHandler<V> extends AsyncLoopHandler<V, V> {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Mapper<? super Throwable, ? extends Iterable<? extends V>> mMapper;

  private final Class<?>[] mTypes;

  ElseLoopLoopHandler(
      @NotNull final Mapper<? super Throwable, ? extends Iterable<? extends V>> mapper,
      @NotNull final Class<?>[] exceptionTypes) {
    mMapper = ConstantConditions.notNull("mapper", mapper);
    mTypes = ConstantConditions.notNull("exception types", exceptionTypes);
    if (Arrays.asList(mTypes).contains(null)) {
      throw new NullPointerException("exception type array contains null values");
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  void addFailure(@NotNull final Throwable failure,
      @NotNull final AsyncResultCollection<V> results) throws Exception {
    for (final Class<?> type : mTypes) {
      if (type.isInstance(failure)) {
        results.addValues((Iterable<V>) mMapper.apply(failure)).set();
        return;
      }
    }

    super.addFailure(failure, results);
  }

  @Override
  void addFailures(@Nullable final Iterable<Throwable> failures,
      @NotNull final AsyncResultCollection<V> results) throws Exception {
    if (failures == null) {
      return;
    }

    final ArrayList<V> outputs = new ArrayList<V>();
    final Class<?>[] types = mTypes;
    final Mapper<? super Throwable, ? extends Iterable<? extends V>> mapper = mMapper;
    try {
      for (final Throwable failure : failures) {
        boolean found = false;
        for (final Class<?> type : types) {
          if (type.isInstance(failure)) {
            Iterables.addAll(mapper.apply(failure), outputs);
            found = true;
            break;
          }
        }

        if (!found) {
          if (outputs.isEmpty()) {
            results.addFailure(failure);

          } else {
            final ArrayList<V> values = new ArrayList<V>(outputs);
            outputs.clear();
            results.addValues(values).addFailure(failure);
          }
        }
      }

    } finally {
      results.addValues(outputs).set();
    }
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new HandlerProxy<V>(mMapper, mTypes);
  }

  private static class HandlerProxy<V> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private HandlerProxy(final Mapper<? super Throwable, ? extends Iterable<? extends V>> mapper,
        final Class<?>[] exceptionTypes) {
      super(proxy(mapper), exceptionTypes);
    }

    @NotNull
    @SuppressWarnings("unchecked")
    Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new ElseLoopLoopHandler<V>(
            (Mapper<? super Throwable, ? extends Iterable<? extends V>>) args[0],
            (Class<?>[]) args[1]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

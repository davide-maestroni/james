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

import java.io.NotSerializableException;
import java.io.ObjectStreamException;
import java.io.Serializable;

import dm.jail.async.AsyncResultCollection;
import dm.jail.config.BuildConfig;
import dm.jail.util.ConstantConditions;

/**
 * Created by davide-maestroni on 02/05/2018.
 */
class ToResultLoopHandler<V> extends AsyncLoopHandler<V, Void> implements Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final AsyncResultCollection<V> mResults;

  @SuppressWarnings("unchecked")
  ToResultLoopHandler(@NotNull final AsyncResultCollection<? super V> results) {
    mResults = (AsyncResultCollection<V>) ConstantConditions.notNull("results", results);
  }

  @Override
  void addFailure(@NotNull final Throwable failure,
      @NotNull final AsyncResultCollection<Void> results) {
    mResults.addFailure(failure);
  }

  @Override
  void addFailures(@Nullable final Iterable<Throwable> failures,
      @NotNull final AsyncResultCollection<Void> results) {
    mResults.addFailures(failures);
  }

  @Override
  void addValue(final V value, @NotNull final AsyncResultCollection<Void> results) {
    mResults.addValue(value);
  }

  @Override
  void addValues(@Nullable final Iterable<V> values,
      @NotNull final AsyncResultCollection<Void> results) {
    mResults.addValues(values);
  }

  @Override
  void set(@NotNull final AsyncResultCollection<Void> results) {
    mResults.set();
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    throw new NotSerializableException();
  }
}

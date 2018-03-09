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

package dm.fates.ext.yield;

import org.jetbrains.annotations.NotNull;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.Comparator;
import java.util.TreeSet;

import dm.fates.eventual.Loop.YieldOutputs;
import dm.fates.eventual.LoopYielder;
import dm.fates.ext.config.BuildConfig;
import dm.fates.util.ConstantConditions;
import dm.fates.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/19/2018.
 */
class DistinctByYielder<V> implements LoopYielder<TreeSet<V>, V, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Comparator<? super V> mComparator;

  DistinctByYielder(@NotNull final Comparator<? super V> comparator) {
    mComparator = ConstantConditions.notNull("comparator", comparator);
  }

  public void done(final TreeSet<V> stack, @NotNull final YieldOutputs<V> outputs) {
  }

  public TreeSet<V> failure(final TreeSet<V> stack, @NotNull final Throwable failure,
      @NotNull final YieldOutputs<V> outputs) {
    outputs.yieldFailure(failure);
    return stack;
  }

  public TreeSet<V> init() {
    return new TreeSet<V>(mComparator);
  }

  public boolean loop(final TreeSet<V> stack) {
    return true;
  }

  public TreeSet<V> value(final TreeSet<V> stack, final V value,
      @NotNull final YieldOutputs<V> outputs) throws Exception {
    if (stack.add(value)) {
      outputs.yieldValue(value);
    }

    return stack;
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new YielderProxy<V>(mComparator);
  }

  private static class YielderProxy<V> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private YielderProxy(final Comparator<? super V> comparator) {
      super(proxy(comparator));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new DistinctByYielder<V>((Comparator<? super V>) args[0]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

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

import dm.fates.eventual.Loop.YieldOutputs;
import dm.fates.eventual.LoopYielder;
import dm.fates.ext.config.BuildConfig;
import dm.fates.ext.yield.MaxByYielder.YielderStack;
import dm.fates.util.ConstantConditions;
import dm.fates.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/19/2018.
 */
class MaxByYielder<V> implements LoopYielder<YielderStack<V>, V, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Comparator<? super V> mComparator;

  MaxByYielder(@NotNull final Comparator<? super V> comparator) {
    mComparator = ConstantConditions.notNull("comparator", comparator);
  }

  public void done(final YielderStack<V> stack, @NotNull final YieldOutputs<V> outputs) {
    if (stack != null) {
      outputs.yieldValue(stack.max);
    }
  }

  public YielderStack<V> failure(final YielderStack<V> stack, @NotNull final Throwable failure,
      @NotNull final YieldOutputs<V> outputs) {
    outputs.yieldFailure(failure);
    return null;
  }

  public YielderStack<V> init() {
    return new YielderStack<V>();
  }

  public boolean loop(final YielderStack<V> stack) {
    return (stack != null);
  }

  public YielderStack<V> value(final YielderStack<V> stack, final V value,
      @NotNull final YieldOutputs<V> outputs) throws Exception {
    if (stack.max == null) {
      stack.max = ConstantConditions.notNull("value", value);

    } else if (mComparator.compare(value, stack.max) > 0) {
      stack.max = value;
    }

    return stack;
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new YielderProxy<V>(mComparator);
  }

  static class YielderStack<V> {

    private V max;
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
        return new MaxByYielder<V>((Comparator<? super V>) args[0]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

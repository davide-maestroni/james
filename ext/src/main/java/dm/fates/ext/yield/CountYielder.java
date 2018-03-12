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

import java.io.ObjectStreamException;
import java.io.Serializable;

import dm.fates.eventual.Loop.YieldOutputs;
import dm.fates.eventual.LoopYielder;
import dm.fates.ext.config.BuildConfig;
import dm.fates.ext.yield.CountYielder.YielderStack;

/**
 * Created by davide-maestroni on 02/19/2018.
 */
class CountYielder<V> implements LoopYielder<YielderStack, V, Long>, Serializable {

  private static final CountYielder<?> sInstance = new CountYielder<Object>();

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private CountYielder() {
  }

  @NotNull
  @SuppressWarnings("unchecked")
  static <V> CountYielder<V> instance() {
    return (CountYielder<V>) sInstance;
  }

  public void done(final YielderStack stack, @NotNull final YieldOutputs<Long> outputs) {
    if (stack != null) {
      outputs.yieldValue(stack.count);
    }
  }

  public YielderStack failure(final YielderStack stack, @NotNull final Throwable failure,
      @NotNull final YieldOutputs<Long> outputs) {
    outputs.yieldFailure(failure);
    return null;
  }

  public YielderStack init() {
    return new YielderStack();
  }

  public boolean loop(final YielderStack stack) {
    return (stack != null);
  }

  public YielderStack value(final YielderStack stack, final V value,
      @NotNull final YieldOutputs<Long> outputs) {
    ++stack.count;
    return stack;
  }

  @NotNull
  private Object readResolve() throws ObjectStreamException {
    return sInstance;
  }

  static class YielderStack {

    private long count;
  }
}

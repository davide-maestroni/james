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

import dm.fates.eventual.Loop.YieldOutputs;
import dm.fates.eventual.LoopYielder;
import dm.fates.ext.config.BuildConfig;
import dm.fates.ext.eventual.TriMapper;
import dm.fates.ext.yield.OnEnumeratedFailureYielder.YielderStack;
import dm.fates.util.ConstantConditions;
import dm.fates.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/27/2018.
 */
class OnEnumeratedFailureYielder<V> implements LoopYielder<YielderStack, V, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final TriMapper<? super Long, ? super Throwable, ? super YieldOutputs<V>, Boolean>
      mMapper;

  OnEnumeratedFailureYielder(
      @NotNull final TriMapper<? super Long, ? super Throwable, ? super YieldOutputs<V>, Boolean>
          mapper) {
    mMapper = ConstantConditions.notNull("mapper", mapper);
  }

  public void done(final YielderStack stack, @NotNull final YieldOutputs<V> outputs) {
  }

  public YielderStack failure(final YielderStack stack, @NotNull final Throwable failure,
      @NotNull final YieldOutputs<V> outputs) throws Exception {
    final Boolean isLoop = mMapper.apply(stack.count, failure, outputs);
    ++stack.count;
    if (isLoop != null) {
      stack.isLoop = isLoop;
    }

    return stack;
  }

  public YielderStack init() {
    return new YielderStack();
  }

  public boolean loop(final YielderStack stack) {
    return stack.isLoop;
  }

  public YielderStack value(final YielderStack stack, final V value,
      @NotNull final YieldOutputs<V> outputs) {
    outputs.yieldValue(value);
    return stack;
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new YielderProxy<V>(mMapper);
  }

  static class YielderStack {

    private long count;

    private boolean isLoop = true;
  }

  private static class YielderProxy<V> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private YielderProxy(
        final TriMapper<? super Long, ? super Throwable, ? super YieldOutputs<V>, Boolean> mapper) {
      super(proxy(mapper));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new OnEnumeratedFailureYielder<V>(
            (TriMapper<? super Long, ? super Throwable, ? super YieldOutputs<V>, Boolean>) args[0]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

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
import dm.fates.eventual.Tester;
import dm.fates.ext.config.BuildConfig;
import dm.fates.util.ConstantConditions;
import dm.fates.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/19/2018.
 */
class AllMatchYielder<V> implements LoopYielder<Boolean, V, Boolean>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Tester<V> mTester;

  AllMatchYielder(@NotNull final Tester<V> tester) {
    mTester = ConstantConditions.notNull("tester", tester);
  }

  public void done(final Boolean stack, @NotNull final YieldOutputs<Boolean> outputs) {
    if ((stack != null) && stack) {
      outputs.yieldValue(Boolean.TRUE);
    }
  }

  public Boolean failure(final Boolean stack, @NotNull final Throwable failure,
      @NotNull final YieldOutputs<Boolean> outputs) {
    outputs.yieldFailure(failure);
    return null;
  }

  public Boolean init() {
    return Boolean.TRUE;
  }

  public boolean loop(final Boolean stack) {
    return (stack != null);
  }

  public Boolean value(final Boolean stack, final V value,
      @NotNull final YieldOutputs<Boolean> outputs) throws Exception {
    if (!mTester.test(value)) {
      outputs.yieldValue(Boolean.FALSE);
      return null;
    }

    return stack;
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new YielderProxy<V>(mTester);
  }

  private static class YielderProxy<V> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private YielderProxy(final Tester<V> tester) {
      super(proxy(tester));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new AllMatchYielder<V>((Tester<V>) args[0]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

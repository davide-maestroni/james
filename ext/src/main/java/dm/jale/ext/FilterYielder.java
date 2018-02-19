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

package dm.jale.ext;

import org.jetbrains.annotations.NotNull;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;

import dm.jale.async.Loop.YieldOutputs;
import dm.jale.async.Loop.Yielder;
import dm.jale.ext.async.Tester;
import dm.jale.ext.config.BuildConfig;
import dm.jale.util.ConstantConditions;
import dm.jale.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/19/2018.
 */
class FilterYielder<V> implements Yielder<Void, V, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Tester<V> mTester;

  FilterYielder(@NotNull final Tester<V> tester) {
    mTester = ConstantConditions.notNull("tester", tester);
  }

  public void done(final Void stack, @NotNull final YieldOutputs<V> outputs) {
  }

  public Void failure(final Void stack, @NotNull final Throwable failure,
      @NotNull final YieldOutputs<V> outputs) {
    outputs.yieldFailure(failure);
    return null;
  }

  public Void init() {
    return null;
  }

  public boolean loop(final Void stack) {
    return true;
  }

  public Void value(final Void stack, final V value, @NotNull final YieldOutputs<V> outputs) throws
      Exception {
    if (mTester.test(value)) {
      outputs.yieldValue(value);
    }

    return null;
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
        return new FilterYielder<V>((Tester<V>) args[0]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

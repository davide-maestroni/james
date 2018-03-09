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

package dm.fates.ext;

import org.jetbrains.annotations.NotNull;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;

import dm.fates.ext.config.BuildConfig;
import dm.fates.ext.eventual.Tester;
import dm.fates.util.ConstantConditions;
import dm.fates.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/19/2018.
 */
class NegatedTester<I> implements Tester<I>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Tester<I> mTester;

  NegatedTester(@NotNull final Tester<I> tester) {
    mTester = ConstantConditions.notNull("tester", tester);
  }

  public boolean test(final I input) throws Exception {
    return !mTester.test(input);
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new TesterProxy<I>(mTester);
  }

  private static class TesterProxy<I> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private TesterProxy(final Tester<I> tester) {
      super(proxy(tester));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new NegatedTester<I>((Tester<I>) args[0]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

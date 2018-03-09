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

package dm.fates;

import org.jetbrains.annotations.NotNull;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;

import dm.fates.BufferedForker.ForkerStack;
import dm.fates.config.BuildConfig;
import dm.fates.eventual.EvaluationCollection;
import dm.fates.eventual.Loop;
import dm.fates.eventual.LoopForker;
import dm.fates.eventual.Statement.Forker;
import dm.fates.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/13/2018.
 */
class BufferedLoopForker<S, V> extends BufferedForker<S, V, EvaluationCollection<V>, Loop<V>>
    implements LoopForker<ForkerStack<S, V, EvaluationCollection<V>, Loop<V>>, V> {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Forker<S, ? super V, ? super EvaluationCollection<V>, ? super Loop<V>> mForker;

  BufferedLoopForker(
      @NotNull final Forker<S, ? super V, ? super EvaluationCollection<V>, ? super Loop<V>>
          forker) {
    super(forker);
    mForker = forker;
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new ForkerProxy<S, V>(mForker);
  }

  private static class ForkerProxy<S, V> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private ForkerProxy(
        final Forker<S, ? super V, ? super EvaluationCollection<V>, ? super Loop<V>> forker) {
      super(proxy(forker));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new BufferedLoopForker<S, V>(
            (Forker<S, ? super V, ? super EvaluationCollection<V>, ? super Loop<V>>) args[0]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

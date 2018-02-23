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

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import dm.jale.eventual.Evaluation;
import dm.jale.eventual.Statement;
import dm.jale.eventual.StatementJoiner;
import dm.jale.ext.config.BuildConfig;

/**
 * Created by davide-maestroni on 02/16/2018.
 */
class AllOfJoiner<V> implements StatementJoiner<List<V>, V, List<V>>, Serializable {

  private static final AllOfJoiner<?> sInstance = new AllOfJoiner<Object>();

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private AllOfJoiner() {
  }

  @NotNull
  @SuppressWarnings("unchecked")
  static <V> AllOfJoiner<V> instance() {
    return (AllOfJoiner<V>) sInstance;
  }

  public List<V> done(final List<V> stack, @NotNull final Evaluation<List<V>> evaluation,
      @NotNull final List<Statement<V>> contexts, final int index) {
    return stack;
  }

  public List<V> failure(final List<V> stack, final Throwable failure,
      @NotNull final Evaluation<List<V>> evaluation, @NotNull final List<Statement<V>> contexts,
      final int index) {
    if (stack != null) {
      evaluation.fail(failure);
    }

    return null;
  }

  public List<V> init(@NotNull final List<Statement<V>> contexts) {
    return new ArrayList<V>();
  }

  public void settle(final List<V> stack, @NotNull final Evaluation<List<V>> evaluation,
      @NotNull final List<Statement<V>> contexts) {
    if (stack != null) {
      evaluation.set(stack);
    }
  }

  public List<V> value(final List<V> stack, final V value,
      @NotNull final Evaluation<List<V>> evaluation, @NotNull final List<Statement<V>> contexts,
      final int index) {
    if (stack != null) {
      stack.add(value);
    }

    return stack;
  }

  @NotNull
  private Object readResolve() throws ObjectStreamException {
    return sInstance;
  }
}

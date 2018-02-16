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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import dm.jale.async.AsyncEvaluation;
import dm.jale.async.AsyncState;
import dm.jale.async.AsyncStatement;
import dm.jale.async.SimpleState;
import dm.jale.async.StatementCombiner;
import dm.jale.ext.config.BuildConfig;

/**
 * Created by davide-maestroni on 02/16/2018.
 */
class StatesOfCombiner<V>
    implements StatementCombiner<List<AsyncState<V>>, V, List<AsyncState<V>>>, Serializable {

  private static final StatesOfCombiner<?> sInstance = new StatesOfCombiner<Object>();

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private StatesOfCombiner() {
  }

  @NotNull
  @SuppressWarnings("unchecked")
  static <V> StatesOfCombiner<V> instance() {
    return (StatesOfCombiner<V>) sInstance;
  }

  public List<AsyncState<V>> done(final List<AsyncState<V>> stack,
      @NotNull final AsyncEvaluation<List<AsyncState<V>>> evaluation,
      @NotNull final List<AsyncStatement<V>> asyncs, final int index) {
    return stack;
  }

  public List<AsyncState<V>> failure(final List<AsyncState<V>> stack, final Throwable failure,
      @NotNull final AsyncEvaluation<List<AsyncState<V>>> evaluation,
      @NotNull final List<AsyncStatement<V>> asyncs, final int index) {
    stack.add(SimpleState.<V>ofFailure(failure));
    return stack;
  }

  public List<AsyncState<V>> init(@NotNull final List<AsyncStatement<V>> asyncs) {
    return new ArrayList<AsyncState<V>>();
  }

  public void settle(final List<AsyncState<V>> stack,
      @NotNull final AsyncEvaluation<List<AsyncState<V>>> evaluation,
      @NotNull final List<AsyncStatement<V>> asyncs) {
    evaluation.set(stack);
  }

  public List<AsyncState<V>> value(final List<AsyncState<V>> stack, final V value,
      @NotNull final AsyncEvaluation<List<AsyncState<V>>> evaluation,
      @NotNull final List<AsyncStatement<V>> asyncs, final int index) {
    stack.add(SimpleState.ofValue(value));
    return stack;
  }
}

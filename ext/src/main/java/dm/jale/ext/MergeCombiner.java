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
import java.util.List;

import dm.jale.async.EvaluationCollection;
import dm.jale.async.Loop;
import dm.jale.async.LoopCombiner;
import dm.jale.ext.config.BuildConfig;

/**
 * Created by davide-maestroni on 02/16/2018.
 */
class MergeCombiner<V> implements LoopCombiner<Void, V, V>, Serializable {

  private static final MergeCombiner<?> sInstance = new MergeCombiner<Object>();

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private MergeCombiner() {
  }

  @NotNull
  @SuppressWarnings("unchecked")
  static <V> MergeCombiner<V> instance() {
    return (MergeCombiner<V>) sInstance;
  }

  public Void done(final Void stack, @NotNull final EvaluationCollection<V> evaluation,
      @NotNull final List<Loop<V>> asyncs, final int index) {
    return null;
  }

  public Void failure(final Void stack, final Throwable failure,
      @NotNull final EvaluationCollection<V> evaluation, @NotNull final List<Loop<V>> asyncs,
      final int index) {
    evaluation.addFailure(failure);
    return null;
  }

  public Void init(@NotNull final List<Loop<V>> asyncs) {
    return null;
  }

  public void settle(final Void stack, @NotNull final EvaluationCollection<V> evaluation,
      @NotNull final List<Loop<V>> asyncs) {
    evaluation.set();
  }

  public Void value(final Void stack, final V value,
      @NotNull final EvaluationCollection<V> evaluation, @NotNull final List<Loop<V>> asyncs,
      final int index) {
    evaluation.addValue(value);
    return null;
  }

  @NotNull
  private Object readResolve() throws ObjectStreamException {
    return sInstance;
  }
}

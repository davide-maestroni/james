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

package dm.fates.ext.join;

import org.jetbrains.annotations.NotNull;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.List;

import dm.fates.eventual.EvaluationCollection;
import dm.fates.eventual.Loop;
import dm.fates.eventual.LoopJoiner;
import dm.fates.ext.config.BuildConfig;

/**
 * Created by davide-maestroni on 02/16/2018.
 */
class MergeJoiner<V> implements LoopJoiner<Void, V, V>, Serializable {

  private static final MergeJoiner<?> sInstance = new MergeJoiner<Object>();

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private MergeJoiner() {
  }

  @NotNull
  @SuppressWarnings("unchecked")
  static <V> MergeJoiner<V> instance() {
    return (MergeJoiner<V>) sInstance;
  }

  public Void done(final Void stack, @NotNull final EvaluationCollection<V> evaluation,
      @NotNull final List<Loop<V>> contexts, final int index) {
    return null;
  }

  public Void failure(final Void stack, final Throwable failure,
      @NotNull final EvaluationCollection<V> evaluation, @NotNull final List<Loop<V>> contexts,
      final int index) {
    evaluation.addFailure(failure);
    return null;
  }

  public Void init(@NotNull final List<Loop<V>> contexts) {
    return null;
  }

  public void settle(final Void stack, @NotNull final EvaluationCollection<V> evaluation,
      @NotNull final List<Loop<V>> contexts) {
    evaluation.set();
  }

  public Void value(final Void stack, final V value,
      @NotNull final EvaluationCollection<V> evaluation, @NotNull final List<Loop<V>> contexts,
      final int index) {
    evaluation.addValue(value);
    return null;
  }

  @NotNull
  private Object readResolve() throws ObjectStreamException {
    return sInstance;
  }
}

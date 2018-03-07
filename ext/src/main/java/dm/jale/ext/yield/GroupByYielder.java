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

package dm.jale.ext.yield;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;

import dm.jale.Eventual;
import dm.jale.eventual.EvaluationCollection;
import dm.jale.eventual.FailureException;
import dm.jale.eventual.Loop;
import dm.jale.eventual.Loop.YieldOutputs;
import dm.jale.eventual.LoopYielder;
import dm.jale.eventual.Mapper;
import dm.jale.eventual.Observer;
import dm.jale.ext.config.BuildConfig;
import dm.jale.ext.eventual.KeyedValue;
import dm.jale.util.ConstantConditions;
import dm.jale.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/27/2018.
 */
class GroupByYielder<K, V>
    implements LoopYielder<HashMap<K, EvaluationCollection<V>>, V, KeyedValue<K, Loop<V>>>,
    Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Mapper<? super V, K> mMapper;

  GroupByYielder(@NotNull final Mapper<? super V, K> mapper) {
    mMapper = ConstantConditions.notNull("mapper", mapper);
  }

  private static void failSafe(
      @NotNull final Collection<? extends EvaluationCollection<?>> evaluations,
      @NotNull final Throwable failure) {
    for (final EvaluationCollection<?> evaluation : evaluations) {
      try {
        evaluation.addFailure(failure).set();

      } catch (final Throwable ignored) {
        // Just ignore it
      }
    }
  }

  public void done(final HashMap<K, EvaluationCollection<V>> stack,
      @NotNull final YieldOutputs<KeyedValue<K, Loop<V>>> outputs) {
    try {
      for (final EvaluationCollection<V> evaluation : stack.values()) {
        evaluation.set();
      }

    } catch (final Throwable t) {
      failSafe(stack.values(), t);
      throw FailureException.wrapIfNot(RuntimeException.class, t);
    }
  }

  public HashMap<K, EvaluationCollection<V>> failure(
      final HashMap<K, EvaluationCollection<V>> stack, @NotNull final Throwable failure,
      @NotNull final YieldOutputs<KeyedValue<K, Loop<V>>> outputs) throws Exception {
    try {
      outputs.yieldFailure(failure);

    } catch (final Throwable t) {
      failSafe(stack.values(), t);
      throw FailureException.wrapIfNot(RuntimeException.class, t);
    }

    return null;
  }

  public HashMap<K, EvaluationCollection<V>> init() {
    return new HashMap<K, EvaluationCollection<V>>();
  }

  public boolean loop(final HashMap<K, EvaluationCollection<V>> stack) {
    return true;
  }

  public HashMap<K, EvaluationCollection<V>> value(final HashMap<K, EvaluationCollection<V>> stack,
      final V value, @NotNull final YieldOutputs<KeyedValue<K, Loop<V>>> outputs) throws Exception {
    try {
      final K key = mMapper.apply(value);
      EvaluationCollection<V> evaluation = stack.get(key);
      if (evaluation == null) {
        final EvaluationObserver<V> observer = new EvaluationObserver<V>();
        outputs.yieldValue(KeyedValue.of(key, new Eventual().loop(observer)));
        stack.put(key, observer);
        evaluation = observer;
      }

      evaluation.addValue(value);

    } catch (final Throwable t) {
      failSafe(stack.values(), t);
      throw FailureException.wrapIfNot(RuntimeException.class, t);
    }

    return stack;
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new YielderProxy<K, V>(mMapper);
  }

  private static class EvaluationObserver<V>
      implements Observer<EvaluationCollection<V>>, EvaluationCollection<V> {

    private EvaluationCollection<V> mEvaluation;

    public void accept(final EvaluationCollection<V> evaluation) {
      mEvaluation = evaluation;
    }

    @NotNull
    public EvaluationCollection<V> addFailure(@NotNull final Throwable failure) {
      return mEvaluation.addFailure(failure);
    }

    @NotNull
    public EvaluationCollection<V> addFailures(
        @Nullable final Iterable<? extends Throwable> failures) {
      return mEvaluation.addFailures(failures);
    }

    @NotNull
    public EvaluationCollection<V> addValue(final V value) {
      return mEvaluation.addValue(value);
    }

    @NotNull
    public EvaluationCollection<V> addValues(@Nullable final Iterable<? extends V> values) {
      return mEvaluation.addValues(values);
    }

    public void set() {
      mEvaluation.set();
    }
  }

  private static class YielderProxy<K, V> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private YielderProxy(final Mapper<? super V, K> mapper) {
      super(proxy(mapper));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new GroupByYielder<K, V>((Mapper<? super V, K>) args[0]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

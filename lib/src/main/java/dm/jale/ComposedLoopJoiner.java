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

package dm.jale;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.List;

import dm.jale.async.EvaluationCollection;
import dm.jale.async.JoinCompleter;
import dm.jale.async.JoinSettler;
import dm.jale.async.JoinUpdater;
import dm.jale.async.Joiner;
import dm.jale.async.Loop;
import dm.jale.async.Mapper;
import dm.jale.config.BuildConfig;
import dm.jale.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/14/2018.
 */
class ComposedLoopJoiner<S, V, R>
    implements Joiner<S, V, EvaluationCollection<R>, Loop<V>>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final JoinCompleter<S, ? super EvaluationCollection<? extends R>, Loop<V>> mDone;

  private final JoinUpdater<S, ? super Throwable, ? super EvaluationCollection<? extends R>,
      Loop<V>>
      mFailure;

  private final Mapper<? super List<Loop<V>>, S> mInit;

  private final JoinSettler<S, ? super EvaluationCollection<? extends R>, Loop<V>> mSettle;

  private final JoinUpdater<S, ? super V, ? super EvaluationCollection<? extends R>, Loop<V>>
      mValue;

  @SuppressWarnings("unchecked")
  ComposedLoopJoiner(@Nullable final Mapper<? super List<Loop<V>>, S> init,
      @Nullable final JoinUpdater<S, ? super V, ? super EvaluationCollection<? extends R>,
          Loop<V>> value,
      @Nullable final JoinUpdater<S, ? super Throwable, ? super EvaluationCollection<? extends
          R>, Loop<V>> failure,
      @Nullable final JoinCompleter<S, ? super EvaluationCollection<? extends R>, Loop<V>> done,
      @Nullable final JoinSettler<S, ? super EvaluationCollection<? extends R>, Loop<V>> settle) {
    mInit = (Mapper<? super List<Loop<V>>, S>) ((init != null) ? init : DefaultInit.sInstance);
    mValue = (JoinUpdater<S, ? super V, ? super EvaluationCollection<? extends R>, Loop<V>>) (
        (value != null) ? value : DefaultUpdater.sInstance);
    mFailure =
        (JoinUpdater<S, ? super Throwable, ? super EvaluationCollection<? extends R>, Loop<V>>) (
            (failure != null) ? failure : DefaultUpdater.sInstance);
    mDone = (JoinCompleter<S, ? super EvaluationCollection<? extends R>, Loop<V>>) ((done != null)
        ? done : DefaultCompleter.sInstance);
    mSettle = (JoinSettler<S, ? super EvaluationCollection<? extends R>, Loop<V>>) ((settle != null)
        ? settle : DefaultSettler.sInstance);
  }

  public S done(final S stack, @NotNull final EvaluationCollection<R> evaluation,
      @NotNull final List<Loop<V>> contexts, final int index) throws Exception {
    return mDone.complete(stack, evaluation, contexts, index);
  }

  public S failure(final S stack, final Throwable failure,
      @NotNull final EvaluationCollection<R> evaluation, @NotNull final List<Loop<V>> contexts,
      final int index) throws Exception {
    return mFailure.update(stack, failure, evaluation, contexts, index);
  }

  public S init(@NotNull final List<Loop<V>> contexts) throws Exception {
    return mInit.apply(contexts);
  }

  public void settle(final S stack, @NotNull final EvaluationCollection<R> evaluation,
      @NotNull final List<Loop<V>> contexts) throws Exception {
    mSettle.settle(stack, evaluation, contexts);
  }

  public S value(final S stack, final V value, @NotNull final EvaluationCollection<R> evaluation,
      @NotNull final List<Loop<V>> contexts, final int index) throws Exception {
    return mValue.update(stack, value, evaluation, contexts, index);
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new CombinerProxy<S, V, R>(mInit, mValue, mFailure, mDone, mSettle);
  }

  private static class CombinerProxy<S, V, R> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private CombinerProxy(final Mapper<? super List<Loop<V>>, S> init,
        final JoinUpdater<S, ? super V, ? super EvaluationCollection<? extends R>, Loop<V>> value,
        final JoinUpdater<S, ? super Throwable, ? super EvaluationCollection<? extends R>,
            Loop<V>> failure,
        final JoinCompleter<S, ? super EvaluationCollection<? extends R>, Loop<V>> done,
        final JoinSettler<S, ? super EvaluationCollection<? extends R>, Loop<V>> settle) {
      super(proxy(init), proxy(value), proxy(failure), proxy(done), proxy(settle));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new ComposedLoopJoiner<S, V, R>((Mapper<? super List<Loop<V>>, S>) args[0],
            (JoinUpdater<S, ? super V, ? super EvaluationCollection<? extends R>, Loop<V>>) args[1],
            (JoinUpdater<S, ? super Throwable, ? super EvaluationCollection<? extends R>,
                Loop<V>>) args[2],
            (JoinCompleter<S, ? super EvaluationCollection<? extends R>, Loop<V>>) args[3],
            (JoinSettler<S, ? super EvaluationCollection<? extends R>, Loop<V>>) args[4]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }

  private static class DefaultCompleter<S, V, R>
      implements JoinCompleter<S, EvaluationCollection<? extends R>, Loop<V>>, Serializable {

    private static final DefaultCompleter<?, ?, ?> sInstance =
        new DefaultCompleter<Object, Object, Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    public S complete(final S stack, @NotNull final EvaluationCollection<? extends R> evaluation,
        @NotNull final List<Loop<V>> loops, final int index) {
      return stack;
    }

    @NotNull
    private Object readResolve() throws ObjectStreamException {
      return sInstance;
    }
  }

  private static class DefaultInit<S, V> implements Mapper<List<Loop<V>>, S>, Serializable {

    private static final DefaultInit<?, ?> sInstance = new DefaultInit<Object, Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    public S apply(final List<Loop<V>> loops) {
      return null;
    }

    @NotNull
    private Object readResolve() throws ObjectStreamException {
      return sInstance;
    }
  }

  private static class DefaultSettler<S, V, R>
      implements JoinSettler<S, EvaluationCollection<? extends R>, Loop<V>>, Serializable {

    private static final DefaultSettler<?, ?, ?> sInstance =
        new DefaultSettler<Object, Object, Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    public void settle(final S stack, @NotNull final EvaluationCollection<? extends R> evaluation,
        @NotNull final List<Loop<V>> loops) throws Exception {
      evaluation.set();
    }

    @NotNull
    private Object readResolve() throws ObjectStreamException {
      return sInstance;
    }
  }

  private static class DefaultUpdater<S, V, I, R>
      implements JoinUpdater<S, I, EvaluationCollection<? extends R>, Loop<V>>, Serializable {

    private static final DefaultUpdater<?, ?, ?, ?> sInstance =
        new DefaultUpdater<Object, Object, Object, Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    public S update(final S stack, final I input,
        @NotNull final EvaluationCollection<? extends R> evaluation,
        @NotNull final List<Loop<V>> loops, final int index) {
      return stack;
    }

    @NotNull
    private Object readResolve() throws ObjectStreamException {
      return sInstance;
    }
  }
}

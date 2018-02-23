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

import dm.jale.config.BuildConfig;
import dm.jale.eventual.Evaluation;
import dm.jale.eventual.JoinCompleter;
import dm.jale.eventual.JoinSettler;
import dm.jale.eventual.JoinUpdater;
import dm.jale.eventual.Joiner;
import dm.jale.eventual.Mapper;
import dm.jale.eventual.Statement;
import dm.jale.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/14/2018.
 */
class ComposedStatementJoiner<S, V, R>
    implements Joiner<S, V, Evaluation<R>, Statement<V>>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final JoinCompleter<S, ? super Evaluation<? extends R>, Statement<V>> mDone;

  private final JoinUpdater<S, ? super Throwable, ? super Evaluation<? extends R>, Statement<V>>
      mFailure;

  private final Mapper<? super List<Statement<V>>, S> mInit;

  private final JoinSettler<S, ? super Evaluation<? extends R>, Statement<V>> mSettle;

  private final JoinUpdater<S, ? super V, ? super Evaluation<? extends R>, Statement<V>> mValue;

  @SuppressWarnings("unchecked")
  ComposedStatementJoiner(@Nullable final Mapper<? super List<Statement<V>>, S> init,
      @Nullable final JoinUpdater<S, ? super V, ? super Evaluation<? extends R>, Statement<V>>
          value,
      @Nullable final JoinUpdater<S, ? super Throwable, ? super Evaluation<? extends R>,
          Statement<V>> failure,
      @Nullable final JoinCompleter<S, ? super Evaluation<? extends R>, Statement<V>> done,
      @Nullable final JoinSettler<S, ? super Evaluation<? extends R>, Statement<V>> settle) {
    mInit = (Mapper<? super List<Statement<V>>, S>) ((init != null) ? init : DefaultInit.sInstance);
    mValue =
        (JoinUpdater<S, ? super V, ? super Evaluation<? extends R>, Statement<V>>) ((value != null)
            ? value : DefaultUpdater.sInstance);
    mFailure = (JoinUpdater<S, ? super Throwable, ? super Evaluation<? extends R>, Statement<V>>) (
        (failure != null) ? failure : DefaultUpdater.sInstance);
    mDone = (JoinCompleter<S, ? super Evaluation<? extends R>, Statement<V>>) ((done != null) ? done
        : DefaultCompleter.sInstance);
    mSettle =
        (JoinSettler<S, ? super Evaluation<? extends R>, Statement<V>>) ((settle != null) ? settle
            : DefaultSettler.sInstance);
  }

  public S done(final S stack, @NotNull final Evaluation<R> evaluation,
      @NotNull final List<Statement<V>> contexts, final int index) throws Exception {
    return mDone.complete(stack, evaluation, contexts, index);
  }

  public S failure(final S stack, final Throwable failure, @NotNull final Evaluation<R> evaluation,
      @NotNull final List<Statement<V>> contexts, final int index) throws Exception {
    return mFailure.update(stack, failure, evaluation, contexts, index);
  }

  public S init(@NotNull final List<Statement<V>> contexts) throws Exception {
    return mInit.apply(contexts);
  }

  public void settle(final S stack, @NotNull final Evaluation<R> evaluation,
      @NotNull final List<Statement<V>> contexts) throws Exception {
    mSettle.settle(stack, evaluation, contexts);
  }

  public S value(final S stack, final V value, @NotNull final Evaluation<R> evaluation,
      @NotNull final List<Statement<V>> contexts, final int index) throws Exception {
    return mValue.update(stack, value, evaluation, contexts, index);
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new JoinerProxy<S, V, R>(mInit, mValue, mFailure, mDone, mSettle);
  }

  private static class DefaultCompleter<S, V, R>
      implements JoinCompleter<S, Evaluation<? extends R>, Statement<V>>, Serializable {

    private static final DefaultCompleter<?, ?, ?> sInstance =
        new DefaultCompleter<Object, Object, Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    public S complete(final S stack, @NotNull final Evaluation<? extends R> evaluation,
        @NotNull final List<Statement<V>> statements, final int index) {
      return stack;
    }

    @NotNull
    private Object readResolve() throws ObjectStreamException {
      return sInstance;
    }
  }

  private static class DefaultInit<S, V> implements Mapper<List<Statement<V>>, S>, Serializable {

    private static final DefaultInit<?, ?> sInstance = new DefaultInit<Object, Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    public S apply(final List<Statement<V>> statements) {
      return null;
    }

    @NotNull
    private Object readResolve() throws ObjectStreamException {
      return sInstance;
    }
  }

  private static class DefaultSettler<S, V, R>
      implements JoinSettler<S, Evaluation<? extends R>, Statement<V>>, Serializable {

    private static final DefaultSettler<?, ?, ?> sInstance =
        new DefaultSettler<Object, Object, Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    public void settle(final S stack, @NotNull final Evaluation<? extends R> evaluation,
        @NotNull final List<Statement<V>> statements) throws Exception {
      evaluation.fail(new IllegalStateException());
    }

    @NotNull
    private Object readResolve() throws ObjectStreamException {
      return sInstance;
    }
  }

  private static class DefaultUpdater<S, V, I, R>
      implements JoinUpdater<S, I, Evaluation<? extends R>, Statement<V>>, Serializable {

    private static final DefaultUpdater<?, ?, ?, ?> sInstance =
        new DefaultUpdater<Object, Object, Object, Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    public S update(final S stack, final I input, @NotNull final Evaluation<? extends R> evaluation,
        @NotNull final List<Statement<V>> statements, final int index) {
      return stack;
    }

    @NotNull
    private Object readResolve() throws ObjectStreamException {
      return sInstance;
    }
  }

  private static class JoinerProxy<S, V, R> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private JoinerProxy(final Mapper<? super List<Statement<V>>, S> init,
        final JoinUpdater<S, ? super V, ? super Evaluation<? extends R>, Statement<V>> value,
        final JoinUpdater<S, ? super Throwable, ? super Evaluation<? extends R>, Statement<V>>
            failure,
        final JoinCompleter<S, ? super Evaluation<? extends R>, Statement<V>> done,
        final JoinSettler<S, ? super Evaluation<? extends R>, Statement<V>> settle) {
      super(proxy(init), proxy(value), proxy(failure), proxy(done), proxy(settle));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new ComposedStatementJoiner<S, V, R>((Mapper<? super List<Statement<V>>, S>) args[0],
            (JoinUpdater<S, ? super V, ? super Evaluation<? extends R>, Statement<V>>) args[1],
            (JoinUpdater<S, ? super Throwable, ? super Evaluation<? extends R>, Statement<V>>)
                args[2],
            (JoinCompleter<S, ? super Evaluation<? extends R>, Statement<V>>) args[3],
            (JoinSettler<S, ? super Evaluation<? extends R>, Statement<V>>) args[4]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

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

package dm.jale.ext.fork;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Executor;

import dm.jale.async.AsyncEvaluations;
import dm.jale.async.AsyncLoop;
import dm.jale.async.AsyncStatement.Forker;
import dm.jale.async.Completer;
import dm.jale.async.Provider;
import dm.jale.async.Updater;
import dm.jale.util.ConstantConditions;

/**
 * Created by davide-maestroni on 02/09/2018.
 */
public class Forkers {

  private Forkers() {
    ConstantConditions.avoid();
  }

  @NotNull
  public static <S, V> Forker<?, V, AsyncEvaluations<V>, AsyncLoop<V>> backoffOn(
      @NotNull final Executor executor, @NotNull final Backoffer<S, V> backoffer) {
    return new BackoffForker<S, V>(executor, backoffer);
  }

  @NotNull
  public static <S, V> Forker<?, V, AsyncEvaluations<V>, AsyncLoop<V>> backoffOn(
      @NotNull final Executor executor, @Nullable final Provider<S> init,
      @Nullable final Updater<S, ? super V, ? super dm.jale.ext.fork.PendingEvaluations<V>> value,
      @Nullable final Updater<S, ? super Throwable, ? super dm.jale.ext.fork
          .PendingEvaluations<V>> failure,
      @Nullable final Completer<S, ? super dm.jale.ext.fork.PendingEvaluations<V>> done) {
    return backoffOn(executor,
        new dm.jale.ext.fork.ComposedBackoffer<S, V>(init, value, failure, done));
  }
}

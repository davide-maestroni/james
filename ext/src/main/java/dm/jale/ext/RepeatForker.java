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

import dm.jale.eventual.Evaluation;
import dm.jale.eventual.Statement;
import dm.jale.eventual.StatementForker;
import dm.jale.ext.config.BuildConfig;

/**
 * Created by davide-maestroni on 02/19/2018.
 */
class RepeatForker<V> implements StatementForker<Void, V>, Serializable {

  private static final RepeatForker<?> sInstance = new RepeatForker<Object>();

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private RepeatForker() {
  }

  @NotNull
  @SuppressWarnings("unchecked")
  static <V> StatementForker<?, V> instance() {
    return (RepeatForker<V>) sInstance;
  }

  public Void done(final Void stack, @NotNull final Statement<V> context) {
    return null;
  }

  public Void evaluation(final Void stack, @NotNull final Evaluation<V> evaluation,
      @NotNull final Statement<V> context) {
    context.evaluate().to(evaluation);
    return null;
  }

  public Void failure(final Void stack, @NotNull final Throwable failure,
      @NotNull final Statement<V> context) {
    return null;
  }

  public Void init(@NotNull final Statement<V> context) {
    return null;
  }

  public Void value(final Void stack, final V value, @NotNull final Statement<V> context) {
    return null;
  }

  @NotNull
  private Object readResolve() throws ObjectStreamException {
    return sInstance;
  }
}

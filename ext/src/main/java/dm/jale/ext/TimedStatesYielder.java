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

import dm.jale.eventual.Loop.YieldOutputs;
import dm.jale.eventual.Loop.Yielder;
import dm.jale.ext.config.BuildConfig;
import dm.jale.ext.eventual.TimedState;

/**
 * Created by davide-maestroni on 02/19/2018.
 */
class TimedStatesYielder<V> implements Yielder<Void, V, TimedState<V>>, Serializable {

  private static final TimedStatesYielder<?> sInstance = new TimedStatesYielder<Object>();

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private TimedStatesYielder() {
  }

  @NotNull
  @SuppressWarnings("unchecked")
  static <V> TimedStatesYielder<V> instance() {
    return (TimedStatesYielder<V>) sInstance;
  }

  public void done(final Void stack, @NotNull final YieldOutputs<TimedState<V>> outputs) {
  }

  public Void failure(final Void stack, @NotNull final Throwable failure,
      @NotNull final YieldOutputs<TimedState<V>> outputs) {
    outputs.yieldValue(TimedState.<V>ofFailure(failure));
    return null;
  }

  public Void init() {
    return null;
  }

  public boolean loop(final Void stack) {
    return true;
  }

  public Void value(final Void stack, final V value,
      @NotNull final YieldOutputs<TimedState<V>> outputs) {
    outputs.yieldValue(TimedState.ofValue(value));
    return null;
  }

  @NotNull
  private Object readResolve() throws ObjectStreamException {
    return sInstance;
  }
}

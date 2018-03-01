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

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.ArrayList;

import dm.jale.config.BuildConfig;
import dm.jale.eventual.Loop.YieldOutputs;
import dm.jale.eventual.LoopYielder;

/**
 * Created by davide-maestroni on 02/23/2018.
 */
class ListYielder<V> implements LoopYielder<ArrayList<V>, V, ArrayList<V>>, Serializable {

  private static final ListYielder<?> sInstance = new ListYielder();

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private ListYielder() {
  }

  @NotNull
  @SuppressWarnings("unchecked")
  static <V> ListYielder<V> instance() {
    return (ListYielder<V>) sInstance;
  }

  public void done(final ArrayList<V> stack, @NotNull final YieldOutputs<ArrayList<V>> outputs) {
    if (stack != null) {
      outputs.yieldValue(stack);
    }
  }

  public ArrayList<V> failure(final ArrayList<V> stack, @NotNull final Throwable failure,
      @NotNull final YieldOutputs<ArrayList<V>> outputs) {
    outputs.yieldFailure(failure);
    return null;
  }

  public ArrayList<V> init() {
    return new ArrayList<V>();
  }

  public boolean loop(final ArrayList<V> stack) {
    return (stack != null);
  }

  public ArrayList<V> value(final ArrayList<V> stack, final V value,
      @NotNull final YieldOutputs<ArrayList<V>> outputs) {
    stack.add(value);
    return stack;
  }

  @NotNull
  private Object readResolve() throws ObjectStreamException {
    return sInstance;
  }
}

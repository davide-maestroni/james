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
import java.util.ArrayList;

import dm.jale.async.Loop.YieldOutputs;
import dm.jale.async.Loop.Yielder;
import dm.jale.ext.config.BuildConfig;

/**
 * Created by davide-maestroni on 02/19/2018.
 */
class DelayedFailuresYielder<V> implements Yielder<ArrayList<Throwable>, V, V>, Serializable {

  private static final DelayedFailuresYielder<?> sInstance = new DelayedFailuresYielder<Object>();

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private DelayedFailuresYielder() {
  }

  @NotNull
  @SuppressWarnings("unchecked")
  static <V> DelayedFailuresYielder<V> instance() {
    return (DelayedFailuresYielder<V>) sInstance;
  }

  public void done(final ArrayList<Throwable> stack, @NotNull final YieldOutputs<V> outputs) {
    outputs.yieldFailures(stack);
  }

  public ArrayList<Throwable> failure(final ArrayList<Throwable> stack,
      @NotNull final Throwable failure, @NotNull final YieldOutputs<V> outputs) {
    stack.add(failure);
    return stack;
  }

  public ArrayList<Throwable> init() {
    return new ArrayList<Throwable>();
  }

  public boolean loop(final ArrayList<Throwable> stack) {
    return true;
  }

  public ArrayList<Throwable> value(final ArrayList<Throwable> stack, final V value,
      @NotNull final YieldOutputs<V> outputs) {
    outputs.yieldValue(value);
    return stack;
  }

  @NotNull
  private Object readResolve() throws ObjectStreamException {
    return sInstance;
  }
}

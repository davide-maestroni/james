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

package dm.jail;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

import dm.jail.async.AsyncLoop.YieldOutputs;
import dm.jail.async.AsyncLoop.Yielder;

/**
 * Created by davide-maestroni on 02/05/2018.
 */
class CollectionYielder<V> implements Yielder<ArrayList<V>, V, V> {

  public void done(final ArrayList<V> stack, @NotNull final YieldOutputs<V> outputs) throws
      Exception {
    if (stack != null) {
      outputs.yieldValues(stack);
    }
  }

  public ArrayList<V> failure(final ArrayList<V> stack, @NotNull final Throwable failure,
      @NotNull final YieldOutputs<V> outputs) throws Exception {
    outputs.yieldFailure(failure);
    return null;
  }

  public ArrayList<V> init() throws Exception {
    return new ArrayList<V>();
  }

  public boolean loop(final ArrayList<V> stack) throws Exception {
    return (stack != null);
  }

  public ArrayList<V> value(final ArrayList<V> stack, final V value,
      @NotNull final YieldOutputs<V> outputs) throws Exception {
    stack.add(value);
    return stack;
  }
}

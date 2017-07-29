/*
 * Copyright 2017 Davide Maestroni
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

package dm.james;

import org.jetbrains.annotations.Nullable;

import java.io.Serializable;

import dm.james.promise.Observer;
import dm.james.promise.PromiseIterable.CallbackIterable;

/**
 * Created by davide-maestroni on 07/27/2017.
 */
class ResolvedIterableObserver<O> implements Observer<CallbackIterable<O>>, Serializable {

  private final Iterable<O> mOutputs;

  ResolvedIterableObserver(@Nullable final Iterable<O> outputs) {
    mOutputs = outputs;
  }

  public void accept(final CallbackIterable<O> callback) {
    callback.addAll(mOutputs);
    callback.resolve();
  }
}

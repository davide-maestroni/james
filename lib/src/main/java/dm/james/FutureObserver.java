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

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Future;

import dm.james.promise.Observer;
import dm.james.promise.Promise.Callback;
import dm.james.util.ConstantConditions;

/**
 * Created by davide-maestroni on 08/07/2017.
 */
class FutureObserver<O> implements Observer<Callback<O>> {

  private final Future<O> mFuture;

  FutureObserver(@NotNull final Future<O> future) {
    mFuture = ConstantConditions.notNull("future", future);
  }

  public void accept(final Callback<O> callback) throws Exception {
    callback.resolve(mFuture.get());
  }
}

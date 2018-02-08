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

import java.io.Serializable;

import dm.jail.async.AsyncEvaluation;
import dm.jail.async.AsyncLoop;
import dm.jail.async.Observer;
import dm.jail.config.BuildConfig;
import dm.jail.util.ConstantConditions;

/**
 * Created by davide-maestroni on 02/02/2018.
 */
class ToStatementObserver<V>
    implements RenewableObserver<AsyncEvaluation<Iterable<V>>>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final AsyncLoop<V> mLoop;

  ToStatementObserver(@NotNull final AsyncLoop<V> loop) {
    mLoop = ConstantConditions.notNull("loop", loop);
  }

  public void accept(final AsyncEvaluation<Iterable<V>> evaluation) {
    mLoop.to(evaluation);
  }

  @NotNull
  public Observer<AsyncEvaluation<Iterable<V>>> renew() {
    return new ToStatementObserver<V>(mLoop.evaluate());
  }
}

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

import java.io.Serializable;

import dm.jale.config.BuildConfig;
import dm.jale.eventual.Evaluation;
import dm.jale.eventual.Loop;
import dm.jale.eventual.Observer;
import dm.jale.util.ConstantConditions;

/**
 * Created by davide-maestroni on 02/02/2018.
 */
class ToStatementObserver<V> implements RenewableObserver<Evaluation<Iterable<V>>>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Loop<V> mLoop;

  ToStatementObserver(@NotNull final Loop<V> loop) {
    mLoop = ConstantConditions.notNull("loop", loop);
  }

  public void accept(final Evaluation<Iterable<V>> evaluation) {
    mLoop.to(evaluation);
  }

  @NotNull
  public Observer<Evaluation<Iterable<V>>> renew() {
    return new ToStatementObserver<V>(mLoop.evaluate());
  }
}

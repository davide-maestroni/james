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

import dm.jail.async.AsyncResult;
import dm.jail.config.BuildConfig;

/**
 * Created by davide-maestroni on 01/14/2018.
 */
class AsyncStatementHandler<V, R> implements Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  void failure(@NotNull final Throwable failure, @NotNull final AsyncResult<R> result) throws Exception {
    result.fail(failure);
  }

  @SuppressWarnings("unchecked")
  void value(final V value, @NotNull final AsyncResult<R> result) throws Exception {
    result.set((R) value);
  }
}

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

package dm.jale.ext.backoff;

import org.jetbrains.annotations.NotNull;

/**
 * Created by davide-maestroni on 02/09/2018.
 */
public interface Backoffer<S, V> {

  void done(S stack, @NotNull PendingEvaluation<V> evaluation) throws Exception;

  S failure(S stack, @NotNull Throwable failure, @NotNull PendingEvaluation<V> evaluation) throws
      Exception;

  S init() throws Exception;

  S value(S stack, V value, @NotNull PendingEvaluation<V> evaluation) throws Exception;
}

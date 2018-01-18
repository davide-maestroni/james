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

package dm.jail.async;

import org.jetbrains.annotations.NotNull;

/**
 * Created by davide-maestroni on 01/08/2018.
 */
public class Pair<V1, V2> {

  private final V1 mFirst;

  private final V2 mSecond;

  private Pair(final V1 first, final V2 second) {
    mFirst = first;
    mSecond = second;
  }

  @NotNull
  public static <V1, V2> Pair<V1, V2> of(V1 first, V2 second) {
    return new Pair<V1, V2>(first, second);
  }

  public V1 first() {
    return mFirst;
  }

  public V2 second() {
    return mSecond;
  }
}

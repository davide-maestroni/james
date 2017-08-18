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

package dm.james.promise;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

/**
 * Created by davide-maestroni on 08/17/2017.
 */
public class RejectionIterableException extends RejectionException implements Iterable<Throwable> {

  private final Collection<Throwable> mCauses;

  public RejectionIterableException(@NotNull final Collection<Throwable> causes) {
    mCauses = Collections.unmodifiableCollection(causes);
  }

  @Override
  public String getMessage() {
    return mCauses.toString();
  }

  public Iterator<Throwable> iterator() {
    return mCauses.iterator();
  }
}

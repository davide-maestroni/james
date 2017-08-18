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

import java.io.ObjectStreamException;
import java.io.Serializable;

import dm.james.promise.Mapper;
import dm.james.promise.RejectionException;

/**
 * Created by davide-maestroni on 07/29/2017.
 */
class RethrowMapper<T> implements Mapper<Throwable, T>, Serializable {

  private static final RethrowMapper<?> sMapper = new RethrowMapper<Object>();

  private RethrowMapper() {
  }

  @NotNull
  @SuppressWarnings("unchecked")
  static <T> Mapper<Throwable, T> instance() {
    return (Mapper<Throwable, T>) sMapper;
  }

  public T apply(final Throwable t) throws Exception {
    throw RejectionException.wrapIfNotException(t);
  }

  Object readResolve() throws ObjectStreamException {
    return sMapper;
  }
}

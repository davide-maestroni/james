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

package dm.jale.ext.fork;

import org.jetbrains.annotations.NotNull;

import java.io.ObjectStreamException;
import java.io.Serializable;

import dm.jale.eventual.Mapper;
import dm.jale.ext.config.BuildConfig;
import dm.jale.ext.eventual.TimedState;

/**
 * Created by davide-maestroni on 02/25/2018.
 */
class TimedStateValueMapper<V> implements Mapper<V, TimedState<V>>, Serializable {

  private static final TimedStateValueMapper<?> sInstance = new TimedStateValueMapper<Object>();

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  @NotNull
  @SuppressWarnings("unchecked")
  static <V> TimedStateValueMapper<V> instance() {
    return (TimedStateValueMapper<V>) sInstance;
  }

  public TimedState<V> apply(final V value) {
    return TimedState.ofValue(value);
  }

  @NotNull
  private Object readResolve() throws ObjectStreamException {
    return sInstance;
  }
}

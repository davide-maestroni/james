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

package dm.fates.ext.eventual;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

import dm.fates.ext.config.BuildConfig;

/**
 * Created by davide-maestroni on 03/07/2018.
 */
public class KeyedValue<K, V> implements Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final K mKey;

  private final V mValue;

  private KeyedValue(final K key, final V value) {
    mKey = key;
    mValue = value;
  }

  @NotNull
  public static <K, V> KeyedValue<K, V> of(final K key, final V value) {
    return new KeyedValue<K, V>(key, value);
  }

  public K getKey() {
    return mKey;
  }

  public V getValue() {
    return mValue;
  }
}

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

package dm.fates.ext;

import org.jetbrains.annotations.NotNull;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.List;

import dm.fates.eventual.Mapper;
import dm.fates.ext.config.BuildConfig;
import dm.fates.ext.eventual.QuadriMapper;
import dm.fates.util.ConstantConditions;
import dm.fates.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/26/2018.
 */
class QuadriZipMapper<V1, V2, V3, V4, R> implements Mapper<List<Object>, R>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final QuadriMapper<V1, V2, V3, V4, R> mMapper;

  QuadriZipMapper(@NotNull final QuadriMapper<V1, V2, V3, V4, R> mapper) {
    mMapper = ConstantConditions.notNull("mapper", mapper);
  }

  @SuppressWarnings("unchecked")
  public R apply(final List<Object> input) throws Exception {
    return mMapper.apply((V1) input.get(0), (V2) input.get(1), (V3) input.get(2),
        (V4) input.get(3));
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new MapperProxy<V1, V2, V3, V4, R>(mMapper);
  }

  private static class MapperProxy<V1, V2, V3, V4, R> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private MapperProxy(final QuadriMapper<V1, V2, V3, V4, R> mapper) {
      super(proxy(mapper));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new QuadriZipMapper<V1, V2, V3, V4, R>((QuadriMapper<V1, V2, V3, V4, R>) args[0]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

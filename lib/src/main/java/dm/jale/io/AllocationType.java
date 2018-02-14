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

package dm.jale.io;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;

import dm.james.promise.Mapper;

/**
 * Created by davide-maestroni on 08/12/2017.
 */
public enum AllocationType {
  HEAP(new Mapper<Integer, ByteBuffer>() {

    public ByteBuffer apply(final Integer limit) {
      return ByteBuffer.allocate(limit);
    }
  }),

  DIRECT(new Mapper<Integer, ByteBuffer>() {

    public ByteBuffer apply(final Integer limit) {
      return ByteBuffer.allocateDirect(limit);
    }
  });

  private final Mapper<Integer, ByteBuffer> mMapper;

  AllocationType(@NotNull final Mapper<Integer, ByteBuffer> mapper) {
    mMapper = mapper;
  }

  @NotNull
  public ByteBuffer allocate(final int limit) {
    try {
      return mMapper.apply(limit);

    } catch (final Exception e) {
      // should never happen
    }

    return ByteBuffer.allocate(limit);
  }
}

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

package dm.jale.ext;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import dm.jale.async.EvaluationCollection;
import dm.jale.async.Observer;
import dm.jale.ext.io.AllocationType;
import dm.jale.ext.io.Chunk;
import dm.jale.ext.io.ChunkOutputStream;
import dm.jale.util.ConstantConditions;

/**
 * Created by davide-maestroni on 02/16/2018.
 */
abstract class ChunkObserver implements Observer<EvaluationCollection<Chunk>> {

  private final AllocationType mAllocationType;

  private final Integer mBufferSize;

  private final Integer mCoreSize;

  private final Integer mPoolSize;

  ChunkObserver(@Nullable final AllocationType allocationType, @Nullable final Integer coreSize,
      @Nullable final Integer bufferSize, @Nullable final Integer poolSize) {
    mCoreSize = (coreSize != null) ? ConstantConditions.positive("coreSize", coreSize) : null;
    mBufferSize =
        (bufferSize != null) ? ConstantConditions.positive("bufferSize", bufferSize) : null;
    mPoolSize = (poolSize != null) ? ConstantConditions.positive("poolSize", poolSize) : null;
    mAllocationType = allocationType;
  }

  @NotNull
  ChunkOutputStream newStream(@NotNull final EvaluationCollection<Chunk> evaluation) {
    if (mCoreSize != null) {
      return new ChunkOutputStream(evaluation, mAllocationType, mCoreSize);
    }

    if ((mBufferSize != null) && (mPoolSize != null)) {
      return new ChunkOutputStream(evaluation, mAllocationType, mBufferSize, mPoolSize);
    }

    return new ChunkOutputStream(evaluation, mAllocationType);
  }
}

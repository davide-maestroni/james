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
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;

import dm.fates.eventual.EvaluationCollection;
import dm.fates.ext.config.BuildConfig;
import dm.fates.ext.io.AllocationType;
import dm.fates.ext.io.Chunk;
import dm.fates.ext.io.ChunkOutputStream;
import dm.fates.util.ConstantConditions;

/**
 * Created by davide-maestroni on 02/15/2018.
 */
class ByteArrayChunkObserver extends ChunkObserver implements Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final byte[] mBuffer;

  ByteArrayChunkObserver(@NotNull final byte[] buffer,
      @Nullable final AllocationType allocationType, @Nullable final Integer coreSize,
      @Nullable final Integer bufferSize, @Nullable final Integer poolSize) {
    super(allocationType, coreSize, bufferSize, poolSize);
    mBuffer = ConstantConditions.notNull("buffer", buffer);
  }

  public void accept(final EvaluationCollection<Chunk> evaluation) throws Exception {
    final ChunkOutputStream outputStream = newStream(evaluation);
    try {
      outputStream.write(mBuffer);
      evaluation.set();

    } finally {
      outputStream.close();
    }
  }
}

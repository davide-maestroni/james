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

import java.io.InputStream;

import dm.jale.async.AsyncEvaluations;
import dm.jale.ext.io.AllocationType;
import dm.jale.ext.io.Chunk;
import dm.jale.ext.io.ChunkOutputStream;
import dm.jale.util.ConstantConditions;

/**
 * Created by davide-maestroni on 02/15/2018.
 */
class InputStreamChunkObserver extends ChunkObserver {

  private final InputStream mInputStream;

  InputStreamChunkObserver(@NotNull final InputStream inputStream,
      @Nullable final AllocationType allocationType, @Nullable final Integer coreSize,
      @Nullable final Integer bufferSize, @Nullable final Integer poolSize) {
    super(allocationType, coreSize, bufferSize, poolSize);
    mInputStream = ConstantConditions.notNull("inputStream", inputStream);
  }

  public void accept(final AsyncEvaluations<Chunk> evaluations) throws Exception {
    final ChunkOutputStream outputStream = newStream(evaluations);
    try {
      outputStream.transfer(mInputStream);
      evaluations.set();

    } finally {
      outputStream.close();
    }
  }
}

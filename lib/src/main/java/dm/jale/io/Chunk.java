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

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

/**
 * Created by davide-maestroni on 08/10/2017.
 */
public interface Chunk extends Closeable {

  // TODO: 13/02/2018 AsyncExt?

  int available();

  byte peek(int index);

  void peek(int index, @NotNull byte[] b, int off, int len);

  void peek(int index, @NotNull byte[] b);

  /**
   * Reads all the bytes and writes them into the specified output stream.
   *
   * @param out the output stream.
   * @return the total number of bytes read.
   * @throws IOException if an I/O error occurs. In particular, an {@code IOException} may
   *                     be thrown if the output stream has been closed.
   */
  int read(@NotNull OutputStream out) throws IOException;

  int read(@NotNull ByteBuffer buffer) throws IOException;

  int read(@NotNull WritableByteChannel channel) throws IOException;
}

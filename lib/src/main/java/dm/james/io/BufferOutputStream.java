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

package dm.james.io;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

import dm.james.promise.PromiseIterable;

/**
 * Created by davide-maestroni on 08/10/2017.
 */
public abstract class BufferOutputStream extends OutputStream {

  @Override
  public void flush() {
  }

  @Override
  public void close() {
  }

  @NotNull
  public abstract PromiseIterable<Buffer> iterable();

  /**
   * Transfers all the bytes from the specified input stream.
   *
   * @param in the input stream.
   * @return the total number of bytes written.
   * @throws java.io.IOException If the first byte cannot be read for any reason other than
   *                             end of file, or if the input stream has been closed, or if
   *                             some other I/O error occurs.
   */
  public long transfer(@NotNull final InputStream in) throws IOException {
    long count = 0;
    for (int b; (b = write(in)) > 0; ) {
      count += b;
    }

    return count;
  }

  public abstract long transfer(@NotNull ReadableByteChannel channel) throws IOException;

  public abstract int write(@NotNull ByteBuffer buffer) throws IOException;

  public abstract int write(@NotNull Buffer buffer) throws IOException;

  /**
   * Writes up to {@code limit} bytes into the output stream by reading them from the specified
   * \input stream.
   *
   * @param in    the input stream.
   * @param limit the maximum number of bytes to write.
   * @return the total number of bytes written into the chunk, or {@code -1} if there is no more
   * data because the end of the stream has been reached.
   * @throws java.lang.IllegalArgumentException if the limit is negative.
   * @throws java.io.IOException                If the first byte cannot be read for any reason
   *                                            other than end of file, or if the input stream has
   *                                            been closed, or if some other I/O error occurs.
   */
  public abstract int write(@NotNull InputStream in, int limit) throws IOException;

  /**
   * Writes some bytes into the output stream by reading them from the specified input stream.
   *
   * @param in the input stream.
   * @return the total number of bytes written into the chunk, or {@code -1} if there is no more
   * data because the end of the stream has been reached.
   * @throws java.io.IOException If the first byte cannot be read for any reason other than end of
   *                             file, or if the input stream has been closed, or if some other
   *                             I/O error occurs.
   */
  public abstract int write(@NotNull InputStream in) throws IOException;
}

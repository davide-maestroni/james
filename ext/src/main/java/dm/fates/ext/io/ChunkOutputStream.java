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

package dm.fates.ext.io;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import dm.fates.eventual.EvaluationCollection;
import dm.fates.ext.config.BuildConfig;
import dm.fates.util.ConstantConditions;
import dm.fates.util.DoubleQueue;

/**
 * Created by davide-maestroni on 08/10/2017.
 */
public class ChunkOutputStream extends OutputStream {

  private static final int DEFAULT_BUFFER_SIZE = 16 << 10;

  private static final int DEFAULT_POOL_SIZE = 16;

  private final AllocationType mAllocation;

  private final int mCorePoolSize;

  private final EvaluationCollection<Chunk> mEvaluation;

  private final int mMaxBufferSize;

  private final DoubleQueue<ByteBuffer> mPool;

  private ByteBuffer mBuffer;

  private boolean mIsClosed;

  public ChunkOutputStream(@NotNull final EvaluationCollection<Chunk> evaluation,
      @Nullable final AllocationType allocationType) {
    this(evaluation, allocationType, DEFAULT_BUFFER_SIZE, DEFAULT_POOL_SIZE);
  }

  public ChunkOutputStream(@NotNull final EvaluationCollection<Chunk> evaluation,
      @Nullable final AllocationType allocationType, final int coreSize) {
    mEvaluation = ConstantConditions.notNull("evaluation", evaluation);
    final int poolSize =
        (mCorePoolSize = ConstantConditions.positive("coreSize", coreSize) / DEFAULT_BUFFER_SIZE);
    mMaxBufferSize = DEFAULT_BUFFER_SIZE;
    mAllocation = (allocationType != null) ? allocationType : AllocationType.HEAP;
    mPool = new DoubleQueue<ByteBuffer>(Math.max(poolSize, 1));
  }

  public ChunkOutputStream(@NotNull final EvaluationCollection<Chunk> evaluation,
      @Nullable final AllocationType allocationType, final int bufferSize, final int poolSize) {
    mEvaluation = ConstantConditions.notNull("evaluation", evaluation);
    mCorePoolSize = ConstantConditions.positive("poolSize", poolSize);
    mMaxBufferSize = ConstantConditions.positive("bufferSize", bufferSize);
    mAllocation = (allocationType != null) ? allocationType : AllocationType.HEAP;
    mPool = new DoubleQueue<ByteBuffer>(poolSize);
  }

  private static boolean outOfBound(final int off, final int len, final int bytes) {
    return (off < 0) || (len < 0) || (len > bytes - off) || ((off + len) < 0);
  }

  /**
   * Transfers all the bytes from the specified input stream.
   *
   * @param in the input stream.
   * @return the total number of bytes written.
   * @throws IOException If the first byte cannot be read for any reason other than
   *                     end of file, or if the input stream has been closed, or if
   *                     some other I/O error occurs.
   */
  public long transfer(@NotNull final InputStream in) throws IOException {
    long count = 0;
    for (int b; (b = write(in)) > 0; ) {
      count += b;
    }

    return count;
  }

  public long transfer(@NotNull final ReadableByteChannel channel) throws IOException {
    if (mIsClosed) {
      throw new IOException("cannot write into a closed output stream");
    }

    int count;
    long written = 0;
    do {
      final ByteBuffer byteBuffer = getBuffer();
      count = channel.read(byteBuffer);
      written += Math.min(0, count);
      if (byteBuffer.remaining() == 0) {
        mBuffer = null;
        mEvaluation.addValue(new DefaultChunk(this, byteBuffer));
      }

    } while (count >= 0);

    return written;
  }

  public int write(@NotNull final ByteBuffer buffer) throws IOException {
    if (mIsClosed) {
      throw new IOException("cannot write into a closed output stream");
    }

    final int len = buffer.remaining();
    if (len == 0) {
      return 0;
    }

    int written = 0;
    do {
      final ByteBuffer byteBuffer = getBuffer();
      final int remaining = byteBuffer.remaining();
      final int count = Math.min(len - written, remaining);
      if (byteBuffer.hasArray()) {
        final int position = byteBuffer.position();
        buffer.get(byteBuffer.array(), position, count);
        byteBuffer.position(position + count);

      } else if (buffer.hasArray()) {
        final int position = buffer.position();
        byteBuffer.put(buffer.array(), position, count);
        buffer.position(position + count);

      } else {
        final byte[] bytes = new byte[count];
        buffer.get(bytes);
        byteBuffer.put(bytes);
      }

      written += count;
      if (byteBuffer.remaining() == 0) {
        mBuffer = null;
        mEvaluation.addValue(new DefaultChunk(this, byteBuffer));
      }

    } while (written < len);

    return written;
  }

  public int write(@NotNull final Chunk chunk) throws IOException {
    if (mIsClosed) {
      throw new IOException("cannot write into a closed output stream");
    }

    final ByteBuffer byteBuffer = getBuffer();
    final int remaining = byteBuffer.remaining();
    final int read;
    if (chunk.available() > remaining) {
      final ByteArrayOutputStream outputStream = new ByteArrayOutputStream(chunk.available());
      chunk.read(outputStream);
      read = outputStream.size();
      write(outputStream.toByteArray());

    } else {
      read = chunk.read(byteBuffer);
      if (byteBuffer.remaining() == 0) {
        mBuffer = null;
        mEvaluation.addValue(new DefaultChunk(this, byteBuffer));
      }
    }

    return read;
  }

  /**
   * Writes up to {@code limit} bytes into the output stream by reading them from the specified
   * input stream.
   *
   * @param in    the input stream.
   * @param limit the maximum number of bytes to write.
   * @return the total number of bytes written into the chunk, or {@code -1} if there is no more
   * data because the end of the stream has been reached.
   * @throws IllegalArgumentException if the limit is negative.
   * @throws IOException              If the first byte cannot be read for any reason
   *                                  other than end of file, or if the input stream has
   *                                  been closed, or if some other I/O error occurs.
   */
  public int write(@NotNull final InputStream in, final int limit) throws IOException {
    if (mIsClosed) {
      throw new IOException("cannot write into a closed output stream");
    }

    if (ConstantConditions.notNegative("limit", limit) == 0) {
      return 0;
    }

    final ByteBuffer byteBuffer = getBuffer();
    final int remaining = byteBuffer.remaining();
    final int position = byteBuffer.position();
    final int read;
    if (byteBuffer.hasArray()) {
      read = in.read(byteBuffer.array(), position, Math.min(remaining, limit));
      if (read >= 0) {
        byteBuffer.position(position + read);
      }

    } else {
      final byte[] bytes = new byte[Math.min(remaining, limit)];
      read = in.read(bytes);
      if (read > 0) {
        byteBuffer.put(bytes, 0, read);
      }
    }

    if (byteBuffer.remaining() == 0) {
      mBuffer = null;
      mEvaluation.addValue(new DefaultChunk(this, byteBuffer));
    }

    return read;
  }

  /**
   * Writes some bytes into the output stream by reading them from the specified input stream.
   *
   * @param in the input stream.
   * @return the total number of bytes written into the chunk, or {@code -1} if there is no more
   * data because the end of the stream has been reached.
   * @throws IOException If the first byte cannot be read for any reason other than end of
   *                     file, or if the input stream has been closed, or if some other
   *                     I/O error occurs.
   */
  public int write(@NotNull final InputStream in) throws IOException {
    return write(in, Integer.MAX_VALUE);
  }

  public int write(@NotNull final ReadableByteChannel channel) throws IOException {
    if (mIsClosed) {
      throw new IOException("cannot write into a closed output stream");
    }

    final ByteBuffer byteBuffer = getBuffer();
    final int read = channel.read(byteBuffer);
    if (byteBuffer.remaining() == 0) {
      mBuffer = null;
      mEvaluation.addValue(new DefaultChunk(this, byteBuffer));
    }

    return read;
  }

  public void write(final int b) throws IOException {
    if (mIsClosed) {
      throw new IOException("cannot write into a closed output stream");
    }

    final ByteBuffer byteBuffer;
    byteBuffer = getBuffer();
    byteBuffer.put((byte) b);
    if (byteBuffer.remaining() == 0) {
      mBuffer = null;
      mEvaluation.addValue(new DefaultChunk(this, byteBuffer));
    }
  }

  @Override
  public void write(@NotNull final byte[] b) throws IOException {
    if (mIsClosed) {
      throw new IOException("cannot write into a closed output stream");
    }

    final int len = b.length;
    if (len == 0) {
      return;
    }

    int written = 0;
    do {
      final ByteBuffer byteBuffer = getBuffer();
      final int remaining = byteBuffer.remaining();
      final int count = Math.min(len - written, remaining);
      byteBuffer.put(b, written, count);
      written += count;
      if (byteBuffer.remaining() == 0) {
        mBuffer = null;
        mEvaluation.addValue(new DefaultChunk(this, byteBuffer));
      }

    } while (written < len);
  }

  @Override
  public void write(@NotNull final byte[] b, final int off, final int len) throws IOException {
    if (mIsClosed) {
      throw new IOException("cannot write into a closed output stream");
    }

    if (outOfBound(off, len, b.length)) {
      throw new IndexOutOfBoundsException();

    } else if (len == 0) {
      return;
    }

    int written = 0;
    do {
      final ByteBuffer byteBuffer = getBuffer();
      final int remaining = byteBuffer.remaining();
      final int count = Math.min(len - written, remaining);
      byteBuffer.put(b, written, count);
      written += count;
      if (byteBuffer.remaining() == 0) {
        mBuffer = null;
        mEvaluation.addValue(new DefaultChunk(this, byteBuffer));
      }

    } while (written < len);
  }

  @Override
  public void flush() {
    final ByteBuffer byteBuffer = getBuffer();
    if (byteBuffer.remaining() == 0) {
      mBuffer = null;
      mEvaluation.addValue(new DefaultChunk(this, byteBuffer));
    }
  }

  @Override
  public void close() {
    if (mIsClosed) {
      return;
    }

    flush();
    mIsClosed = true;
  }

  @NotNull
  private ByteBuffer acquire() {
    ByteBuffer byteBuffer = null;
    synchronized (mPool) {
      final DoubleQueue<ByteBuffer> pool = mPool;
      if (!pool.isEmpty()) {
        byteBuffer = pool.removeFirst();
      }
    }

    if (byteBuffer != null) {
      return byteBuffer;
    }

    return mAllocation.allocate(mMaxBufferSize);
  }

  @NotNull
  private ByteBuffer getBuffer() {
    final ByteBuffer byteBuffer = mBuffer;
    if (byteBuffer != null) {
      return byteBuffer;
    }

    return (mBuffer = acquire());
  }

  private void release(@NotNull final ByteBuffer buffer) {
    buffer.clear();
    synchronized (mPool) {
      final DoubleQueue<ByteBuffer> pool = mPool;
      if (pool.size() < mCorePoolSize) {
        pool.add(buffer);
      }
    }
  }

  private static class DefaultChunk implements Chunk, Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final Object mMutex = new Object();

    private final ChunkOutputStream mOutputStream;

    private ByteBuffer mByteBuffer;

    private DefaultChunk(@NotNull final ChunkOutputStream outputStream,
        @NotNull final ByteBuffer buffer) {
      mOutputStream = outputStream;
      mByteBuffer = buffer;
      buffer.flip();
    }

    public int available() {
      synchronized (mMutex) {
        final ByteBuffer byteBuffer = mByteBuffer;
        return (byteBuffer != null) ? byteBuffer.remaining() : 0;
      }
    }

    public void close() {
      final ByteBuffer byteBuffer;
      synchronized (mMutex) {
        byteBuffer = mByteBuffer;
        mByteBuffer = null;
      }

      if (byteBuffer != null) {
        mOutputStream.release(byteBuffer);
      }
    }

    @NotNull
    private Object writeReplace() throws ObjectStreamException {
      final ByteBuffer byteBuffer;
      synchronized (mMutex) {
        byteBuffer = mByteBuffer;
        if (byteBuffer == null) {
          return new SerializableChunk();
        }

        mByteBuffer = null;
      }

      try {
        if (byteBuffer.hasArray()) {
          return new SerializableChunk(byteBuffer.array());
        }

        final byte[] bytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(bytes);
        return new SerializableChunk(bytes);

      } finally {
        mOutputStream.release(byteBuffer);
      }
    }

    public byte peek(final int index) {
      synchronized (mMutex) {
        final ByteBuffer byteBuffer = mByteBuffer;
        if (byteBuffer == null) {
          throw new IndexOutOfBoundsException();
        }

        final int remaining = byteBuffer.remaining();
        if ((index < 0) || (index >= remaining)) {
          throw new IndexOutOfBoundsException();
        }

        return byteBuffer.get(index);
      }
    }

    public void peek(int index, @NotNull final byte[] b, int off, final int len) {
      synchronized (mMutex) {
        final ByteBuffer byteBuffer = mByteBuffer;
        if (byteBuffer == null) {
          throw new IndexOutOfBoundsException();
        }

        final int remaining = byteBuffer.remaining();
        if ((index < 0) || ((index + len) >= remaining)) {
          throw new IndexOutOfBoundsException();
        }

        if (byteBuffer.hasArray()) {
          System.arraycopy(byteBuffer.array(), index, b, off, len);

        } else {
          for (int i = 0; i < len; ++i) {
            b[off + i] = byteBuffer.get(index + i);
          }
        }
      }
    }

    public void peek(int index, @NotNull final byte[] b) {
      peek(index, b, 0, b.length);
    }

    public int read(@NotNull final OutputStream out) throws IOException {
      final ByteBuffer byteBuffer;
      synchronized (mMutex) {
        byteBuffer = mByteBuffer;
        if (byteBuffer == null) {
          throw new IOException("buffer already consumed");
        }

        mByteBuffer = null;
      }

      final int remaining = byteBuffer.remaining();
      try {
        if (byteBuffer.hasArray()) {
          out.write(byteBuffer.array());

        } else {
          final byte[] bytes = new byte[remaining];
          byteBuffer.get(bytes);
          out.write(bytes);
        }

      } finally {
        mOutputStream.release(byteBuffer);
      }

      return remaining;
    }

    public int read(@NotNull final ByteBuffer buffer) throws IOException {
      final ByteBuffer byteBuffer;
      synchronized (mMutex) {
        byteBuffer = mByteBuffer;
        if (byteBuffer == null) {
          throw new IOException("buffer already consumed");
        }

        mByteBuffer = null;
      }

      final int remaining = byteBuffer.remaining();
      try {
        buffer.put(byteBuffer);

      } finally {
        mOutputStream.release(byteBuffer);
      }

      return remaining;
    }

    public int read(@NotNull final WritableByteChannel channel) throws IOException {
      final ByteBuffer byteBuffer;
      synchronized (mMutex) {
        byteBuffer = mByteBuffer;
        if (byteBuffer == null) {
          throw new IOException("buffer already consumed");
        }

        mByteBuffer = null;
      }

      final int remaining = byteBuffer.remaining();
      try {
        while (byteBuffer.remaining() > 0) {
          channel.write(byteBuffer);
        }

      } finally {
        mOutputStream.release(byteBuffer);
      }

      return remaining;
    }
  }

  private static class SerializableChunk implements Chunk, Serializable {

    private static final byte[] EMPTY_BUFFER = new byte[0];

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final Object mMutex = new Object();

    private byte[] mBuffer;

    private SerializableChunk() {
      this(EMPTY_BUFFER);
    }

    private SerializableChunk(@NotNull final byte[] buffer) {
      mBuffer = buffer;
    }

    private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
      final byte[] buffer = new byte[in.readInt()];
      int read = 0;
      int offset = 0;
      while ((read >= 0) && (offset < buffer.length)) {
        read = in.read(buffer, offset, buffer.length - offset);
        offset += read;
      }

      mBuffer = buffer;
    }

    private void writeObject(final ObjectOutputStream out) throws IOException {
      final byte[] buffer = mBuffer;
      out.writeInt(buffer.length);
      if (buffer.length > 0) {
        out.write(buffer);
      }
    }

    public int available() {
      synchronized (mMutex) {
        return mBuffer.length;
      }
    }

    public void close() {
      synchronized (mMutex) {
        mBuffer = EMPTY_BUFFER;
      }
    }

    public byte peek(final int index) {
      synchronized (mMutex) {
        return mBuffer[index];
      }
    }

    public void peek(final int index, @NotNull final byte[] b, final int off, final int len) {
      synchronized (mMutex) {
        System.arraycopy(mBuffer, index, b, off, len);
      }
    }

    public void peek(final int index, @NotNull final byte[] b) {
      peek(index, b, 0, b.length);
    }

    public int read(@NotNull final OutputStream out) throws IOException {
      final byte[] bytes;
      synchronized (mMutex) {
        bytes = mBuffer;
        mBuffer = EMPTY_BUFFER;
      }

      out.write(bytes);
      return bytes.length;
    }

    public int read(@NotNull final ByteBuffer buffer) throws IOException {
      final byte[] bytes;
      synchronized (mMutex) {
        bytes = mBuffer;
        mBuffer = EMPTY_BUFFER;
      }

      buffer.put(bytes);
      return bytes.length;
    }

    public int read(@NotNull final WritableByteChannel channel) throws IOException {
      final byte[] bytes;
      synchronized (mMutex) {
        bytes = mBuffer;
        mBuffer = EMPTY_BUFFER;
      }

      final ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
      while (byteBuffer.remaining() > 0) {
        channel.write(byteBuffer);
      }

      return bytes.length;
    }
  }
}

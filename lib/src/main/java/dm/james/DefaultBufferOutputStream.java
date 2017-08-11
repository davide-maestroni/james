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

package dm.james;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import dm.james.io.Buffer;
import dm.james.io.BufferOutputStream;
import dm.james.promise.DeferredPromiseIterable;
import dm.james.promise.PromiseIterable;
import dm.james.util.ConstantConditions;
import dm.james.util.DoubleQueue;
import dm.james.util.SerializableProxy;

/**
 * Created by davide-maestroni on 08/10/2017.
 */
class DefaultBufferOutputStream extends BufferOutputStream implements Serializable {

  private static final int DEFAULT_BUFFER_SIZE = 16 << 10;

  private static final int DEFAULT_POOL_SIZE = 16;

  private final int mCorePoolSize;

  private final DeferredPromiseIterable<Buffer, Buffer> mIterable;

  private final int mMaxBufferSize;

  private final Object mMutex = new Object();

  private final DoubleQueue<ByteBuffer> mPool;

  private ByteBuffer mBuffer;

  private boolean mIsClosed;

  DefaultBufferOutputStream(@NotNull final DeferredPromiseIterable<Buffer, Buffer> iterable) {
    this(iterable, DEFAULT_BUFFER_SIZE, DEFAULT_POOL_SIZE);
  }

  DefaultBufferOutputStream(@NotNull final DeferredPromiseIterable<Buffer, Buffer> iterable,
      final int coreSize) {
    mIterable = ConstantConditions.notNull(iterable);
    final int poolSize = (mCorePoolSize =
        ConstantConditions.notNegative("coreSize", coreSize) / DEFAULT_BUFFER_SIZE);
    mMaxBufferSize = DEFAULT_BUFFER_SIZE;
    mPool = new DoubleQueue<ByteBuffer>(Math.max(poolSize, 1));
  }

  DefaultBufferOutputStream(@NotNull final DeferredPromiseIterable<Buffer, Buffer> iterable,
      final int bufferSize, final int poolSize) {
    mIterable = ConstantConditions.notNull(iterable);
    mCorePoolSize = ConstantConditions.notNegative("poolSize", poolSize);
    mMaxBufferSize = ConstantConditions.notNegative("bufferSize", bufferSize);
    mPool = new DoubleQueue<ByteBuffer>(Math.max(poolSize, 1));
  }

  private static boolean outOfBound(final int off, final int len, final int bytes) {
    return (off < 0) || (len < 0) || (len > bytes - off) || ((off + len) < 0);
  }

  @Override
  public void flush() {
    final ByteBuffer byteBuffer;
    synchronized (mMutex) {
      byteBuffer = getBuffer();
      if (byteBuffer.position() == 0) {
        return;
      }

      mBuffer = null;
    }

    mIterable.add(new DefaultBuffer(byteBuffer));
  }

  @Override
  public void close() {
    synchronized (mMutex) {
      if (mIsClosed) {
        return;
      }

      mIsClosed = true;
    }

    flush();
    mIterable.resolve();
  }

  @NotNull
  public PromiseIterable<Buffer> iterable() {
    return mIterable;
  }

  public long transfer(@NotNull final ReadableByteChannel channel) throws IOException {
    int count;
    long written = 0;
    do {
      final boolean isAdd;
      final ByteBuffer byteBuffer;
      synchronized (mMutex) {
        if (mIsClosed) {
          throw new IOException("cannot write into a closed output stream");
        }

        byteBuffer = getBuffer();
        count = channel.read(byteBuffer);
        written += Math.min(0, count);
        if (byteBuffer.remaining() == 0) {
          isAdd = true;
          mBuffer = null;

        } else {
          isAdd = false;
        }
      }

      if (isAdd) {
        mIterable.add(new DefaultBuffer(byteBuffer));
      }

    } while (count >= 0);

    return written;
  }

  public int write(@NotNull final ByteBuffer buffer) throws IOException {
    final int len = buffer.remaining();
    if (len == 0) {
      return 0;
    }

    int written = 0;
    do {
      final boolean isAdd;
      final ByteBuffer byteBuffer;
      synchronized (mMutex) {
        if (mIsClosed) {
          throw new IOException("cannot write into a closed output stream");
        }

        byteBuffer = getBuffer();
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
          isAdd = true;
          mBuffer = null;

        } else {
          isAdd = false;
        }
      }

      if (isAdd) {
        mIterable.add(new DefaultBuffer(byteBuffer));
      }

    } while (written < len);

    return written;
  }

  public int write(@NotNull final Buffer buffer) throws IOException {
    return 0;
  }

  public int write(@NotNull final InputStream in, final int limit) throws IOException {
    if (ConstantConditions.notNegative("limit", limit) == 0) {
      return 0;
    }

    final int read;
    final boolean isAdd;
    final ByteBuffer byteBuffer;
    synchronized (mMutex) {
      if (mIsClosed) {
        throw new IOException("cannot write into a closed output stream");
      }

      byteBuffer = getBuffer();
      final int remaining = byteBuffer.remaining();
      final int position = byteBuffer.position();
      if (byteBuffer.hasArray()) {
        read = in.read(byteBuffer.array(), position, Math.min(remaining, limit));
        if (read > 0) {
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
        isAdd = true;
        mBuffer = null;

      } else {
        isAdd = false;
      }
    }

    if (isAdd) {
      mIterable.add(new DefaultBuffer(byteBuffer));
    }

    return read;
  }

  public int write(@NotNull final InputStream in) throws IOException {
    return write(in, Integer.MAX_VALUE);
  }

  public void write(final int b) throws IOException {
    final boolean isAdd;
    final ByteBuffer byteBuffer;
    synchronized (mMutex) {
      if (mIsClosed) {
        throw new IOException("cannot write into a closed output stream");
      }

      byteBuffer = getBuffer();
      byteBuffer.put((byte) b);
      if (byteBuffer.remaining() == 0) {
        isAdd = true;
        mBuffer = null;

      } else {
        isAdd = false;
      }
    }

    if (isAdd) {
      mIterable.add(new DefaultBuffer(byteBuffer));
    }
  }

  @Override
  public void write(@NotNull final byte[] b) throws IOException {
    final int len = b.length;
    if (len == 0) {
      return;
    }

    int written = 0;
    do {
      final boolean isAdd;
      final ByteBuffer byteBuffer;
      synchronized (mMutex) {
        if (mIsClosed) {
          throw new IOException("cannot write into a closed output stream");
        }

        byteBuffer = getBuffer();
        final int remaining = byteBuffer.remaining();
        final int count = Math.min(len - written, remaining);
        byteBuffer.put(b, written, count);
        written += count;
        if (byteBuffer.remaining() == 0) {
          isAdd = true;
          mBuffer = null;

        } else {
          isAdd = false;
        }
      }

      if (isAdd) {
        mIterable.add(new DefaultBuffer(byteBuffer));
      }

    } while (written < len);
  }

  @Override
  public void write(@NotNull final byte[] b, final int off, final int len) throws IOException {
    if (outOfBound(off, len, b.length)) {
      throw new IndexOutOfBoundsException();

    } else if (len == 0) {
      return;
    }

    int written = 0;
    do {
      final boolean isAdd;
      final ByteBuffer byteBuffer;
      synchronized (mMutex) {
        if (mIsClosed) {
          throw new IOException("cannot write into a closed output stream");
        }

        byteBuffer = getBuffer();
        final int remaining = byteBuffer.remaining();
        final int count = Math.min(len - written, remaining);
        byteBuffer.put(b, written, count);
        written += count;
        if (byteBuffer.remaining() == 0) {
          isAdd = true;
          mBuffer = null;

        } else {
          isAdd = false;
        }
      }

      if (isAdd) {
        mIterable.add(new DefaultBuffer(byteBuffer));
      }

    } while (written < len);
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

    // TODO: 10/08/2017 direct??
    return ByteBuffer.allocate(mMaxBufferSize);
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

  private Object writeReplace() throws ObjectStreamException {
    return new StreamProxy(mIterable, mMaxBufferSize, mCorePoolSize);
  }

  private static class SerializableBuffer implements Buffer, Serializable {

    private static final byte[] EMPTY_BUFFER = new byte[0];

    private final Object mMutex = new Object();

    private byte[] mBuffer;

    private SerializableBuffer() {
      this(EMPTY_BUFFER);
    }

    private SerializableBuffer(@NotNull final byte[] buffer) {
      mBuffer = buffer;
    }

    public int available() {
      synchronized (mMutex) {
        return mBuffer.length;
      }
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

  private static class StreamProxy extends SerializableProxy {

    private StreamProxy(final DeferredPromiseIterable<Buffer, Buffer> iterable,
        final int bufferSize, final int poolSize) {
      super(iterable, bufferSize, poolSize);
    }

    @SuppressWarnings("unchecked")
    Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new DefaultBufferOutputStream((DeferredPromiseIterable<Buffer, Buffer>) args[0],
            (Integer) args[1], (Integer) args[2]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }

  private class DefaultBuffer implements Buffer, Serializable {

    private final Object mMutex = new Object();

    private ByteBuffer mByteBuffer;

    private DefaultBuffer(@NotNull final ByteBuffer buffer) {
      mByteBuffer = buffer;
      buffer.flip();
    }

    private Object writeReplace() throws ObjectStreamException {
      final ByteBuffer byteBuffer;
      synchronized (mMutex) {
        byteBuffer = mByteBuffer;
        if (byteBuffer == null) {
          return new SerializableBuffer();
        }

        mByteBuffer = null;
      }

      try {
        if (byteBuffer.hasArray()) {
          return new SerializableBuffer(byteBuffer.array());
        }

        final byte[] bytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(bytes);
        return new SerializableBuffer(bytes);

      } finally {
        release(byteBuffer);
      }
    }

    public int available() {
      synchronized (mMutex) {
        final ByteBuffer byteBuffer = mByteBuffer;
        return (byteBuffer != null) ? byteBuffer.remaining() : 0;
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
        release(byteBuffer);
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
        release(byteBuffer);
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
        release(byteBuffer);
      }

      return remaining;
    }
  }
}

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

package dm.jail;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;

import dm.jail.async.AsyncResults;
import dm.jail.async.Mapper;
import dm.jail.config.BuildConfig;
import dm.jail.log.LogLevel;
import dm.jail.log.LogPrinter;
import dm.jail.log.Logger;
import dm.jail.util.ConstantConditions;
import dm.jail.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/01/2018.
 */
class TryStatementLoopHandler<V, R> extends AsyncStatementLoopHandler<V, R>
    implements Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Mapper<? super V, ? extends Closeable> mCloseable;

  private final AsyncStatementLoopHandler<V, R> mHandler;

  private final Logger mLogger;

  TryStatementLoopHandler(@NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final AsyncStatementLoopHandler<V, R> handler, @Nullable final LogPrinter printer,
      @Nullable final LogLevel level) {
    mCloseable = ConstantConditions.notNull("closeable", closeable);
    mHandler = ConstantConditions.notNull("handler", handler);
    mLogger = Logger.newLogger(printer, level, this);
  }

  @Override
  void value(final V value, @NotNull final AsyncResults<R> results) throws Exception {
    final Closeable closeable = mCloseable.apply(value);
    try {
      mHandler.value(value, results);

    } finally {
      Asyncs.close(closeable, mLogger);
    }
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    final Logger logger = mLogger;
    return new HandlerProxy<V, R>(mCloseable, mHandler, logger.getLogPrinter(),
        logger.getLogLevel());
  }

  private static class HandlerProxy<V, R> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private HandlerProxy(final Mapper<? super V, ? extends Closeable> closeable,
        final AsyncStatementLoopHandler<V, R> handler, final LogPrinter printer,
        final LogLevel level) {
      super(proxy(closeable), handler, printer, level);
    }

    @NotNull
    @SuppressWarnings("unchecked")
    Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new TryStatementLoopHandler<V, R>((Mapper<? super V, ? extends Closeable>) args[0],
            (AsyncStatementLoopHandler<V, R>) args[1], (LogPrinter) args[2], (LogLevel) args[3]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

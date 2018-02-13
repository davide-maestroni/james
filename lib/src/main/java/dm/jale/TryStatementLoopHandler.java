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

package dm.jale;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.Locale;

import dm.jale.async.AsyncEvaluations;
import dm.jale.async.Mapper;
import dm.jale.config.BuildConfig;
import dm.jale.log.Logger;
import dm.jale.util.ConstantConditions;
import dm.jale.util.SerializableProxy;

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
      @NotNull final AsyncStatementLoopHandler<V, R> handler, @Nullable final String loggerName) {
    mCloseable = ConstantConditions.notNull("closeable", closeable);
    mHandler = ConstantConditions.notNull("handler", handler);
    mLogger = Logger.newLogger(this, loggerName, Locale.ENGLISH);
  }

  @Override
  void value(final V value, @NotNull final AsyncEvaluations<R> evaluations) throws Exception {
    final Closeable closeable = mCloseable.apply(value);
    try {
      mHandler.value(value, evaluations);

    } finally {
      Asyncs.close(closeable, mLogger);
    }
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new HandlerProxy<V, R>(mCloseable, mHandler, mLogger.getName());
  }

  private static class HandlerProxy<V, R> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private HandlerProxy(final Mapper<? super V, ? extends Closeable> closeable,
        final AsyncStatementLoopHandler<V, R> handler, final String loggerName) {
      super(proxy(closeable), handler, loggerName);
    }

    @NotNull
    @SuppressWarnings("unchecked")
    Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new TryStatementLoopHandler<V, R>((Mapper<? super V, ? extends Closeable>) args[0],
            (AsyncStatementLoopHandler<V, R>) args[1], (String) args[2]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

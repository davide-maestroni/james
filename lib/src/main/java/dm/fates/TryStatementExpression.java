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

package dm.fates;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.Locale;

import dm.fates.config.BuildConfig;
import dm.fates.eventual.Evaluation;
import dm.fates.eventual.Mapper;
import dm.fates.log.Logger;
import dm.fates.util.ConstantConditions;
import dm.fates.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/01/2018.
 */
class TryStatementExpression<V, R> extends StatementExpression<V, R> implements Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Mapper<? super V, ? extends Closeable> mCloseable;

  private final StatementExpression<V, R> mHandler;

  private final Logger mLogger;

  TryStatementExpression(@NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final StatementExpression<V, R> expression, @Nullable final String loggerName) {
    mCloseable = ConstantConditions.notNull("closeable", closeable);
    mHandler = ConstantConditions.notNull("expression", expression);
    mLogger = Logger.newLogger(this, loggerName, Locale.ENGLISH);
  }

  @Override
  void value(final V value, @NotNull final Evaluation<R> evaluation) throws Exception {
    final Closeable closeable = mCloseable.apply(value);
    try {
      mHandler.value(value, evaluation);

    } finally {
      Eventuals.close(closeable, mLogger);
    }
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new HandlerProxy<V, R>(mCloseable, mHandler, mLogger.getName());
  }

  private static class HandlerProxy<V, R> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private HandlerProxy(final Mapper<? super V, ? extends Closeable> closeable,
        final StatementExpression<V, R> expression, final String loggerName) {
      super(proxy(closeable), expression, loggerName);
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new TryStatementExpression<V, R>((Mapper<? super V, ? extends Closeable>) args[0],
            (StatementExpression<V, R>) args[1], (String) args[2]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

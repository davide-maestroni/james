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

import dm.jale.config.BuildConfig;
import dm.jale.eventual.Action;
import dm.jale.eventual.Evaluation;
import dm.jale.eventual.Mapper;
import dm.jale.eventual.Statement;
import dm.jale.log.Logger;
import dm.jale.util.ConstantConditions;
import dm.jale.util.SerializableProxy;

/**
 * Created by davide-maestroni on 02/01/2018.
 */
class TryEvalStatementExpression<V, R> extends StatementExpression<V, R> implements Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Mapper<? super V, ? extends Closeable> mCloseable;

  private final Logger mLogger;

  private final Mapper<? super V, ? extends Statement<R>> mMapper;

  TryEvalStatementExpression(@NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final Mapper<? super V, ? extends Statement<R>> mapper,
      @Nullable final String loggerName) {
    mCloseable = ConstantConditions.notNull("closeable", closeable);
    mMapper = ConstantConditions.notNull("mapper", mapper);
    mLogger = Logger.newLogger(this, loggerName, Locale.ENGLISH);
  }

  @Override
  void value(final V value, @NotNull final Evaluation<R> evaluation) throws Exception {
    mMapper.apply(value).whenDone(new Action() {

      public void perform() throws Exception {
        Eventuals.close(mCloseable.apply(value), mLogger);
      }
    }).evaluated().to(evaluation);
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new HandlerProxy<V, R>(mCloseable, mMapper, mLogger.getName());
  }

  private static class HandlerProxy<V, R> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private HandlerProxy(final Mapper<? super V, ? extends Closeable> closeable,
        final Mapper<? super V, ? extends Statement<R>> mapper, final String loggerName) {
      super(proxy(closeable), proxy(mapper), loggerName);
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new TryEvalStatementExpression<V, R>(
            (Mapper<? super V, ? extends Closeable>) args[0],
            (Mapper<? super V, ? extends Statement<R>>) args[1], (String) args[2]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

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
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import dm.james.executor.InterruptedExecutionException;
import dm.james.log.Log;
import dm.james.log.Log.Level;
import dm.james.log.Logger;
import dm.james.promise.Mapper;
import dm.james.promise.Observer;
import dm.james.promise.PromiseIterable;
import dm.james.promise.PromiseIterable.CallbackIterable;
import dm.james.promise.PromiseIterable.StatefulHandler;
import dm.james.promise.Provider;
import dm.james.promise.RejectionException;
import dm.james.util.ConstantConditions;
import dm.james.util.SerializableProxy;

/**
 * Created by davide-maestroni on 08/05/2017.
 */
class UsingIterableObserver<I extends Closeable, O>
    implements Observer<CallbackIterable<O>>, Serializable {

  private final Logger mLogger;

  private final Mapper<List<I>, PromiseIterable<O>> mMapper;

  private final Iterable<Provider<I>> mProviders;

  UsingIterableObserver(@NotNull final Iterable<Provider<I>> providers,
      @NotNull final Mapper<List<I>, PromiseIterable<O>> mapper, @Nullable final Log log,
      @Nullable final Level level) {
    mProviders = ConstantConditions.notNull("providers", providers);
    mMapper = ConstantConditions.notNull("mapper", mapper);
    mLogger = Logger.newLogger(log, level, this);
  }

  private static void safeClose(@Nullable final Closeable closeable, @NotNull final Logger logger) {
    if (closeable == null) {
      return;
    }

    try {
      closeable.close();

    } catch (final IOException e) {
      logger.wrn(e, "suppressed exception");
    }
  }

  public void accept(final CallbackIterable<O> callback) throws Exception {
    final Logger logger = mLogger;
    final ArrayList<I> closeables = new ArrayList<I>();
    try {
      for (final Provider<I> provider : mProviders) {
        closeables.add(provider.get());
      }

    } catch (final Throwable t) {
      InterruptedExecutionException.throwIfInterrupt(t);
      for (final I closeable : closeables) {
        safeClose(closeable, logger);
      }

      throw RejectionException.wrapIfNot(RuntimeException.class, t);
    }

    callback.addAllDeferred(mMapper.apply(new ArrayList<I>(closeables))
                                   .then(new CloseableHandler<O>(closeables, logger.getLog(),
                                       logger.getLogLevel())));
    callback.resolve();
  }

  private Object writeReplace() throws ObjectStreamException {
    final Logger logger = mLogger;
    return new ObserverProxy<I, O>(mProviders, mMapper, logger.getLog(), logger.getLogLevel());
  }

  private static class CloseableHandler<O> implements StatefulHandler<O, O, Void>, Serializable {

    private final List<? extends Closeable> mCloseables;

    private final Logger mLogger;

    CloseableHandler(@NotNull final List<? extends Closeable> closeables, @Nullable final Log log,
        @Nullable final Level level) {
      mCloseables = closeables;
      mLogger = Logger.newLogger(log, level, this);
    }

    public Void create(@NotNull final CallbackIterable<O> callback) {
      return null;
    }

    public Void fulfill(final Void state, final O input,
        @NotNull final CallbackIterable<O> callback) {
      callback.add(input);
      return null;
    }

    public Void reject(final Void state, final Throwable reason,
        @NotNull final CallbackIterable<O> callback) {
      // TODO: 05/08/2017 fix it
      callback.reject(reason);
      return null;
    }

    public void resolve(final Void state, @NotNull final CallbackIterable<O> callback) {
      @SuppressWarnings("UnnecessaryLocalVariable") final Logger logger = mLogger;
      for (final Closeable closeable : mCloseables) {
        safeClose(closeable, logger);
      }

      callback.resolve();
    }

    private Object writeReplace() throws ObjectStreamException {
      final Logger logger = mLogger;
      return new HandlerProxy<O>(mCloseables, logger.getLog(), logger.getLogLevel());
    }

    private static class HandlerProxy<O> extends SerializableProxy {

      private HandlerProxy(final List<? extends Closeable> closeable, final Log log,
          final Level logLevel) {
        super(closeable, log, logLevel);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new UsingIterableObserver.CloseableHandler<O>((List<? extends Closeable>) args[0],
              (Log) args[1], (Level) args[2]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }
  }

  private static class ObserverProxy<I extends Closeable, O> extends SerializableProxy {

    private ObserverProxy(final Iterable<Provider<I>> providers,
        final Mapper<List<I>, PromiseIterable<O>> mapper, final Log log, final Level logLevel) {
      super(providers, mapper, log, logLevel);
    }

    @SuppressWarnings("unchecked")
    Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new UsingIterableObserver<I, O>((Iterable<Provider<I>>) args[0],
            (Mapper<List<I>, PromiseIterable<O>>) args[1], (Log) args[2], (Level) args[3]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}

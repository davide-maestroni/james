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

import dm.james.log.Log;
import dm.james.log.Log.Level;
import dm.james.promise.DeferredPromise;
import dm.james.promise.Mapper;
import dm.james.promise.Observer;
import dm.james.promise.Promise;
import dm.james.promise.Promise.Callback;
import dm.james.promise.Promise.Handler;
import dm.james.promise.Promise.Processor;
import dm.james.promise.PromiseIterable;
import dm.james.promise.Provider;
import dm.james.util.ConstantConditions;
import dm.james.util.ReflectionUtils;
import dm.james.util.SerializableProxy;

/**
 * Created by davide-maestroni on 06/30/2017.
 */
public class Bond implements Serializable {

  private final Log mLog;

  private final Level mLogLevel;

  private final PropagationType mPropagationType;

  public Bond() {
    this(null, null, null);
  }

  private Bond(@Nullable final PropagationType propagationType, @Nullable final Log log,
      @Nullable final Level level) {
    mPropagationType = propagationType;
    mLog = log;
    mLogLevel = level;
  }

  @NotNull
  public <O> Promise<O> aPlus(@NotNull final Promise<O> promise) {
    if (promise instanceof APlusPromise) {
      return promise;
    }

    return new APlusPromise<O>(this, promise);
  }

  @NotNull
  public <O> PromiseIterable<O> all(final Iterable<Promise<O>> promises) {
    return null;
  }

  @NotNull
  public <O> PromiseIterable<O> any(final Iterable<Promise<O>> promises) {
    return null;
  }

  @NotNull
  public <O> Mapper<Promise<O>, Promise<O>> cache() {
    return new CacheMapper<O>(this.<O>deferred());
  }

  @NotNull
  public <I> DeferredPromise<I, I> deferred() {
    return new DefaultDeferredPromise<I, I>(mPropagationType, mLog, mLogLevel);
  }

  @NotNull
  public <O> PromiseIterable<O> each(final Iterable<Promise<O>> promises) {
    return null;
  }

  @NotNull
  public <O> Promise<O> promise(@NotNull final Observer<Callback<O>> observer) {
    return new DefaultPromise<O>(observer, mPropagationType, mLog, mLogLevel);
  }

  @NotNull
  public <O> Promise<O> rejected(final Throwable reason) {
    return promise(new RejectedObserver<O>(reason));
  }

  @NotNull
  public <O> Promise<O> resolved(final O output) {
    return promise(new ResolvedObserver<O>(output));
  }

  @NotNull
  public <O> PromiseIterable<O> resolvedIterable(final Iterable<O> outputs) {
    return null;
  }

  @NotNull
  public <I extends Closeable, O> Promise<O> tryUsing(@NotNull final Provider<I> provider,
      @NotNull final Handler<I, O, Callback<O>> handler) {
    return promise(new CloseableObserver<I, O>(provider, handler));
  }

  @NotNull
  public Bond withLog(@Nullable final Log log) {
    return new Bond(mPropagationType, log, mLogLevel);
  }

  @NotNull
  public Bond withLogLevel(@Nullable final Level level) {
    return new Bond(mPropagationType, mLog, level);
  }

  @NotNull
  public Bond withPropagation(@Nullable final PropagationType propagationType) {
    return new Bond(propagationType, mLog, mLogLevel);
  }

  private Object writeReplace() throws ObjectStreamException {
    final BondProxy proxy = new BondProxy();
    proxy.setPropagationType(mPropagationType);
    proxy.setLogLevel(mLogLevel);
    final Log log = mLog;
    if (log instanceof Serializable) {
      proxy.setLog(log);

    } else if (log != null) {
      proxy.setLogClass(log.getClass());
    }

    return proxy;
  }

  @SuppressWarnings("unused")
  private static class BondProxy implements Serializable {

    private Log mLog;

    private Class<? extends Log> mLogClass;

    private Level mLogLevel;

    private PropagationType mPropagationType;

    Log getLog() {
      return mLog;
    }

    void setLog(final Log log) {
      mLog = log;
    }

    Class<? extends Log> getLogClass() {
      return mLogClass;
    }

    void setLogClass(final Class<? extends Log> logClass) {
      mLogClass = logClass;
    }

    Level getLogLevel() {
      return mLogLevel;
    }

    void setLogLevel(final Level logLevel) {
      mLogLevel = logLevel;
    }

    PropagationType getPropagationType() {
      return mPropagationType;
    }

    void setPropagationType(final PropagationType propagationType) {
      mPropagationType = propagationType;
    }

    @SuppressWarnings("unchecked")
    Object readResolve() throws ObjectStreamException {
      try {
        Log log = mLog;
        final Class<? extends Log> logClass = mLogClass;
        if ((log == null) && (logClass != null)) {
          log = ReflectionUtils.getDefaultConstructor(logClass).newInstance();
        }

        return new Bond(mPropagationType, log, mLogLevel);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }

  private static class CacheMapper<O> implements Mapper<Promise<O>, Promise<O>>, Serializable {

    private final DeferredPromise<O, O> mDeferred;

    private CacheMapper(@NotNull final DeferredPromise<O, O> deferred) {
      mDeferred = deferred;
    }

    public Promise<O> apply(final Promise<O> promise) {
      return CachedPromise.create(promise, mDeferred);
    }
  }

  private static class CloseableCallback<O> implements Callback<O>, Processor<O, Object> {

    private final Callback<O> mCallback;

    private final Closeable mCloseable;

    private CloseableCallback(@NotNull final Closeable closeable,
        @NotNull final Callback<O> callback) {
      mCloseable = ConstantConditions.notNull("closeable", closeable);
      mCallback = callback;
    }

    public void defer(@NotNull final Promise<O> promise) {
      promise.then(this);
    }

    public void reject(final Throwable reason) {
      try {
        mCallback.reject(reason);

      } finally {
        try {
          mCloseable.close();

        } catch (final IOException ignored) {
          // suppressed
        }
      }
    }

    public void resolve(final O output) {
      try {
        mCallback.resolve(output);

      } finally {
        try {
          mCloseable.close();

        } catch (final IOException e) {
          mCallback.reject(e);
        }
      }
    }

    public void reject(final Throwable reason, @NotNull final Callback<Object> callback) {
      reject(reason);
    }

    public void resolve(final O input, @NotNull final Callback<Object> callback) {
      resolve(input);
    }
  }

  private static class CloseableObserver<I extends Closeable, O>
      implements Observer<Callback<O>>, Serializable {

    private final Handler<I, O, Callback<O>> mHandler;

    private final Provider<I> mProvider;

    private CloseableObserver(@NotNull final Provider<I> provider,
        @NotNull final Handler<I, O, Callback<O>> handler) {
      mProvider = ConstantConditions.notNull("provider", provider);
      mHandler = ConstantConditions.notNull("handler", handler);
    }

    public void accept(final Callback<O> callback) throws Exception {
      final I closeable = mProvider.get();
      mHandler.accept(closeable, new CloseableCallback<O>(closeable, callback));
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ObserverProxy<I, O>(mProvider, mHandler);
    }

    private static class ObserverProxy<I extends Closeable, O> extends SerializableProxy {

      private ObserverProxy(final Provider<I> provider, final Handler<I, O, Callback<O>> handler) {
        super(provider, handler);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new CloseableObserver<I, O>((Provider<I>) args[0],
              (Handler<I, O, Callback<O>>) args[1]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }
  }
}

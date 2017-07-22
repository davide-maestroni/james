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

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;

import dm.james.log.Log;
import dm.james.log.Log.Level;
import dm.james.promise.Mapper;
import dm.james.promise.Observer;
import dm.james.promise.Promise;
import dm.james.promise.Promise.Callback;
import dm.james.promise.Promise.StatelessProcessor;
import dm.james.promise.ResolvablePromise;
import dm.james.util.ReflectionUtils;

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
  public <O> Mapper<Promise<O>, Promise<O>> cache() {
    return new CacheMapper<O>(this.<O>resolvable());
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
  public <I> ResolvablePromise<I, I> resolvable() {
    return new DefaultResolvablePromise<I, I>(mPropagationType, mLog, mLogLevel);
  }

  @NotNull
  public <O> Promise<O> resolved(final O output) {
    return promise(new ResolvedObserver<O>(output));
  }

  @NotNull
  public <O> Promise<O> resolved() {
    return promise(new EmptyObserver<O>());
  }

  @NotNull
  public <O> Promise<O> trust(@NotNull final Promise<O> promise) {
    return promise(new Observer<Callback<O>>() {

      public void accept(final Callback<O> callback) {
        callback.defer(promise);
      }
    });
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

    private final ResolvablePromise<O, O> mResolvable;

    private CacheMapper(@NotNull final ResolvablePromise<O, O> resolvable) {
      mResolvable = resolvable;
    }

    public Promise<O> apply(final Promise<O> promise) {
      final ResolvablePromise<O, O> resolvable = mResolvable;
      promise.then(new CacheProcessor<O>(resolvable));
      return resolvable;
    }
  }

  private static class CacheProcessor<O> implements StatelessProcessor<O, O>, Serializable {

    private final ResolvablePromise<O, O> mResolvable;

    private CacheProcessor(@NotNull final ResolvablePromise<O, O> resolvable) {
      mResolvable = resolvable;
    }

    public void reject(final Throwable reason, @NotNull final Callback<O> callback) {
      mResolvable.reject(reason);
    }

    public void resolve(@NotNull final Callback<O> callback) {
      mResolvable.resolve();
    }

    public void resolve(final O input, @NotNull final Callback<O> callback) {
      mResolvable.resolve(input);
    }
  }

  private static class EmptyObserver<O> implements Observer<Callback<O>>, Serializable {

    public void accept(final Callback<O> callback) {
      callback.resolve();
    }
  }

  private static class RejectedObserver<O> implements Observer<Callback<O>>, Serializable {

    private final Throwable mReason;

    private RejectedObserver(final Throwable reason) {
      mReason = reason;
    }

    public void accept(final Callback<O> callback) {
      callback.reject(mReason);
    }
  }

  private static class ResolvedObserver<O> implements Observer<Callback<O>>, Serializable {

    private final O mOutput;

    private ResolvedObserver(final O output) {
      mOutput = output;
    }

    public void accept(final Callback<O> callback) {
      callback.resolve(mOutput);
    }
  }
}

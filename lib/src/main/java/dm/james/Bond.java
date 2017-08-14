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
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import dm.james.executor.ScheduledExecutor;
import dm.james.executor.ScheduledExecutors;
import dm.james.handler.Handlers;
import dm.james.io.Buffer;
import dm.james.io.BufferOutputStream;
import dm.james.log.Log;
import dm.james.log.Log.Level;
import dm.james.promise.DeferredPromise;
import dm.james.promise.DeferredPromiseIterable;
import dm.james.promise.Mapper;
import dm.james.promise.Observer;
import dm.james.promise.Promise;
import dm.james.promise.Promise.Callback;
import dm.james.promise.Promise.Handler;
import dm.james.promise.PromiseIterable;
import dm.james.promise.PromiseIterable.CallbackIterable;
import dm.james.reflect.CallbackMapper;
import dm.james.reflect.PromisifiedObject;
import dm.james.reflect.SimpleCallbackMapper;
import dm.james.util.ConstantConditions;
import dm.james.util.ReflectionUtils;
import dm.james.util.SerializableProxy;

/**
 * Created by davide-maestroni on 06/30/2017.
 */
public class Bond implements Serializable {

  // TODO: 06/08/2017 ByteBuffer => Chunk => ChunkOutputStream => PromiseIterable
  // TODO: 06/08/2017 Handlers
  // TODO: 08/08/2017 promisify
  // TODO: 08/08/2017 BinaryObserver??
  // TODO: 06/08/2017 james-android, james-retrofit, james-swagger

  private static final NoMapper sNoMapper = new NoMapper();

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
    if ((promise instanceof MappedPromise)
        && (((MappedPromise) promise).mapper() instanceof APlusMapper)) {
      return promise;
    }

    return new MappedPromise<O>(new APlusMapper(this), promise);
  }

  @NotNull
  public <O> PromiseIterable<O> all(@NotNull final Iterable<? extends Promise<?>> promises) {
    return this.<O>resolvedIterable(null).allSorted(new PromisesHandler<O>(promises), null);
  }

  @NotNull
  public <O> PromiseIterable<O> all(@NotNull final Promise<? extends Iterable<O>> promise) {
    return each(promise).all(IdentityMapper.<Iterable<O>>instance());
  }

  @NotNull
  public <O> PromiseIterable<O> all(@NotNull final ScheduledExecutor executor,
      @NotNull final Iterable<? extends Promise<?>> promises) {
    return this.<O>resolvedIterable(null).then(null, null, Handlers.<O>resolveOn(executor))
                                         .allSorted(new PromisesHandler<O>(promises), null);
  }

  @NotNull
  public <O> PromiseIterable<O> all(@NotNull final ScheduledExecutor executor,
      @NotNull final Promise<? extends Iterable<O>> promise) {
    return each(executor, promise).all(IdentityMapper.<Iterable<O>>instance());
  }

  @NotNull
  public <O> PromiseIterable<O> any(@NotNull final Iterable<? extends Promise<?>> promises) {
    return this.<O>each(promises).any(IdentityMapper.<O>instance());
  }

  @NotNull
  public <O> PromiseIterable<O> any(@NotNull final Promise<? extends Iterable<O>> promise) {
    return each(promise).any(IdentityMapper.<O>instance());
  }

  @NotNull
  public <O> PromiseIterable<O> any(@NotNull final ScheduledExecutor executor,
      @NotNull final Iterable<? extends Promise<?>> promises) {
    return this.<O>each(executor, promises).any(IdentityMapper.<O>instance());
  }

  @NotNull
  public <O> PromiseIterable<O> any(@NotNull final ScheduledExecutor executor,
      @NotNull final Promise<? extends Iterable<O>> promise) {
    return each(executor, promise).any(IdentityMapper.<O>instance());
  }

  @NotNull
  public BufferOutputStream bufferStream(@Nullable final AllocationType allocationType) {
    return new DefaultBufferOutputStream(this.<Buffer>deferredIterable(), allocationType);
  }

  @NotNull
  public BufferOutputStream bufferStream(@Nullable final AllocationType allocationType,
      final int coreSize) {
    return new DefaultBufferOutputStream(this.<Buffer>deferredIterable(), allocationType, coreSize);
  }

  @NotNull
  public BufferOutputStream bufferStream(@Nullable final AllocationType allocationType,
      final int bufferSize, final int poolSize) {
    return new DefaultBufferOutputStream(this.<Buffer>deferredIterable(), allocationType,
        bufferSize, poolSize);
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
  public <I> DeferredPromiseIterable<I, I> deferredIterable() {
    return new DefaultDeferredPromiseIterable<I, I>(mPropagationType, mLog, mLogLevel);
  }

  @NotNull
  public <O> PromiseIterable<O> each(@NotNull final Iterable<? extends Promise<?>> promises) {
    return iterable(new PromisesHandler<O>(promises));
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public <O> PromiseIterable<O> each(@NotNull final Promise<? extends Iterable<O>> promise) {
    if (promise instanceof PromiseIterable) {
      return (PromiseIterable<O>) promise;
    }

    return iterable(new IterableObserver<O>(promise));
  }

  @NotNull
  public <O> PromiseIterable<O> each(@NotNull final ScheduledExecutor executor,
      @NotNull final Iterable<? extends Promise<?>> promises) {
    return iterable(executor, new PromisesHandler<O>(promises));
  }

  @NotNull
  public <O> PromiseIterable<O> each(@NotNull final ScheduledExecutor executor,
      @NotNull final Promise<? extends Iterable<O>> promise) {
    return iterable(executor, new IterableObserver<O>(promise));
  }

  @NotNull
  public <O> Promise<O> from(@NotNull final Future<O> future) {
    return promise(new FutureObserver<O>(future));
  }

  @NotNull
  public <O> Promise<O> from(@NotNull final Callable<O> callable) {
    return promise(new CallableObserver<O>(callable));
  }

  @NotNull
  public <O> Promise<O> from(@NotNull final ScheduledExecutor executor,
      @NotNull final Future<O> future) {
    return promise(executor, new FutureObserver<O>(future));
  }

  @NotNull
  public <O> Promise<O> from(@NotNull final ScheduledExecutor executor,
      @NotNull final Callable<O> callable) {
    return promise(executor, new CallableObserver<O>(callable));
  }

  @NotNull
  public <O> PromiseIterable<O> iterable(
      @NotNull final Observer<? super CallbackIterable<O>> observer) {
    return new DefaultPromiseIterable<O>(observer, mPropagationType, mLog, mLogLevel);
  }

  @NotNull
  public <O> PromiseIterable<O> iterable(@NotNull final ScheduledExecutor executor,
      @NotNull final Observer<? super CallbackIterable<O>> observer) {
    return iterable(new ScheduledIterableObserver<O>(executor, observer));
  }

  @NotNull
  public <O> Promise<O> promise(@NotNull final Observer<? super Callback<O>> observer) {
    return new DefaultPromise<O>(observer, mPropagationType, mLog, mLogLevel);
  }

  @NotNull
  public <O> Promise<O> promise(@NotNull final ScheduledExecutor executor,
      @NotNull final Observer<? super Callback<O>> observer) {
    return promise(new ScheduledObserver<O>(executor, observer));
  }

  // TODO: 09/08/2017 remove all??
  @NotNull
  public PromisifiedObject promisify(@NotNull final Class<?> target) {
    return promisify(target, sNoMapper);
  }

  @NotNull
  public PromisifiedObject promisify(@NotNull final Class<?> target,
      @NotNull final CallbackMapper mapper) {
    return promisify(ScheduledExecutors.immediateExecutor(), target, mapper);
  }

  @NotNull
  public PromisifiedObject promisify(@NotNull final Object target) {
    return promisify(target, sNoMapper);
  }

  @NotNull
  public PromisifiedObject promisify(@NotNull final Object target,
      @NotNull final CallbackMapper mapper) {
    return promisify(ScheduledExecutors.immediateExecutor(), target, mapper);
  }

  @NotNull
  public PromisifiedObject promisify(@NotNull final ScheduledExecutor executor,
      @NotNull final Class<?> target) {
    return promisify(executor, target, sNoMapper);
  }

  @NotNull
  public PromisifiedObject promisify(@NotNull final ScheduledExecutor executor,
      @NotNull final Class<?> target, @NotNull final CallbackMapper mapper) {
    return new DefaultPromisedObject(this, executor, null, target, mapper);
  }

  @NotNull
  public PromisifiedObject promisify(@NotNull final ScheduledExecutor executor,
      @NotNull final Object target) {
    return promisify(executor, target, sNoMapper);
  }

  @NotNull
  public PromisifiedObject promisify(@NotNull final ScheduledExecutor executor,
      @NotNull final Object target, @NotNull final CallbackMapper mapper) {
    return new DefaultPromisedObject(this, executor, target, target.getClass(), mapper);
  }
  // TODO: 09/08/2017 remove all??

  @NotNull
  public <O> Promise<O> rejected(final Throwable reason) {
    return promise(new RejectedObserver<O>(reason));
  }

  @NotNull
  public <O> PromiseIterable<O> rejectedIterable(final Throwable reason) {
    return iterable(new RejectedObserver<O>(reason));
  }

  @NotNull
  public <O> Promise<O> resolved(final O output) {
    return promise(new ResolvedObserver<O>(output));
  }

  @NotNull
  public <O> PromiseIterable<O> resolvedIterable(@Nullable final Iterable<O> outputs) {
    return iterable(new ResolvedIterableObserver<O>(outputs));
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

  private static class APlusMapper implements Mapper<Promise<?>, Promise<?>>, Serializable {

    private final Bond mBond;

    private APlusMapper(@NotNull final Bond bond) {
      mBond = bond;
    }

    @SuppressWarnings("unchecked")
    public Promise<?> apply(final Promise<?> promise) {
      return ((Promise<Object>) promise).apply(mBond.cache());
    }
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
      return BoundPromise.create(promise, mDeferred);
    }
  }

  private static class DefaultPromisedObject implements PromisifiedObject, Serializable {

    private final Bond mBond;

    private final ScheduledExecutor mExecutor;

    private final CallbackMapper mMapper;

    private final Object mTarget;

    private final Class<?> mTargetClass;

    DefaultPromisedObject(@NotNull final Bond bond, @NotNull final ScheduledExecutor executor,
        final Object target, @NotNull final Class<?> targetClass,
        @NotNull final CallbackMapper mapper) {
      mExecutor = ConstantConditions.notNull("executor", executor);
      mTargetClass = ConstantConditions.notNull("class", targetClass);
      mMapper = ConstantConditions.notNull("mapper", mapper);
      mTarget = target;
      mBond = bond;
    }

    @NotNull
    public <O> PromiseIterable<O> iterable(@NotNull final Method method,
        @Nullable final Object... params) {
      return mBond.iterable(mExecutor,
          PromisifiedObservers.<O>iterableObserver(mTarget, method, params, mMapper));
    }

    @NotNull
    public <O> PromiseIterable<O> iterable(@NotNull final String name,
        @Nullable final Object... params) {
      return mBond.iterable(mExecutor,
          PromisifiedObservers.<O>iterableObserver(mTarget, mTargetClass, name, params, mMapper));
    }

    @NotNull
    public <O> Promise<O> promise(@NotNull final Method method, @Nullable final Object... params) {
      return mBond.promise(mExecutor,
          PromisifiedObservers.<O>observer(mTarget, method, params, mMapper));
    }

    @NotNull
    public <O> Promise<O> promise(@NotNull final String name, @Nullable final Object... params) {
      return mBond.promise(mExecutor,
          PromisifiedObservers.<O>observer(mTarget, mTargetClass, name, params, mMapper));
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ObjectProxy(mBond, mExecutor, mTarget, mTargetClass, mMapper);
    }

    private static class ObjectProxy extends SerializableProxy {

      private ObjectProxy(final Bond bond, final ScheduledExecutor executor, final Object target,
          final Class<?> targetClass, final CallbackMapper mapper) {
        super(bond, executor, target, targetClass, proxy(mapper));
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new DefaultPromisedObject((Bond) args[0], (ScheduledExecutor) args[1], args[2],
              (Class<?>) args[3], (CallbackMapper) args[4]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }
  }

  private static class IterableObserver<O> implements Observer<CallbackIterable<O>>, Serializable {

    private final Promise<? extends Iterable<O>> mPromise;

    private IterableObserver(@NotNull final Promise<? extends Iterable<O>> promise) {
      mPromise = ConstantConditions.notNull("promise", promise);
    }

    public void accept(final CallbackIterable<O> callback) {
      callback.addAllDeferred(mPromise);
      callback.resolve();
    }
  }

  private static class NoMapper extends SimpleCallbackMapper implements Serializable {

    public boolean canMapCallback(@NotNull final Class<?> type) {
      return false;
    }

    public Object mapCallback(@NotNull final Class<?> type,
        @NotNull final Callback<Object> callback) {
      return null;
    }
  }

  private static class PromisesHandler<O>
      implements Handler<Iterable<O>, CallbackIterable<O>>, Observer<CallbackIterable<O>>,
      Serializable {

    private final Iterable<? extends Promise<?>> mPromises;

    private PromisesHandler(@NotNull final Iterable<? extends Promise<?>> promises) {
      mPromises = ConstantConditions.notNull("promises", promises);
    }

    @SuppressWarnings("unchecked")
    public void accept(final Iterable<O> input, final CallbackIterable<O> callback) throws
        Exception {
      for (final Promise<?> promise : mPromises) {
        if (promise instanceof PromiseIterable) {
          callback.addAllDeferred((PromiseIterable<O>) promise);

        } else {
          callback.addDeferred((Promise<O>) promise);
        }
      }

      callback.resolve();
    }

    @SuppressWarnings("unchecked")
    public void accept(final CallbackIterable<O> callback) {
      for (final Promise<?> promise : mPromises) {
        if (promise instanceof PromiseIterable) {
          callback.addAllDeferred((PromiseIterable<O>) promise);

        } else {
          callback.addDeferred((Promise<O>) promise);
        }
      }

      callback.resolve();
    }
  }
}

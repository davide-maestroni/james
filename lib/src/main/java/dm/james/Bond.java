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
import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import dm.james.executor.ScheduledExecutor;
import dm.james.handler.Handlers;
import dm.james.log.Log;
import dm.james.log.Log.Level;
import dm.james.math.Operation;
import dm.james.promise.DeferredPromise;
import dm.james.promise.DeferredPromiseIterable;
import dm.james.promise.Mapper;
import dm.james.promise.Observer;
import dm.james.promise.Promise;
import dm.james.promise.Promise.Callback;
import dm.james.promise.PromiseIterable;
import dm.james.promise.PromiseIterable.CallbackIterable;
import dm.james.promise.PromiseIterable.StatelessHandler;
import dm.james.promise.Provider;
import dm.james.util.ConstantConditions;
import dm.james.util.ReflectionUtils;

import static dm.james.math.Numbers.getHigherPrecisionOperation;
import static dm.james.math.Numbers.getOperation;

/**
 * Created by davide-maestroni on 06/30/2017.
 */
public class Bond implements Serializable {

  // TODO: 06/08/2017 Handlers
  // TODO: 06/08/2017 ByteBuffer => Chunk => ChunkOutputStream => PromiseIterable
  // TODO: 06/08/2017 james-android, james-retrofit, james-swagger

  private static final EndpointsType DEFAULT_ENDPOINTS = EndpointsType.INCLUSIVE;

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
  private static <O> Object[] replaceParams(@NotNull final Object[] params,
      @NotNull final Mapper<Callback<O>, Object> mapper, @NotNull final Callback<O> callback) throws
      Exception {
    boolean found = false;
    final Object[] objects = params.clone();
    for (int i = 0; i < objects.length; ++i) {
      if (objects[i] == PromiseFunction.CALLBACK) {
        if (found) {
          throw new IllegalArgumentException(
              "input parameters include more then one callback function: " + Arrays.toString(
                  params));
        }

        found = true;
        objects[i] = mapper.apply(callback);
      }
    }

    if (!found) {
      throw new IllegalArgumentException(
          "input parameters do not include the callback function: " + Arrays.toString(params));
    }

    return objects;
  }

  private static void validateParams(@NotNull final Object[] params, final int length) {
    if (params.length != length) {
      throw new IllegalArgumentException(
          "invalid number of parameters: expected <" + length + "> but was <" + params.length
              + ">");
    }
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
    return this.<O>resolvedIterable(null).allSorted(new PromisesHandler<O>(promises));
  }

  @NotNull
  public <O> PromiseIterable<O> all(@NotNull final Promise<? extends Iterable<O>> promise) {
    return each(promise).all(IdentityMapper.<Iterable<O>>instance());
  }

  @NotNull
  public <O> PromiseIterable<O> all(@NotNull final ScheduledExecutor executor,
      @NotNull final Iterable<? extends Promise<?>> promises) {
    return this.<O>resolvedIterable(null).all(Handlers.<Iterable<O>>scheduleOn(executor))
                                         .allSorted(new PromisesHandler<O>(promises));
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
  public <O> PromiseIterable<O> each(@NotNull final Promise<? extends Iterable<O>> promise) {
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

  @NotNull
  public <O> PromiseFunction<O> promisify(final Object target, @NotNull final Method method,
      @NotNull final Mapper<Callback<O>, Object> mapper) {
    return new DefaultPromiseFunction<O>(target, method, mapper);
  }

  @NotNull
  public <I, O> PromiseFunction<O> promisify(@NotNull final Observer<I> method,
      @NotNull final Mapper<Callback<O>, Object> mapper) {
    return new DefaultPromiseFunction1<I, O>(method, mapper);
  }

  @NotNull
  public <I1, I2, O> PromiseFunction<O> promisify(@NotNull final Observer2<I1, I2> method,
      @NotNull final Mapper<Callback<O>, Object> mapper) {
    return new DefaultPromiseFunction2<I1, I2, O>(method, mapper);
  }

  @NotNull
  public <I1, I2, I3, O> PromiseFunction<O> promisify(@NotNull final Observer3<I1, I2, I3> method,
      @NotNull final Mapper<Callback<O>, Object> mapper) {
    return new DefaultPromiseFunction3<I1, I2, I3, O>(method, mapper);
  }

  @NotNull
  public <I1, I2, I3, I4, O> PromiseFunction<O> promisify(
      @NotNull final Observer4<I1, I2, I3, I4> method,
      @NotNull final Mapper<Callback<O>, Object> mapper) {
    return new DefaultPromiseFunction4<I1, I2, I3, I4, O>(method, mapper);
  }

  @NotNull
  public <I1, I2, I3, I4, I5, O> PromiseFunction<O> promisify(
      @NotNull final Observer5<I1, I2, I3, I4, I5> method,
      @NotNull final Mapper<Callback<O>, Object> mapper) {
    return new DefaultPromiseFunction5<I1, I2, I3, I4, I5, O>(method, mapper);
  }

  /**
   * Returns a consumer generating the specified range of numbers.
   * <br>
   * The stream will generate a range of numbers up to and including the {@code end} number, by
   * applying a default increment of {@code +1} or {@code -1} depending on the comparison between
   * the first and the last number. That is, if the first number is less than the last, the
   * increment will be {@code +1}. On the contrary, if the former is greater than the latter, the
   * increment will be {@code -1}.
   * <br>
   * The endpoint values will be included or not in the range based on the specified type.
   * <br>
   * Note that the {@code end} number will be returned only if the incremented value will exactly
   * match it.
   *
   * @param endpoints the type of endpoints inclusion.
   * @param start     the first number in the range.
   * @param end       the last number in the range.
   * @param <N>       the number type.
   * @return the consumer instance.
   */
  @NotNull
  @SuppressWarnings("unchecked")
  public <N extends Number> PromiseIterable<N> range(@NotNull final EndpointsType endpoints,
      @NotNull final N start, @NotNull final N end) {
    final Operation<?> operation = getHigherPrecisionOperation(start.getClass(), end.getClass());
    return range(endpoints, start, end,
        (N) getOperation(start.getClass()).convert((operation.compare(start, end) <= 0) ? 1 : -1));
  }

  /**
   * Returns a consumer generating the specified range of numbers.
   * <br>
   * The stream will generate a range of numbers by applying the specified increment up to the
   * {@code end} number.
   * <br>
   * The endpoint values will be included or not in the range based on the specified type.
   * <br>
   * Note that the {@code end} number will be returned only if the incremented value will exactly
   * match it.
   *
   * @param endpoints the type of endpoints inclusion.
   * @param start     the first number in the range.
   * @param end       the last number in the range.
   * @param increment the increment to apply to the current number.
   * @param <N>       the number type.
   * @return the consumer instance.
   */
  @NotNull
  public <N extends Number> PromiseIterable<N> range(@NotNull final EndpointsType endpoints,
      @NotNull final N start, @NotNull final N end, @NotNull final N increment) {
    return iterable(new NumberRangeObserver<N>(endpoints, start, end, increment));
  }

  /**
   * Returns a consumer generating the specified range of data.
   * <br>
   * The generated data will start from the specified first one up to the specified last one, by
   * computing each next element through the specified function.
   * <br>
   * The endpoint values will be included or not in the range based on the specified type.
   *
   * @param endpoints the type of endpoints inclusion.
   * @param start     the first element in the range.
   * @param end       the last element in the range.
   * @param increment the mapper incrementing the current element.
   * @param <O>       the output data type.
   * @return the consumer instance.
   */
  @NotNull
  public <O extends Comparable<? super O>> PromiseIterable<O> range(
      @NotNull final EndpointsType endpoints, @NotNull final O start, @NotNull final O end,
      @NotNull final Mapper<O, O> increment) {
    return iterable(new RangeObserver<O>(endpoints, start, end, increment));
  }

  @NotNull
  public <N extends Number> PromiseIterable<N> range(@NotNull final N start, @NotNull final N end) {
    return range(DEFAULT_ENDPOINTS, start, end);
  }

  @NotNull
  public <N extends Number> PromiseIterable<N> range(@NotNull final N start, @NotNull final N end,
      @NotNull final N increment) {
    return range(DEFAULT_ENDPOINTS, start, end, increment);
  }

  @NotNull
  public <O extends Comparable<? super O>> PromiseIterable<O> range(@NotNull final O start,
      @NotNull final O end, @NotNull final Mapper<O, O> increment) {
    return range(DEFAULT_ENDPOINTS, start, end, increment);
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public <N extends Number> PromiseIterable<N> range(@NotNull final ScheduledExecutor executor,
      @NotNull final EndpointsType endpoints, @NotNull final N start, @NotNull final N end) {
    final Operation<?> operation = getHigherPrecisionOperation(start.getClass(), end.getClass());
    return range(executor, endpoints, start, end,
        (N) getOperation(start.getClass()).convert((operation.compare(start, end) <= 0) ? 1 : -1));
  }

  @NotNull
  public <N extends Number> PromiseIterable<N> range(@NotNull final ScheduledExecutor executor,
      @NotNull final EndpointsType endpoints, @NotNull final N start, @NotNull final N end,
      @NotNull final N increment) {
    return iterable(executor, new NumberRangeObserver<N>(endpoints, start, end, increment));
  }

  @NotNull
  public <O extends Comparable<? super O>> PromiseIterable<O> range(
      @NotNull final ScheduledExecutor executor, @NotNull final EndpointsType endpoints,
      @NotNull final O start, @NotNull final O end, @NotNull final Mapper<O, O> increment) {
    return iterable(executor, new RangeObserver<O>(endpoints, start, end, increment));
  }

  @NotNull
  public <N extends Number> PromiseIterable<N> range(@NotNull final ScheduledExecutor executor,
      @NotNull final N start, @NotNull final N end) {
    return range(executor, DEFAULT_ENDPOINTS, start, end);
  }

  @NotNull
  public <N extends Number> PromiseIterable<N> range(@NotNull final ScheduledExecutor executor,
      @NotNull final N start, @NotNull final N end, @NotNull final N increment) {
    return range(executor, DEFAULT_ENDPOINTS, start, end, increment);
  }

  @NotNull
  public <O extends Comparable<? super O>> PromiseIterable<O> range(
      @NotNull final ScheduledExecutor executor, @NotNull final O start, @NotNull final O end,
      @NotNull final Mapper<O, O> increment) {
    return range(executor, DEFAULT_ENDPOINTS, start, end, increment);
  }

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

  /**
   * Returns a consumer generating the specified sequence of data.
   * <br>
   * The generated data will start from the specified first and will produce the specified number
   * of elements, by computing each next one through the specified function.
   *
   * @param start the first element of the sequence.
   * @param size  the size of the sequence.
   * @param next  the function computing the next element.
   * @param <O>   the data type.
   * @return the consumer instance.
   * @throws java.lang.IllegalArgumentException if the size is not positive.
   */
  @NotNull
  public <O> PromiseIterable<O> sequence(@NotNull final O start, final long size,
      @NotNull final SequenceIncrement<O> next) {
    return iterable(new SequenceObserver<O>(start, size, next));
  }

  @NotNull
  public <O> PromiseIterable<O> sequence(@NotNull final ScheduledExecutor executor,
      @NotNull final O start, final long size, @NotNull final SequenceIncrement<O> next) {
    return iterable(new SequenceObserver<O>(start, size, next));
  }

  @NotNull
  public <I extends Closeable, O> PromiseIterable<O> tryIterable(
      @NotNull final Iterable<Provider<I>> providers,
      @NotNull final Mapper<List<I>, PromiseIterable<O>> mapper) {
    return iterable(new TryIterableObserver<I, O>(providers, mapper, mLog, mLogLevel));
  }

  @NotNull
  public <I extends Closeable, O> PromiseIterable<O> tryIterable(
      @NotNull final ScheduledExecutor executor, @NotNull final Iterable<Provider<I>> providers,
      @NotNull final Mapper<List<I>, PromiseIterable<O>> mapper) {
    return iterable(executor, new TryIterableObserver<I, O>(providers, mapper, mLog, mLogLevel));
  }

  @NotNull
  public <I extends Closeable, O> Promise<O> tryPromise(
      @NotNull final Iterable<Provider<I>> providers,
      @NotNull final Mapper<List<I>, Promise<O>> mapper) {
    return promise(new TryObserver<I, O>(providers, mapper, mLog, mLogLevel));
  }

  @NotNull
  public <I extends Closeable, O> Promise<O> tryPromise(@NotNull final ScheduledExecutor executor,
      @NotNull final Iterable<Provider<I>> providers,
      @NotNull final Mapper<List<I>, Promise<O>> mapper) {
    return promise(executor, new TryObserver<I, O>(providers, mapper, mLog, mLogLevel));
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

  public interface Observer2<I1, I2> {

    void accept(I1 input1, I2 input2) throws Exception;
  }

  public interface Observer3<I1, I2, I3> {

    void accept(I1 input1, I2 input2, I3 input3) throws Exception;
  }

  public interface Observer4<I1, I2, I3, I4> {

    void accept(I1 input1, I2 input2, I3 input3, I4 input4) throws Exception;
  }

  public interface Observer5<I1, I2, I3, I4, I5> {

    void accept(I1 input1, I2 input2, I3 input3, I4 input4, I5 input5) throws Exception;
  }

  interface PromiseFunction<O> {

    Object CALLBACK = new Object();

    @NotNull
    Promise<O> call(@NotNull Object... params);

    @NotNull
    Promise<O> callAsync(@NotNull ScheduledExecutor executor, @NotNull Object... params);
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

  private static class PromisesHandler<O>
      implements StatelessHandler<Iterable<O>, O>, Observer<CallbackIterable<O>>, Serializable {

    private final Iterable<? extends Promise<?>> mPromises;

    private PromisesHandler(@NotNull final Iterable<? extends Promise<?>> promises) {
      mPromises = ConstantConditions.notNull("promises", promises);
    }

    public void reject(final Throwable reason, @NotNull final CallbackIterable<O> callback) {
      callback.reject(reason);
    }

    @SuppressWarnings("unchecked")
    public void resolve(final Iterable<O> input, @NotNull final CallbackIterable<O> callback) {
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

  private class DefaultPromiseFunction<O> implements PromiseFunction<O>, Serializable {

    private final Mapper<Callback<O>, Object> mMapper;

    private final Method mMethod;

    private final Object mTarget;

    private DefaultPromiseFunction(final Object target, @NotNull final Method method,
        @NotNull final Mapper<Callback<O>, Object> mapper) {
      mMethod = ConstantConditions.notNull("method", method);
      mMapper = ConstantConditions.notNull("mapper", mapper);
      mTarget = target;
    }

    @NotNull
    public Promise<O> call(@NotNull final Object... params) {
      return promise(new FunctionObserver(params));
    }

    private class FunctionObserver implements Observer<Callback<O>>, Serializable {

      private final Object[] mParams;

      private FunctionObserver(@NotNull final Object... params) {
        validateParams(params, mMethod.getParameterTypes().length);
        mParams = params.clone();
      }

      public void accept(final Callback<O> callback) throws Exception {
        mMethod.invoke(mTarget, replaceParams(mParams, mMapper, callback));
      }
    }

    @NotNull
    public Promise<O> callAsync(@NotNull final ScheduledExecutor executor,
        @NotNull final Object... params) {
      return promise(executor, new FunctionObserver(params));
    }
  }

  private class DefaultPromiseFunction1<I, O> implements PromiseFunction<O>, Serializable {

    private final Mapper<Callback<O>, Object> mMapper;

    private final Observer<I> mMethod;

    private DefaultPromiseFunction1(@NotNull final Observer<I> method,
        @NotNull final Mapper<Callback<O>, Object> mapper) {
      mMethod = ConstantConditions.notNull("method", method);
      mMapper = ConstantConditions.notNull("mapper", mapper);
    }

    private class FunctionObserver implements Observer<Callback<O>>, Serializable {

      private final Object[] mParams;

      private FunctionObserver(@NotNull final Object... params) {
        validateParams(params, 1);
        mParams = params.clone();
      }

      @SuppressWarnings("unchecked")
      public void accept(final Callback<O> callback) throws Exception {
        final Object[] params = replaceParams(mParams, mMapper, callback);
        mMethod.accept((I) params[0]);
      }
    }

    @NotNull
    public Promise<O> call(@NotNull final Object... params) {
      return promise(new FunctionObserver(params));
    }

    @NotNull
    public Promise<O> callAsync(@NotNull final ScheduledExecutor executor,
        @NotNull final Object... params) {
      return promise(executor, new FunctionObserver(params));
    }
  }

  private class DefaultPromiseFunction2<I1, I2, O> implements PromiseFunction<O>, Serializable {

    private final Mapper<Callback<O>, Object> mMapper;

    private final Observer2<I1, I2> mMethod;

    private DefaultPromiseFunction2(@NotNull final Observer2<I1, I2> method,
        @NotNull final Mapper<Callback<O>, Object> mapper) {
      mMethod = ConstantConditions.notNull("method", method);
      mMapper = ConstantConditions.notNull("mapper", mapper);
    }

    private class FunctionObserver implements Observer<Callback<O>>, Serializable {

      private final Object[] mParams;

      private FunctionObserver(@NotNull final Object... params) {
        validateParams(params, 2);
        mParams = params.clone();
      }

      @SuppressWarnings("unchecked")
      public void accept(final Callback<O> callback) throws Exception {
        final Object[] params = replaceParams(mParams, mMapper, callback);
        mMethod.accept((I1) params[0], (I2) params[1]);
      }
    }

    @NotNull
    public Promise<O> call(@NotNull final Object... params) {
      return promise(new FunctionObserver(params));
    }

    @NotNull
    public Promise<O> callAsync(@NotNull final ScheduledExecutor executor,
        @NotNull final Object... params) {
      return promise(executor, new FunctionObserver(params));
    }
  }

  private class DefaultPromiseFunction3<I1, I2, I3, O> implements PromiseFunction<O>, Serializable {

    private final Mapper<Callback<O>, Object> mMapper;

    private final Observer3<I1, I2, I3> mMethod;

    private DefaultPromiseFunction3(@NotNull final Observer3<I1, I2, I3> method,
        @NotNull final Mapper<Callback<O>, Object> mapper) {
      mMethod = ConstantConditions.notNull("method", method);
      mMapper = ConstantConditions.notNull("mapper", mapper);
    }

    private class FunctionObserver implements Observer<Callback<O>>, Serializable {

      private final Object[] mParams;

      private FunctionObserver(@NotNull final Object... params) {
        validateParams(params, 3);
        mParams = params.clone();
      }

      @SuppressWarnings("unchecked")
      public void accept(final Callback<O> callback) throws Exception {
        final Object[] params = replaceParams(mParams, mMapper, callback);
        mMethod.accept((I1) params[0], (I2) params[1], (I3) params[2]);
      }
    }

    @NotNull
    public Promise<O> call(@NotNull final Object... params) {
      return promise(new FunctionObserver(params));
    }

    @NotNull
    public Promise<O> callAsync(@NotNull final ScheduledExecutor executor,
        @NotNull final Object... params) {
      return promise(executor, new FunctionObserver(params));
    }
  }

  private class DefaultPromiseFunction4<I1, I2, I3, I4, O>
      implements PromiseFunction<O>, Serializable {

    private final Mapper<Callback<O>, Object> mMapper;

    private final Observer4<I1, I2, I3, I4> mMethod;

    private DefaultPromiseFunction4(@NotNull final Observer4<I1, I2, I3, I4> method,
        @NotNull final Mapper<Callback<O>, Object> mapper) {
      mMethod = ConstantConditions.notNull("method", method);
      mMapper = ConstantConditions.notNull("mapper", mapper);
    }

    private class FunctionObserver implements Observer<Callback<O>>, Serializable {

      private final Object[] mParams;

      private FunctionObserver(@NotNull final Object... params) {
        validateParams(params, 4);
        mParams = params.clone();
      }

      @SuppressWarnings("unchecked")
      public void accept(final Callback<O> callback) throws Exception {
        final Object[] params = replaceParams(mParams, mMapper, callback);
        mMethod.accept((I1) params[0], (I2) params[1], (I3) params[2], (I4) params[3]);
      }
    }

    @NotNull
    public Promise<O> call(@NotNull final Object... params) {
      return promise(new FunctionObserver(params));
    }

    @NotNull
    public Promise<O> callAsync(@NotNull final ScheduledExecutor executor,
        @NotNull final Object... params) {
      return promise(executor, new FunctionObserver(params));
    }
  }

  private class DefaultPromiseFunction5<I1, I2, I3, I4, I5, O>
      implements PromiseFunction<O>, Serializable {

    private final Mapper<Callback<O>, Object> mMapper;

    private final Observer5<I1, I2, I3, I4, I5> mMethod;

    private DefaultPromiseFunction5(@NotNull final Observer5<I1, I2, I3, I4, I5> method,
        @NotNull final Mapper<Callback<O>, Object> mapper) {
      mMethod = ConstantConditions.notNull("method", method);
      mMapper = ConstantConditions.notNull("mapper", mapper);
    }

    private class FunctionObserver implements Observer<Callback<O>>, Serializable {

      private final Object[] mParams;

      private FunctionObserver(@NotNull final Object... params) {
        validateParams(params, 4);
        mParams = params.clone();
      }

      @SuppressWarnings("unchecked")
      public void accept(final Callback<O> callback) throws Exception {
        final Object[] params = replaceParams(mParams, mMapper, callback);
        mMethod.accept((I1) params[0], (I2) params[1], (I3) params[2], (I4) params[3],
            (I5) params[4]);
      }
    }

    @NotNull
    public Promise<O> call(@NotNull final Object... params) {
      return promise(new FunctionObserver(params));
    }

    @NotNull
    public Promise<O> callAsync(@NotNull final ScheduledExecutor executor,
        @NotNull final Object... params) {
      return promise(executor, new FunctionObserver(params));
    }
  }
}

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

package dm.james.promise2;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import dm.james.executor.ScheduledExecutor;
import dm.james.executor.ScheduledExecutors;
import dm.james.log.Log;
import dm.james.log.Log.Level;
import dm.james.log.Logger;
import dm.james.promise.Action;
import dm.james.promise.CancellationException;
import dm.james.promise.Mapper;
import dm.james.promise.Observer;
import dm.james.promise.PromiseInspection;
import dm.james.promise.RejectionException;
import dm.james.promise.ScheduledData;
import dm.james.promise.TimeoutException;
import dm.james.util.Backoff;
import dm.james.util.ConstantConditions;
import dm.james.util.InterruptedExecutionException;
import dm.james.util.SerializableProxy;
import dm.james.util.ThreadUtils;
import dm.james.util.TimeUtils;
import dm.james.util.TimeUtils.Condition;

/**
 * Created by davide-maestroni on 12/05/2017.
 */
public class ValuePromise<V> implements Promise<V> {

  private static final Object REMOVED = new Object();

  private static final InspectFulfill<?> sInspectFulfill = new InspectFulfill<Object>();

  private static final InspectReject<?> sInspectReject = new InspectReject<Object>();

  private static final CallbackHandler<Object, Object, IterableCallback<Object>> sSpreadFulfill =
      new CallbackHandler<Object, Object, IterableCallback<Object>>() {

        public void accept(final Object value,
            @NotNull final IterableCallback<Object> callback) throws Exception {
          if (value instanceof Iterable) {
            callback.fulfillAllAndContinue((Iterable<?>) value).resolve();

          } else {
            callback.fulfill(value);
          }
        }

        Object readResolve() throws ObjectStreamException {
          return sSpreadFulfill;
        }
      };

  private static DefaultFulfillHandler<?, ?> sDefaultFulfillHandler =
      new DefaultFulfillHandler<Object, Object>();

  private static DefaultRejectHandler<?> sDefaultRejectHandler = new DefaultRejectHandler<Object>();

  private final ScheduledExecutor mExecutor;

  private final ChainHead<?> mHead;

  private final boolean mIsOrdered;

  private final boolean mIsTrying;

  private final Logger mLogger;

  private final Object mMutex;

  private final Observer<Callback<?>> mObserver;

  private final PromiseChain<?, V> mTail;

  private PromiseChain<V, ?> mChain;

  private PromiseState mState = PromiseState.Pending;

  @SuppressWarnings("unchecked")
  ValuePromise(@NotNull final Observer<? super Callback<V>> observer, @Nullable final Log log,
      @Nullable final Level level) {
    mObserver = (Observer<Callback<?>>) ConstantConditions.notNull("observer", observer);
    mExecutor = ScheduledExecutors.immediateExecutor();
    mIsOrdered = false;
    mIsTrying = false;
    mLogger = Logger.newLogger(log, level, this);
    final ChainHead<V> head = new ChainHead<V>();
    head.setLogger(mLogger);
    head.setNext(new ChainTail());
    mMutex = head.getMutex();
    mHead = head;
    mTail = head;
    try {
      observer.accept(head);

    } catch (final Throwable t) {
      InterruptedExecutionException.throwIfInterrupt(t);
      head.reject(t);
    }
  }

  @SuppressWarnings("unchecked")
  private ValuePromise(@NotNull final Observer<Callback<?>> observer,
      @NotNull final ScheduledExecutor executor, final boolean isOrdered, final boolean isTrying,
      @Nullable final Log log, @Nullable final Level level, @NotNull final ChainHead<?> head,
      @NotNull final PromiseChain<?, V> tail) {
    // serialization
    mObserver = observer;
    mExecutor = executor;
    mIsOrdered = isOrdered;
    mIsTrying = isTrying;
    mLogger = Logger.newLogger(log, level, this);
    mMutex = head.getMutex();
    mHead = head;
    mTail = tail;
    tail.setNext(new ChainTail());
    PromiseChain<?, ?> chain = head;
    while (!chain.isTail()) {
      chain.setLogger(mLogger);
      chain = chain.mNext;
    }

    try {
      observer.accept(head);

    } catch (final Throwable t) {
      InterruptedExecutionException.throwIfInterrupt(t);
      head.reject(t);
    }
  }

  @SuppressWarnings("unchecked")
  private ValuePromise(@NotNull final Observer<Callback<?>> observer,
      @NotNull final ScheduledExecutor executor, final boolean isOrdered, final boolean isTrying,
      @NotNull final Logger logger, @NotNull final ChainHead<?> head,
      @NotNull final PromiseChain<?, ?> tail, @NotNull final PromiseChain<?, V> chain) {
    // chain
    mObserver = observer;
    mExecutor = executor;
    mIsOrdered = isOrdered;
    mIsTrying = isTrying;
    mLogger = logger;
    mMutex = head.getMutex();
    mHead = head;
    mTail = chain;
    chain.setNext(new ChainTail());
    ((PromiseChain<?, Object>) tail).setNext((PromiseChain<Object, V>) chain);
  }

  @SuppressWarnings("unchecked")
  private ValuePromise(@NotNull final Observer<Callback<?>> observer,
      @NotNull final ScheduledExecutor executor, final boolean isOrdered, final boolean isTrying,
      @NotNull final Logger logger, @NotNull final ChainHead<?> head,
      @NotNull final PromiseChain<?, V> tail) {
    // copy
    mObserver = observer;
    mExecutor = executor;
    mIsOrdered = isOrdered;
    mIsTrying = isTrying;
    mLogger = logger;
    mMutex = head.getMutex();
    mHead = head;
    mTail = tail;
    tail.setNext(new ChainTail());
    try {
      observer.accept(head);

    } catch (final Throwable t) {
      InterruptedExecutionException.throwIfInterrupt(t);
      head.reject(t);
    }
  }

  private static void close(@Nullable final Object object, @NotNull final Logger logger) throws
      RejectionException {
    if (!(object instanceof Closeable)) {
      return;
    }

    try {
      ((Closeable) object).close();

    } catch (final IOException e) {
      logger.err(e, "Error while closing closeable: " + object);
      throw new RejectionException(e);
    }
  }

  @NotNull
  @SuppressWarnings("unchecked")
  private static <V, R> DefaultFulfillHandler<V, R> defaultFulfillHandler() {
    return (DefaultFulfillHandler<V, R>) sDefaultFulfillHandler;
  }

  @NotNull
  @SuppressWarnings("unchecked")
  private static <R> DefaultRejectHandler<R> defaultRejectHandler() {
    return (DefaultRejectHandler<R>) sDefaultRejectHandler;
  }

  private static void safeClose(@Nullable final Object object, @NotNull final Logger logger) {
    if (!(object instanceof Closeable)) {
      return;
    }

    try {
      ((Closeable) object).close();

    } catch (final IOException e) {
      logger.wrn(e, "Suppressed exception");
    }
  }

  @NotNull
  public <E> Iterable<E> asIterable() {
    return new PromiseIterable<E>(this);
  }

  public boolean cancel() {
    PromiseChain<?, ?> chain = mHead;
    final CancellationException reason = new CancellationException();
    while (!chain.isTail()) {
      if (chain.cancel(reason)) {
        return true;
      }

      chain = chain.mNext;
    }

    return false;
  }

  @NotNull
  public <E, R, S> Promise<R> collect(
      @NotNull final ReductionHandler<E, R, S, ? super Callback<R>> handler) {
    return then(new ReduceFulfill<V, E, R, S>(handler), new ReduceReject<E, R, S>(handler));
  }

  @NotNull
  public <E, R, S> Promise<R> collect(@Nullable final Mapper<? super Callback<R>, S> create,
      @Nullable final ReductionFulfill<E, R, S, ? super Callback<R>> fulfill,
      @Nullable final ReductionReject<R, S, ? super Callback<R>> reject,
      @Nullable final ReductionResolve<R, S, ? super Callback<R>> resolve) {
    return collect(
        new ComposedReductionHandler<E, R, S, Callback<R>>(create, fulfill, reject, resolve));
  }

  @NotNull
  public <E, R, S> Promise<R> collectTrying(
      @NotNull final ReductionHandler<E, R, S, ? super Callback<R>> handler) {
    final Logger logger = mLogger;
    return then(new ReduceFulfillTrying<V, E, R, S>(handler, logger.getLog(), logger.getLogLevel()),
        new ReduceRejectTrying<E, R, S>(handler, logger.getLog(), logger.getLogLevel()));
  }

  @NotNull
  public <E, R, S> Promise<R> collectTrying(@Nullable final Mapper<? super Callback<R>, S> create,
      @Nullable final ReductionFulfill<E, R, S, ? super Callback<R>> fulfill,
      @Nullable final ReductionReject<R, S, ? super Callback<R>> reject,
      @Nullable final ReductionResolve<R, S, ? super Callback<R>> resolve) {
    return collectTrying(
        new ComposedReductionHandler<E, R, S, Callback<R>>(create, fulfill, reject, resolve));
  }

  @NotNull
  public <E, R> Promise<Iterable<R>> forEach(
      @Nullable final CallbackHandler<E, R, ? super IterableCallback<R>> fulfill,
      @Nullable final CallbackHandler<Throwable, R, ? super IterableCallback<R>> reject) {
    return spread().forEach(fulfill, reject);
  }

  @NotNull
  public <R> Promise<Iterable<R>> forEachCatch(@Nullable final Mapper<Throwable, R> mapper) {
    return spread().forEachCatch(mapper);
  }

  @NotNull
  public <E, R> Promise<Iterable<R>> forEachMap(@Nullable final Mapper<E, R> mapper) {
    return spread().forEachMap(mapper);
  }

  @NotNull
  public <E, R> Promise<Iterable<R>> forEachMap(@Nullable final Mapper<E, R> mapper,
      final int minBatchSize, final int maxBatchSize) {
    return spread().forEachMap(mapper, minBatchSize, maxBatchSize);
  }

  @NotNull
  public <E> Promise<Iterable<E>> forEachSchedule(@NotNull final ScheduledExecutor executor,
      @NotNull final Backoff<ScheduledData<E>> backoff) {
    return spread().forEachSchedule(executor, backoff);
  }

  @NotNull
  public <E> Promise<Iterable<E>> forEachSchedule(@NotNull final ScheduledExecutor executor) {
    return spread().forEachSchedule(executor);
  }

  @Nullable
  public RejectionException getReason() {
    return getReason(-1, TimeUnit.MILLISECONDS);
  }

  @Nullable
  public RejectionException getReason(final long timeout, @NotNull final TimeUnit timeUnit) {
    deadLockWarning(timeout);
    final ChainHead<?> head = mHead;
    synchronized (mMutex) {
      try {
        if (TimeUtils.waitUntil(mMutex, new Condition() {

          public boolean isTrue() {
            checkBound();
            return head.getState().isResolved();
          }
        }, timeout, timeUnit)) {
          return head.getException();
        }

      } catch (final InterruptedException e) {
        throw new InterruptedExecutionException(e);
      }
    }

    throw new TimeoutException(
        "timeout while waiting for promise rejection [" + timeout + " " + timeUnit + "]");
  }

  public RejectionException getReasonOr(final RejectionException other, final long timeout,
      @NotNull final TimeUnit timeUnit) {
    deadLockWarning(timeout);
    final ChainHead<?> head = mHead;
    synchronized (mMutex) {
      try {
        if (TimeUtils.waitUntil(mMutex, new Condition() {

          public boolean isTrue() {
            checkBound();
            return head.getState().isResolved();
          }
        }, timeout, timeUnit)) {
          return head.getException();
        }

      } catch (final InterruptedException e) {
        throw new InterruptedExecutionException(e);
      }
    }

    return other;
  }

  public V getValue() {
    return getValue(-1, TimeUnit.MILLISECONDS);
  }

  @SuppressWarnings("unchecked")
  public V getValue(final long timeout, @NotNull final TimeUnit unit) {
    deadLockWarning(timeout);
    final ChainHead<?> head = mHead;
    synchronized (mMutex) {
      try {
        if (TimeUtils.waitUntil(mMutex, new Condition() {

          public boolean isTrue() {
            checkBound();
            return head.getState().isResolved();
          }
        }, timeout, unit)) {
          final V value = (V) head.getValue();
          if (value == REMOVED) {
            throw new NoSuchElementException();
          }

          return value;
        }

      } catch (final InterruptedException e) {
        throw new InterruptedExecutionException(e);
      }
    }

    throw new TimeoutException(
        "timeout while waiting for promise resolution [" + timeout + " " + unit + "]");
  }

  @SuppressWarnings("unchecked")
  public V getValueOr(final V other, final long timeout, @NotNull final TimeUnit unit) {
    deadLockWarning(timeout);
    final ChainHead<?> head = mHead;
    synchronized (mMutex) {
      try {
        if (TimeUtils.waitUntil(mMutex, new Condition() {

          public boolean isTrue() {
            checkBound();
            return head.getState().isResolved();
          }
        }, timeout, unit)) {
          final V value = (V) head.getValue();
          if (value == REMOVED) {
            throw new NoSuchElementException();
          }

          return value;
        }

      } catch (final InterruptedException e) {
        throw new InterruptedExecutionException(e);
      }
    }

    return other;
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public Promise<PromiseInspection<V>> inspect() {
    return then((InspectFulfill<V>) sInspectFulfill, (InspectReject<V>) sInspectReject);
  }

  @NotNull
  public <E> BlockingIterator<PromiseInspection<E>> inspectIterator() {
    return inspectIterator(-1, TimeUnit.MILLISECONDS);
  }

  @NotNull
  public <E> BlockingIterator<PromiseInspection<E>> inspectIterator(final long timeout,
      @NotNull final TimeUnit unit) {
    return new InspectIterator<E>(this.<E>iterator(timeout, unit));
  }

  public boolean isChained() {
    synchronized (mMutex) {
      return (mChain != null);
    }
  }

  @NotNull
  public <E> BlockingIterator<E> iterator() {
    return iterator(-1, TimeUnit.MILLISECONDS);
  }

  @NotNull
  public <E> BlockingIterator<E> iterator(final long timeout, @NotNull final TimeUnit unit) {
    return new ValueIterator<E>(timeout, unit);
  }

  @NotNull
  public <E> Promise<Iterable<E>> onEachFulfill(@NotNull final Observer<E> observer) {
    return spread().onEachFulfill(observer);
  }

  @NotNull
  public <E> Promise<Iterable<E>> onEachReject(@NotNull final Observer<Throwable> observer) {
    return spread().onEachReject(observer);
  }

  @NotNull
  public Promise<V> onFulfill(@NotNull final Observer<? super V> observer) {
    return then(new FulfillHandler<V>(observer), null);
  }

  @NotNull
  public Promise<V> onReject(@NotNull final Observer<? super Throwable> observer) {
    return then(null, new RejectHandler<V>(observer));
  }

  @NotNull
  public Promise<V> onResolve(@NotNull final Action action) {
    final ResolveObserver observer = new ResolveObserver(action);
    return then(new FulfillHandler<V>(observer), new RejectHandler<V>(observer));
  }

  @NotNull
  public Promise<V> ordered() {
    return new ValuePromise<V>(mObserver, mExecutor, true, mIsTrying, mLogger, mHead, mTail,
        new PromiseChainThen<Object, V>(null, null));
  }

  @NotNull
  public <E, R, S> Promise<Iterable<R>> reduce(
      @NotNull final ReductionHandler<E, R, S, ? super IterableCallback<R>> handler) {
    return spread().reduce(handler);
  }

  @NotNull
  public <E, R, S> Promise<Iterable<R>> reduce(
      @Nullable final Mapper<? super IterableCallback<R>, S> create,
      @Nullable final ReductionFulfill<E, R, S, ? super IterableCallback<R>> fulfill,
      @Nullable final ReductionReject<R, S, ? super IterableCallback<R>> reject,
      @Nullable final ReductionResolve<R, S, ? super IterableCallback<R>> resolve) {
    return spread().reduce(create, fulfill, reject, resolve);
  }

  @NotNull
  public <E, R, S> Promise<Iterable<R>> reduceTrying(
      @NotNull final ReductionHandler<E, R, S, ? super IterableCallback<R>> handler) {
    return spread().reduceTrying(handler);
  }

  @NotNull
  public <E, R, S> Promise<Iterable<R>> reduceTrying(
      @Nullable final Mapper<? super IterableCallback<R>, S> create,
      @Nullable final ReductionFulfill<E, R, S, ? super IterableCallback<R>> fulfill,
      @Nullable final ReductionReject<R, S, ? super IterableCallback<R>> reject,
      @Nullable final ReductionResolve<R, S, ? super IterableCallback<R>> resolve) {
    return spread().reduceTrying(create, fulfill, reject, resolve);
  }

  @NotNull
  public Promise<V> renew() {
    return copy();
  }

  @NotNull
  public <R> Promise<R> then(@Nullable final CallbackHandler<V, R, ? super Callback<R>> fulfill,
      @Nullable final CallbackHandler<Throwable, R, ? super Callback<R>> reject) {
    return chain(mIsTrying ? new PromiseChainThenTrying<V, R>(fulfill, reject)
        : new PromiseChainThen<V, R>(fulfill, reject));
  }

  @NotNull
  public Promise<V> thenCatch(@NotNull final Mapper<Throwable, V> mapper) {
    return then(null, new MapperRejectHandler<V>(mapper));
  }

  @NotNull
  public <R> Promise<R> thenMap(@NotNull final Mapper<V, R> mapper) {
    return then(new MapperFulfillHandler<V, R>(mapper), null);
  }

  @NotNull
  public Promise<V> thenSchedule(@NotNull final ScheduledExecutor executor) {
    return chain(mIsTrying ? new PromiseChainThenTrying<V, V>(new ScheduleFulfill<V>(executor),
        new ScheduleReject<V>(executor))
        : new PromiseChainThen<V, V>(new ScheduleFulfill<V>(executor),
            new ScheduleReject<V>(executor)), mExecutor, executor);
  }

  @NotNull
  public <R> Promise<Iterable<R>> thenSpread(
      @Nullable final CallbackHandler<V, R, ? super IterableCallback<R>> fulfill,
      @Nullable final CallbackHandler<Throwable, R, ? super IterableCallback<R>> reject) {
    final OpenPromise<R, R> openPromise = null;
    then(new SpreadFulfill<V, R>(openPromise, fulfill),
        new SpreadReject<V, R>(openPromise, reject));
    return openPromise;
  }

  @NotNull
  public Promise<V> trying() {
    return new ValuePromise<V>(mObserver, mExecutor, mIsTrying, true, mLogger, mHead, mTail,
        new PromiseChainThen<Object, V>(null, null));
  }

  public void waitDone() {
    waitDone(-1, TimeUnit.MILLISECONDS);
  }

  public boolean waitDone(final long timeout, @NotNull final TimeUnit unit) {
    deadLockWarning(timeout);
    synchronized (mMutex) {
      try {
        if (TimeUtils.waitUntil(mMutex, new Condition() {

          public boolean isTrue() {
            return (mState.isResolved() || mHead.getState().isResolved());
          }
        }, timeout, unit)) {
          return true;
        }

      } catch (final InterruptedException e) {
        throw new InterruptedExecutionException(e);
      }
    }

    return false;
  }

  @NotNull
  public <R, S> Promise<R> whenChained(
      @NotNull final ChainHandler<V, R, S, ? super Callback<R>> handler) {
    return null;
  }

  @NotNull
  public <R, S> Promise<R> whenChained(@Nullable final Mapper<? super Promise<V>, S> create,
      @Nullable final ChainHandle<V, S> handle,
      @Nullable final ChainThen<V, R, S, ? super Callback<R>> then) {
    return null;
  }

  @NotNull
  public <R, S> Promise<Iterable<R>> whenEachChained(
      @NotNull final ChainHandler<V, R, S, ? super IterableCallback<R>> handler) {
    return null;
  }

  @NotNull
  public <R, S> Promise<Iterable<R>> whenEachChained(
      @Nullable final Mapper<? super Promise<V>, S> create,
      @Nullable final ChainHandle<V, S> handle,
      @Nullable final ChainThen<V, R, S, ? super IterableCallback<R>> then) {
    return null;
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public <R> Promise<R> wrap(
      @NotNull final Mapper<? super Promise<?>, ? extends Promise<?>> mapper) {
    return new MappedPromise<R>(mapper,
        this.wrapOnce((Mapper<? super Promise<? super V>, ? extends Promise<R>>) mapper));
  }

  @NotNull
  public <R> Promise<R> wrapOnce(
      @NotNull final Mapper<? super Promise<V>, ? extends Promise<R>> mapper) {
    try {
      return mapper.apply(this);

    } catch (final Exception e) {
      throw RejectionException.wrapIfNotRejectionException(e);
    }
  }

  public boolean cancel(final boolean mayInterruptIfRunning) {
    return cancel();
  }

  public boolean isCancelled() {
    // TODO: 06/12/2017 implement...???
    return isRejected() && (reason() instanceof CancellationException);
  }

  public boolean isDone() {
    return isResolved();
  }

  public V get() throws InterruptedException, ExecutionException {
    try {
      return getValue();

    } catch (final InterruptedExecutionException e) {
      final InterruptedException cause = (InterruptedException) e.getCause();
      throw (cause != null) ? cause : new InterruptedException();

    } catch (final CancellationException e) {
      throw new java.util.concurrent.CancellationException(e.getMessage());

    } catch (final RejectionException e) {
      throw new ExecutionException(e);
    }
  }

  public V get(final long timeout, @NotNull final TimeUnit unit) throws InterruptedException,
      ExecutionException, java.util.concurrent.TimeoutException {
    try {
      return getValue(timeout, unit);

    } catch (final InterruptedExecutionException e) {
      final InterruptedException cause = (InterruptedException) e.getCause();
      throw (cause != null) ? cause : new InterruptedException();

    } catch (final CancellationException e) {
      throw new java.util.concurrent.CancellationException(e.getMessage());

    } catch (final RejectionException e) {
      throw new ExecutionException(e);

    } catch (final TimeoutException e) {
      throw new java.util.concurrent.TimeoutException(e.getMessage());
    }
  }

  @NotNull
  private <R> Promise<R> chain(@NotNull final PromiseChain<V, R> chain) {
    return chain(chain, mExecutor, mExecutor);
  }

  @NotNull
  private <R> Promise<R> chain(@NotNull final PromiseChain<V, R> chain,
      @NotNull final ScheduledExecutor chainExecutor,
      @NotNull final ScheduledExecutor newExecutor) {
    final ChainHead<?> head = mHead;
    final Logger logger = mLogger;
    final boolean isBound;
    final Runnable binding;
    synchronized (mMutex) {
      if (mChain != null) {
        isBound = true;
        binding = null;

      } else {
        isBound = false;
        chain.setLogger(logger);
        binding = head.bind(chain, chainExecutor);
        mChain = chain;
        mMutex.notifyAll();
      }
    }

    if (isBound) {
      return copy().chain(chain); // TODO: 06/12/2017 fail or notify

    }

    final ValuePromise<R> promise =
        new ValuePromise<R>(mObserver, newExecutor, mIsOrdered, mIsTrying, logger, head, mTail,
            chain);
    if (binding != null) {
      binding.run();
    }

    return promise;
  }

  private void checkBound() {
    if (mChain != null) {
      throw new IllegalStateException("the promise has been bound");
    }
  }

  @SuppressWarnings("unchecked")
  private ValuePromise<V> copy() {
    final Logger logger = mLogger;
    final ChainHead<?> head = mHead;
    final ChainHead<?> newHead = head.copy();
    newHead.setLogger(logger);
    PromiseChain<?, ?> newTail = newHead;
    PromiseChain<?, ?> next = head;
    while (next != mTail) {
      next = next.mNext;
      PromiseChain<?, ?> chain = next.copy();
      chain.setLogger(logger);
      ((PromiseChain<?, Object>) newTail).setNext((PromiseChain<Object, ?>) chain);
      newTail = chain;
    }

    return new ValuePromise<V>(mObserver, mExecutor, mIsOrdered, mIsTrying, logger, newHead,
        (PromiseChain<?, V>) newTail);
  }

  private void deadLockWarning(final long waitTime) {
    if (waitTime == 0) {
      return;
    }

    final Logger logger = mLogger;
    if ((logger.getLogLevel().compareTo(Level.WARNING) <= 0) && ThreadUtils.isOwnedThread()) {
      logger.wrn("ATTENTION: possible deadlock detected! Try to avoid waiting on managed threads");
    }
  }

  @NotNull
  @SuppressWarnings("unchecked")
  private <E> Promise<Iterable<E>> spread() {
    return thenSpread((CallbackHandler<V, E, ? super IterableCallback<E>>) sSpreadFulfill, null);
  }

  private Object writeReplace() throws ObjectStreamException {
    final ArrayList<PromiseChain<?, ?>> chains = new ArrayList<PromiseChain<?, ?>>();
    final ChainHead<?> head = mHead;
    PromiseChain<?, ?> chain = head;
    while (chain != mTail) {
      if (chain != head) {
        chains.add(chain);
      }

      chain = chain.mNext;
    }

    if (chain != head) {
      chains.add(chain);
    }

    final Logger logger = mLogger;
    return new PromiseProxy(mObserver, mExecutor, mIsOrdered, mIsTrying, logger.getLog(),
        logger.getLogLevel(), chains);
  }

  private enum PromiseState {
    Pending(false), Fulfilled(true), Rejected(true);

    private final boolean mIsResolved;

    PromiseState(final boolean isResolved) {
      mIsResolved = isResolved;
    }

    public boolean isResolved() {
      return mIsResolved;
    }
  }

  private static class ChainHead<V> extends PromiseChain<V, V> {

    private final Object mMutex = new Object();

    private Throwable mException;

    private StatePending mInnerState = new StatePending();

    private PromiseState mState = PromiseState.Pending;

    private Object mValue;

    @Nullable
    Runnable bind(@NotNull final PromiseChain<?, ?> chain,
        @NotNull final ScheduledExecutor executor) {
      final Runnable binding = mInnerState.bind(chain, executor);
      mState = PromiseState.Pending;
      return binding;
    }

    @NotNull
    ChainHead<V> copy() {
      return new ChainHead<V>();
    }

    @Nullable
    RejectionException getException() {
      return mInnerState.getException();
    }

    @NotNull
    Object getMutex() {
      return mMutex;
    }

    @NotNull
    PromiseState getState() {
      return mState;
    }

    Object getValue() {
      return mInnerState.getValue();
    }

    void innerFulfill(final Object value) {
      mInnerState.innerFulfill(value);
      mState = PromiseState.Fulfilled;
    }

    void innerReject(@Nullable final Throwable reason) {
      mInnerState.innerReject(reason);
      mState = PromiseState.Rejected;
    }

    void removeValue() {
      mInnerState.removeValue();
    }

    private class StateFulfilled extends StatePending {

      @Nullable
      @Override
      Runnable bind(@NotNull final PromiseChain<?, ?> chain,
          @NotNull final ScheduledExecutor executor) {
        getLogger().dbg("Binding promise [%s => %s]", PromiseState.Fulfilled, PromiseState.Pending);
        final Object value = mValue;
        mValue = null;
        mInnerState = new StatePending();
        return new Runnable() {

          public void run() {
            executor.execute(new Runnable() {

              @SuppressWarnings("unchecked")
              public void run() {
                ((PromiseChain<Object, ?>) chain).fulfill(value);
              }
            });
          }
        };
      }

      @Override
      Object getValue() {
        return mValue;
      }

      @Override
      void innerReject(@Nullable final Throwable reason) {
        throw exception(PromiseState.Fulfilled);
      }

      @Override
      void innerFulfill(final Object value) {
        throw exception(PromiseState.Fulfilled);
      }

      @Override
      void removeValue() {
        mValue = REMOVED;
      }
    }

    private class StatePending {

      @Nullable
      Runnable bind(@NotNull final PromiseChain<?, ?> chain,
          @NotNull final ScheduledExecutor executor) {
        return null;
      }

      @NotNull
      IllegalStateException exception(@NotNull final PromiseState state) {
        return new IllegalStateException("invalid state: " + state);
      }

      @Nullable
      RejectionException getException() {
        return null;
      }

      Object getValue() {
        throw exception(PromiseState.Pending);
      }

      void innerFulfill(final Object value) {
        getLogger().dbg("Fulfilling promise with value [%s => %s]: %s", PromiseState.Pending,
            PromiseState.Fulfilled, value);
        mValue = value;
        mInnerState = new StateFulfilled();
      }

      void innerReject(@Nullable final Throwable reason) {
        getLogger().dbg("Rejecting promise with reason [%s => %s]: %s", PromiseState.Pending,
            PromiseState.Rejected, reason);
        mException = reason;
        mInnerState = new StateRejected();
      }

      void removeValue() {
        throw exception(PromiseState.Pending);
      }
    }

    private class StateRejected extends StatePending {

      @Nullable
      @Override
      Runnable bind(@NotNull final PromiseChain<?, ?> chain,
          @NotNull final ScheduledExecutor executor) {
        getLogger().dbg("Binding promise [%s => %s]", PromiseState.Rejected, PromiseState.Pending);
        final Throwable exception = mException;
        mException = null;
        mInnerState = new StatePending();
        return new Runnable() {

          public void run() {
            executor.execute(new Runnable() {

              public void run() {
                chain.reject(exception);
              }
            });
          }
        };
      }

      @Nullable
      @Override
      RejectionException getException() {
        return RejectionException.wrapIfNotRejectionException(mException);
      }

      @Override
      void innerReject(@Nullable final Throwable reason) {
        throw exception(PromiseState.Rejected);
      }

      @Override
      void innerFulfill(final Object value) {
        throw exception(PromiseState.Rejected);
      }

      @Override
      void removeValue() {
        mInnerState = new StateFulfilled();
        mValue = REMOVED;
      }
    }

    @Override
    public void fulfill(final PromiseChain<V, ?> next, final V value) {
      next.fulfill(value);
    }

    @Override
    public void reject(final PromiseChain<V, ?> next, final Throwable reason) {
      next.reject(reason);
    }
  }

  private static class DefaultFulfillHandler<V, R>
      implements CallbackHandler<V, R, Callback<R>>, Serializable {

    Object readResolve() throws ObjectStreamException {
      return sDefaultFulfillHandler;
    }

    @SuppressWarnings("unchecked")
    public void accept(final V value, @NotNull final Callback<R> callback) throws Exception {
      callback.fulfill((R) value);
    }

  }

  private static class DefaultRejectHandler<R>
      implements CallbackHandler<Throwable, R, Callback<R>>, Serializable {

    Object readResolve() throws ObjectStreamException {
      return sDefaultRejectHandler;
    }

    public void accept(final Throwable value, @NotNull final Callback<R> callback) throws
        Exception {
      callback.reject(value);
    }
  }

  private static class FulfillHandler<V>
      implements CallbackHandler<V, V, Callback<V>>, Serializable {

    private final Observer<? super V> mObserver;

    private FulfillHandler(@NotNull final Observer<? super V> observer) {
      mObserver = ConstantConditions.notNull("observer", observer);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new HandlerProxy<V>(mObserver);
    }

    private static class HandlerProxy<V> extends SerializableProxy {

      private HandlerProxy(final Observer<? super V> observer) {
        super(proxy(observer));
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new FulfillHandler<V>((Observer<? super V>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void accept(final V value, @NotNull final Callback<V> callback) throws Exception {
      mObserver.accept(value);
      callback.fulfill(value);
    }

  }

  private static class InspectFulfill<V>
      implements CallbackHandler<V, PromiseInspection<V>, Callback<PromiseInspection<V>>>,
      Serializable {

    Object readResolve() throws ObjectStreamException {
      return sInspectFulfill;
    }

    public void accept(final V value, @NotNull final Callback<PromiseInspection<V>> callback) throws
        Exception {
      callback.fulfill(new PromiseInspectionFulfilled<V>(value));
    }
  }

  private static class InspectReject<V>
      implements CallbackHandler<Throwable, PromiseInspection<V>, Callback<PromiseInspection<V>>>,
      Serializable {

    Object readResolve() throws ObjectStreamException {
      return sInspectReject;
    }

    public void accept(final Throwable value,
        @NotNull final Callback<PromiseInspection<V>> callback) throws Exception {
      callback.fulfill(new PromiseInspectionRejected<V>(value));
    }
  }

  private static class MapperFulfillHandler<V, R>
      implements CallbackHandler<V, R, Callback<R>>, Serializable {

    private final Mapper<? super V, ? extends R> mMapper;

    private MapperFulfillHandler(@NotNull final Mapper<? super V, ? extends R> mapper) {
      mMapper = ConstantConditions.notNull("mapper", mapper);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new HandlerProxy<V, R>(mMapper);
    }

    private static class HandlerProxy<V, R> extends SerializableProxy {

      private HandlerProxy(Mapper<? super V, ? extends R> mapper) {
        super(proxy(mapper));
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new MapperFulfillHandler<V, R>((Mapper<? super V, ? extends R>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void accept(final V value, @NotNull final Callback<R> callback) throws Exception {
      callback.fulfill(mMapper.apply(value));
    }
  }

  private static class MapperRejectHandler<V>
      implements CallbackHandler<Throwable, V, Callback<V>>, Serializable {

    private final Mapper<? super Throwable, ? extends V> mMapper;

    private MapperRejectHandler(@NotNull final Mapper<? super Throwable, ? extends V> mapper) {
      mMapper = ConstantConditions.notNull("mapper", mapper);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new HandlerProxy<V>(mMapper);
    }

    private static class HandlerProxy<V> extends SerializableProxy {

      private HandlerProxy(Mapper<? super Throwable, ? extends V> mapper) {
        super(proxy(mapper));
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new MapperRejectHandler<V>((Mapper<? super Throwable, ? extends V>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void accept(final Throwable value, @NotNull final Callback<V> callback) throws
        Exception {
      callback.fulfill(mMapper.apply(value));
    }
  }

  private static abstract class PromiseChain<V, R> implements Callback<V>, Serializable {

    private transient volatile StatePending mInnerState = new StatePending();

    private transient Logger mLogger;

    private transient volatile PromiseChain<R, ?> mNext;

    public final void fulfill(final V value) {
      mInnerState.fulfill();
      fulfill(mNext, value);
    }

    public final void reject(final Throwable reason) {
      if (mInnerState.reject(reason)) {
        reject(mNext, reason);
      }
    }

    @SuppressWarnings("unchecked")
    public final void resolve(@NotNull final Thenable<? extends V> thenable) {
      mInnerState.fulfill();
      ((Thenable<V>) thenable).then(new CallbackHandler<V, Void, Callback<Void>>() {

        public void accept(final V value, @NotNull final Callback<Void> callback) throws Exception {
          fulfill(mNext, value);
        }
      }, new CallbackHandler<Throwable, Void, Callback<Void>>() {

        public void accept(final Throwable reason, @NotNull final Callback<Void> callback) throws
            Exception {
          PromiseChain.this.reject(mNext, reason);
        }
      });
    }

    boolean cancel(@Nullable final Throwable reason) {
      if (mInnerState.reject(reason)) {
        reject(mNext, reason);
        return true;
      }

      return false;
    }

    @NotNull
    abstract PromiseChain<V, R> copy();

    abstract void fulfill(PromiseChain<R, ?> next, V value);

    Logger getLogger() {
      return mLogger;
    }

    void setLogger(@NotNull final Logger logger) {
      mLogger = logger.subContextLogger(this);
    }

    boolean isTail() {
      return false;
    }

    abstract void reject(PromiseChain<R, ?> next, Throwable reason);

    void setNext(@NotNull PromiseChain<R, ?> next) {
      mNext = next;
    }

    private class StateFulfilled extends StatePending {

      @Override
      void fulfill() {
        mLogger.wrn("Promise has been already resolved");
        throw new IllegalStateException("promise has been already resolved");
      }

      @Override
      boolean reject(final Throwable reason) {
        mLogger.wrn(reason, "Suppressed rejection");
        return false;
      }
    }

    private class StatePending {

      void fulfill() {
        mInnerState = new StateFulfilled();
      }

      boolean reject(final Throwable reason) {
        mInnerState = new StateRejected(reason);
        return true;
      }
    }

    private class StateRejected extends StatePending {

      private final Throwable mReason;

      private StateRejected(final Throwable reason) {
        mReason = reason;
      }

      @Override
      boolean reject(final Throwable reason) {
        mLogger.wrn(reason, "Suppressed rejection");
        return false;
      }

      @Override
      void fulfill() {
        final Throwable reason = mReason;
        mLogger.wrn("Promise has been already rejected with reason: %s", reason);
        throw RejectionException.wrapIfNotRejectionException(reason);
      }
    }
  }

  private static class PromiseChainThen<V, R> extends PromiseChain<V, R> {

    private final CallbackHandler<? super V, R, ? super Callback<R>> mFulfill;

    private final CallbackHandler<? super Throwable, R, ? super Callback<R>> mReject;

    @SuppressWarnings("unchecked")
    private PromiseChainThen(
        @Nullable final CallbackHandler<? super V, R, ? super Callback<R>> fulfill,
        @Nullable final CallbackHandler<? super Throwable, R, ? super Callback<R>> reject) {
      mFulfill = (CallbackHandler<? super V, R, ? super Callback<R>>) ((fulfill != null) ? fulfill
          : defaultFulfillHandler());
      mReject =
          (CallbackHandler<? super Throwable, R, ? super Callback<R>>) ((reject != null) ? reject
              : defaultRejectHandler());
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ChainProxy<V, R>(mFulfill, mReject);
    }

    private static class ChainProxy<V, R> extends SerializableProxy {

      private ChainProxy(CallbackHandler<? super V, R, ? super Callback<R>> fulfill,
          CallbackHandler<? super Throwable, R, ? super Callback<R>> reject) {
        super(proxy(fulfill), proxy(reject));
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new PromiseChainThen<V, R>((CallbackHandler<V, R, ? super Callback<R>>) args[0],
              (CallbackHandler<Throwable, R, ? super Callback<R>>) args[1]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    @NotNull
    PromiseChain<V, R> copy() {
      return new PromiseChainThen<V, R>(mFulfill, mReject);
    }

    @Override
    void reject(final PromiseChain<R, ?> next, final Throwable reason) {
      try {
        getLogger().dbg("Processing rejection with reason: %s", reason);
        mReject.accept(reason, next);

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Promise has been cancelled");
        next.reject(e);

      } catch (final Throwable t) {
        InterruptedExecutionException.throwIfInterrupt(t);
        getLogger().err(t, "Error while processing rejection with reason: %s", reason);
        next.reject(t);
      }
    }

    @Override
    void fulfill(final PromiseChain<R, ?> next, final V value) {
      try {
        getLogger().dbg("Processing fulfillment with value: %s", value);
        mFulfill.accept(value, next);

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Promise has been cancelled");
        next.reject(e);

      } catch (final Throwable t) {
        InterruptedExecutionException.throwIfInterrupt(t);
        getLogger().err(t, "Error while processing fulfillment: %s", value);
        next.reject(t);
      }
    }
  }

  private static class PromiseChainThenTrying<V, R> extends PromiseChain<V, R> {

    private final CallbackHandler<? super V, R, ? super Callback<R>> mFulfill;

    private final CallbackHandler<? super Throwable, R, ? super Callback<R>> mReject;

    @SuppressWarnings("unchecked")
    private PromiseChainThenTrying(
        @Nullable final CallbackHandler<? super V, R, ? super Callback<R>> fulfill,
        @Nullable final CallbackHandler<? super Throwable, R, ? super Callback<R>> reject) {
      mFulfill = (CallbackHandler<? super V, R, ? super Callback<R>>) ((fulfill != null) ? fulfill
          : defaultFulfillHandler());
      mReject =
          (CallbackHandler<? super Throwable, R, ? super Callback<R>>) ((reject != null) ? reject
              : defaultRejectHandler());
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ChainProxy<V, R>(mFulfill, mReject);
    }

    private static class ChainProxy<V, R> extends SerializableProxy {

      private ChainProxy(CallbackHandler<? super V, R, ? super Callback<R>> fulfill,
          CallbackHandler<? super Throwable, R, ? super Callback<R>> reject) {
        super(proxy(fulfill), proxy(reject));
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new PromiseChainThenTrying<V, R>(
              (CallbackHandler<V, R, ? super Callback<R>>) args[0],
              (CallbackHandler<Throwable, R, ? super Callback<R>>) args[1]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    @NotNull
    PromiseChain<V, R> copy() {
      return new PromiseChainThenTrying<V, R>(mFulfill, mReject);
    }

    @Override
    void reject(final PromiseChain<R, ?> next, final Throwable reason) {
      try {
        getLogger().dbg("Processing rejection with reason: %s", reason);
        mReject.accept(reason, next);

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Promise has been cancelled");
        next.reject(e);

      } catch (final Throwable t) {
        InterruptedExecutionException.throwIfInterrupt(t);
        getLogger().err(t, "Error while processing rejection with reason: %s", reason);
        next.reject(t);
      }
    }

    @Override
    void fulfill(final PromiseChain<R, ?> next, final V value) {
      try {
        getLogger().dbg("Processing fulfillment with value: %s", value);
        mFulfill.accept(value, next);

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Promise has been cancelled");
        next.reject(e);

      } catch (final Throwable t) {
        InterruptedExecutionException.throwIfInterrupt(t);
        getLogger().err(t, "Error while processing fulfillment: %s", value);
        next.reject(t);

      } finally {
        close(value, getLogger());
      }
    }
  }

  private static class PromiseInspectionFulfilled<V> implements PromiseInspection<V>, Serializable {

    private final V mValue;

    private PromiseInspectionFulfilled(final V value) {
      mValue = value;
    }

    public boolean isFulfilled() {
      return true;
    }

    public boolean isPending() {
      return false;
    }

    public boolean isRejected() {
      return false;
    }

    public boolean isResolved() {
      return true;
    }

    public Throwable reason() {
      throw new IllegalStateException("the promise is not rejected");
    }

    public V value() {
      return mValue;
    }
  }

  private static class PromiseInspectionRejected<V> implements PromiseInspection<V>, Serializable {

    private final Throwable mReason;

    private PromiseInspectionRejected(final Throwable reason) {
      mReason = reason;
    }

    public boolean isFulfilled() {
      return false;
    }

    public boolean isPending() {
      return false;
    }

    public boolean isRejected() {
      return true;
    }

    public boolean isResolved() {
      return true;
    }

    public Throwable reason() {
      return mReason;
    }

    public V value() {
      throw new IllegalStateException("the promise is not fulfilled");
    }
  }

  private static class PromiseIterable<E> implements Iterable<E>, Serializable {

    private final ValuePromise<?> mPromise;

    private PromiseIterable(@NotNull final ValuePromise<?> promise) {
      mPromise = promise;
    }

    @NotNull
    public Iterator<E> iterator() {
      return mPromise.iterator();
    }
  }

  private static class PromiseProxy extends SerializableProxy {

    private PromiseProxy(final Observer<? extends Callback<?>> observer,
        final ScheduledExecutor executor, final boolean isOrdered, final boolean isTrying,
        final Log log, final Level logLevel, final List<PromiseChain<?, ?>> chains) {
      super(proxy(observer), executor, isOrdered, isTrying, log, logLevel, chains);
    }

    @SuppressWarnings("unchecked")
    Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        final ChainHead<Object> head = new ChainHead<Object>();
        PromiseChain<?, ?> tail = head;
        for (final PromiseChain<?, ?> chain : (List<PromiseChain<?, ?>>) args[4]) {
          ((PromiseChain<?, Object>) tail).setNext((PromiseChain<Object, ?>) chain);
          tail = chain;
        }

        return new ValuePromise<Object>((Observer<Callback<?>>) args[0],
            (ScheduledExecutor) args[1], (Boolean) args[2], (Boolean) args[3], (Log) args[4],
            (Level) args[5], head, (PromiseChain<?, Object>) tail);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }

  private static class ReduceFulfill<V, E, R, S>
      implements CallbackHandler<V, R, Callback<R>>, Serializable {

    private final ReductionHandler<E, R, S, ? super Callback<R>> mHandler;

    private ReduceFulfill(@NotNull final ReductionHandler<E, R, S, ? super Callback<R>> handler) {
      mHandler = ConstantConditions.notNull("handler", handler);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new HandlerProxy<V, E, R, S>(mHandler);
    }

    private static class HandlerProxy<V, E, R, S> extends SerializableProxy {

      private HandlerProxy(final ReductionHandler<E, R, S, ? super Callback<R>> handler) {
        super(proxy(handler));
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ReduceFulfill<V, E, R, S>(
              (ReductionHandler<E, R, S, ? super Callback<R>>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    @SuppressWarnings("unchecked")
    public void accept(final V value, @NotNull final Callback<R> callback) throws Exception {
      final ReductionHandler<E, R, S, ? super Callback<R>> handler = mHandler;
      S state = handler.create(callback);
      if (value instanceof Iterable) {
        for (final E element : (Iterable<E>) value) {
          state = handler.fulfill(state, element, callback);
        }

      } else {
        state = handler.fulfill(state, (E) value, callback);
      }

      handler.resolve(state, callback);
    }
  }

  private static class ReduceFulfillTrying<V, E, R, S>
      implements CallbackHandler<V, R, Callback<R>>, Serializable {

    private final ReductionHandler<E, R, S, ? super Callback<R>> mHandler;

    private final Logger mLogger;

    private ReduceFulfillTrying(
        @NotNull final ReductionHandler<E, R, S, ? super Callback<R>> handler,
        @Nullable final Log log, @Nullable final Level level) {
      mHandler = ConstantConditions.notNull("handler", handler);
      mLogger = Logger.newLogger(log, level, this);
    }

    private Object writeReplace() throws ObjectStreamException {
      final Logger logger = mLogger;
      return new HandlerProxy<V, E, R, S>(mHandler, logger.getLog(), logger.getLogLevel());
    }

    private static class HandlerProxy<V, E, R, S> extends SerializableProxy {

      private HandlerProxy(final ReductionHandler<E, R, S, ? super Callback<R>> handler,
          final Log log, final Level level) {
        super(proxy(handler), log, level);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ReduceFulfillTrying<V, E, R, S>(
              (ReductionHandler<E, R, S, ? super Callback<R>>) args[0], (Log) args[1],
              (Level) args[2]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    @SuppressWarnings("unchecked")
    public void accept(final V value, @NotNull final Callback<R> callback) throws Exception {
      final ReductionHandler<E, R, S, ? super Callback<R>> handler = mHandler;
      S state = handler.create(callback);
      try {
        if (value instanceof Iterable) {
          for (final E element : (Iterable<E>) value) {
            state = handler.fulfill(state, element, callback);
          }

        } else {
          state = handler.fulfill(state, (E) value, callback);
        }

        handler.resolve(state, callback);

      } finally {
        close(state, mLogger);
      }
    }
  }

  private static class ReduceReject<E, R, S>
      implements CallbackHandler<Throwable, R, Callback<R>>, Serializable {

    private final ReductionHandler<E, R, S, ? super Callback<R>> mHandler;

    private ReduceReject(@NotNull final ReductionHandler<E, R, S, ? super Callback<R>> handler) {
      mHandler = ConstantConditions.notNull("handler", handler);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new HandlerProxy<E, R, S>(mHandler);
    }

    private static class HandlerProxy<E, R, S> extends SerializableProxy {

      private HandlerProxy(final ReductionHandler<E, R, S, ? super Callback<R>> handler) {
        super(proxy(handler));
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ReduceReject<E, R, S>(
              (ReductionHandler<E, R, S, ? super Callback<R>>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    @SuppressWarnings("unchecked")
    public void accept(final Throwable value, @NotNull final Callback<R> callback) throws
        Exception {
      final ReductionHandler<E, R, S, ? super Callback<R>> handler = mHandler;
      S state = handler.create(callback);
      state = handler.reject(state, value, callback);
      handler.resolve(state, callback);
    }
  }

  private static class ReduceRejectTrying<E, R, S>
      implements CallbackHandler<Throwable, R, Callback<R>>, Serializable {

    private final ReductionHandler<E, R, S, ? super Callback<R>> mHandler;

    private final Logger mLogger;

    private ReduceRejectTrying(
        @NotNull final ReductionHandler<E, R, S, ? super Callback<R>> handler,
        @Nullable final Log log, @Nullable final Level level) {
      mHandler = ConstantConditions.notNull("handler", handler);
      mLogger = Logger.newLogger(log, level, this);
    }

    private Object writeReplace() throws ObjectStreamException {
      final Logger logger = mLogger;
      return new HandlerProxy<E, R, S>(mHandler, logger.getLog(), logger.getLogLevel());
    }

    private static class HandlerProxy<E, R, S> extends SerializableProxy {

      private HandlerProxy(final ReductionHandler<E, R, S, ? super Callback<R>> handler,
          final Log log, final Level level) {
        super(proxy(handler), log, level);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ReduceRejectTrying<E, R, S>(
              (ReductionHandler<E, R, S, ? super Callback<R>>) args[0], (Log) args[1],
              (Level) args[2]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    @SuppressWarnings("unchecked")
    public void accept(final Throwable value, @NotNull final Callback<R> callback) throws
        Exception {
      final ReductionHandler<E, R, S, ? super Callback<R>> handler = mHandler;
      S state = handler.create(callback);
      try {
        state = handler.reject(state, value, callback);
        handler.resolve(state, callback);

      } finally {
        close(state, mLogger);
      }
    }
  }

  private static class RejectHandler<V>
      implements CallbackHandler<Throwable, V, Callback<V>>, Serializable {

    private final Observer<? super Throwable> mObserver;

    private RejectHandler(@NotNull final Observer<? super Throwable> observer) {
      mObserver = ConstantConditions.notNull("observer", observer);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new HandlerProxy<V>(mObserver);
    }

    private static class HandlerProxy<V> extends SerializableProxy {

      private HandlerProxy(final Observer<? super Throwable> observer) {
        super(proxy(observer));
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new RejectHandler<V>((Observer<? super Throwable>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void accept(final Throwable value, @NotNull final Callback<V> callback) throws
        Exception {
      mObserver.accept(value);
      callback.reject(value);
    }
  }

  private static class ResolveObserver implements Observer<Object>, Serializable {

    private final Action mAction;

    private ResolveObserver(@NotNull final Action action) {
      mAction = ConstantConditions.notNull("action", action);
    }

    public void accept(final Object input) throws Exception {
      mAction.perform();
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ObserverProxy(mAction);
    }

    private static class ObserverProxy extends SerializableProxy {

      private ObserverProxy(final Action action) {
        super(proxy(action));
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ResolveObserver((Action) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }
  }

  private static class ScheduleFulfill<V>
      implements CallbackHandler<V, V, Callback<V>>, Serializable {

    private final ScheduledExecutor mExecutor;

    private ScheduleFulfill(@NotNull final ScheduledExecutor executor) {
      mExecutor = ConstantConditions.notNull("executor", executor);
    }

    public void accept(final V value, @NotNull final Callback<V> callback) throws Exception {
      mExecutor.execute(new Runnable() {

        public void run() {
          try {
            callback.fulfill(value);

          } catch (final Throwable t) {
            callback.reject(t);
            InterruptedExecutionException.throwIfInterrupt(t);
          }
        }
      });
    }
  }

  private static class ScheduleReject<V>
      implements CallbackHandler<Throwable, V, Callback<V>>, Serializable {

    private final ScheduledExecutor mExecutor;

    private ScheduleReject(@NotNull final ScheduledExecutor executor) {
      mExecutor = ConstantConditions.notNull("executor", executor);
    }

    public void accept(final Throwable value, @NotNull final Callback<V> callback) throws
        Exception {
      if (value instanceof CancellationException) {
        callback.reject(value);

      } else {
        mExecutor.execute(new Runnable() {

          public void run() {
            callback.reject(value);
          }
        });
      }
    }
  }

  private static class SpreadFulfill<V, R>
      implements CallbackHandler<V, Void, Callback<Void>>, Serializable {

    private final CallbackHandler<V, R, ? super Callback<R>> mFulfill;

    private final OpenPromise<R, ?> mPromise;

    @SuppressWarnings("unchecked")
    private SpreadFulfill(@NotNull final OpenPromise<R, ?> promise,
        @Nullable final CallbackHandler<V, R, ? super IterableCallback<R>> fulfill) {
      mFulfill = (CallbackHandler<V, R, ? super Callback<R>>) ((fulfill != null) ? fulfill
          : defaultFulfillHandler());
      mPromise = promise;
    }

    private Object writeReplace() throws ObjectStreamException {
      return new HandlerProxy<V, R>(mPromise, mFulfill);
    }

    private static class HandlerProxy<V, R> extends SerializableProxy {

      private HandlerProxy(OpenPromise<R, ?> promise,
          CallbackHandler<V, R, ? super IterableCallback<R>> fulfill) {
        super(promise, proxy(fulfill));
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new SpreadFulfill<V, R>((OpenPromise<R, ?>) args[0],
              (CallbackHandler<V, R, ? super IterableCallback<R>>) args[1]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void accept(final V value, @NotNull final Callback<Void> callback) {
      final OpenPromise<R, ?> promise = mPromise;
      try {
        mFulfill.accept(value, promise);

      } catch (final Throwable t) {
        promise.reject(t);
        InterruptedExecutionException.throwIfInterrupt(t);
      }
    }

  }

  private static class SpreadReject<V, R>
      implements CallbackHandler<Throwable, Void, Callback<Void>>, Serializable {

    private final OpenPromise<R, ?> mPromise;

    private final CallbackHandler<Throwable, R, ? super Callback<R>> mReject;

    @SuppressWarnings("unchecked")
    private SpreadReject(@NotNull final OpenPromise<R, ?> promise,
        @Nullable final CallbackHandler<Throwable, R, ? super IterableCallback<R>> reject) {
      mReject = (CallbackHandler<Throwable, R, ? super Callback<R>>) ((reject != null) ? reject
          : defaultRejectHandler());
      mPromise = promise;
    }

    private Object writeReplace() throws ObjectStreamException {
      return new HandlerProxy<V, R>(mPromise, mReject);
    }

    private static class HandlerProxy<V, R> extends SerializableProxy {

      private HandlerProxy(OpenPromise<R, ?> promise,
          CallbackHandler<Throwable, R, ? super IterableCallback<R>> reject) {
        super(promise, proxy(reject));
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new SpreadReject<V, R>((OpenPromise<R, ?>) args[0],
              (CallbackHandler<Throwable, R, ? super IterableCallback<R>>) args[1]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void accept(final Throwable value, @NotNull final Callback<Void> callback) {
      final OpenPromise<R, ?> promise = mPromise;
      try {
        mReject.accept(value, promise);

      } catch (final Throwable t) {
        promise.reject(t);
        InterruptedExecutionException.throwIfInterrupt(t);
      }
    }

  }

  private class ChainTail extends PromiseChain<V, Object> {

    private ChainTail() {
      setLogger(mLogger);
    }

    @Override
    boolean isTail() {
      return true;
    }

    @NotNull
    @Override
    PromiseChain<V, Object> copy() {
      return ConstantConditions.unsupported();
    }

    @Override
    void reject(final PromiseChain<Object, ?> next, final Throwable reason) {
      final PromiseChain<V, ?> chain;
      synchronized (mMutex) {
        try {
          mState = PromiseState.Rejected;
          if ((chain = mChain) == null) {
            mHead.innerReject(reason);
            return;
          }

        } finally {
          mMutex.notifyAll();
        }
      }

      chain.reject(reason);
    }

    @Override
    void fulfill(final PromiseChain<Object, ?> next, final V value) {
      final PromiseChain<V, ?> chain;
      synchronized (mMutex) {
        try {
          mState = PromiseState.Fulfilled;
          if ((chain = mChain) == null) {
            mHead.innerFulfill(value);
            return;
          }

        } finally {
          mMutex.notifyAll();
        }
      }

      chain.fulfill(value);
    }
  }

  private class InspectIterator<E> implements BlockingIterator<PromiseInspection<E>> {

    private final BlockingIterator<E> mIterator;

    private InspectIterator(@NotNull final BlockingIterator<E> iterator) {
      mIterator = iterator;
    }

    public boolean hasNext() {
      try {
        return mIterator.hasNext();

      } catch (final RejectionException ignored) {
        return true;
      }
    }

    public List<PromiseInspection<E>> next(final int maxSize) {
      final ArrayList<PromiseInspection<E>> inspections = new ArrayList<PromiseInspection<E>>();
      try {
        final List<E> values = mIterator.next(maxSize);
        for (final E value : values) {
          inspections.add(new PromiseInspectionFulfilled<E>(value));
        }

      } catch (final RejectionException e) {
        inspections.add(new PromiseInspectionRejected<E>(e.getCause()));
      }

      return inspections;
    }

    public List<PromiseInspection<E>> nextAll() {
      final ArrayList<PromiseInspection<E>> inspections = new ArrayList<PromiseInspection<E>>();
      try {
        final List<E> values = mIterator.nextAll();
        for (final E value : values) {
          inspections.add(new PromiseInspectionFulfilled<E>(value));
        }

      } catch (final RejectionException e) {
        inspections.add(new PromiseInspectionRejected<E>(e.getCause()));
      }

      return inspections;
    }

    public PromiseInspection<E> nextOr(final PromiseInspection<E> defaultValue) {
      try {
        return new PromiseInspectionFulfilled<E>(mIterator.next());

      } catch (final TimeoutException e) {
        return defaultValue;

      } catch (final RejectionException e) {
        return new PromiseInspectionRejected<E>(e.getCause());
      }
    }

    public List<PromiseInspection<E>> take(final int maxSize) {
      final ArrayList<PromiseInspection<E>> inspections = new ArrayList<PromiseInspection<E>>();
      try {
        final List<E> values = mIterator.take(maxSize);
        for (final E value : values) {
          inspections.add(new PromiseInspectionFulfilled<E>(value));
        }

      } catch (final RejectionException e) {
        inspections.add(new PromiseInspectionRejected<E>(e.getCause()));
      }

      return inspections;
    }

    public PromiseInspection<E> take() {
      try {
        return new PromiseInspectionFulfilled<E>(mIterator.take());

      } catch (final RejectionException e) {
        return new PromiseInspectionRejected<E>(e.getCause());
      }
    }

    public List<PromiseInspection<E>> takeAll() {
      final ArrayList<PromiseInspection<E>> inspections = new ArrayList<PromiseInspection<E>>();
      try {
        final List<E> values = mIterator.takeAll();
        for (final E value : values) {
          inspections.add(new PromiseInspectionFulfilled<E>(value));
        }

      } catch (final RejectionException e) {
        inspections.add(new PromiseInspectionRejected<E>(e.getCause()));
      }

      return inspections;
    }

    public PromiseInspection<E> takeOr(final PromiseInspection<E> defaultValue) {
      try {
        return new PromiseInspectionFulfilled<E>(mIterator.take());

      } catch (final TimeoutException e) {
        return defaultValue;

      } catch (final RejectionException e) {
        return new PromiseInspectionRejected<E>(e.getCause());
      }
    }

    public PromiseInspection<E> next() {
      try {
        return new PromiseInspectionFulfilled<E>(mIterator.next());

      } catch (final RejectionException e) {
        return new PromiseInspectionRejected<E>(e.getCause());
      }
    }
  }

  private class ValueIterator<E> implements BlockingIterator<E> {

    private final TimeUnit mOriginalTimeUnit;

    private final long mOriginalTimeout;

    private final TimeUnit mTimeUnit;

    private final long mTimeout;

    private Iterator<E> mIterator;

    private ValueIterator(final long timeout, @NotNull final TimeUnit timeUnit) {
      mOriginalTimeout = timeout;
      mOriginalTimeUnit = timeUnit;
      if (timeout >= 0) {
        mTimeUnit = ((timeUnit.toNanos(timeout) % TimeUnit.MILLISECONDS.toNanos(1)) == 0)
            ? TimeUnit.MILLISECONDS : TimeUnit.NANOSECONDS;
        mTimeout = mTimeUnit.convert(timeout, timeUnit) + TimeUtils.currentTimeIn(mTimeUnit);

      } else {
        mTimeUnit = TimeUnit.MILLISECONDS;
        mTimeout = -1;
      }
    }

    @SuppressWarnings("unchecked")
    private Iterator<E> getIterator(final boolean timeout) {
      synchronized (mMutex) {
        if (mIterator == null) {
          final long time = remainingTime();
          try {
            final V value = getValue(time, TimeUnit.MILLISECONDS);
            if (value == REMOVED) {
              return Collections.emptyIterator();
            }

            mIterator =
                (value instanceof Iterable) ? ((Iterable<E>) value).iterator() : new Iterator<E>() {

                  private boolean mIsNext = true;

                  private boolean mIsRemoved = false;

                  public boolean hasNext() {
                    return mIsNext;
                  }

                  public E next() {
                    if (!mIsNext) {
                      throw new NoSuchElementException();
                    }

                    mIsNext = false;
                    return (E) value;
                  }

                  public void remove() {
                    if (mIsNext || mIsRemoved) {
                      throw new IllegalStateException();
                    }

                    mIsRemoved = true;
                    synchronized (mMutex) {
                      mHead.removeValue();
                    }
                  }
                };

          } catch (final TimeoutException e) {
            if (timeout) {
              throw e;
            }
          }
        }

        return mIterator;
      }
    }

    private long remainingTime() {
      final long timeout = mTimeout;
      if (timeout < 0) {
        return -1;
      }

      final long remainingTime = timeout - TimeUtils.currentTimeIn(mTimeUnit);
      if (remainingTime < 0) {
        throw timeoutException();
      }

      return remainingTime;
    }

    @NotNull
    private TimeoutException timeoutException() {
      return new TimeoutException(
          "timeout while iterating promise resolutions [" + mOriginalTimeout + " "
              + mOriginalTimeUnit + "]");
    }

    public boolean hasNext() {
      return getIterator(true).hasNext();
    }

    public List<E> next(final int maxSize) {
      final Iterator<E> iterator = getIterator(false);
      final ArrayList<E> values = new ArrayList<E>();
      if (iterator == null) {
        return values;
      }

      int count = 0;
      while (iterator.hasNext() && (count < maxSize)) {
        values.add(iterator.next());
        ++count;
      }

      return values;
    }

    public List<E> nextAll() {
      final Iterator<E> iterator = getIterator(true);
      final ArrayList<E> values = new ArrayList<E>();
      while (iterator.hasNext()) {
        values.add(iterator.next());
      }

      return values;
    }

    public E nextOr(final E defaultValue) {
      final Iterator<E> iterator = getIterator(false);
      return (iterator != null) ? iterator.next() : defaultValue;
    }

    public List<E> take(final int maxSize) {
      final Iterator<E> iterator = getIterator(false);
      final ArrayList<E> values = new ArrayList<E>();
      if (iterator == null) {
        return values;
      }

      int count = 0;
      while (iterator.hasNext() && (count < maxSize)) {
        values.add(iterator.next());
        iterator.remove();
        ++count;
      }

      return values;
    }

    public E take() {
      final Iterator<E> iterator = getIterator(true);
      final E value = iterator.next();
      iterator.remove();
      return value;
    }

    public List<E> takeAll() {
      final Iterator<E> iterator = getIterator(true);
      final ArrayList<E> values = new ArrayList<E>();
      while (iterator.hasNext()) {
        values.add(iterator.next());
        iterator.remove();
      }

      return values;
    }

    public E takeOr(final E defaultValue) {
      final Iterator<E> iterator = getIterator(false);
      if (iterator != null) {
        final E value = iterator.next();
        iterator.remove();
        return value;
      }

      return defaultValue;
    }

    public E next() {
      return getIterator(true).next();
    }
  }

  public boolean isFulfilled() {
    synchronized (mMutex) {
      return (mState == PromiseState.Fulfilled) || (mHead.getState() == PromiseState.Fulfilled);
    }
  }

  public boolean isPending() {
    synchronized (mMutex) {
      return (mState == PromiseState.Pending) || (mHead.getState() == PromiseState.Pending);
    }
  }

  public boolean isRejected() {
    synchronized (mMutex) {
      return (mState == PromiseState.Rejected) || (mHead.getState() == PromiseState.Rejected);
    }
  }

  public boolean isResolved() {
    synchronized (mMutex) {
      return (mState.isResolved() || mHead.getState().isResolved());
    }
  }

  public Throwable reason() {
    synchronized (mMutex) {
      if (!isRejected()) {
        throw new IllegalStateException("the promise is not rejected");
      }

      @SuppressWarnings("ThrowableResultOfMethodCallIgnored") final Throwable reason =
          getReason(0, TimeUnit.MILLISECONDS);
      return (reason != null) ? reason.getCause() : null;
    }
  }

  public V value() {
    synchronized (mMutex) {
      if (!isFulfilled()) {
        throw new IllegalStateException("the promise is not fulfilled");
      }

      return getValue(0, TimeUnit.MILLISECONDS);
    }
  }
}

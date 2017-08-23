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
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import dm.james.BackoffHandler.BackoffData;
import dm.james.executor.ScheduledExecutor;
import dm.james.executor.ScheduledExecutors;
import dm.james.log.Log;
import dm.james.log.Log.Level;
import dm.james.log.Logger;
import dm.james.promise.Action;
import dm.james.promise.CancellationException;
import dm.james.promise.Chainable;
import dm.james.promise.ChainableIterable;
import dm.james.promise.Mapper;
import dm.james.promise.Observer;
import dm.james.promise.Promise;
import dm.james.promise.PromiseInspection;
import dm.james.promise.PromiseIterable;
import dm.james.promise.RejectionException;
import dm.james.promise.RejectionIterableException;
import dm.james.promise.ScheduledData;
import dm.james.promise.TimeoutException;
import dm.james.util.Backoff;
import dm.james.util.ConstantConditions;
import dm.james.util.DoubleQueue;
import dm.james.util.InterruptedExecutionException;
import dm.james.util.Iterables;
import dm.james.util.SerializableProxy;
import dm.james.util.ThreadUtils;
import dm.james.util.TimeUtils;
import dm.james.util.TimeUtils.Condition;

/**
 * Created by davide-maestroni on 07/23/2017.
 */
class DefaultPromiseIterable<O> implements PromiseIterable<O>, Serializable {

  // TODO: 26/07/2017 test rejection propagation

  private final ScheduledExecutor mFulfillExecutor;

  private final ChainHead<?> mHead;

  private final Logger mLogger;

  private final Object mMutex;

  private final Observer<CallbackIterable<?>> mObserver;

  private final ScheduledExecutor mRejectExecutor;

  private final PromiseChain<?, O> mTail;

  private PromiseChain<O, ?> mChain;

  private PromiseState mState = PromiseState.Pending;

  @SuppressWarnings("unchecked")
  DefaultPromiseIterable(@NotNull final Observer<? super CallbackIterable<O>> observer,
      @Nullable final Log log, @Nullable final Level level) {
    mObserver = (Observer<CallbackIterable<?>>) ConstantConditions.notNull("observer", observer);
    mFulfillExecutor = ScheduledExecutors.immediateExecutor();
    mRejectExecutor = ScheduledExecutors.immediateExecutor();
    mLogger = Logger.newLogger(log, level, this);
    final ChainHead<O> head = new ChainHead<O>();
    head.setLogger(mLogger);
    head.setNext(new ChainTail(head));
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
  private DefaultPromiseIterable(@NotNull final Observer<CallbackIterable<?>> observer,
      @NotNull final ScheduledExecutor fulfillExecutor,
      @NotNull final ScheduledExecutor rejectExecutor, @Nullable final Log log,
      @Nullable final Level level, @NotNull final ChainHead<?> head,
      @NotNull final PromiseChain<?, O> tail) {
    // serialization
    mObserver = observer;
    mFulfillExecutor = fulfillExecutor;
    mRejectExecutor = rejectExecutor;
    mLogger = Logger.newLogger(log, level, this);
    mMutex = head.getMutex();
    mHead = head;
    mTail = tail;
    tail.setNext(new ChainTail((ChainHead<O>) head));
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
  private DefaultPromiseIterable(@NotNull final Observer<CallbackIterable<?>> observer,
      @NotNull final ScheduledExecutor fulfillExecutor,
      @NotNull final ScheduledExecutor rejectExecutor, @NotNull final Logger logger,
      @NotNull final ChainHead<?> head, @NotNull final PromiseChain<?, O> tail,
      final boolean observe) {
    // copy/bind
    mObserver = observer;
    mFulfillExecutor = fulfillExecutor;
    mRejectExecutor = rejectExecutor;
    mLogger = logger;
    mMutex = head.getMutex();
    mHead = head;
    mTail = tail;
    tail.setNext(new ChainTail((ChainHead<O>) head));
    if (observe) {
      try {
        observer.accept(head);

      } catch (final Throwable t) {
        InterruptedExecutionException.throwIfInterrupt(t);
        head.reject(t);
      }
    }
  }

  private static void close(@Nullable final Iterable<?> objects, @NotNull final Logger logger) {
    if (objects == null) {
      return;
    }

    final Iterator<?> iterator = objects.iterator();
    try {
      while (iterator.hasNext()) {
        close(iterator.next(), logger);
      }

    } catch (final Throwable t) {
      while (iterator.hasNext()) {
        safeClose(iterator.next(), logger);
      }

      InterruptedExecutionException.throwIfInterrupt(t);
      throw RejectionException.wrapIfNot(RuntimeException.class, t);
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

  private static void safeClose(@Nullable final Iterable<?> objects, @NotNull final Logger logger) {
    if (objects == null) {
      return;
    }

    for (final Object object : objects) {
      safeClose(object, logger);
    }
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
  public <R> PromiseIterable<R> all(
      @Nullable final Handler<Iterable<O>, ? super CallbackIterable<R>> fulfill,
      @Nullable final Handler<Throwable, ? super CallbackIterable<R>> reject) {
    final Logger logger = mLogger;
    return then(new HandlerAll<O, R>(fulfill, reject, logger.getLog(), logger.getLogLevel()));
  }

  @NotNull
  public <R> PromiseIterable<R> all(@NotNull final Mapper<Iterable<O>, Iterable<R>> mapper) {
    return all(new HandlerMapAll<O, R>(mapper), null);
  }

  @NotNull
  public <R> PromiseIterable<R> allSorted(
      @Nullable final Handler<Iterable<O>, ? super CallbackIterable<R>> fulfill,
      @Nullable final Handler<Throwable, ? super CallbackIterable<R>> reject) {
    final Logger logger = mLogger;
    return thenSorted(new HandlerAll<O, R>(fulfill, reject, logger.getLog(), logger.getLogLevel()));
  }

  @NotNull
  public <R> PromiseIterable<R> allTry(
      @Nullable final Handler<Iterable<O>, ? super CallbackIterable<R>> fulfill,
      @Nullable final Handler<Throwable, ? super CallbackIterable<R>> reject) {
    final Logger logger = mLogger;
    return all((fulfill != null) ? new HandlerTryIterable<O, R>(fulfill, logger.getLog(),
            logger.getLogLevel()) : null,
        (reject != null) ? new HandlerTry<Throwable, R>(reject, logger.getLog(),
            logger.getLogLevel()) : null);
  }

  @NotNull
  public <R> PromiseIterable<R> allTry(@NotNull final Mapper<Iterable<O>, Iterable<R>> mapper) {
    return allTry(new HandlerMapAll<O, R>(mapper), null);
  }

  @NotNull
  public <R> PromiseIterable<R> allTrySorted(
      @Nullable final Handler<Iterable<O>, ? super CallbackIterable<R>> fulfill,
      @Nullable final Handler<Throwable, ? super CallbackIterable<R>> reject) {
    final Logger logger = mLogger;
    return allSorted((fulfill != null) ? new HandlerTryIterable<O, R>(fulfill, logger.getLog(),
            logger.getLogLevel()) : null,
        (reject != null) ? new HandlerTry<Throwable, R>(reject, logger.getLog(),
            logger.getLogLevel()) : null);
  }

  @NotNull
  public <R> PromiseIterable<R> any(@Nullable final Handler<O, ? super CallbackIterable<R>> fulfill,
      @Nullable final Handler<Throwable, ? super CallbackIterable<R>> reject) {
    final Logger logger = mLogger;
    return then(new HandlerAny<O, R>(fulfill, reject, logger.getLog(), logger.getLogLevel()));
  }

  @NotNull
  public <R> PromiseIterable<R> any(@NotNull final Mapper<O, R> mapper) {
    return any(new HandlerMapEach<O, R>(mapper), null);
  }

  @NotNull
  public <R> PromiseIterable<R> anySorted(
      @Nullable final Handler<O, ? super CallbackIterable<R>> fulfill,
      @Nullable final Handler<Throwable, ? super CallbackIterable<R>> reject) {
    final Logger logger = mLogger;
    return thenSorted(new HandlerAny<O, R>(fulfill, reject, logger.getLog(), logger.getLogLevel()));
  }

  @NotNull
  public <R> PromiseIterable<R> anyTry(
      @Nullable final Handler<O, ? super CallbackIterable<R>> fulfill,
      @Nullable final Handler<Throwable, ? super CallbackIterable<R>> reject) {
    final Logger logger = mLogger;
    return any(
        (fulfill != null) ? new HandlerTry<O, R>(fulfill, logger.getLog(), logger.getLogLevel())
            : null, (reject != null) ? new HandlerTry<Throwable, R>(reject, logger.getLog(),
            logger.getLogLevel()) : null);
  }

  @NotNull
  public <R> PromiseIterable<R> anyTry(@NotNull final Mapper<O, R> mapper) {
    return anyTry(new HandlerMapEach<O, R>(mapper), null);
  }

  @NotNull
  public <R> PromiseIterable<R> anyTrySorted(
      @Nullable final Handler<O, ? super CallbackIterable<R>> fulfill,
      @Nullable final Handler<Throwable, ? super CallbackIterable<R>> reject) {
    final Logger logger = mLogger;
    return anySorted(
        (fulfill != null) ? new HandlerTry<O, R>(fulfill, logger.getLog(), logger.getLogLevel())
            : null, (reject != null) ? new HandlerTry<Throwable, R>(reject, logger.getLog(),
            logger.getLogLevel()) : null);
  }

  @NotNull
  public <R> PromiseIterable<R> applyAll(
      @NotNull final Mapper<PromiseIterable<O>, PromiseIterable<R>> mapper) {
    try {
      return ConstantConditions.notNull("promise", mapper.apply(this));

    } catch (final Throwable t) {
      InterruptedExecutionException.throwIfInterrupt(t);
      mLogger.err(t, "Error while applying promise transformation");
      throw RejectionException.wrapIfNotRejectionException(t);
    }
  }

  @NotNull
  public <R> PromiseIterable<R> applyAny(@NotNull final Mapper<Promise<O>, Promise<R>> mapper) {
    final Logger logger = mLogger;
    return any(new HandlerApplyFulfill<O, R>(mapper, logger.getLog(), logger.getLogLevel()),
        new HandlerApplyReject<O, R>(mapper, logger.getLog(), logger.getLogLevel()));
  }

  @NotNull
  public <R> PromiseIterable<R> applyEach(@NotNull final Mapper<Promise<O>, Promise<R>> mapper) {
    final Logger logger = mLogger;
    return each(new HandlerApplyFulfill<O, R>(mapper, logger.getLog(), logger.getLogLevel()),
        new HandlerApplyReject<O, R>(mapper, logger.getLog(), logger.getLogLevel()));
  }

  @NotNull
  public <R> PromiseIterable<R> applyEachSorted(
      @NotNull final Mapper<Promise<O>, Promise<R>> mapper) {
    final Logger logger = mLogger;
    return eachSorted(new HandlerApplyFulfill<O, R>(mapper, logger.getLog(), logger.getLogLevel()),
        new HandlerApplyReject<O, R>(mapper, logger.getLog(), logger.getLogLevel()));
  }

  @NotNull
  public PromiseIterable<O> backoffOn(@NotNull final ScheduledExecutor executor,
      @NotNull final Backoff<ScheduledData<O>> backoff) {
    return chain(
        new ChainStatefulSorted<O, O, BackoffData<O>>(new BackoffHandler<O>(executor, backoff)),
        executor, executor);
  }

  @NotNull
  public PromiseIterable<O> catchAll(@NotNull final Iterable<Class<? extends Throwable>> errors,
      @NotNull final Mapper<Throwable, Iterable<O>> mapper) {
    return all(null, new HandlerCatchAllFiltered<O>(errors, mapper));
  }

  @NotNull
  public PromiseIterable<O> catchAll(@NotNull final Mapper<Throwable, Iterable<O>> mapper) {
    return all(null, new HandlerCatchAll<O>(mapper));
  }

  @NotNull
  public Promise<PromiseInspection<Iterable<O>>> inspect() {
    return toPromise().inspect();
  }

  @NotNull
  public PromiseIterable<O> scheduleAll(@Nullable ScheduledExecutor fulfillExecutor,
      @Nullable ScheduledExecutor rejectExecutor) {
    if (fulfillExecutor == null) {
      fulfillExecutor = mFulfillExecutor;
    }

    if (rejectExecutor == null) {
      rejectExecutor = mRejectExecutor;
    }

    final Logger logger = mLogger;
    final HandlerAll<O, O> handler =
        new HandlerAll<O, O>(new ScheduleFulfillAll<O>(fulfillExecutor),
            new ScheduleReject<O>(rejectExecutor), logger.getLog(), logger.getLogLevel());
    return chain(new ChainStateful<O, O, ArrayList<O>>(handler), fulfillExecutor, rejectExecutor);
  }

  @NotNull
  public PromiseIterable<O> whenFulfilled(@NotNull final Observer<Iterable<O>> observer) {
    return all(new HandlerFulfilledAll<O>(observer), null);
  }

  @NotNull
  public PromiseIterable<O> whenRejected(@NotNull final Observer<Throwable> observer) {
    return all(null, new HandlerRejectedAll<O>(observer));
  }

  @NotNull
  public PromiseIterable<O> whenResolved(@NotNull final Action action) {
    return then(null, null, new HandlerResolvedAll<O>(action));
  }

  @NotNull
  public PromiseIterable<O> catchAny(@NotNull final Iterable<Class<? extends Throwable>> errors,
      @NotNull final Mapper<Throwable, O> mapper) {
    return any(null, new HandlerCatchEachFiltered<O>(errors, mapper));
  }

  @NotNull
  public PromiseIterable<O> catchAny(@NotNull final Mapper<Throwable, O> mapper) {
    return any(null, new HandlerCatchEach<O>(mapper));
  }

  @NotNull
  public PromiseIterable<O> catchEach(@NotNull final Iterable<Class<? extends Throwable>> errors,
      @NotNull final Mapper<Throwable, O> mapper) {
    return chain(new ChainMap<O, O>(null, new HandlerCatchEachFiltered<O>(errors, mapper),
        Integer.MAX_VALUE));
  }

  @NotNull
  public PromiseIterable<O> catchEach(@NotNull final Mapper<Throwable, O> mapper) {
    return chain(new ChainMap<O, O>(null, new HandlerCatchMap<O>(mapper), Integer.MAX_VALUE));
  }

  @NotNull
  public <R> PromiseIterable<R> each(
      @Nullable final Handler<O, ? super CallbackIterable<R>> fulfill,
      @Nullable final Handler<Throwable, ? super CallbackIterable<R>> reject) {
    return then(fulfill, reject, null);
  }

  @NotNull
  public <R> PromiseIterable<R> each(final int minBatchSize, @NotNull final Mapper<O, R> mapper) {
    return chain(new ChainMapGroup<O, R>(mapper, minBatchSize));
  }

  @NotNull
  public <R> PromiseIterable<R> each(@NotNull final Mapper<O, R> mapper) {
    return each(mapper, Integer.MAX_VALUE);
  }

  @NotNull
  public <R> PromiseIterable<R> each(@NotNull final Mapper<O, R> mapper, final int maxBatchSize) {
    return chain(new ChainMap<O, R>(mapper, null, maxBatchSize));
  }

  @NotNull
  public <R> PromiseIterable<R> eachSorted(
      @Nullable final Handler<O, ? super CallbackIterable<R>> fulfill,
      @Nullable final Handler<Throwable, ? super CallbackIterable<R>> reject) {
    return thenSorted(fulfill, reject, null);
  }

  @NotNull
  public <R> PromiseIterable<R> eachTry(
      @Nullable final Handler<O, ? super CallbackIterable<R>> fulfill,
      @Nullable final Handler<Throwable, ? super CallbackIterable<R>> reject) {
    final Logger logger = mLogger;
    return each(
        (fulfill != null) ? new HandlerTry<O, R>(fulfill, logger.getLog(), logger.getLogLevel())
            : null, (reject != null) ? new HandlerTry<Throwable, R>(reject, logger.getLog(),
            logger.getLogLevel()) : null);
  }

  @NotNull
  public <R> PromiseIterable<R> eachTry(final int minBatchSize,
      @NotNull final Mapper<O, R> mapper) {
    final Logger logger = mLogger;
    return each(minBatchSize, new MapperTry<O, R>(mapper, logger.getLog(), logger.getLogLevel()));
  }

  @NotNull
  public <R> PromiseIterable<R> eachTry(@NotNull final Mapper<O, R> mapper) {
    return eachTry(mapper, Integer.MAX_VALUE);
  }

  @NotNull
  public <R> PromiseIterable<R> eachTry(@NotNull final Mapper<O, R> mapper,
      final int maxBatchSize) {
    final Logger logger = mLogger;
    return each(new MapperTry<O, R>(mapper, logger.getLog(), logger.getLogLevel()), maxBatchSize);
  }

  @NotNull
  public <R> PromiseIterable<R> eachTrySorted(
      @Nullable final Handler<O, ? super CallbackIterable<R>> fulfill,
      @Nullable final Handler<Throwable, ? super CallbackIterable<R>> reject) {
    final Logger logger = mLogger;
    return eachSorted(
        (fulfill != null) ? new HandlerTry<O, R>(fulfill, logger.getLog(), logger.getLogLevel())
            : null, (reject != null) ? new HandlerTry<Throwable, R>(reject, logger.getLog(),
            logger.getLogLevel()) : null);
  }

  @NotNull
  public List<O> get(final int maxSize) {
    return get(maxSize, -1, TimeUnit.MILLISECONDS);
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public List<O> get(final int maxSize, final long timeout, @NotNull final TimeUnit timeUnit) {
    deadLockWarning(timeout);
    final ChainHead<?> head = mHead;
    synchronized (mMutex) {
      try {
        if (TimeUtils.waitUntil(mMutex, new Condition() {

          public boolean isTrue() {
            checkBound();
            return ((head.getOutputs().size() >= maxSize) || head.getState().isResolved());
          }
        }, timeout, timeUnit)) {
          final DoubleQueue<O> outputs = (DoubleQueue<O>) head.getOutputs();
          final Iterator<O> iterator = outputs.iterator();
          final ArrayList<O> result = new ArrayList<O>();
          for (int i = 0; (i < maxSize) && iterator.hasNext(); ++i) {
            result.add(iterator.next());
          }

          return result;
        }

      } catch (final InterruptedException e) {
        throw new InterruptedExecutionException(e);
      }
    }

    throw new TimeoutException(
        "timeout while waiting for promise resolution [" + timeout + " " + timeUnit + "]");
  }

  @NotNull
  public List<O> getAll() {
    return getAll(-1, TimeUnit.MILLISECONDS);
  }

  @NotNull
  public List<O> getAll(final long timeout, @NotNull final TimeUnit timeUnit) {
    return get(Integer.MAX_VALUE, timeout, timeUnit);
  }

  public O getAny() {
    return getAny(-1, TimeUnit.MILLISECONDS);
  }

  @SuppressWarnings("unchecked")
  public O getAny(final long timeout, @NotNull final TimeUnit timeUnit) {
    deadLockWarning(timeout);
    final ChainHead<?> head = mHead;
    synchronized (mMutex) {
      try {
        if (TimeUtils.waitUntil(mMutex, new Condition() {

          public boolean isTrue() {
            checkBound();
            return (!head.getOutputs().isEmpty() || head.getState().isResolved());
          }
        }, timeout, timeUnit)) {
          return ((DoubleQueue<O>) head.getOutputs()).peekFirst();
        }

      } catch (final InterruptedException e) {
        throw new InterruptedExecutionException(e);
      }
    }

    throw new TimeoutException(
        "timeout while waiting for promise resolution [" + timeout + " " + timeUnit + "]");
  }

  @SuppressWarnings("unchecked")
  public O getAnyOr(final O other, final long timeout, @NotNull final TimeUnit timeUnit) {
    deadLockWarning(timeout);
    final ChainHead<?> head = mHead;
    synchronized (mMutex) {
      try {
        if (TimeUtils.waitUntil(mMutex, new Condition() {

          public boolean isTrue() {
            checkBound();
            return (!head.getOutputs().isEmpty() || head.getState().isResolved());
          }
        }, timeout, timeUnit)) {
          return ((DoubleQueue<O>) head.getOutputs()).peekFirst();
        }

      } catch (final InterruptedException e) {
        throw new InterruptedExecutionException(e);
      }
    }

    return other;
  }

  @NotNull
  public PromiseIterable<PromiseInspection<O>> inspectAll() {
    return all(new HandlerInspectFulfillAll<O>(), new HandlerInspectReject<O>());
  }

  @NotNull
  public PromiseIterable<PromiseInspection<O>> inspectAny() {
    return any(new HandlerInspectFulfill<O>(), new HandlerInspectReject<O>());
  }

  @NotNull
  public PromiseIterable<PromiseInspection<O>> inspectEach() {
    return each(new HandlerInspectFulfill<O>(), new HandlerInspectReject<O>());
  }

  public boolean isSettled() {
    synchronized (mMutex) {
      return mHead.isSettled();
    }
  }

  @NotNull
  public Iterator<O> iterator(final long timeout, @NotNull final TimeUnit timeUnit) {
    return new PromiseIterator(timeout, timeUnit);
  }

  public O remove() {
    return remove(-1, TimeUnit.MILLISECONDS);
  }

  @NotNull
  public List<O> remove(final int maxSize) {
    return remove(maxSize, -1, TimeUnit.MILLISECONDS);
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public List<O> remove(final int maxSize, final long timeout, @NotNull final TimeUnit timeUnit) {
    deadLockWarning(timeout);
    final ChainHead<?> head = mHead;
    synchronized (mMutex) {
      try {
        if (TimeUtils.waitUntil(mMutex, new Condition() {

          public boolean isTrue() {
            checkBound();
            return ((head.getOutputs().size() >= maxSize) || head.getState().isResolved());
          }
        }, timeout, timeUnit)) {
          final DoubleQueue<O> outputs = (DoubleQueue<O>) head.getOutputs();
          final ArrayList<O> removed = new ArrayList<O>();
          for (int i = 0; (i < maxSize) && !outputs.isEmpty(); --i) {
            removed.add(outputs.removeFirst());
          }

          return removed;
        }

      } catch (final InterruptedException e) {
        throw new InterruptedExecutionException(e);
      }
    }

    throw new TimeoutException(
        "timeout while waiting for promise resolution [" + timeout + " " + timeUnit + "]");
  }

  @SuppressWarnings("unchecked")
  public O remove(final long timeout, @NotNull final TimeUnit timeUnit) {
    deadLockWarning(timeout);
    final ChainHead<?> head = mHead;
    synchronized (mMutex) {
      try {
        if (TimeUtils.waitUntil(mMutex, new Condition() {

          public boolean isTrue() {
            checkBound();
            return (!head.getOutputs().isEmpty() || head.getState().isResolved());
          }
        }, timeout, timeUnit)) {
          return ((DoubleQueue<O>) head.getOutputs()).removeFirst();
        }

      } catch (final InterruptedException e) {
        throw new InterruptedExecutionException(e);
      }
    }

    throw new TimeoutException(
        "timeout while waiting for promise resolution [" + timeout + " " + timeUnit + "]");
  }

  @NotNull
  public List<O> removeAll() {
    return remove(Integer.MAX_VALUE);
  }

  @NotNull
  public List<O> removeAll(final long timeout, @NotNull final TimeUnit timeUnit) {
    return remove(Integer.MAX_VALUE, timeout, timeUnit);
  }

  @SuppressWarnings("unchecked")
  public O removeOr(final O other, final long timeout, @NotNull final TimeUnit timeUnit) {
    deadLockWarning(timeout);
    final ChainHead<?> head = mHead;
    synchronized (mMutex) {
      try {
        if (TimeUtils.waitUntil(mMutex, new Condition() {

          public boolean isTrue() {
            checkBound();
            return (!head.getOutputs().isEmpty() || head.getState().isResolved());
          }
        }, timeout, timeUnit)) {
          return ((DoubleQueue<O>) head.getOutputs()).removeFirst();
        }

      } catch (final InterruptedException e) {
        throw new InterruptedExecutionException(e);
      }
    }

    return other;
  }

  @NotNull
  public PromiseIterable<O> scheduleAny(@Nullable ScheduledExecutor fulfillExecutor,
      @Nullable ScheduledExecutor rejectExecutor) {
    if (fulfillExecutor == null) {
      fulfillExecutor = mFulfillExecutor;
    }

    if (rejectExecutor == null) {
      rejectExecutor = mRejectExecutor;
    }

    final Logger logger = mLogger;
    final HandlerAny<O, O> handler = new HandlerAny<O, O>(new ScheduleFulfill<O>(fulfillExecutor),
        new ScheduleReject<O>(rejectExecutor), logger.getLog(), logger.getLogLevel());
    return chain(new ChainStateful<O, O, AnyState<O>>(handler), fulfillExecutor, rejectExecutor);
  }

  @NotNull
  public PromiseIterable<O> scheduleEach(@Nullable ScheduledExecutor fulfillExecutor,
      @Nullable ScheduledExecutor rejectExecutor) {
    if (fulfillExecutor == null) {
      fulfillExecutor = mFulfillExecutor;
    }

    if (rejectExecutor == null) {
      rejectExecutor = mRejectExecutor;
    }

    return chain(new ChainHandler<O, O>(new ScheduleFulfill<O>(fulfillExecutor),
            new ScheduleReject<O>(rejectExecutor), new ScheduleResolve<O>(fulfillExecutor)),
        fulfillExecutor, rejectExecutor);
  }

  @NotNull
  public PromiseIterable<O> scheduleEachSorted(@Nullable ScheduledExecutor fulfillExecutor,
      @Nullable ScheduledExecutor rejectExecutor) {
    if (fulfillExecutor == null) {
      fulfillExecutor = mFulfillExecutor;
    }

    if (rejectExecutor == null) {
      rejectExecutor = mRejectExecutor;
    }

    return chain(new ChainHandlerSorted<O, O>(new ScheduleFulfill<O>(fulfillExecutor),
            new ScheduleReject<O>(rejectExecutor), new ScheduleResolve<O>(fulfillExecutor)),
        fulfillExecutor, rejectExecutor);
  }

  @NotNull
  public <R> PromiseIterable<R> then(
      @Nullable final Handler<O, ? super CallbackIterable<R>> fulfill,
      @Nullable final Handler<Throwable, ? super CallbackIterable<R>> reject,
      @Nullable final Observer<? super CallbackIterable<R>> resolve) {
    return chain(new ChainHandler<O, R>(fulfill, reject, resolve));
  }

  @NotNull
  public <R, S> PromiseIterable<R> then(@NotNull final StatefulHandler<O, R, S> handler) {
    return chain(new ChainStateful<O, R, S>(handler));
  }

  @NotNull
  public <R> PromiseIterable<R> thenSorted(
      @Nullable final Handler<O, ? super CallbackIterable<R>> fulfill,
      @Nullable final Handler<Throwable, ? super CallbackIterable<R>> reject,
      @Nullable final Observer<? super CallbackIterable<R>> resolve) {
    return chain(new ChainHandlerSorted<O, R>(fulfill, reject, resolve));
  }

  @NotNull
  public <R, S> PromiseIterable<R> thenSorted(@NotNull final StatefulHandler<O, R, S> handler) {
    return chain(new ChainStatefulSorted<O, R, S>(handler));
  }

  @NotNull
  public <R> PromiseIterable<R> thenTry(
      @Nullable final Handler<O, ? super CallbackIterable<R>> fulfill,
      @Nullable final Handler<Throwable, ? super CallbackIterable<R>> reject,
      @Nullable final Observer<? super CallbackIterable<R>> resolve) {
    final Logger logger = mLogger;
    return then(
        (fulfill != null) ? new HandlerTry<O, R>(fulfill, logger.getLog(), logger.getLogLevel())
            : null, reject, resolve);
  }

  @NotNull
  public <R> PromiseIterable<R> thenTrySorted(
      @Nullable final Handler<O, ? super CallbackIterable<R>> fulfill,
      @Nullable final Handler<Throwable, ? super CallbackIterable<R>> reject,
      @Nullable final Observer<? super CallbackIterable<R>> resolve) {
    final Logger logger = mLogger;
    return thenSorted(
        (fulfill != null) ? new HandlerTry<O, R>(fulfill, logger.getLog(), logger.getLogLevel())
            : null, reject, resolve);
  }

  @NotNull
  public <R, S extends Closeable> PromiseIterable<R> thenTryState(
      @NotNull final StatefulHandler<O, R, S> handler) {
    final Logger logger = mLogger;
    return then(new StatefulHandlerTry<O, R, S>(handler, logger.getLog(), logger.getLogLevel()));
  }

  @NotNull
  public <R, S extends Closeable> PromiseIterable<R> thenTryStateSorted(
      @NotNull final StatefulHandler<O, R, S> handler) {
    final Logger logger = mLogger;
    return thenSorted(
        new StatefulHandlerTry<O, R, S>(handler, logger.getLog(), logger.getLogLevel()));
  }

  public void waitSettled() {
    waitSettled(-1, TimeUnit.MILLISECONDS);
  }

  public boolean waitSettled(final long timeout, @NotNull final TimeUnit timeUnit) {
    deadLockWarning(timeout);
    synchronized (mMutex) {
      try {
        if (TimeUtils.waitUntil(mMutex, new Condition() {

          public boolean isTrue() {
            return mHead.isSettled();
          }
        }, timeout, timeUnit)) {
          return true;
        }

      } catch (final InterruptedException e) {
        throw new InterruptedExecutionException(e);
      }
    }

    return false;
  }

  @NotNull
  public PromiseIterable<O> whenFulfilledAny(@NotNull final Observer<O> observer) {
    return any(new MapperFulfilledEach<O>(observer));
  }

  @NotNull
  public PromiseIterable<O> whenFulfilledEach(@NotNull final Observer<O> observer) {
    return each(new MapperFulfilledEach<O>(observer));
  }

  @NotNull
  public PromiseIterable<O> whenRejectedAny(@NotNull final Observer<Throwable> observer) {
    return any(null, new HandlerRejectedEach<O>(observer));
  }

  @NotNull
  public PromiseIterable<O> whenRejectedEach(@NotNull final Observer<Throwable> observer) {
    return each(null, new HandlerRejectedEach<O>(observer));
  }

  @NotNull
  public <R> Promise<R> apply(@NotNull final Mapper<Promise<Iterable<O>>, Promise<R>> mapper) {
    try {
      return ConstantConditions.notNull("promise", mapper.apply(this));

    } catch (final Throwable t) {
      InterruptedExecutionException.throwIfInterrupt(t);
      mLogger.err(t, "Error while applying promise transformation");
      throw RejectionException.wrapIfNotRejectionException(t);
    }
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

  public Iterable<O> get() {
    return get(-1, TimeUnit.MILLISECONDS);
  }

  @SuppressWarnings("unchecked")
  public Iterable<O> get(final long timeout, @NotNull final TimeUnit timeUnit) {
    deadLockWarning(timeout);
    @SuppressWarnings("UnnecessaryLocalVariable") final ChainHead<?> head = mHead;
    synchronized (mMutex) {
      try {
        if (TimeUtils.waitUntil(mMutex, new Condition() {

          public boolean isTrue() {
            checkBound();
            return head.getState().isResolved();
          }
        }, timeout, timeUnit)) {
          return new ResolutionIterable<O>(this);
        }

      } catch (final InterruptedException e) {
        throw new InterruptedExecutionException(e);
      }
    }

    throw new TimeoutException(
        "timeout while waiting for promise resolution [" + timeout + " " + timeUnit + "]");
  }

  @SuppressWarnings("unchecked")
  public Iterable<O> getOr(final Iterable<O> other, final long timeout,
      @NotNull final TimeUnit timeUnit) {
    deadLockWarning(timeout);
    @SuppressWarnings("UnnecessaryLocalVariable") final ChainHead<?> head = mHead;
    synchronized (mMutex) {
      try {
        if (TimeUtils.waitUntil(mMutex, new Condition() {

          public boolean isTrue() {
            checkBound();
            return head.getState().isResolved();
          }
        }, timeout, timeUnit)) {
          return new ResolutionIterable<O>(this);
        }

      } catch (final InterruptedException e) {
        throw new InterruptedExecutionException(e);
      }
    }

    return other;
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

  public boolean isChained() {
    synchronized (mMutex) {
      return (mChain != null);
    }
  }

  @NotNull
  public <R> Promise<R> then(@Nullable final Handler<Iterable<O>, ? super Callback<R>> fulfill,
      @Nullable final Handler<Throwable, ? super Callback<R>> reject) {
    return toPromise().then(fulfill, reject);
  }

  @NotNull
  public <R> Promise<R> then(@NotNull final Mapper<Iterable<O>, R> mapper) {
    return toPromise().then(mapper);
  }

  @NotNull
  public <R> Promise<R> thenTry(@Nullable final Handler<Iterable<O>, ? super Callback<R>> fulfill,
      @Nullable final Handler<Throwable, ? super Callback<R>> reject) {
    return toPromise().thenTry(fulfill, reject);
  }

  @NotNull
  public <R> Promise<R> thenTry(@NotNull final Mapper<Iterable<O>, R> mapper) {
    return toPromise().thenTry(mapper);
  }

  public void waitResolved() {
    waitResolved(-1, TimeUnit.MILLISECONDS);
  }

  public boolean waitResolved(final long timeout, @NotNull final TimeUnit timeUnit) {
    deadLockWarning(timeout);
    synchronized (mMutex) {
      try {
        if (TimeUtils.waitUntil(mMutex, new Condition() {

          public boolean isTrue() {
            return (mState.isResolved() || mHead.getState().isResolved());
          }
        }, timeout, timeUnit)) {
          return true;
        }

      } catch (final InterruptedException e) {
        throw new InterruptedExecutionException(e);
      }
    }

    return false;
  }

  public boolean isFulfilled() {
    synchronized (mMutex) {
      return (mState == PromiseState.Fulfilled) || (mHead.getState() == PromiseState.Fulfilled);
    }
  }

  public Iterator<O> iterator() {
    return iterator(-1, TimeUnit.MILLISECONDS);
  }

  @NotNull
  private <R> PromiseIterable<R> chain(@NotNull final PromiseChain<O, R> chain) {
    return chain(chain, mFulfillExecutor, mRejectExecutor);
  }

  @NotNull
  @SuppressWarnings("unchecked")
  private <R> PromiseIterable<R> chain(@NotNull final PromiseChain<O, R> chain,
      @NotNull final ScheduledExecutor fulfillExecutor,
      @NotNull final ScheduledExecutor rejectExecutor) {
    final Logger logger = mLogger;
    final ChainHead<?> head = mHead;
    final boolean isBound;
    final Runnable binding;
    synchronized (mMutex) {
      if (mChain != null) {
        isBound = true;
        binding = null;

      } else {
        isBound = false;
        chain.setLogger(logger);
        binding = ((ChainHead<O>) head).bind(chain);
        mChain = chain;
        mMutex.notifyAll();
      }
    }

    if (isBound) {
      return copy().chain(chain);
    }

    final PromiseIterable<R> promise =
        new DefaultPromiseIterable<R>(mObserver, fulfillExecutor, rejectExecutor, logger, head,
            chain, false);
    if (binding != null) {
      mFulfillExecutor.execute(binding);
    }

    ((PromiseChain<?, Object>) mTail).setNext((PromiseChain<Object, O>) chain);
    return promise;
  }

  private void checkBound() {
    if (mChain != null) {
      throw new IllegalStateException("the promise has been bound");
    }
  }

  @SuppressWarnings("unchecked")
  private DefaultPromiseIterable<O> copy() {
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

    return new DefaultPromiseIterable<O>(mObserver, mFulfillExecutor, mRejectExecutor, logger,
        newHead, (PromiseChain<?, O>) newTail, true);
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
  private Promise<Iterable<O>> toPromise() {
    final Logger logger = mLogger;
    return BoundPromise.create(this,
        new DefaultDeferredPromise<Iterable<O>, Iterable<O>>(logger.getLog(),
            logger.getLogLevel()));
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
    return new PromiseProxy(mObserver, mFulfillExecutor, mRejectExecutor, logger.getLog(),
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

  private interface AnyState<R> extends Observer<CallbackIterable<R>> {

    boolean isRejection();
  }

  private interface Resolution<O> {

    void consume(@NotNull PromiseChain<O, ?> chain);

    @Nullable
    RejectionException getError();

    @NotNull
    DoubleQueue<O> getOutputs();

    void throwError();
  }

  private static class AnyStateRejected<R> implements AnyState<R> {

    private final ArrayList<Throwable> mReasons = new ArrayList<Throwable>();

    private AnyStateRejected(final Throwable reason) {
      mReasons.add(reason);
    }

    public void accept(final CallbackIterable<R> callback) throws Exception {
      callback.reject(new RejectionIterableException(mReasons));
    }

    public boolean isRejection() {
      return true;
    }

    void add(final Throwable reason) {
      mReasons.add(reason);
    }
  }

  private static class AnyStateResolved<R> implements AnyState<R> {

    public void accept(final CallbackIterable<R> callback) throws Exception {
    }

    public boolean isRejection() {
      return false;
    }
  }

  private static class ChainHandler<O, R> extends PromiseChain<O, R> {

    private final AtomicLong mCallbackCount = new AtomicLong(1);

    private final Handler<O, ? super CallbackIterable<R>> mFulfill;

    private final Handler<Throwable, ? super CallbackIterable<R>> mReject;

    private final Observer<? super CallbackIterable<R>> mResolve;

    @SuppressWarnings("unchecked")
    private ChainHandler(@Nullable final Handler<O, ? super CallbackIterable<R>> fulfill,
        @Nullable final Handler<Throwable, ? super CallbackIterable<R>> reject,
        @Nullable final Observer<? super CallbackIterable<R>> resolve) {
      mFulfill = (Handler<O, ? super CallbackIterable<R>>) ((fulfill != null) ? fulfill
          : new HandlerFulfill<O, R>());
      mReject = (Handler<Throwable, ? super CallbackIterable<R>>) ((reject != null) ? reject
          : new HandlerReject<R>());
      mResolve = (Observer<? super CallbackIterable<R>>) ((resolve != null) ? resolve
          : new ResolveObserver<R>());
    }

    @Override
    void add(final PromiseChain<R, ?> next, final O input) {
      final ChainCallback callback = new ChainCallback(next);
      try {
        mFulfill.accept(input, callback);

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Promise has been cancelled");
        next.addRejectionSafe(e);
        innerResolve(next);

      } catch (final Throwable t) {
        InterruptedExecutionException.throwIfInterrupt(t);
        getLogger().err(t, "Error while processing input: %s", input);
        next.addRejectionSafe(t);
        innerResolve(next);
      }
    }

    private void innerResolve(final PromiseChain<R, ?> next) {
      if (mCallbackCount.decrementAndGet() == 0) {
        try {
          mResolve.accept(next);

        } catch (final CancellationException e) {
          getLogger().wrn(e, "Promise has been cancelled");

        } catch (final Throwable t) {
          InterruptedExecutionException.throwIfInterrupt(t);
          getLogger().err(t, "Error while propagating resolution");
        }
      }
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ChainProxy<O, R>(mFulfill, mReject, mResolve);
    }

    private static class ChainProxy<O, R> extends SerializableProxy {

      private ChainProxy(final Handler<O, ? super CallbackIterable<R>> fulfill,
          final Handler<Throwable, ? super CallbackIterable<R>> reject,
          final Observer<? super CallbackIterable<R>> resolve) {
        super(proxy(fulfill), proxy(reject), proxy(resolve));
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ChainHandler<O, R>((Handler<O, ? super CallbackIterable<R>>) args[0],
              (Handler<Throwable, ? super CallbackIterable<R>>) args[1],
              (Observer<? super CallbackIterable<R>>) args[2]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    private class ChainCallback implements CallbackIterable<R> {

      private final PromiseChain<R, ?> mNext;

      private ChainCallback(final PromiseChain<R, ?> next) {
        mCallbackCount.incrementAndGet();
        mNext = next;
      }

      private ChainCallback(final PromiseChain<R, ?> next, final int count) {
        mCallbackCount.addAndGet(count);
        mNext = next;
      }

      public void add(final R output) {
        mNext.add(output);
      }

      public void defer(@NotNull final Chainable<R> chainable) {
        addDeferred(chainable);
        innerResolve(mNext);
      }

      public void resolve(final R output) {
        add(output);
        innerResolve(mNext);
      }

      public void reject(final Throwable reason) {
        mNext.addRejectionSafe(reason);
        innerResolve(mNext);
      }

      @SuppressWarnings("unchecked")
      public void addAllDeferred(@NotNull final Chainable<? extends Iterable<R>> chainable) {
        if (chainable instanceof ChainableIterable) {
          mCallbackCount.incrementAndGet();
          ((ChainableIterable<R>) chainable).then(new Handler<R, Callback<Void>>() {

            public void accept(final R input, final Callback<Void> callback) {
              mNext.add(input);
            }
          }, new Handler<Throwable, Callback<Void>>() {

            public void accept(final Throwable reason, final Callback<Void> callback) {
              mNext.addRejection(reason);
            }
          }, new Observer<CallbackIterable<Void>>() {

            public void accept(final CallbackIterable<Void> input) {
              innerResolve(mNext);
            }
          });

        } else {
          mCallbackCount.incrementAndGet();
          ((Chainable<Iterable<R>>) chainable).then(new Handler<Iterable<R>, Callback<Void>>() {

            public void accept(final Iterable<R> input, final Callback<Void> callback) {
              mNext.addAll(input);
              innerResolve(mNext);
            }
          }, new Handler<Throwable, Callback<Void>>() {

            public void accept(final Throwable reason, final Callback<Void> callback) {
              mNext.addRejection(reason);
              innerResolve(mNext);
            }
          });
        }
      }

      @SuppressWarnings("unchecked")
      public void addAllDeferred(@Nullable final Iterable<? extends Chainable<?>> chainables) {
        if (chainables == null) {
          return;
        }

        for (final Chainable<?> chainable : chainables) {
          if (chainable instanceof ChainableIterable) {
            addAllDeferred((ChainableIterable<R>) chainable);

          } else {
            addDeferred((Chainable<R>) chainable);
          }
        }
      }

      public void addAll(@Nullable final Iterable<R> outputs) {
        mNext.addAll(outputs);
      }

      public void addDeferred(@NotNull final Chainable<R> chainable) {
        mCallbackCount.incrementAndGet();
        chainable.then(new Handler<R, Callback<Void>>() {

          public void accept(final R input, final Callback<Void> callback) {
            mNext.add(input);
            innerResolve(mNext);
          }
        }, new Handler<Throwable, Callback<Void>>() {

          public void accept(final Throwable reason, final Callback<Void> callback) {
            mNext.addRejection(reason);
            innerResolve(mNext);
          }
        });
      }

      public void addRejection(final Throwable reason) {
        mNext.addRejection(reason);
      }

      public void resolve() {
        innerResolve(mNext);
      }
    }

    @Override
    void addAll(final PromiseChain<R, ?> next, final Iterable<O> inputs) {
      final ChainCallback callback = new ChainCallback(next, Iterables.size(inputs));
      for (final O input : inputs) {
        try {
          mFulfill.accept(input, callback);

        } catch (final CancellationException e) {
          getLogger().wrn(e, "Promise has been cancelled");
          next.addRejectionSafe(e);
          innerResolve(next);

        } catch (final Throwable t) {
          InterruptedExecutionException.throwIfInterrupt(t);
          getLogger().err(t, "Error while processing input: %s", input);
          next.addRejectionSafe(t);
          innerResolve(next);
        }
      }
    }

    @NotNull
    @Override
    PromiseChain<O, R> copy() {
      return new ChainHandler<O, R>(mFulfill, mReject, mResolve);
    }

    @Override
    void addRejection(final PromiseChain<R, ?> next, final Throwable reason) {
      final ChainCallback callback = new ChainCallback(next);
      try {
        mReject.accept(reason, callback);

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Promise has been cancelled");
        next.addRejectionSafe(e);
        innerResolve(next);

      } catch (final Throwable t) {
        InterruptedExecutionException.throwIfInterrupt(t);
        getLogger().err(t, "Error while processing rejection with reason: %s", reason);
        next.addRejectionSafe(t);
        innerResolve(next);
      }
    }

    @Override
    void resolve(final PromiseChain<R, ?> next) {
      innerResolve(next);
    }
  }

  private static class ChainHandlerSorted<O, R> extends PromiseChain<O, R> {

    private final AtomicLong mCallbackCount = new AtomicLong(1);

    private final Handler<O, ? super CallbackIterable<R>> mFulfill;

    private final Object mMutex = new Object();

    private final NestedQueue<Resolution<R>> mQueue = new NestedQueue<Resolution<R>>();

    private final Handler<Throwable, ? super CallbackIterable<R>> mReject;

    private final Observer<? super CallbackIterable<R>> mResolve;

    @SuppressWarnings("unchecked")
    private ChainHandlerSorted(@Nullable final Handler<O, ? super CallbackIterable<R>> fulfill,
        @Nullable final Handler<Throwable, ? super CallbackIterable<R>> reject,
        @Nullable final Observer<? super CallbackIterable<R>> resolve) {
      mFulfill = (Handler<O, ? super CallbackIterable<R>>) ((fulfill != null) ? fulfill
          : new HandlerFulfill<O, R>());
      mReject = (Handler<Throwable, ? super CallbackIterable<R>>) ((reject != null) ? reject
          : new HandlerReject<R>());
      mResolve = (Observer<? super CallbackIterable<R>>) ((resolve != null) ? resolve
          : new ResolveObserver<R>());
    }

    // TODO: 31/07/2017 find tradeoff and switch between transferTo and removeFirst
    private void flushQueue(final PromiseChain<R, ?> next) {
      Resolution<R> resolution = removeFirst();
      try {
        while (resolution != null) {
          resolution.consume(next);
          resolution = removeFirst();
        }

      } finally {
        synchronized (mMutex) {
          mQueue.clear();
        }
      }
    }

    private void innerResolve(final PromiseChain<R, ?> next) {
      if (mCallbackCount.decrementAndGet() == 0) {
        try {
          flushQueue(next);
          mResolve.accept(next);

        } catch (final CancellationException e) {
          getLogger().wrn(e, "Promise has been cancelled");
          next.reject(e);

        } catch (final Throwable t) {
          InterruptedExecutionException.throwIfInterrupt(t);
          getLogger().err(t, "Error while processing resolution");
          next.reject(t);
        }
      }
    }

    private Resolution<R> removeFirst() {
      synchronized (mMutex) {
        if (mQueue.isEmpty()) {
          return null;
        }

        return mQueue.removeFirst();
      }
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ChainProxy<O, R>(mFulfill, mReject, mResolve);
    }

    private static class ChainProxy<O, R> extends SerializableProxy {

      private ChainProxy(final Handler<O, ? super CallbackIterable<R>> fulfill,
          final Handler<Throwable, ? super CallbackIterable<R>> reject,
          final Observer<? super CallbackIterable<R>> resolve) {
        super(proxy(fulfill), proxy(reject), proxy(resolve));
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ChainHandlerSorted<O, R>((Handler<O, ? super CallbackIterable<R>>) args[0],
              (Handler<Throwable, ? super CallbackIterable<R>>) args[1],
              (Observer<? super CallbackIterable<R>>) args[2]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    private class ChainCallback implements CallbackIterable<R> {

      private final PromiseChain<R, ?> mNext;

      private ChainCallback(final PromiseChain<R, ?> next) {
        mCallbackCount.incrementAndGet();
        mNext = next;
      }

      private ChainCallback(final PromiseChain<R, ?> next, final int count) {
        mCallbackCount.addAndGet(count);
        mNext = next;
      }

      public void add(final R output) {
        synchronized (mMutex) {
          mQueue.add(new ResolutionOutput<R>(output));
        }

        flushQueue(mNext);
      }

      public void defer(@NotNull final Chainable<R> chainable) {
        addDeferred(chainable);
        resolve();
      }

      public void resolve(final R output) {
        add(output);
        innerResolve(mNext);
      }

      @SuppressWarnings("unchecked")
      public void addAllDeferred(@NotNull final Chainable<? extends Iterable<R>> chainable) {
        final NestedQueue<Resolution<R>> queue;
        synchronized (mMutex) {
          queue = mQueue.addNested();
        }

        mCallbackCount.incrementAndGet();
        if (chainable instanceof ChainableIterable) {
          ((ChainableIterable<R>) chainable).then(new Handler<R, CallbackIterable<Void>>() {

            public void accept(final R input, final CallbackIterable<Void> callback) {
              synchronized (mMutex) {
                queue.add(new ResolutionOutput<R>(input));
              }

              flushQueue(mNext);
            }
          }, new Handler<Throwable, CallbackIterable<Void>>() {

            public void accept(final Throwable reason, final CallbackIterable<Void> callback) {
              synchronized (mMutex) {
                queue.add(new ResolutionThrowable<R>(reason));
              }

              flushQueue(mNext);
            }
          }, new Observer<CallbackIterable<Void>>() {

            public void accept(final CallbackIterable<Void> callback) {
              synchronized (mMutex) {
                queue.close();
              }

              final PromiseChain<R, ?> next = mNext;
              flushQueue(next);
              innerResolve(next);
            }
          });

        } else {
          ((Chainable<Iterable<R>>) chainable).then(new Handler<Iterable<R>, Callback<Void>>() {

            public void accept(final Iterable<R> input, final Callback<Void> callback) {
              synchronized (mMutex) {
                queue.add(new ResolutionOutputs<R>(input));
                queue.close();
              }

              final PromiseChain<R, ?> next = mNext;
              flushQueue(next);
              innerResolve(next);
            }
          }, new Handler<Throwable, Callback<Void>>() {

            public void accept(final Throwable reason, final Callback<Void> callback) {
              synchronized (mMutex) {
                queue.add(new ResolutionThrowable<R>(reason));
                queue.close();
              }

              final PromiseChain<R, ?> next = mNext;
              flushQueue(next);
              innerResolve(next);
            }
          });
        }
      }

      public void reject(final Throwable reason) {
        try {
          addRejection(reason);
          innerResolve(mNext);

        } catch (final CancellationException e) {
          getLogger().wrn(e, "Promise has been cancelled");

        } catch (final Throwable t) {
          InterruptedExecutionException.throwIfInterrupt(t);
          getLogger().err(reason, "Suppressed rejection");
        }
      }

      public void addAll(@Nullable final Iterable<R> outputs) {
        synchronized (mMutex) {
          mQueue.add(new ResolutionOutputs<R>(outputs));
        }

        flushQueue(mNext);
      }

      @SuppressWarnings("unchecked")
      public void addAllDeferred(@Nullable final Iterable<? extends Chainable<?>> chainables) {
        if (chainables == null) {
          return;
        }

        for (final Chainable<?> chainable : chainables) {
          if (chainable instanceof PromiseIterable) {
            addAllDeferred((ChainableIterable<R>) chainable);

          } else {
            addDeferred((Chainable<R>) chainable);
          }
        }
      }

      public void addDeferred(@NotNull final Chainable<R> chainable) {
        final NestedQueue<Resolution<R>> queue;
        synchronized (mMutex) {
          queue = mQueue.addNested();
        }

        mCallbackCount.incrementAndGet();
        chainable.then(new Handler<R, Callback<Void>>() {

          public void accept(final R input, final Callback<Void> callback) {
            synchronized (mMutex) {
              queue.add(new ResolutionOutput<R>(input));
              queue.close();
            }

            final PromiseChain<R, ?> next = mNext;
            flushQueue(next);
            innerResolve(next);

          }
        }, new Handler<Throwable, Callback<Void>>() {

          public void accept(final Throwable reason, final Callback<Void> callback) {
            synchronized (mMutex) {
              queue.add(new ResolutionThrowable<R>(reason));
              queue.close();
            }

            final PromiseChain<R, ?> next = mNext;
            flushQueue(next);
            innerResolve(next);
          }
        });
      }

      public void addRejection(final Throwable reason) {
        synchronized (mMutex) {
          mQueue.add(new ResolutionThrowable<R>(reason));
        }

        flushQueue(mNext);
      }

      public void resolve() {
        final PromiseChain<R, ?> next = mNext;
        flushQueue(next);
        innerResolve(next);
      }
    }

    @Override
    void add(final PromiseChain<R, ?> next, final O input) {
      final ChainCallback callback = new ChainCallback(next);
      try {
        mFulfill.accept(input, callback);

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Promise has been cancelled");
        next.addRejectionSafe(e);
        innerResolve(next);

      } catch (final Throwable t) {
        InterruptedExecutionException.throwIfInterrupt(t);
        getLogger().err(t, "Error while processing input: %s", input);
        next.addRejectionSafe(t);
        innerResolve(next);
      }
    }

    @Override
    void addAll(final PromiseChain<R, ?> next, final Iterable<O> inputs) {
      final ChainCallback callback = new ChainCallback(next, Iterables.size(inputs));
      for (final O input : inputs) {
        try {
          mFulfill.accept(input, callback);

        } catch (final CancellationException e) {
          getLogger().wrn(e, "Promise has been cancelled");
          next.addRejectionSafe(e);
          innerResolve(next);

        } catch (final Throwable t) {
          InterruptedExecutionException.throwIfInterrupt(t);
          getLogger().err(t, "Error while processing input: %s", input);
          next.addRejectionSafe(t);
          innerResolve(next);
        }
      }
    }

    @NotNull
    @Override
    PromiseChain<O, R> copy() {
      return new ChainHandlerSorted<O, R>(mFulfill, mReject, mResolve);
    }

    @Override
    void addRejection(final PromiseChain<R, ?> next, final Throwable reason) {
      final ChainCallback callback = new ChainCallback(next);
      try {
        mReject.accept(reason, callback);

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Promise has been cancelled");
        next.addRejectionSafe(e);
        innerResolve(next);

      } catch (final Throwable t) {
        InterruptedExecutionException.throwIfInterrupt(t);
        getLogger().err(t, "Error while processing rejection with reason: %s", reason);
        next.addRejectionSafe(t);
        innerResolve(next);
      }
    }

    @Override
    void resolve(final PromiseChain<R, ?> next) {
      innerResolve(next);
    }
  }

  private static class ChainHead<O> extends PromiseChain<O, O> {

    private final Object mMutex = new Object();

    private StatePending mInnerState = new StatePending();

    private ArrayList<Resolution<O>> mOutputs = new ArrayList<Resolution<O>>();

    private ResolutionOutputs<O> mResolution = new ResolutionOutputs<O>();

    private PromiseState mState = PromiseState.Pending;

    private ChainHead() {
      mOutputs.add(mResolution);
    }

    @Nullable
    public RejectionException getException() {
      for (final Resolution<O> output : mOutputs) {
        final RejectionException exception = output.getError();
        if (exception != null) {
          return exception;
        }
      }

      return null;
    }

    @Nullable
    Runnable bind(final PromiseChain<O, ?> chain) {
      final Runnable binding = mInnerState.bind(chain);
      mState = PromiseState.Pending;
      return binding;
    }

    @NotNull
    List<Resolution<O>> consumeOutputs() {
      final ArrayList<Resolution<O>> outputs = mOutputs;
      if ((outputs.size() == 1) && mResolution.isEmpty()) {
        return Collections.emptyList();
      }

      mOutputs = new ArrayList<Resolution<O>>();
      mResolution = new ResolutionOutputs<O>();
      mOutputs.add(mResolution);
      return outputs;
    }

    @NotNull
    Object getMutex() {
      return mMutex;
    }

    @NotNull
    DoubleQueue<O> getOutputs() {
      final ArrayList<Resolution<O>> outputs = mOutputs;
      final int size = outputs.size();
      if (size == 1) {
        return outputs.get(0).getOutputs();

      } else if (size > 1) {
        for (final Resolution<O> output : outputs) {
          output.throwError();
        }
      }

      return new DoubleQueue<O>();
    }

    @NotNull
    PromiseState getState() {
      return mState;
    }

    void innerAdd(final O output) {
      getLogger().dbg("Adding promise fulfillment [%s => %s]: %s", PromiseState.Pending,
          PromiseState.Pending, output);
      mResolution.add(output);
    }

    void innerAddRejection(final Throwable reason) {
      getLogger().dbg("Rejecting promise with reason [%s => %s]: %s", PromiseState.Pending,
          PromiseState.Rejected, reason);
      final ArrayList<Resolution<O>> outputs = mOutputs;
      outputs.add(new ResolutionThrowable<O>(reason));
      mResolution = new ResolutionOutputs<O>();
      outputs.add(mResolution);
      mState = PromiseState.Rejected;
    }

    void innerResolve() {
      mInnerState.innerResolve();
      if (mState == PromiseState.Pending) {
        mState = PromiseState.Fulfilled;
      }
    }

    boolean isSettled() {
      return mInnerState.isComplete();
    }

    private class StatePending {

      @Nullable
      Runnable bind(final PromiseChain<O, ?> chain) {
        chain.prepend(consumeOutputs());
        return null;
      }

      @NotNull
      IllegalStateException exception(@NotNull final PromiseState state) {
        return new IllegalStateException("invalid state: " + state);
      }

      void innerResolve() {
        getLogger().dbg("Resolving promise with resolution [%s => %s]: %s", PromiseState.Pending,
            PromiseState.Fulfilled, mOutputs);
        mInnerState = new StateResolved();
      }

      boolean isComplete() {
        return false;
      }
    }

    private class StateResolved extends StatePending {

      @Nullable
      @Override
      Runnable bind(final PromiseChain<O, ?> chain) {
        getLogger().dbg("Binding promise [%s => %s]", PromiseState.Fulfilled, PromiseState.Pending);
        mInnerState = new StatePending();
        chain.prepend(consumeOutputs());
        return new Runnable() {

          public void run() {
            chain.resolve();
          }
        };
      }

      @Override
      void innerResolve() {
        throw exception(PromiseState.Fulfilled);
      }

      @Override
      boolean isComplete() {
        return true;
      }
    }

    @Override
    void add(final PromiseChain<O, ?> next, final O input) {
      next.add(input);
    }

    @Override
    void addAll(final PromiseChain<O, ?> next, final Iterable<O> inputs) {
      next.addAll(inputs);
    }

    @NotNull
    @Override
    ChainHead<O> copy() {
      return new ChainHead<O>();
    }

    @Override
    void addRejection(final PromiseChain<O, ?> next, final Throwable reason) {
      next.addRejection(reason);
    }

    @Override
    void resolve(final PromiseChain<O, ?> next) {
      next.resolve();
    }
  }

  private static class ChainMap<O, R> extends PromiseChain<O, R> {

    private final Handler<Throwable, Callback<R>> mErrorHandler;

    private final int mMaxBatchSize;

    private final Mapper<O, R> mOutputMapper;

    @SuppressWarnings("unchecked")
    private ChainMap(@Nullable final Mapper<O, R> outputMapper,
        @Nullable final Handler<Throwable, Callback<R>> errorHandler, final int maxBatchSize) {
      mMaxBatchSize = ConstantConditions.positive("maxBatchSize", maxBatchSize);
      mOutputMapper =
          (outputMapper != null) ? outputMapper : (Mapper<O, R>) IdentityMapper.instance();
      mErrorHandler = (errorHandler != null) ? errorHandler : new HandlerReject<R>();
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ChainProxy<O, R>(mOutputMapper, mErrorHandler, mMaxBatchSize);
    }

    private static class ChainProxy<O, R> extends SerializableProxy {

      private ChainProxy(final Mapper<O, R> outputMapper,
          final Handler<Throwable, Callback<R>> errorHandler, final int maxBatchSize) {
        super(proxy(outputMapper), proxy(errorHandler), maxBatchSize);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ChainMap<O, R>((Mapper<O, R>) args[0],
              (Handler<Throwable, Callback<R>>) args[1], (Integer) args[2]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    @Override
    void add(final PromiseChain<R, ?> next, final O input) {
      try {
        next.add(mOutputMapper.apply(input));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Promise has been cancelled");
        next.addRejectionSafe(e);

      } catch (final Throwable t) {
        InterruptedExecutionException.throwIfInterrupt(t);
        getLogger().err(t, "Error while processing input: %s", input);
        next.addRejectionSafe(t);
      }
    }

    @Override
    void addAll(final PromiseChain<R, ?> next, final Iterable<O> inputs) {
      ArrayList<R> outputs = new ArrayList<R>();
      final Iterator<O> iterator = inputs.iterator();
      @SuppressWarnings("UnnecessaryLocalVariable") final int maxBatchSize = mMaxBatchSize;
      while (iterator.hasNext()) {
        try {
          for (int i = 0; (i < maxBatchSize) && iterator.hasNext(); ++i) {
            outputs.add(mOutputMapper.apply(iterator.next()));
          }

          next.addAll(outputs);
          outputs = new ArrayList<R>();

        } catch (final Throwable t) {
          InterruptedExecutionException.throwIfInterrupt(t);
          getLogger().err(t, "Error while processing input: %s", inputs);
          try {
            next.addAll(outputs);

          } catch (final CancellationException e) {
            getLogger().wrn(e, "Promise has been cancelled");

          } catch (final Throwable reason) {
            InterruptedExecutionException.throwIfInterrupt(t);
            getLogger().wrn(reason, "Suppressed rejection");
          }

          next.addRejectionSafe(t);
        }
      }
    }

    @NotNull
    @Override
    PromiseChain<O, R> copy() {
      return new ChainMap<O, R>(mOutputMapper, mErrorHandler, mMaxBatchSize);
    }

    @Override
    void addRejection(final PromiseChain<R, ?> next, final Throwable reason) {
      try {
        mErrorHandler.accept(reason, next);

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Promise has been cancelled");
        next.addRejectionSafe(e);

      } catch (final Throwable t) {
        InterruptedExecutionException.throwIfInterrupt(t);
        getLogger().err(t, "Error while processing rejection with reason: %s", reason);
        next.addRejectionSafe(t);
      }
    }

    @Override
    void resolve(final PromiseChain<R, ?> next) {
      try {
        next.resolve();

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Promise has been cancelled");

      } catch (final Throwable t) {
        InterruptedExecutionException.throwIfInterrupt(t);
        getLogger().err(t, "Error while propagating resolution");
      }
    }
  }

  private static class ChainMapGroup<O, R> extends PromiseChain<O, R> {

    private final int mMinBatchSize;

    private final Object mMutex = new Object();

    private final Mapper<O, R> mOutputMapper;

    private DoubleQueue<R> mOutputs = new DoubleQueue<R>();

    @SuppressWarnings("unchecked")
    private ChainMapGroup(@Nullable final Mapper<O, R> outputMapper, final int minBatchSize) {
      mMinBatchSize = ConstantConditions.positive("minBatchSize", minBatchSize);
      mOutputMapper =
          (outputMapper != null) ? outputMapper : (Mapper<O, R>) IdentityMapper.instance();
    }

    private void flush(final PromiseChain<R, ?> next, final R result) {
      final DoubleQueue<R> toAdd;
      synchronized (mMutex) {
        final DoubleQueue<R> outputs = mOutputs;
        outputs.add(result);
        if (outputs.size() >= mMinBatchSize) {
          toAdd = outputs;
          mOutputs = new DoubleQueue<R>();

        } else {
          toAdd = null;
        }
      }

      if (toAdd != null) {
        next.addAll(toAdd);
      }
    }

    private void flush(final PromiseChain<R, ?> next, final List<R> results) {
      final DoubleQueue<R> toAdd;
      synchronized (mMutex) {
        final DoubleQueue<R> outputs = mOutputs;
        outputs.addAll(results);
        if (outputs.size() >= mMinBatchSize) {
          toAdd = outputs;
          mOutputs = new DoubleQueue<R>();

        } else {
          toAdd = null;
        }
      }

      if (toAdd != null) {
        next.addAll(toAdd);
      }
    }

    private void flush(final PromiseChain<R, ?> next) {
      final DoubleQueue<R> outputs;
      synchronized (mMutex) {
        outputs = mOutputs;
        mOutputs = new DoubleQueue<R>();
      }

      try {
        next.addAll(outputs);

      } catch (final Throwable t) {
        InterruptedExecutionException.throwIfInterrupt(t);
        getLogger().wrn(t, "Suppressed rejection");
      }
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ChainProxy<O, R>(mOutputMapper, mMinBatchSize);
    }

    private static class ChainProxy<O, R> extends SerializableProxy {

      private ChainProxy(final Mapper<O, R> outputMapper, final int minBatchSize) {
        super(proxy(outputMapper), minBatchSize);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ChainMapGroup<O, R>((Mapper<O, R>) args[0], (Integer) args[1]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    @Override
    void add(final PromiseChain<R, ?> next, final O input) {
      try {
        flush(next, mOutputMapper.apply(input));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Promise has been cancelled");
        flush(next);
        next.addRejectionSafe(e);

      } catch (final Throwable t) {
        InterruptedExecutionException.throwIfInterrupt(t);
        getLogger().err(t, "Error while processing input: %s", input);
        flush(next);
        next.addRejectionSafe(t);
      }
    }

    @Override
    void addAll(final PromiseChain<R, ?> next, final Iterable<O> inputs) {
      final ArrayList<R> outputs = new ArrayList<R>();
      try {
        for (final O input : inputs) {
          outputs.add(mOutputMapper.apply(input));
        }

      } catch (final Throwable t) {
        InterruptedExecutionException.throwIfInterrupt(t);
        getLogger().err(t, "Error while processing input: %s", inputs);
        try {
          flush(next, outputs);

        } catch (final CancellationException e) {
          getLogger().wrn(e, "Promise has been cancelled");

        } catch (final Throwable reason) {
          InterruptedExecutionException.throwIfInterrupt(t);
          getLogger().wrn(reason, "Suppressed rejection");
        }

        flush(next);
        next.addRejectionSafe(t);
      }
    }

    @NotNull
    @Override
    PromiseChain<O, R> copy() {
      return new ChainMapGroup<O, R>(mOutputMapper, mMinBatchSize);
    }

    @Override
    void addRejection(final PromiseChain<R, ?> next, final Throwable reason) {
      try {
        flush(next);
        next.addRejection(reason);

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Promise has been cancelled");
        next.addRejectionSafe(e);

      } catch (final Throwable t) {
        InterruptedExecutionException.throwIfInterrupt(t);
        getLogger().err(t, "Error while processing rejection with reason: %s", reason);
        next.addRejectionSafe(t);
      }
    }

    @Override
    void resolve(final PromiseChain<R, ?> next) {
      final DoubleQueue<R> outputs;
      synchronized (mMutex) {
        outputs = mOutputs;
        mOutputs = new DoubleQueue<R>();
      }

      try {
        next.addAll(outputs);
        next.resolve();

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Promise has been cancelled");

      } catch (final Throwable t) {
        InterruptedExecutionException.throwIfInterrupt(t);
        getLogger().err(t, "Error while propagating resolution");
      }
    }
  }

  private static class ChainStateful<O, R, S> extends PromiseChain<O, R> {

    private final ScheduledExecutor mExecutor;

    private final StatefulHandler<O, R, S> mHandler;

    private boolean mIsCreated;

    private boolean mIsRejected;

    private S mState;

    private ChainStateful(@NotNull final StatefulHandler<O, R, S> handler) {
      mHandler = ConstantConditions.notNull("handler", handler);
      mExecutor = ScheduledExecutors.withThrottling(ScheduledExecutors.immediateExecutor(), 1);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ChainProxy<O, R, S>(mHandler);
    }

    private static class ChainProxy<O, R, S> extends SerializableProxy {

      private ChainProxy(final StatefulHandler<O, R, S> handler) {
        super(proxy(handler));
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ChainStateful<O, R, S>((StatefulHandler<O, R, S>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    @Override
    void add(final PromiseChain<R, ?> next, final O input) {
      mExecutor.execute(new Runnable() {

        public void run() {
          if (mIsRejected) {
            getLogger().wrn("Ignoring fulfillment: %s", input);
            return;
          }

          try {
            final StatefulHandler<O, R, S> handler = mHandler;
            if (!mIsCreated) {
              mIsCreated = true;
              mState = handler.create(next);
            }

            mState = handler.fulfill(mState, input, next);

          } catch (final CancellationException e) {
            getLogger().wrn(e, "Promise has been cancelled");
            mIsRejected = true;
            next.reject(e);

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            getLogger().err(t, "Error while processing fulfillment: %s", input);
            mIsRejected = true;
            next.reject(t);
          }
        }
      });
    }

    @Override
    void addAll(final PromiseChain<R, ?> next, final Iterable<O> inputs) {
      mExecutor.execute(new Runnable() {

        public void run() {
          if (mIsRejected) {
            getLogger().wrn("Ignoring fulfillments: %s", Iterables.toString(inputs));
            return;
          }

          try {
            final StatefulHandler<O, R, S> handler = mHandler;
            if (!mIsCreated) {
              mIsCreated = true;
              mState = handler.create(next);
            }

            for (final O input : inputs) {
              mState = handler.fulfill(mState, input, next);
            }

          } catch (final CancellationException e) {
            getLogger().wrn(e, "Promise has been cancelled");
            mIsRejected = true;
            next.reject(e);

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            getLogger().err(t, "Error while processing fulfillments: %s",
                Iterables.toString(inputs));
            mIsRejected = true;
            next.reject(t);
          }
        }
      });
    }

    @NotNull
    @Override
    PromiseChain<O, R> copy() {
      return new ChainStateful<O, R, S>(mHandler);
    }

    @Override
    void addRejection(final PromiseChain<R, ?> next, final Throwable reason) {
      mExecutor.execute(new Runnable() {

        public void run() {
          if (mIsRejected) {
            getLogger().wrn(reason, "Ignoring rejection");
            return;
          }

          try {
            final StatefulHandler<O, R, S> handler = mHandler;
            if (!mIsCreated) {
              mIsCreated = true;
              mState = handler.create(next);
            }

            mState = handler.reject(mState, reason, next);

          } catch (final CancellationException e) {
            getLogger().wrn(e, "Promise has been cancelled");
            mIsRejected = true;
            next.reject(e);

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            getLogger().err(t, "Error while processing rejection with reason: %s", reason);
            mIsRejected = true;
            next.reject(t);
          }
        }
      });
    }

    @Override
    void resolve(final PromiseChain<R, ?> next) {
      mExecutor.execute(new Runnable() {

        public void run() {
          if (mIsRejected) {
            getLogger().wrn("Ignoring resolution");
            return;
          }

          try {
            final StatefulHandler<O, R, S> handler = mHandler;
            if (!mIsCreated) {
              mIsCreated = true;
              mState = handler.create(next);
            }

            handler.resolve(mState, next);

          } catch (final CancellationException e) {
            getLogger().wrn(e, "Promise has been cancelled");
            mIsRejected = true;
            next.reject(e);

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            getLogger().err(t, "Error while processing resolution");
            mIsRejected = true;
            next.reject(t);
          }
        }
      });
    }
  }

  private static class ChainStatefulSorted<O, R, S> extends PromiseChain<O, R> {

    private final ScheduledExecutor mExecutor;

    private final StatefulHandler<O, R, S> mHandler;

    private final AtomicBoolean mIsResolved = new AtomicBoolean(false);

    private final Object mMutex = new Object();

    private final NestedQueue<Resolution<R>> mQueue = new NestedQueue<Resolution<R>>();

    private boolean mIsCreated;

    private boolean mIsRejected;

    private S mState;

    private ChainStatefulSorted(@NotNull final StatefulHandler<O, R, S> handler) {
      mHandler = ConstantConditions.notNull("handler", handler);
      mExecutor = ScheduledExecutors.withThrottling(ScheduledExecutors.immediateExecutor(), 1);
    }

    // TODO: 31/07/2017 find tradeoff and switch between transferTo and removeFirst
    private void flushQueue(final PromiseChain<R, ?> next) {
      Resolution<R> resolution = removeFirst();
      try {
        while (resolution != null) {
          resolution.consume(next);
          resolution = removeFirst();
        }

      } finally {
        synchronized (mMutex) {
          mQueue.clear();
        }
      }

      final boolean closed;
      synchronized (mMutex) {
        final NestedQueue<Resolution<R>> queue = mQueue;
        closed = queue.isEmpty() && queue.isClosed();
      }

      if (closed && !mIsResolved.getAndSet(true)) {
        try {
          next.resolve();

        } catch (final CancellationException e) {
          getLogger().wrn(e, "Promise has been cancelled");

        } catch (final Throwable t) {
          InterruptedExecutionException.throwIfInterrupt(t);
          getLogger().err(t, "Error while propagating resolution");
        }
      }
    }

    private Resolution<R> removeFirst() {
      synchronized (mMutex) {
        final NestedQueue<Resolution<R>> queue = mQueue;
        if (queue.isEmpty()) {
          return null;
        }

        return queue.removeFirst();
      }
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ChainProxy<O, R, S>(mHandler);
    }

    private static class ChainProxy<O, R, S> extends SerializableProxy {

      private ChainProxy(final StatefulHandler<O, R, S> handler) {
        super(proxy(handler));
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ChainStatefulSorted<O, R, S>((StatefulHandler<O, R, S>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    private class ChainCallback implements CallbackIterable<R> {

      private final PromiseChain<R, ?> mNext;

      private ChainCallback(final PromiseChain<R, ?> next) {
        mNext = next;
      }

      public void defer(@NotNull final Chainable<R> chainable) {
        addDeferred(chainable);
        resolve();
      }

      public void resolve(final R output) {
        synchronized (mMutex) {
          final NestedQueue<Resolution<R>> queue = mQueue;
          queue.add(new ResolutionOutput<R>(output));
          queue.close();
        }

        flushQueue(mNext);
      }

      public void add(final R output) {
        synchronized (mMutex) {
          mQueue.add(new ResolutionOutput<R>(output));
        }

        flushQueue(mNext);
      }

      public void reject(final Throwable reason) {
        synchronized (mMutex) {
          final NestedQueue<Resolution<R>> queue = mQueue;
          queue.add(new ResolutionThrowable<R>(reason));
          queue.close();
        }

        try {
          flushQueue(mNext);

        } catch (final CancellationException e) {
          getLogger().wrn(e, "Promise has been cancelled");

        } catch (final Throwable t) {
          InterruptedExecutionException.throwIfInterrupt(t);
          getLogger().err(reason, "Suppressed rejection");
        }
      }

      @SuppressWarnings("unchecked")
      public void addAllDeferred(@NotNull final Chainable<? extends Iterable<R>> chainable) {
        final NestedQueue<Resolution<R>> queue;
        synchronized (mMutex) {
          queue = mQueue.addNested();
        }

        if (chainable instanceof ChainableIterable) {
          ((ChainableIterable<R>) chainable).then(new Handler<R, CallbackIterable<Void>>() {

            public void accept(final R input, final CallbackIterable<Void> callback) {
              synchronized (mMutex) {
                queue.add(new ResolutionOutput<R>(input));
              }

              flushQueue(mNext);
            }
          }, new Handler<Throwable, CallbackIterable<Void>>() {

            public void accept(final Throwable reason, final CallbackIterable<Void> callback) {
              synchronized (mMutex) {
                queue.add(new ResolutionThrowable<R>(reason));
              }

              flushQueue(mNext);
            }
          }, new Observer<CallbackIterable<Void>>() {

            public void accept(final CallbackIterable<Void> callback) {
              synchronized (mMutex) {
                queue.close();
              }

              flushQueue(mNext);
            }
          });

        } else {
          ((Chainable<Iterable<R>>) chainable).then(new Handler<Iterable<R>, Callback<Void>>() {

            public void accept(final Iterable<R> input, final Callback<Void> callback) {
              synchronized (mMutex) {
                queue.add(new ResolutionOutputs<R>(input));
                queue.close();
              }

              flushQueue(mNext);

            }
          }, new Handler<Throwable, Callback<Void>>() {

            public void accept(final Throwable reason, final Callback<Void> callback) {
              synchronized (mMutex) {
                queue.add(new ResolutionThrowable<R>(reason));
                queue.close();
              }

              flushQueue(mNext);
            }
          });
        }
      }

      public void addAll(@Nullable final Iterable<R> outputs) {
        synchronized (mMutex) {
          mQueue.add(new ResolutionOutputs<R>(outputs));
        }

        flushQueue(mNext);
      }

      @SuppressWarnings("unchecked")
      public void addAllDeferred(@Nullable final Iterable<? extends Chainable<?>> chainables) {
        if (chainables == null) {
          return;
        }

        for (final Chainable<?> chainable : chainables) {
          if (chainable instanceof ChainableIterable) {
            addAllDeferred((ChainableIterable<R>) chainable);

          } else {
            addDeferred((Chainable<R>) chainable);
          }
        }
      }

      public void addDeferred(@NotNull final Chainable<R> chainable) {
        final NestedQueue<Resolution<R>> queue;
        synchronized (mMutex) {
          queue = mQueue.addNested();
        }

        chainable.then(new Handler<R, Callback<Void>>() {

          public void accept(final R input, final Callback<Void> callback) {
            synchronized (mMutex) {
              queue.add(new ResolutionOutput<R>(input));
              queue.close();
            }

            flushQueue(mNext);

          }
        }, new Handler<Throwable, Callback<Void>>() {

          public void accept(final Throwable reason, final Callback<Void> callback) {
            synchronized (mMutex) {
              queue.add(new ResolutionThrowable<R>(reason));
              queue.close();
            }

            flushQueue(mNext);

          }
        });
      }

      public void addRejection(final Throwable reason) {
        synchronized (mMutex) {
          mQueue.add(new ResolutionThrowable<R>(reason));
        }

        flushQueue(mNext);
      }

      public void resolve() {
        synchronized (mMutex) {
          mQueue.close();
        }

        flushQueue(mNext);
      }
    }

    @Override
    void add(final PromiseChain<R, ?> next, final O input) {
      mExecutor.execute(new Runnable() {

        public void run() {
          if (mIsRejected) {
            getLogger().wrn("Ignoring fulfillment: %s", input);
            return;
          }

          try {
            final ChainCallback callback = new ChainCallback(next);
            final StatefulHandler<O, R, S> handler = mHandler;
            if (!mIsCreated) {
              mIsCreated = true;
              mState = handler.create(callback);
            }

            mState = handler.fulfill(mState, input, callback);

          } catch (final CancellationException e) {
            getLogger().wrn(e, "Promise has been cancelled");
            mIsRejected = true;
            next.reject(e);

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            getLogger().err(t, "Error while processing fulfillment: %s", input);
            mIsRejected = true;
            next.reject(t);
          }
        }
      });
    }

    @Override
    void addAll(final PromiseChain<R, ?> next, final Iterable<O> inputs) {
      mExecutor.execute(new Runnable() {

        public void run() {
          if (mIsRejected) {
            getLogger().wrn("Ignoring fulfillments: %s", Iterables.toString(inputs));
            return;
          }

          try {
            final ChainCallback callback = new ChainCallback(next);
            final StatefulHandler<O, R, S> handler = mHandler;
            if (!mIsCreated) {
              mIsCreated = true;
              mState = handler.create(callback);
            }

            for (final O input : inputs) {
              mState = handler.fulfill(mState, input, callback);
            }

          } catch (final CancellationException e) {
            getLogger().wrn(e, "Promise has been cancelled");
            mIsRejected = true;
            next.reject(e);

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            getLogger().err(t, "Error while processing fulfillments: %s",
                Iterables.toString(inputs));
            mIsRejected = true;
            next.reject(t);
          }
        }
      });
    }

    @NotNull
    @Override
    PromiseChain<O, R> copy() {
      return new ChainStatefulSorted<O, R, S>(mHandler);
    }

    @Override
    void addRejection(final PromiseChain<R, ?> next, final Throwable reason) {
      mExecutor.execute(new Runnable() {

        public void run() {
          if (mIsRejected) {
            getLogger().wrn(reason, "Ignoring rejection");
            return;
          }

          try {
            final ChainCallback callback = new ChainCallback(next);
            final StatefulHandler<O, R, S> handler = mHandler;
            if (!mIsCreated) {
              mIsCreated = true;
              mState = handler.create(callback);
            }

            mState = handler.reject(mState, reason, callback);

          } catch (final CancellationException e) {
            getLogger().wrn(e, "Promise has been cancelled");
            mIsRejected = true;
            next.reject(e);

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            getLogger().err(t, "Error while processing rejection with reason: %s", reason);
            mIsRejected = true;
            next.reject(t);
          }
        }
      });
    }

    @Override
    void resolve(final PromiseChain<R, ?> next) {
      mExecutor.execute(new Runnable() {

        public void run() {
          if (mIsRejected) {
            getLogger().wrn("Ignoring resolution");
            return;
          }

          try {
            final ChainCallback callback = new ChainCallback(next);
            final StatefulHandler<O, R, S> handler = mHandler;
            if (!mIsCreated) {
              mIsCreated = true;
              mState = handler.create(callback);
            }

            handler.resolve(mState, callback);

          } catch (final CancellationException e) {
            getLogger().wrn(e, "Promise has been cancelled");
            mIsRejected = true;
            next.reject(e);

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            getLogger().err(t, "Error while processing resolution");
            mIsRejected = true;
            next.reject(t);
          }
        }
      });
    }
  }

  private static class HandlerAll<O, R>
      implements StatefulHandler<O, R, ArrayList<O>>, Serializable {

    private final Handler<Iterable<O>, ? super CallbackIterable<R>> mFulfill;

    private final Logger mLogger;

    private final Handler<Throwable, ? super CallbackIterable<R>> mReject;

    @SuppressWarnings("unchecked")
    private HandlerAll(@Nullable final Handler<Iterable<O>, ? super CallbackIterable<R>> fulfill,
        @Nullable final Handler<Throwable, ? super CallbackIterable<R>> reject,
        @Nullable final Log log, @Nullable final Level level) {
      mFulfill = (Handler<Iterable<O>, ? super CallbackIterable<R>>) ((fulfill != null) ? fulfill
          : new HandlerResolve<O, R>());
      mReject = (Handler<Throwable, ? super CallbackIterable<R>>) ((reject != null) ? reject
          : new HandlerReject<R>());
      mLogger = Logger.newLogger(log, level, this);
    }

    public ArrayList<O> create(@NotNull final CallbackIterable<R> callback) {
      return new ArrayList<O>();
    }

    private Object writeReplace() throws ObjectStreamException {
      final Logger logger = mLogger;
      return new HandlerProxy<O, R>(mFulfill, mReject, logger.getLog(), logger.getLogLevel());
    }

    private static class HandlerProxy<O, R> extends SerializableProxy {

      private HandlerProxy(final Handler<Iterable<O>, ? super CallbackIterable<R>> fulfill,
          final Handler<Throwable, ? super CallbackIterable<R>> reject, final Log log,
          final Level level) {
        super(proxy(fulfill), proxy(reject), log, level);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new HandlerAll<O, R>((Handler<Iterable<O>, ? super CallbackIterable<R>>) args[0],
              (Handler<Throwable, ? super CallbackIterable<R>>) args[1], (Log) args[2],
              (Level) args[3]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public ArrayList<O> fulfill(final ArrayList<O> state, final O input,
        @NotNull final CallbackIterable<R> callback) {
      if (state != null) {
        state.add(input);
      }

      return state;
    }

    public ArrayList<O> reject(final ArrayList<O> state, final Throwable reason,
        @NotNull final CallbackIterable<R> callback) throws Exception {
      if (state != null) {
        mReject.accept(reason, callback);

      } else {
        mLogger.wrn(reason, "Suppressed rejection");
      }

      return null;
    }

    public void resolve(final ArrayList<O> state,
        @NotNull final CallbackIterable<R> callback) throws Exception {
      if (state != null) {
        mFulfill.accept(state, callback);

      } else {
        callback.resolve();
      }
    }
  }

  private static class HandlerAny<O, R>
      implements StatefulHandler<O, R, AnyState<R>>, Serializable {

    private final Handler<O, ? super CallbackIterable<R>> mFulfill;

    private final Logger mLogger;

    private final Handler<Throwable, ? super CallbackIterable<R>> mReject;

    @SuppressWarnings("unchecked")
    private HandlerAny(@Nullable final Handler<O, ? super CallbackIterable<R>> fulfill,
        @Nullable final Handler<Throwable, ? super CallbackIterable<R>> reject,
        @Nullable final Log log, @Nullable final Level level) {
      mFulfill = (Handler<O, ? super CallbackIterable<R>>) ((fulfill != null) ? fulfill
          : new HandlerFulfill<O, R>());
      mReject = (Handler<Throwable, ? super CallbackIterable<R>>) ((reject != null) ? reject
          : new HandlerReject<R>());
      mLogger = Logger.newLogger(log, level, this);
    }

    private Object writeReplace() throws ObjectStreamException {
      final Logger logger = mLogger;
      return new HandlerProxy<O, R>(mFulfill, mReject, logger.getLog(), logger.getLogLevel());
    }

    private static class HandlerProxy<O, R> extends SerializableProxy {

      private HandlerProxy(final Handler<O, ? super CallbackIterable<R>> fulfill,
          final Handler<Throwable, ? super CallbackIterable<R>> reject, final Log log,
          final Level level) {
        super(proxy(fulfill), proxy(reject), log, level);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new HandlerAny<O, R>((Handler<O, ? super CallbackIterable<R>>) args[0],
              (Handler<Throwable, ? super CallbackIterable<R>>) args[1], (Log) args[2],
              (Level) args[3]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public AnyState<R> create(@NotNull final CallbackIterable<R> callback) {
      return null;
    }

    public AnyState<R> fulfill(final AnyState<R> state, final O input,
        @NotNull final CallbackIterable<R> callback) throws Exception {
      if ((state == null) || state.isRejection()) {
        mFulfill.accept(input, callback);
        return new AnyStateResolved<R>();
      }

      return state;
    }

    @SuppressWarnings("unchecked")
    public AnyState<R> reject(final AnyState<R> state, final Throwable reason,
        @NotNull final CallbackIterable<R> callback) throws Exception {
      if (state == null) {
        return new AnyStateRejected<R>(reason);

      } else if (state.isRejection()) {
        ((AnyStateRejected<R>) state).add(reason);
      }

      return state;
    }

    public void resolve(final AnyState<R> state, @NotNull final CallbackIterable<R> callback) throws
        Exception {
      if (state != null) {
        state.accept(callback);

      } else {
        callback.resolve();
      }
    }
  }

  private static class HandlerApplyFulfill<O, R>
      implements Handler<O, CallbackIterable<R>>, Serializable {

    private final Log mLog;

    private final Level mLogLevel;

    private final Mapper<Promise<O>, Promise<R>> mMapper;

    private HandlerApplyFulfill(final Mapper<Promise<O>, Promise<R>> mapper, final Log log,
        final Level logLevel) {
      mMapper = ConstantConditions.notNull("mapper", mapper);
      mLog = log;
      mLogLevel = logLevel;
    }

    private Object writeReplace() throws ObjectStreamException {
      return new HandlerProxy<O, R>(mMapper, mLog, mLogLevel);
    }

    private static class HandlerProxy<O, R> extends SerializableProxy {

      private HandlerProxy(final Mapper<Promise<O>, Promise<R>> mapper, final Log log,
          final Level logLevel) {
        super(proxy(mapper), log, logLevel);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new HandlerApplyFulfill<O, R>((Mapper<Promise<O>, Promise<R>>) args[0],
              (Log) args[1], (Level) args[2]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void accept(final O input, final CallbackIterable<R> callback) throws Exception {
      callback.addDeferred(
          mMapper.apply(new DefaultPromise<O>(new ObserverResolved<O>(input), mLog, mLogLevel)));
      callback.resolve();
    }
  }

  private static class HandlerApplyReject<O, R>
      implements Handler<Throwable, CallbackIterable<R>>, Serializable {

    private final Log mLog;

    private final Level mLogLevel;

    private final Mapper<Promise<O>, Promise<R>> mMapper;

    private HandlerApplyReject(final Mapper<Promise<O>, Promise<R>> mapper, final Log log,
        final Level logLevel) {
      mMapper = ConstantConditions.notNull("mapper", mapper);
      mLog = log;
      mLogLevel = logLevel;
    }

    private Object writeReplace() throws ObjectStreamException {
      return new HandlerProxy<O, R>(mMapper, mLog, mLogLevel);
    }

    private static class HandlerProxy<O, R> extends SerializableProxy {

      private HandlerProxy(final Mapper<Promise<O>, Promise<R>> mapper, final Log log,
          final Level logLevel) {
        super(proxy(mapper), log, logLevel);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new HandlerApplyReject<O, R>((Mapper<Promise<O>, Promise<R>>) args[0],
              (Log) args[1], (Level) args[2]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void accept(final Throwable reason, final CallbackIterable<R> callback) throws
        Exception {
      callback.addDeferred(
          mMapper.apply(new DefaultPromise<O>(new ObserverRejected<O>(reason), mLog, mLogLevel)));
      callback.resolve();
    }
  }

  private static class HandlerCatchAll<O>
      implements Handler<Throwable, CallbackIterable<O>>, Serializable {

    private final Mapper<Throwable, Iterable<O>> mMapper;

    private HandlerCatchAll(@NotNull final Mapper<Throwable, Iterable<O>> mapper) {
      mMapper = ConstantConditions.notNull("mapper", mapper);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new HandlerProxy<O>(mMapper);
    }

    private static class HandlerProxy<O> extends SerializableProxy {

      private HandlerProxy(final Mapper<Throwable, Iterable<O>> mapper) {
        super(proxy(mapper));
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new HandlerCatchAll<O>((Mapper<Throwable, Iterable<O>>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void accept(final Throwable reason, final CallbackIterable<O> callback) throws
        Exception {
      callback.addAll(mMapper.apply(reason));
      callback.resolve();
    }
  }

  private static class HandlerCatchAllFiltered<O>
      implements Handler<Throwable, CallbackIterable<O>>, Serializable {

    private final Iterable<Class<? extends Throwable>> mErrors;

    private final Mapper<Throwable, Iterable<O>> mMapper;

    private HandlerCatchAllFiltered(@NotNull final Iterable<Class<? extends Throwable>> errors,
        @NotNull final Mapper<Throwable, Iterable<O>> mapper) {
      mErrors = ConstantConditions.notNull("errors", errors);
      mMapper = ConstantConditions.notNull("mapper", mapper);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new HandlerProxy<O>(mErrors, mMapper);
    }

    private static class HandlerProxy<O> extends SerializableProxy {

      private HandlerProxy(final Iterable<Class<? extends Throwable>> errors,
          final Mapper<Throwable, Iterable<O>> mapper) {
        super(errors, proxy(mapper));
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new HandlerCatchAllFiltered<O>((Iterable<Class<? extends Throwable>>) args[0],
              (Mapper<Throwable, Iterable<O>>) args[1]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void accept(final Throwable reason, final CallbackIterable<O> callback) throws
        Exception {
      for (final Class<? extends Throwable> error : mErrors) {
        if (error.isInstance(reason)) {
          callback.addAll(mMapper.apply(reason));
          callback.resolve();
          return;
        }
      }

      callback.reject(reason);
    }
  }

  private static class HandlerCatchEach<O>
      implements Handler<Throwable, CallbackIterable<O>>, Serializable {

    private final Mapper<Throwable, O> mMapper;

    private HandlerCatchEach(@NotNull final Mapper<Throwable, O> mapper) {
      mMapper = ConstantConditions.notNull("mapper", mapper);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new HandlerProxy<O>(mMapper);
    }

    private static class HandlerProxy<O> extends SerializableProxy {

      private HandlerProxy(final Mapper<Throwable, O> mapper) {
        super(proxy(mapper));
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new HandlerCatchEach<O>((Mapper<Throwable, O>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void accept(final Throwable reason, final CallbackIterable<O> callback) throws
        Exception {
      callback.resolve(mMapper.apply(reason));
    }
  }

  private static class HandlerCatchEachFiltered<O>
      implements Handler<Throwable, Callback<O>>, Serializable {

    private final Iterable<Class<? extends Throwable>> mErrors;

    private final Mapper<Throwable, O> mMapper;

    private HandlerCatchEachFiltered(@NotNull final Iterable<Class<? extends Throwable>> errors,
        @NotNull final Mapper<Throwable, O> mapper) {
      mErrors = ConstantConditions.notNull("errors", errors);
      mMapper = ConstantConditions.notNull("mapper", mapper);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new HandlerProxy<O>(mErrors, mMapper);
    }

    private static class HandlerProxy<O> extends SerializableProxy {

      private HandlerProxy(final Iterable<Class<? extends Throwable>> errors,
          final Mapper<Throwable, O> mapper) {
        super(errors, proxy(mapper));
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new HandlerCatchEachFiltered<O>((Iterable<Class<? extends Throwable>>) args[0],
              (Mapper<Throwable, O>) args[1]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void accept(final Throwable reason, final Callback<O> callback) throws Exception {
      for (final Class<? extends Throwable> error : mErrors) {
        if (error.isInstance(reason)) {
          callback.resolve(mMapper.apply(reason));
          return;
        }
      }

      callback.reject(reason);
    }
  }

  private static class HandlerCatchMap<O> implements Handler<Throwable, Callback<O>>, Serializable {

    private final Mapper<Throwable, O> mMapper;

    private HandlerCatchMap(@NotNull final Mapper<Throwable, O> mapper) {
      mMapper = ConstantConditions.notNull("mapper", mapper);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new HandlerProxy<O>(mMapper);
    }

    private static class HandlerProxy<O> extends SerializableProxy {

      private HandlerProxy(final Mapper<Throwable, O> mapper) {
        super(proxy(mapper));
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new HandlerCatchMap<O>((Mapper<Throwable, O>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void accept(final Throwable reason, final Callback<O> callback) throws Exception {
      callback.resolve(mMapper.apply(reason));
    }
  }

  private static class HandlerFulfill<O, R> implements Handler<O, Callback<R>>, Serializable {

    @SuppressWarnings("unchecked")
    public void accept(final O input, @NotNull final Callback<R> callback) {
      callback.resolve((R) input);
    }
  }

  private static class HandlerFulfilledAll<O>
      implements Handler<Iterable<O>, CallbackIterable<O>>, Serializable {

    private final Observer<Iterable<O>> mObserver;

    private HandlerFulfilledAll(@NotNull final Observer<Iterable<O>> observer) {
      mObserver = ConstantConditions.notNull("observer", observer);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new HandlerProxy<O>(mObserver);
    }

    private static class HandlerProxy<O> extends SerializableProxy {

      private HandlerProxy(final Observer<Iterable<O>> observer) {
        super(proxy(observer));
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new HandlerFulfilledAll<O>((Observer<Iterable<O>>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void accept(final Iterable<O> input, final CallbackIterable<O> callback) throws
        Exception {
      mObserver.accept(input);
      callback.resolve();
    }
  }

  private static class HandlerInspectFulfill<O>
      implements Handler<O, Callback<PromiseInspection<O>>>, Serializable {

    public void accept(final O input, final Callback<PromiseInspection<O>> callback) {
      callback.resolve(new PromiseInspectionFulfilled<O>(input));
    }
  }

  private static class HandlerInspectFulfillAll<O>
      implements Handler<Iterable<O>, CallbackIterable<PromiseInspection<O>>>, Serializable {

    public void accept(final Iterable<O> inputs,
        final CallbackIterable<PromiseInspection<O>> callback) {
      for (final O input : inputs) {
        callback.add(new PromiseInspectionFulfilled<O>(input));
      }

      callback.resolve();
    }
  }

  private static class HandlerInspectReject<O>
      implements Handler<Throwable, Callback<PromiseInspection<O>>>, Serializable {

    public void accept(final Throwable reason, final Callback<PromiseInspection<O>> callback) {
      callback.resolve(new PromiseInspectionRejected<O>(reason));
    }
  }

  private static class HandlerMapAll<O, R>
      implements Handler<Iterable<O>, CallbackIterable<R>>, Serializable {

    private final Mapper<Iterable<O>, Iterable<R>> mMapper;

    private HandlerMapAll(final Mapper<Iterable<O>, Iterable<R>> mapper) {
      mMapper = ConstantConditions.notNull("mapper", mapper);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new HandlerProxy<O, R>(mMapper);
    }

    private static class HandlerProxy<O, R> extends SerializableProxy {

      private HandlerProxy(final Mapper<Iterable<O>, Iterable<R>> mapper) {
        super(proxy(mapper));
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new HandlerMapAll<O, R>((Mapper<Iterable<O>, Iterable<R>>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void accept(final Iterable<O> input, final CallbackIterable<R> callback) throws
        Exception {
      callback.addAll(mMapper.apply(input));
      callback.resolve();
    }
  }

  private static class HandlerMapEach<O, R>
      implements Handler<O, CallbackIterable<R>>, Serializable {

    private final Mapper<O, R> mMapper;

    private HandlerMapEach(final Mapper<O, R> mapper) {
      mMapper = ConstantConditions.notNull("mapper", mapper);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new HandlerProxy<O, R>(mMapper);
    }

    private static class HandlerProxy<O, R> extends SerializableProxy {

      private HandlerProxy(final Mapper<O, R> mapper) {
        super(proxy(mapper));
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new HandlerMapEach<O, R>((Mapper<O, R>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void accept(final O input, final CallbackIterable<R> callback) throws Exception {
      callback.add(mMapper.apply(input));
      callback.resolve();
    }
  }

  private static class HandlerReject<R> implements Handler<Throwable, Callback<R>>, Serializable {

    public void accept(final Throwable reason, @NotNull final Callback<R> callback) {
      callback.reject(reason);
    }
  }

  private static class HandlerRejectedAll<O>
      implements Handler<Throwable, CallbackIterable<O>>, Serializable {

    private final Observer<Throwable> mObserver;

    private HandlerRejectedAll(@NotNull final Observer<Throwable> observer) {
      mObserver = ConstantConditions.notNull("observer", observer);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new HandlerProxy<O>(mObserver);
    }

    private static class HandlerProxy<O> extends SerializableProxy {

      private HandlerProxy(final Observer<Throwable> observer) {
        super(proxy(observer));
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new HandlerRejectedAll<O>((Observer<Throwable>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void accept(final Throwable reason, final CallbackIterable<O> callback) throws
        Exception {
      mObserver.accept(reason);
      callback.reject(reason);
    }
  }

  private static class HandlerRejectedEach<O>
      implements Handler<Throwable, CallbackIterable<O>>, Serializable {

    private final Observer<Throwable> mObserver;

    private HandlerRejectedEach(@NotNull final Observer<Throwable> observer) {
      mObserver = ConstantConditions.notNull("observer", observer);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new HandlerProxy<O>(mObserver);
    }

    private static class HandlerProxy<O> extends SerializableProxy {

      private HandlerProxy(final Observer<Throwable> observer) {
        super(proxy(observer));
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new HandlerRejectedEach<O>((Observer<Throwable>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void accept(final Throwable reason, final CallbackIterable<O> callback) throws
        Exception {
      mObserver.accept(reason);
      callback.reject(reason);
    }
  }

  private static class HandlerResolve<O, R>
      implements Handler<Iterable<O>, CallbackIterable<R>>, Serializable {

    @SuppressWarnings("unchecked")
    public void accept(final Iterable<O> input, final CallbackIterable<R> callback) {
      callback.addAll((Iterable<R>) input);
      callback.resolve();
    }
  }

  private static class HandlerResolvedAll<O>
      implements Observer<CallbackIterable<O>>, Serializable {

    private final Action mAction;

    private HandlerResolvedAll(@NotNull final Action action) {
      mAction = ConstantConditions.notNull("action", action);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new HandlerProxy<O>(mAction);
    }

    private static class HandlerProxy<O> extends SerializableProxy {

      private HandlerProxy(final Action action) {
        super(proxy(action));
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new HandlerResolvedAll<O>((Action) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void accept(final CallbackIterable<O> callback) throws Exception {
      mAction.perform();
      callback.resolve();
    }
  }

  private static class HandlerTry<O, R> implements Handler<O, CallbackIterable<R>>, Serializable {

    private final Handler<O, ? super CallbackIterable<R>> mHandler;

    private final Logger mLogger;

    private HandlerTry(@NotNull final Handler<O, ? super CallbackIterable<R>> handler,
        @Nullable final Log log, @Nullable final Level level) {
      mHandler = ConstantConditions.notNull("handler", handler);
      mLogger = Logger.newLogger(log, level, this);
    }

    private Object writeReplace() throws ObjectStreamException {
      final Logger logger = mLogger;
      return new HandlerProxy<O, R>(mHandler, logger.getLog(), logger.getLogLevel());
    }

    private static class HandlerProxy<O, R> extends SerializableProxy {

      private HandlerProxy(final Handler<O, ? super CallbackIterable<R>> handler, final Log log,
          final Level level) {
        super(proxy(handler), log, level);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new HandlerTry<O, R>((Handler<O, ? super CallbackIterable<R>>) args[0],
              (Log) args[1], (Level) args[2]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void accept(final O input, final CallbackIterable<R> callback) throws Exception {
      try {
        mHandler.accept(input, new TryCallback<R>(input, callback, mLogger));

      } catch (final Throwable t) {
        safeClose(input, mLogger);
        InterruptedExecutionException.throwIfInterrupt(t);
        throw RejectionException.wrapIfNotException(t);
      }
    }
  }

  private static class HandlerTryIterable<O, R>
      implements Handler<Iterable<O>, CallbackIterable<R>>, Serializable {

    private final Handler<Iterable<O>, ? super CallbackIterable<R>> mHandler;

    private final Logger mLogger;

    private HandlerTryIterable(
        @NotNull final Handler<Iterable<O>, ? super CallbackIterable<R>> handler,
        @Nullable final Log log, @Nullable final Level level) {
      mHandler = ConstantConditions.notNull("handler", handler);
      mLogger = Logger.newLogger(log, level, this);
    }

    private Object writeReplace() throws ObjectStreamException {
      final Logger logger = mLogger;
      return new HandlerProxy<O, R>(mHandler, logger.getLog(), logger.getLogLevel());
    }

    private static class HandlerProxy<O, R> extends SerializableProxy {

      private HandlerProxy(final Handler<Iterable<O>, ? super CallbackIterable<R>> handler,
          final Log log, final Level level) {
        super(proxy(handler), log, level);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new HandlerTryIterable<O, R>(
              (Handler<Iterable<O>, ? super CallbackIterable<R>>) args[0], (Log) args[1],
              (Level) args[2]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void accept(final Iterable<O> inputs, final CallbackIterable<R> callback) throws
        Exception {
      try {
        mHandler.accept(inputs, new TryCallbackIterable<R>(inputs, callback, mLogger));

      } catch (final Throwable t) {
        safeClose(inputs, mLogger);
        InterruptedExecutionException.throwIfInterrupt(t);
        throw RejectionException.wrapIfNotException(t);
      }
    }
  }

  private static class MapperFulfilledEach<O> implements Mapper<O, O>, Serializable {

    private final Observer<O> mObserver;

    private MapperFulfilledEach(@NotNull final Observer<O> observer) {
      mObserver = ConstantConditions.notNull("observer", observer);
    }

    public O apply(final O input) throws Exception {
      mObserver.accept(input);
      return input;
    }

    private Object writeReplace() throws ObjectStreamException {
      return new HandlerProxy<O>(mObserver);
    }

    private static class HandlerProxy<O> extends SerializableProxy {

      private HandlerProxy(final Observer<O> observer) {
        super(proxy(observer));
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new MapperFulfilledEach<O>((Observer<O>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }
  }

  private static class MapperTry<O, R> implements Mapper<O, R>, Serializable {

    private final Logger mLogger;

    private final Mapper<O, R> mMapper;

    private MapperTry(@NotNull final Mapper<O, R> mapper, @Nullable final Log log,
        @Nullable final Level level) {
      mMapper = ConstantConditions.notNull("mapper", mapper);
      mLogger = Logger.newLogger(log, level, this);
    }

    private Object writeReplace() throws ObjectStreamException {
      final Logger logger = mLogger;
      return new MapperProxy<O, R>(mMapper, logger.getLog(), logger.getLogLevel());
    }

    private static class MapperProxy<O, R> extends SerializableProxy {

      private MapperProxy(final Mapper<O, R> mapper, final Log log, final Level level) {
        super(proxy(mapper), log, level);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new MapperTry<O, R>((Mapper<O, R>) args[0], (Log) args[1], (Level) args[2]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public R apply(final O input) throws Exception {
      final R output;
      try {
        output = mMapper.apply(input);

      } catch (final Throwable t) {
        safeClose(input, mLogger);
        InterruptedExecutionException.throwIfInterrupt(t);
        throw RejectionException.wrapIfNotException(t);
      }

      close(input, mLogger);
      return output;
    }
  }

  private static abstract class PromiseChain<I, O> implements CallbackIterable<I>, Serializable {

    private transient final Object mMutex = new Object();

    private transient long mDeferredCount = 1;

    private transient volatile StatePending mInnerState = new StatePending();

    private transient volatile Logger mLogger;

    private transient volatile PromiseChain<O, ?> mNext;

    abstract void add(PromiseChain<O, ?> next, I input);

    abstract void addAll(PromiseChain<O, ?> next, Iterable<I> inputs);

    abstract void addRejection(PromiseChain<O, ?> next, Throwable reason);

    final void addRejectionSafe(final Throwable reason) {
      final Runnable command;
      synchronized (mMutex) {
        command = mInnerState.addRejection(reason);
      }

      if (command != null) {
        command.run();
        addRejection(mNext, reason);
      }
    }

    boolean cancel(@Nullable final Throwable reason) {
      final Runnable command;
      synchronized (mMutex) {
        command = mInnerState.reject(reason);
      }

      if (command != null) {
        command.run();
        addRejection(mNext, reason);
        innerResolve();
        return true;
      }

      return false;
    }

    @NotNull
    abstract PromiseChain<I, O> copy();

    Logger getLogger() {
      return mLogger;
    }

    void setLogger(@NotNull final Logger logger) {
      mLogger = logger.subContextLogger(this);
    }

    boolean isTail() {
      return false;
    }

    void prepend(@NotNull final List<Resolution<I>> resolutions) {
      if (!resolutions.isEmpty()) {
        mInnerState = new StatePrepending(resolutions);
      }
    }

    abstract void resolve(PromiseChain<O, ?> next);

    void setNext(@NotNull PromiseChain<O, ?> next) {
      mNext = next;
    }

    private void innerResolve() {
      final boolean needResolve;
      synchronized (mMutex) {
        needResolve = (--mDeferredCount == 0);
      }

      if (needResolve) {
        resolve(mNext);
      }
    }

    private class StatePending implements Runnable {

      @Nullable
      Runnable add() {
        return this;
      }

      @Nullable
      Runnable addRejection(final Throwable reason) {
        return this;
      }

      @Nullable
      Runnable reject(final Throwable reason) {
        mInnerState = new StateRejected(reason);
        return this;
      }

      @Nullable
      Runnable resolve() {
        mInnerState = new StateResolved();
        return this;
      }

      public void run() {
      }
    }

    private class StatePrepending extends StatePending {

      private final List<Resolution<I>> mResolutions;

      private StatePrepending(@NotNull final List<Resolution<I>> resolutions) {
        mResolutions = resolutions;
      }

      @Nullable
      @Override
      Runnable add() {
        mInnerState = new StatePending();
        return this;
      }

      public void run() {
        for (final Resolution<I> resolution : mResolutions) {
          resolution.consume(PromiseChain.this);
        }
      }

      @Nullable
      @Override
      Runnable addRejection(final Throwable reason) {
        return add();
      }

      @Nullable
      @Override
      Runnable reject(final Throwable reason) {
        mInnerState = new StatePending();
        return new Runnable() {

          public void run() {
            StatePrepending.this.run();
            synchronized (mMutex) {
              mInnerState = new StateRejected(reason);
            }
          }
        };
      }

      @Nullable
      @Override
      Runnable resolve() {
        mInnerState = new StatePending();
        return new Runnable() {

          public void run() {
            StatePrepending.this.run();
            synchronized (mMutex) {
              mInnerState = new StateResolved();
            }
          }
        };
      }
    }

    private class StateRejected extends StatePending {

      private final Throwable mException;

      private StateRejected(final Throwable reason) {
        mException = reason;
      }

      @Nullable
      @Override
      Runnable add() {
        final Throwable exception = mException;
        mLogger.wrn("Promise has been already rejected with reason: %s", exception);
        throw RejectionException.wrapIfNotRejectionException(exception);
      }

      @Nullable
      @Override
      Runnable addRejection(final Throwable reason) {
        final Throwable exception = mException;
        mLogger.wrn("Promise has been already rejected with reason: %s", exception);
        throw RejectionException.wrapIfNotRejectionException(exception);
      }

      @Nullable
      @Override
      Runnable reject(final Throwable reason) {
        mLogger.wrn(reason, "Suppressed rejection");
        return null;
      }

      @Nullable
      @Override
      Runnable resolve() {
        final Throwable exception = mException;
        mLogger.wrn("Promise has been already rejected with reason: %s", exception);
        throw RejectionException.wrapIfNotRejectionException(exception);
      }
    }

    private class StateResolved extends StatePending {

      @Nullable
      @Override
      Runnable add() {
        mLogger.wrn("Promise has been already resolved");
        throw new IllegalStateException("promise has been already resolved");
      }

      @Nullable
      @Override
      Runnable addRejection(final Throwable reason) {
        mLogger.wrn("Promise has been already resolved");
        throw new IllegalStateException("promise has been already resolved");
      }

      @Nullable
      @Override
      Runnable reject(final Throwable reason) {
        mLogger.wrn(reason, "Suppressed rejection");
        return null;
      }

      @Nullable
      @Override
      Runnable resolve() {
        mLogger.wrn("Promise has been already resolved");
        throw new IllegalStateException("promise has been already resolved");
      }
    }

    public final void defer(@NotNull final Chainable<I> chainable) {
      addDeferred(chainable);
      resolve();
    }

    public final void resolve(final I output) {
      final Runnable command;
      synchronized (mMutex) {
        command = mInnerState.resolve();
      }

      if (command != null) {
        command.run();
        add(mNext, output);
        innerResolve();
      }
    }

    public final void addRejection(final Throwable reason) {
      final Runnable command;
      synchronized (mMutex) {
        command = mInnerState.add();
      }

      if (command != null) {
        command.run();
        addRejection(mNext, reason);
      }
    }

    public final void add(final I output) {
      final Runnable command;
      synchronized (mMutex) {
        command = mInnerState.add();
      }

      if (command != null) {
        command.run();
        add(mNext, output);
      }
    }

    public final void addAll(@Nullable final Iterable<I> outputs) {
      final Runnable command;
      synchronized (mMutex) {
        command = mInnerState.add();
      }

      if (command != null) {
        command.run();

        if (outputs != null) {
          addAll(mNext, outputs);
        }
      }
    }

    @SuppressWarnings("unchecked")
    public void addAllDeferred(@Nullable final Iterable<? extends Chainable<?>> chainables) {
      if (chainables == null) {
        return;
      }

      for (final Chainable<?> chainable : chainables) {
        if (chainable instanceof ChainableIterable) {
          addAllDeferred((ChainableIterable<I>) chainable);

        } else {
          addDeferred((Chainable<I>) chainable);
        }
      }
    }

    @SuppressWarnings("unchecked")
    public final void addAllDeferred(@NotNull final Chainable<? extends Iterable<I>> chainable) {
      final Runnable command;
      synchronized (mMutex) {
        command = mInnerState.add();
        ++mDeferredCount;
      }

      if (command != null) {
        command.run();
        if (chainable instanceof ChainableIterable) {
          ((ChainableIterable<I>) chainable).then(new Handler<I, CallbackIterable<Void>>() {

            public void accept(final I input, final CallbackIterable<Void> callback) {
              PromiseChain.this.add(mNext, input);
            }
          }, new Handler<Throwable, CallbackIterable<Void>>() {

            public void accept(final Throwable reason, final CallbackIterable<Void> callback) {
              PromiseChain.this.addRejection(mNext, reason);
            }
          }, new Observer<CallbackIterable<Void>>() {

            public void accept(final CallbackIterable<Void> callback) {
              PromiseChain.this.innerResolve();
            }
          });

        } else {
          ((Chainable<Iterable<I>>) chainable).then(new Handler<Iterable<I>, Callback<Void>>() {

            public void accept(final Iterable<I> inputs, final Callback<Void> callback) {
              if (inputs != null) {
                PromiseChain.this.addAll(mNext, inputs);
              }

              PromiseChain.this.innerResolve();
            }
          }, new Handler<Throwable, Callback<Void>>() {

            public void accept(final Throwable reason, final Callback<Void> callback) {
              PromiseChain.this.addRejection(mNext, reason);
              PromiseChain.this.innerResolve();
            }
          });
        }
      }
    }

    public final void addDeferred(@NotNull final Chainable<I> chainable) {
      final Runnable command;
      synchronized (mMutex) {
        command = mInnerState.add();
        ++mDeferredCount;
      }

      if (command != null) {
        command.run();
        chainable.then(new Handler<I, Callback<Void>>() {

          public void accept(final I input, final Callback<Void> callback) {
            PromiseChain.this.add(mNext, input);
            PromiseChain.this.innerResolve();
          }
        }, new Handler<Throwable, Callback<Void>>() {

          public void accept(final Throwable reason, final Callback<Void> callback) {
            PromiseChain.this.addRejection(mNext, reason);
            PromiseChain.this.innerResolve();
          }
        });
      }
    }

    public final void resolve() {
      final Runnable command;
      synchronized (mMutex) {
        command = mInnerState.resolve();
      }

      if (command != null) {
        command.run();
        innerResolve();
      }
    }

    public final void reject(final Throwable reason) {
      final Runnable command;
      synchronized (mMutex) {
        command = mInnerState.reject(reason);
      }

      if (command != null) {
        command.run();
        addRejection(mNext, reason);
        innerResolve();
      }
    }
  }

  private static class PromiseInspectionFulfilled<O> implements PromiseInspection<O>, Serializable {

    private final O mOutput;

    private PromiseInspectionFulfilled(final O output) {
      mOutput = output;
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

    public O value() {
      return mOutput;
    }
  }

  private static class PromiseInspectionRejected<O> implements PromiseInspection<O>, Serializable {

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

    public O value() {
      throw new IllegalStateException("the promise is not fulfilled");
    }
  }

  private static class PromiseProxy extends SerializableProxy {

    private PromiseProxy(final Observer<? extends CallbackIterable<?>> observer,
        final ScheduledExecutor fulfillExecutor, final ScheduledExecutor rejectExecutor,
        final Log log, final Level logLevel, final List<PromiseChain<?, ?>> chains) {
      super(proxy(observer), fulfillExecutor, rejectExecutor, log, logLevel, chains);
    }

    @SuppressWarnings("unchecked")
    Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        final ChainHead<Object> head = new ChainHead<Object>();
        PromiseChain<?, ?> tail = head;
        for (final PromiseChain<?, ?> chain : (List<PromiseChain<?, ?>>) args[5]) {
          ((PromiseChain<?, Object>) tail).setNext((PromiseChain<Object, ?>) chain);
          tail = chain;
        }

        return new DefaultPromiseIterable<Object>((Observer<CallbackIterable<?>>) args[0],
            (ScheduledExecutor) args[1], (ScheduledExecutor) args[2], (Log) args[3],
            (Level) args[4], head, (PromiseChain<?, Object>) tail);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }

  private static class ResolutionIterable<O> implements Iterable<O> {

    private final Iterable<O> mIterable;

    private ResolutionIterable(@NotNull final Iterable<O> iterable) {
      mIterable = iterable;
    }

    public Iterator<O> iterator() {
      return mIterable.iterator();
    }
  }

  private static class ResolutionOutput<O> implements Resolution<O> {

    private final O mOutput;

    private ResolutionOutput(final O output) {
      mOutput = output;
    }

    public void consume(@NotNull final PromiseChain<O, ?> chain) {
      chain.add(mOutput);
    }

    @Nullable
    public RejectionException getError() {
      return null;
    }

    @NotNull
    public DoubleQueue<O> getOutputs() {
      final DoubleQueue<O> outputs = new DoubleQueue<O>();
      outputs.add(mOutput);
      return outputs;
    }

    public void throwError() {
    }
  }

  private static class ResolutionOutputs<O> implements Resolution<O> {

    private final DoubleQueue<O> mOutputs = new DoubleQueue<O>();

    private ResolutionOutputs() {
    }

    private ResolutionOutputs(final Iterable<O> inputs) {
      if (inputs != null) {
        Iterables.addAll(inputs, mOutputs);
      }
    }

    void add(final O input) {
      mOutputs.add(input);
    }

    boolean isEmpty() {
      return mOutputs.isEmpty();
    }

    public void consume(@NotNull final PromiseChain<O, ?> chain) {
      chain.addAll(mOutputs);
    }

    @Nullable
    public RejectionException getError() {
      return null;
    }

    @NotNull
    public DoubleQueue<O> getOutputs() {
      return mOutputs;
    }

    public void throwError() {
    }
  }

  private static class ResolutionThrowable<O> implements Resolution<O> {

    private final Throwable mReason;

    private ResolutionThrowable(final Throwable reason) {
      mReason = reason;
    }

    public void consume(@NotNull final PromiseChain<O, ?> chain) {
      chain.addRejection(mReason);
    }

    @Nullable
    public RejectionException getError() {
      return RejectionException.wrapIfNotRejectionException(mReason);
    }

    @NotNull
    public DoubleQueue<O> getOutputs() {
      throw getError();
    }

    public void throwError() {
      throw getError();
    }
  }

  private static class ResolveObserver<R> implements Observer<CallbackIterable<R>>, Serializable {

    public void accept(final CallbackIterable<R> callback) {
      callback.resolve();
    }
  }

  private static class ScheduleFulfill<I> implements Handler<I, Callback<I>>, Serializable {

    private final ScheduledExecutor mExecutor;

    private ScheduleFulfill(@NotNull final ScheduledExecutor executor) {
      mExecutor = executor;
    }

    public void accept(final I input, final Callback<I> callback) throws Exception {
      mExecutor.execute(new Runnable() {

        public void run() {
          try {
            callback.resolve(input);

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
          }
        }
      });
    }
  }

  private static class ScheduleFulfillAll<I>
      implements Handler<Iterable<I>, CallbackIterable<I>>, Serializable {

    private final ScheduledExecutor mExecutor;

    private ScheduleFulfillAll(@NotNull final ScheduledExecutor executor) {
      mExecutor = executor;
    }

    public void accept(final Iterable<I> inputs, final CallbackIterable<I> callback) throws
        Exception {
      mExecutor.execute(new Runnable() {

        public void run() {
          try {
            callback.addAll(inputs);
            callback.resolve();

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
          }
        }
      });
    }
  }

  private static class ScheduleReject<I> implements Handler<Throwable, Callback<I>>, Serializable {

    private final ScheduledExecutor mExecutor;

    private ScheduleReject(@NotNull final ScheduledExecutor executor) {
      mExecutor = executor;
    }

    public void accept(final Throwable input, final Callback<I> callback) throws Exception {
      mExecutor.execute(new Runnable() {

        public void run() {
          callback.reject(input);
        }
      });
    }
  }

  private static class ScheduleResolve<I> implements Observer<CallbackIterable<I>>, Serializable {

    private final ScheduledExecutor mExecutor;

    private ScheduleResolve(@NotNull final ScheduledExecutor executor) {
      mExecutor = executor;
    }

    public void accept(final CallbackIterable<I> callback) throws Exception {
      mExecutor.execute(new Runnable() {

        public void run() {
          try {
            callback.resolve();

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
          }
        }
      });
    }
  }

  private static class StatefulHandlerTry<O, R, S extends Closeable>
      implements StatefulHandler<O, R, S>, Serializable {

    private final StatefulHandler<O, R, S> mHandler;

    private final Logger mLogger;

    private StatefulHandlerTry(@NotNull final StatefulHandler<O, R, S> handler,
        @Nullable final Log log, @Nullable final Level level) {
      mHandler = ConstantConditions.notNull("handler", handler);
      mLogger = Logger.newLogger(log, level, this);
    }

    private Object writeReplace() throws ObjectStreamException {
      final Logger logger = mLogger;
      return new HandlerProxy<O, R, S>(mHandler, logger.getLog(), logger.getLogLevel());
    }

    private static class HandlerProxy<O, R, S extends Closeable> extends SerializableProxy {

      private HandlerProxy(final StatefulHandler<O, R, S> handler, final Log log,
          final Level level) {
        super(proxy(handler), log, level);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new StatefulHandlerTry<O, R, S>((StatefulHandler<O, R, S>) args[0], (Log) args[1],
              (Level) args[2]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public S create(@NotNull final CallbackIterable<R> callback) throws Exception {
      return mHandler.create(callback);
    }

    public S fulfill(final S state, final O input,
        @NotNull final CallbackIterable<R> callback) throws Exception {
      try {
        return mHandler.fulfill(state, input, new TryCallback<R>(state, callback, mLogger));

      } catch (final Throwable t) {
        safeClose(state, mLogger);
        InterruptedExecutionException.throwIfInterrupt(t);
        throw RejectionException.wrapIfNotException(t);
      }
    }

    public S reject(final S state, final Throwable reason,
        @NotNull final CallbackIterable<R> callback) throws Exception {
      try {
        return mHandler.reject(state, reason, new TryCallback<R>(state, callback, mLogger));

      } catch (final Throwable t) {
        safeClose(state, mLogger);
        InterruptedExecutionException.throwIfInterrupt(t);
        throw RejectionException.wrapIfNotException(t);
      }
    }

    public void resolve(final S state, @NotNull final CallbackIterable<R> callback) throws
        Exception {
      try {
        mHandler.resolve(state, new TryCallback<R>(state, callback, mLogger));

      } catch (final Throwable t) {
        safeClose(state, mLogger);
        InterruptedExecutionException.throwIfInterrupt(t);
        throw RejectionException.wrapIfNotException(t);
      }
    }
  }

  private static class TryCallback<O> implements CallbackIterable<O> {

    private final CallbackIterable<O> mCallback;

    private final Object mCloseable;

    private final Logger mLogger;

    private TryCallback(final Object closeable, @NotNull final CallbackIterable<O> callback,
        @NotNull final Logger logger) {
      mCloseable = closeable;
      mCallback = callback;
      mLogger = logger;
    }

    public void defer(@NotNull final Chainable<O> chainable) {
      close(mCloseable, mLogger);
      mCallback.defer(chainable);
    }

    public void resolve(final O output) {
      close(mCloseable, mLogger);
      mCallback.resolve(output);
    }

    public void add(final O output) {
      mCallback.add(output);
    }

    public void addAll(@Nullable final Iterable<O> outputs) {
      mCallback.addAll(outputs);
    }

    public void addAllDeferred(@Nullable final Iterable<? extends Chainable<?>> chainables) {
      mCallback.addAllDeferred(chainables);
    }

    public void addAllDeferred(@NotNull final Chainable<? extends Iterable<O>> chainable) {
      mCallback.addAllDeferred(chainable);
    }

    public void addDeferred(@NotNull final Chainable<O> chainable) {
      mCallback.addDeferred(chainable);
    }

    public void addRejection(final Throwable reason) {
      mCallback.addRejection(reason);
    }

    public void reject(final Throwable reason) {
      close(mCloseable, mLogger);
      mCallback.reject(reason);
    }

    public void resolve() {
      close(mCloseable, mLogger);
      mCallback.resolve();
    }
  }

  private static class TryCallbackIterable<O> implements CallbackIterable<O> {

    private final CallbackIterable<O> mCallback;

    private final Iterable<?> mCloseables;

    private final Logger mLogger;

    private TryCallbackIterable(final Iterable<?> closeables,
        @NotNull final CallbackIterable<O> callback, @NotNull final Logger logger) {
      mCloseables = closeables;
      mCallback = callback;
      mLogger = logger;
    }

    public void defer(@NotNull final Chainable<O> chainable) {
      close(mCloseables, mLogger);
      mCallback.defer(chainable);
    }

    public void resolve(final O output) {
      close(mCloseables, mLogger);
      mCallback.resolve(output);
    }

    public void add(final O output) {
      mCallback.add(output);
    }

    public void addAll(@Nullable final Iterable<O> outputs) {
      mCallback.addAll(outputs);
    }

    public void addAllDeferred(@Nullable final Iterable<? extends Chainable<?>> chainables) {
      mCallback.addAllDeferred(chainables);
    }

    public void addAllDeferred(@NotNull final Chainable<? extends Iterable<O>> chainable) {
      mCallback.addAllDeferred(chainable);
    }

    public void addDeferred(@NotNull final Chainable<O> chainable) {
      mCallback.addDeferred(chainable);
    }

    public void addRejection(final Throwable reason) {
      mCallback.addRejection(reason);
    }

    public void reject(final Throwable reason) {
      close(mCloseables, mLogger);
      mCallback.reject(reason);
    }

    public void resolve() {
      close(mCloseables, mLogger);
      mCallback.resolve();
    }
  }

  private class ChainTail extends PromiseChain<O, Object> {

    private final ChainHead<O> mHead;

    private ChainTail(@NotNull final ChainHead<O> head) {
      mHead = head;
      setLogger(mLogger);
    }

    @Override
    boolean isTail() {
      return true;
    }

    @Override
    void add(final PromiseChain<Object, ?> next, final O input) {
      final PromiseChain<O, ?> chain;
      synchronized (mMutex) {
        try {
          if ((chain = mChain) == null) {
            mHead.innerAdd(input);
            return;
          }

        } finally {
          mMutex.notifyAll();
        }
      }

      chain.add(input);
    }

    @Override
    void addAll(final PromiseChain<Object, ?> next, final Iterable<O> inputs) {
      final PromiseChain<O, ?> chain;
      synchronized (mMutex) {
        try {
          if ((chain = mChain) == null) {
            @SuppressWarnings("UnnecessaryLocalVariable") final ChainHead<O> head = mHead;
            for (final O input : inputs) {
              head.innerAdd(input);
            }

            return;
          }

        } finally {
          mMutex.notifyAll();
        }
      }

      chain.addAll(inputs);
    }

    @NotNull
    @Override
    PromiseChain<O, Object> copy() {
      return ConstantConditions.unsupported();
    }

    @Override
    void addRejection(final PromiseChain<Object, ?> next, final Throwable reason) {
      final PromiseChain<O, ?> chain;
      synchronized (mMutex) {
        try {
          mState = PromiseState.Rejected;
          if ((chain = mChain) == null) {
            mHead.innerAddRejection(reason);
            return;
          }

        } finally {
          mMutex.notifyAll();
        }
      }

      chain.addRejection(reason);
    }

    @Override
    void resolve(final PromiseChain<Object, ?> next) {
      final PromiseChain<O, ?> chain;
      synchronized (mMutex) {
        try {
          if (mState == PromiseState.Pending) {
            mState = PromiseState.Fulfilled;
          }

          if ((chain = mChain) == null) {
            mHead.innerResolve();
            return;
          }

        } finally {
          mMutex.notifyAll();
        }
      }

      chain.resolve();
    }
  }

  private class PromiseIterator implements Iterator<O> {

    private final TimeUnit mOriginalTimeUnit;

    private final long mOriginalTimeout;

    private final TimeUnit mTimeUnit;

    private final long mTimeout;

    private int mIndex;

    private boolean mIsRemoved;

    private PromiseIterator(final long timeout, @NotNull final TimeUnit timeUnit) {
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
    public boolean hasNext() {
      final ChainHead<?> head = mHead;
      final long timeout = remainingTime();
      deadLockWarning(timeout);
      synchronized (mMutex) {
        final int index = mIndex;
        try {
          if (TimeUtils.waitUntil(mMutex, new Condition() {

            public boolean isTrue() {
              checkBound();
              return ((head.getOutputs().size() > index) || head.getState().isResolved());
            }
          }, timeout, mTimeUnit)) {
            final DoubleQueue<O> outputs = (DoubleQueue<O>) head.getOutputs();
            return (outputs.size() > index);
          }

        } catch (final InterruptedException e) {
          throw new InterruptedExecutionException(e);
        }
      }

      throw timeoutException();
    }

    @SuppressWarnings("unchecked")
    public O next() {
      final ChainHead<?> head = mHead;
      final long timeout = remainingTime();
      deadLockWarning(timeout);
      synchronized (mMutex) {
        final int index = mIndex;
        try {
          if (TimeUtils.waitUntil(mMutex, new Condition() {

            public boolean isTrue() {
              checkBound();
              return ((head.getOutputs().size() > index) || head.getState().isResolved());
            }
          }, timeout, mTimeUnit)) {
            final DoubleQueue<O> outputs = (DoubleQueue<O>) head.getOutputs();
            if (outputs.size() <= index) {
              throw new NoSuchElementException();
            }

            if (mIndex != index) {
              throw new ConcurrentModificationException();
            }

            ++mIndex;
            mIsRemoved = false;
            return outputs.get(index);
          }

        } catch (final InterruptedException e) {
          throw new InterruptedExecutionException(e);
        }
      }

      throw timeoutException();
    }

    @SuppressWarnings("unchecked")
    public void remove() {
      synchronized (mMutex) {
        checkBound();
        if (mIsRemoved) {
          throw new IllegalStateException("already removed");
        }

        final int index = mIndex;
        if (index == 0) {
          throw new IllegalStateException("next() not called yet");
        }

        final DoubleQueue<O> outputs = (DoubleQueue<O>) mHead.getOutputs();
        if (outputs.size() <= index) {
          throw new ConcurrentModificationException();
        }

        outputs.remove(index);
        mIsRemoved = true;
        --mIndex;
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

      @SuppressWarnings("ThrowableResultOfMethodCallIgnored") final RejectionException reason =
          getReason(0, TimeUnit.MILLISECONDS);
      return (reason != null) ? reason.getCause() : null;
    }
  }

  public Iterable<O> value() {
    synchronized (mMutex) {
      if (!isFulfilled()) {
        throw new IllegalStateException("the promise is not fulfilled");
      }

      return get(0, TimeUnit.MILLISECONDS);
    }
  }
}

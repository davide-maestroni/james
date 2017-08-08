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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import dm.james.executor.ScheduledExecutor;
import dm.james.executor.ScheduledExecutors;
import dm.james.log.Log;
import dm.james.log.Log.Level;
import dm.james.log.Logger;
import dm.james.promise.Action;
import dm.james.promise.Mapper;
import dm.james.promise.Observer;
import dm.james.promise.Promise;
import dm.james.promise.PromiseIterable;
import dm.james.promise.RejectionException;
import dm.james.promise.TimeoutException;
import dm.james.util.ConstantConditions;
import dm.james.util.InterruptedExecutionException;
import dm.james.util.SerializableProxy;
import dm.james.util.SimpleQueue;
import dm.james.util.ThreadUtils;
import dm.james.util.TimeUtils;
import dm.james.util.TimeUtils.Condition;

/**
 * Created by davide-maestroni on 07/23/2017.
 */
class DefaultPromiseIterable<O> implements PromiseIterable<O>, Serializable {

  // TODO: 26/07/2017 test rejection propagation

  private final ChainHead<?> mHead;

  private final Logger mLogger;

  private final Object mMutex;

  private final Observer<CallbackIterable<?>> mObserver;

  private final PropagationType mPropagationType;

  private final PromiseChain<?, O> mTail;

  private PromiseChain<O, ?> mBond;

  private PromiseState mState = PromiseState.Pending;

  @SuppressWarnings("unchecked")
  DefaultPromiseIterable(@NotNull final Observer<? super CallbackIterable<O>> observer,
      @Nullable final PropagationType propagationType, @Nullable final Log log,
      @Nullable final Level level) {
    mObserver = (Observer<CallbackIterable<?>>) ConstantConditions.notNull("observer", observer);
    mLogger = Logger.newLogger(log, level, this);
    mPropagationType = (propagationType != null) ? propagationType : PropagationType.LOOP;
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
      @NotNull final PropagationType propagationType, @Nullable final Log log,
      @Nullable final Level level, @NotNull final ChainHead<?> head,
      @NotNull final PromiseChain<?, O> tail) {
    // serialization
    mObserver = observer;
    mPropagationType = propagationType;
    mLogger = Logger.newLogger(log, level, this);
    mMutex = head.getMutex();
    mHead = head;
    mTail = tail;
    tail.setNext(new ChainTail((ChainHead<O>) head));
    PromiseChain<?, ?> chain = head;
    while (chain != tail) {
      chain.setLogger(mLogger);
      chain = chain.mNext;
    }

    chain.setLogger(mLogger);
    try {
      observer.accept(head);

    } catch (final Throwable t) {
      InterruptedExecutionException.throwIfInterrupt(t);
      head.reject(t);
    }
  }

  @SuppressWarnings("unchecked")
  private DefaultPromiseIterable(@NotNull final Observer<CallbackIterable<?>> observer,
      @NotNull final PropagationType propagationType, @NotNull final Logger logger,
      @NotNull final ChainHead<?> head, @NotNull final PromiseChain<?, O> tail,
      final boolean observe) {
    // copy/bind
    mObserver = observer;
    mPropagationType = propagationType;
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

  @NotNull
  public <R> PromiseIterable<R> all(@NotNull final Handler<Iterable<O>, Iterable<R>> handler) {
    return all(new HandlerStatelessIterable<O, R>(handler));
  }

  @NotNull
  public <R> PromiseIterable<R> all(
      @Nullable final ObserverHandler<Iterable<O>, ? super CallbackIterable<R>> resolve,
      @Nullable final ObserverHandler<Throwable, ? super CallbackIterable<R>> reject) {
    return all(new HandlerObserve<Iterable<O>, R>(resolve, reject));
  }

  @NotNull
  public <R> PromiseIterable<R> all(@NotNull final Mapper<Iterable<O>, Iterable<R>> mapper) {
    return all(new HandlerMapAll<O, R>(mapper));
  }

  @NotNull
  public <R> PromiseIterable<R> all(@NotNull final StatelessHandler<Iterable<O>, R> handler) {
    return then(new HandlerAll<O, R>(handler));
  }

  @NotNull
  public <R> PromiseIterable<R> allSorted(
      @Nullable final ObserverHandler<Iterable<O>, ? super CallbackIterable<R>> resolve,
      @Nullable final ObserverHandler<Throwable, ? super CallbackIterable<R>> reject) {
    return allSorted(new HandlerObserve<Iterable<O>, R>(resolve, reject));
  }

  @NotNull
  public <R> PromiseIterable<R> allSorted(@NotNull final StatelessHandler<Iterable<O>, R> handler) {
    return thenSorted(new HandlerAll<O, R>(handler));
  }

  @NotNull
  public <R> PromiseIterable<R> any(@NotNull final Handler<O, R> handler) {
    return any(new HandlerStateless<O, R>(handler));
  }

  @NotNull
  public <R> PromiseIterable<R> any(
      @Nullable final ObserverHandler<O, ? super CallbackIterable<R>> resolve,
      @Nullable final ObserverHandler<Throwable, ? super CallbackIterable<R>> reject) {
    return any(new HandlerObserve<O, R>(resolve, reject));
  }

  @NotNull
  public <R> PromiseIterable<R> any(@NotNull final Mapper<O, R> mapper) {
    return any(new HandlerMapEach<O, R>(mapper));
  }

  @NotNull
  public <R> PromiseIterable<R> any(@NotNull final StatelessHandler<O, R> handler) {
    return then(new HandlerAny<O, R>(handler));
  }

  @NotNull
  public <R> PromiseIterable<R> anySorted(
      @Nullable final ObserverHandler<O, ? super CallbackIterable<R>> resolve,
      @Nullable final ObserverHandler<Throwable, ? super CallbackIterable<R>> reject) {
    return anySorted(new HandlerObserve<O, R>(resolve, reject));
  }

  @NotNull
  public <R> PromiseIterable<R> anySorted(@NotNull final StatelessHandler<O, R> handler) {
    return thenSorted(new HandlerAny<O, R>(handler));
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
    return any(new HandlerApplyEach<O, R>(mapper, mPropagationType, logger.getLog(),
        logger.getLogLevel()));
  }

  @NotNull
  public <R> PromiseIterable<R> applyEach(@NotNull final Mapper<Promise<O>, Promise<R>> mapper) {
    final Logger logger = mLogger;
    return each(new HandlerApplyEach<O, R>(mapper, mPropagationType, logger.getLog(),
        logger.getLogLevel()));
  }

  @NotNull
  public <R> PromiseIterable<R> applyEachSorted(
      @NotNull final Mapper<Promise<O>, Promise<R>> mapper) {
    final Logger logger = mLogger;
    return eachSorted(new HandlerApplyEach<O, R>(mapper, mPropagationType, logger.getLog(),
        logger.getLogLevel()));
  }

  @NotNull
  public PromiseIterable<O> catchAny(@NotNull final Mapper<Throwable, Iterable<O>> mapper) {
    return all(new HandlerCatchAll<O>(mapper));
  }

  @NotNull
  public PromiseIterable<O> whenFulfilled(@NotNull final Observer<Iterable<O>> observer) {
    return all(new HandlerFulfilledAll<O>(observer));
  }

  @NotNull
  public PromiseIterable<O> whenRejected(@NotNull final Observer<Throwable> observer) {
    return all(new HandlerRejectedAll<O>(observer));
  }

  @NotNull
  public PromiseIterable<O> whenResolved(@NotNull final Action action) {
    return all(new HandlerResolvedAll<O>(action));
  }

  @NotNull
  public PromiseIterable<O> catchEach(final int minBatchSize,
      @NotNull final Mapper<Throwable, O> mapper) {
    return chain(new ChainMapGroup<O, O>(mPropagationType, null, mapper, minBatchSize));
  }

  @NotNull
  public PromiseIterable<O> catchEach(@NotNull final Mapper<Throwable, O> mapper) {
    return catchEach(mapper, Integer.MAX_VALUE);
  }

  @NotNull
  public PromiseIterable<O> catchEach(@NotNull final Mapper<Throwable, O> mapper,
      final int maxBatchSize) {
    return chain(new ChainMap<O, O>(mPropagationType, null, mapper, maxBatchSize));
  }

  @NotNull
  public <R> PromiseIterable<R> each(@NotNull final Handler<O, R> handler) {
    return each(new HandlerStateless<O, R>(handler));
  }

  @NotNull
  public <R> PromiseIterable<R> each(
      @Nullable final ObserverHandler<O, ? super CallbackIterable<R>> resolve,
      @Nullable final ObserverHandler<Throwable, ? super CallbackIterable<R>> reject) {
    return each(new HandlerObserve<O, R>(resolve, reject));
  }

  @NotNull
  public <R> PromiseIterable<R> each(final int minBatchSize, @NotNull final Mapper<O, R> mapper) {
    return chain(new ChainMapGroup<O, R>(mPropagationType, mapper, null, minBatchSize));
  }

  @NotNull
  public <R> PromiseIterable<R> each(@NotNull final Mapper<O, R> mapper) {
    return each(mapper, Integer.MAX_VALUE);
  }

  @NotNull
  public <R> PromiseIterable<R> each(@NotNull final Mapper<O, R> mapper, final int maxBatchSize) {
    return chain(new ChainMap<O, R>(mPropagationType, mapper, null, maxBatchSize));
  }

  @NotNull
  public <R> PromiseIterable<R> each(@NotNull final StatelessHandler<O, R> handler) {
    return chain(new ChainStateless<O, R>(mPropagationType, handler));
  }

  @NotNull
  public <R> PromiseIterable<R> eachSorted(@NotNull final Handler<O, R> handler) {
    return eachSorted(new HandlerStateless<O, R>(handler));
  }

  @NotNull
  public <R> PromiseIterable<R> eachSorted(
      @Nullable final ObserverHandler<O, ? super CallbackIterable<R>> resolve,
      @Nullable final ObserverHandler<Throwable, ? super CallbackIterable<R>> reject) {
    return eachSorted(new HandlerObserve<O, R>(resolve, reject));
  }

  @NotNull
  public <R> PromiseIterable<R> eachSorted(@NotNull final StatelessHandler<O, R> handler) {
    return chain(new ChainStatelessSorted<O, R>(mPropagationType, handler));
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
          final SimpleQueue<O> outputs = (SimpleQueue<O>) head.getOutputs();
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
          return ((SimpleQueue<O>) head.getOutputs()).peekFirst();
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
          return ((SimpleQueue<O>) head.getOutputs()).peekFirst();
        }

      } catch (final InterruptedException e) {
        throw new InterruptedExecutionException(e);
      }
    }

    return other;
  }

  @NotNull
  public Iterator<O> iterator(final long timeout, @NotNull final TimeUnit timeUnit) {
    return new PromiseIterator(timeout, timeUnit);
  }

  public O remove() {
    return remove(-1, TimeUnit.MILLISECONDS);
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
          return ((SimpleQueue<O>) head.getOutputs()).removeFirst();
        }

      } catch (final InterruptedException e) {
        throw new InterruptedExecutionException(e);
      }
    }

    throw new TimeoutException(
        "timeout while waiting for promise resolution [" + timeout + " " + timeUnit + "]");
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
          final SimpleQueue<O> outputs = (SimpleQueue<O>) head.getOutputs();
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
          return ((SimpleQueue<O>) head.getOutputs()).removeFirst();
        }

      } catch (final InterruptedException e) {
        throw new InterruptedExecutionException(e);
      }
    }

    return other;
  }

  @NotNull
  public <R, S> PromiseIterable<R> then(@NotNull final StatefulHandler<O, R, S> handler) {
    return chain(new ChainStateful<O, R, S>(mPropagationType, handler));
  }

  @NotNull
  public <R, S> PromiseIterable<R> thenSorted(@NotNull final StatefulHandler<O, R, S> handler) {
    return chain(new ChainStatefulSorted<O, R, S>(mPropagationType, handler));
  }

  public void waitComplete() {
    waitComplete(-1, TimeUnit.MILLISECONDS);
  }

  public boolean waitComplete(final long timeout, @NotNull final TimeUnit timeUnit) {
    deadLockWarning(timeout);
    synchronized (mMutex) {
      try {
        if (TimeUtils.waitUntil(mMutex, new Condition() {

          public boolean isTrue() {
            return mHead.isComplete();
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
    return any(new HandlerFulfilledEach<O>(observer));
  }

  @NotNull
  public PromiseIterable<O> whenFulfilledEach(@NotNull final Observer<O> observer) {
    return each(new HandlerFulfilledEach<O>(observer));
  }

  @NotNull
  public PromiseIterable<O> whenRejectedEach(@NotNull final Observer<Throwable> observer) {
    return each(new HandlerRejectedEach<O>(observer));
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

  @Nullable
  public RejectionException getError() {
    return getError(-1, TimeUnit.MILLISECONDS);
  }

  @Nullable
  public RejectionException getError(final long timeout, @NotNull final TimeUnit timeUnit) {
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

  public RejectionException getErrorOr(final RejectionException other, final long timeout,
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

  public boolean isBound() {
    synchronized (mMutex) {
      return (mBond != null);
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

  @NotNull
  public <R> Promise<R> then(@NotNull final Handler<Iterable<O>, R> handler) {
    return toPromise().then(handler);
  }

  @NotNull
  public <R> Promise<R> then(
      @Nullable final ObserverHandler<Iterable<O>, ? super Callback<R>> resolve,
      @Nullable final ObserverHandler<Throwable, ? super Callback<R>> reject) {
    return toPromise().then(resolve, reject);
  }

  @NotNull
  public <R> Promise<R> then(@NotNull final Mapper<Iterable<O>, R> mapper) {
    return toPromise().then(mapper);
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

  public Iterator<O> iterator() {
    return iterator(-1, TimeUnit.MILLISECONDS);
  }

  @NotNull
  @SuppressWarnings("unchecked")
  private <R> PromiseIterable<R> chain(@NotNull final PromiseChain<O, R> chain) {
    final Logger logger = mLogger;
    final ChainHead<?> head = mHead;
    final boolean isBound;
    final Runnable binding;
    final PromiseIterable<R> promise;
    synchronized (mMutex) {
      if (mBond != null) {
        isBound = true;
        promise = null;
        binding = null;

      } else {
        isBound = false;
        chain.setLogger(logger);
        binding = ((ChainHead<O>) head).bind(chain);
        mBond = chain;
        mMutex.notifyAll();
        promise =
            new DefaultPromiseIterable<R>(mObserver, mPropagationType, logger, head, chain, false);
      }
    }

    if (isBound) {
      return copy().chain(chain);
    }

    if (binding != null) {
      binding.run();
    }

    ((PromiseChain<?, Object>) mTail).setNext((PromiseChain<Object, O>) chain);
    return promise;
  }

  private void checkBound() {
    if (mBond != null) {
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

    return new DefaultPromiseIterable<O>(mObserver, mPropagationType, logger, newHead,
        (PromiseChain<?, O>) newTail, true);
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
        new DefaultDeferredPromise<Iterable<O>, Iterable<O>>(mPropagationType, logger.getLog(),
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
    return new PromiseProxy(mObserver, mPropagationType, logger.getLog(), logger.getLogLevel(),
        chains);
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

  private interface Resolution<O> {

    void consume(@NotNull PromiseChain<O, ?> chain);

    @Nullable
    RejectionException getError();

    @NotNull
    SimpleQueue<O> getOutputs();

    void throwError();
  }

  private static class ChainHead<O> extends PromiseChain<O, O> {

    private final Object mMutex = new Object();

    private StatePending mInnerState = new StatePending();

    private ArrayList<Resolution<O>> mOutputs = new ArrayList<Resolution<O>>();

    private ResolutionFulfilled<O> mResolution = new ResolutionFulfilled<O>();

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

    @Override
    void add(final PromiseChain<O, ?> next, final O input) {
      try {
        next.add(input);

      } catch (final Throwable t) {
        InterruptedExecutionException.throwIfInterrupt(t);
        getLogger().err(t, "Error while propagating resolution: %s", input);
        next.reject(t);
      }
    }

    @Nullable
    Runnable bind(final PromiseChain<O, ?> chain) {
      final Runnable binding = mInnerState.bind(chain);
      mInnerState = new StatePending();
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
      mResolution = new ResolutionFulfilled<O>();
      mOutputs.add(mResolution);
      return outputs;
    }

    @NotNull
    Object getMutex() {
      return mMutex;
    }

    @NotNull
    SimpleQueue<O> getOutputs() {
      final ArrayList<Resolution<O>> outputs = mOutputs;
      final int size = outputs.size();
      if (size == 1) {
        return outputs.get(0).getOutputs();

      } else if (size > 1) {
        for (final Resolution<O> output : outputs) {
          output.throwError();
        }
      }

      return new SimpleQueue<O>();
    }

    @NotNull
    PromiseState getState() {
      return mState;
    }

    void innerAdd(final O output) {
      getLogger().dbg("Adding promise resolution [%s => %s]: %s", PromiseState.Pending,
          PromiseState.Pending, output);
      mResolution.add(output);
    }

    void innerAddRejection(final Throwable reason) {
      getLogger().dbg("Rejecting promise with reason [%s => %s]: %s", PromiseState.Pending,
          PromiseState.Rejected, reason);
      final ArrayList<Resolution<O>> outputs = mOutputs;
      outputs.add(new ResolutionRejected<O>(reason));
      mResolution = new ResolutionFulfilled<O>();
      outputs.add(mResolution);
      mState = PromiseState.Rejected;
    }

    void innerResolve() {
      mInnerState.innerResolve();
      mState = PromiseState.Fulfilled;
    }

    boolean isComplete() {
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
    void addAll(final PromiseChain<O, ?> next, final Iterable<O> inputs) {
      try {
        next.addAll(inputs);

      } catch (final Throwable t) {
        InterruptedExecutionException.throwIfInterrupt(t);
        getLogger().err(t, "Error while propagating resolutions: %s", inputs);
        next.reject(t);
      }
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
      try {
        next.resolve();

      } catch (final Throwable t) {
        InterruptedExecutionException.throwIfInterrupt(t);
        getLogger().err(t, "Error while propagating resolution");
        next.reject(t);
      }
    }
  }

  private static class ChainMap<O, R> extends PromiseChain<O, R> {

    private final Mapper<Throwable, R> mErrorMapper;

    private final ScheduledExecutor mExecutor;

    private final int mMaxBatchSize;

    private final Mapper<O, R> mOutputMapper;

    private final PropagationType mPropagationType;

    @SuppressWarnings("unchecked")
    private ChainMap(@NotNull final PropagationType propagationType,
        @Nullable final Mapper<O, R> outputMapper, @Nullable final Mapper<Throwable, R> errorMapper,
        final int maxBatchSize) {
      mMaxBatchSize = ConstantConditions.positive("max batch size", maxBatchSize);
      mOutputMapper =
          (outputMapper != null) ? outputMapper : (Mapper<O, R>) IdentityMapper.instance();
      mErrorMapper = (errorMapper != null) ? errorMapper : RethrowMapper.<R>instance();
      mPropagationType = propagationType;
      mExecutor = propagationType.executor();
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ChainProxy<O, R>(mPropagationType, mOutputMapper, mErrorMapper, mMaxBatchSize);
    }

    private static class ChainProxy<O, R> extends SerializableProxy {

      private ChainProxy(final PropagationType propagationType, final Mapper<O, R> outputMapper,
          final Mapper<Throwable, R> errorMapper, final int maxBatchSize) {
        super(propagationType, outputMapper, errorMapper, maxBatchSize);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ChainMap<O, R>((PropagationType) args[0], (Mapper<O, R>) args[1],
              (Mapper<Throwable, R>) args[2], (Integer) args[3]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    @Override
    void add(final PromiseChain<R, ?> next, final O input) {
      mExecutor.execute(new Runnable() {

        public void run() {
          try {
            next.add(mOutputMapper.apply(input));

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            getLogger().err(t, "Error while processing input: %s", input);
            next.addRejection(t);
          }
        }
      });
    }

    @Override
    void addAll(final PromiseChain<R, ?> next, final Iterable<O> inputs) {
      mExecutor.execute(new Runnable() {

        @SuppressWarnings("WhileLoopReplaceableByForEach")
        public void run() {
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

              } catch (final Throwable reason) {
                InterruptedExecutionException.throwIfInterrupt(t);
                getLogger().wrn("Suppressed rejection with reason: %s", reason);
              }

              next.addRejection(t);
            }
          }
        }
      });
    }

    @NotNull
    @Override
    PromiseChain<O, R> copy() {
      return new ChainMap<O, R>(mPropagationType, mOutputMapper, mErrorMapper, mMaxBatchSize);
    }

    @Override
    void addRejection(final PromiseChain<R, ?> next, final Throwable reason) {
      mExecutor.execute(new Runnable() {

        public void run() {
          try {
            next.add(mErrorMapper.apply(reason));

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            getLogger().err(t, "Error while processing rejection with reason: %s", reason);
            next.addRejection(t);
          }
        }
      });
    }

    @Override
    void resolve(final PromiseChain<R, ?> next) {
      mExecutor.execute(new Runnable() {

        public void run() {
          try {
            next.resolve();

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            getLogger().err(t, "Error while propagating resolution");
            next.reject(t);
          }
        }
      });
    }
  }

  private static class ChainMapGroup<O, R> extends PromiseChain<O, R> {

    private final Mapper<Throwable, R> mErrorMapper;

    private final ScheduledExecutor mExecutor;

    private final int mMinBatchSize;

    private final Object mMutex = new Object();

    private final Mapper<O, R> mOutputMapper;

    private final PropagationType mPropagationType;

    private SimpleQueue<R> mOutputs = new SimpleQueue<R>();

    @SuppressWarnings("unchecked")
    private ChainMapGroup(@NotNull final PropagationType propagationType,
        @Nullable final Mapper<O, R> outputMapper, @Nullable final Mapper<Throwable, R> errorMapper,
        final int minBatchSize) {
      mMinBatchSize = ConstantConditions.positive("min batch size", minBatchSize);
      mOutputMapper =
          (outputMapper != null) ? outputMapper : (Mapper<O, R>) IdentityMapper.instance();
      mErrorMapper = (errorMapper != null) ? errorMapper : RethrowMapper.<R>instance();
      mPropagationType = propagationType;
      mExecutor = propagationType.executor();
    }

    private void flush(final PromiseChain<R, ?> next, final R result) {
      final SimpleQueue<R> toAdd;
      synchronized (mMutex) {
        final SimpleQueue<R> outputs = mOutputs;
        outputs.add(result);
        if (outputs.size() >= mMinBatchSize) {
          toAdd = outputs;
          mOutputs = new SimpleQueue<R>();

        } else {
          toAdd = null;
        }
      }

      if (toAdd != null) {
        next.addAll(toAdd);
      }
    }

    private void flush(final PromiseChain<R, ?> next, final List<R> results) {
      final SimpleQueue<R> toAdd;
      synchronized (mMutex) {
        final SimpleQueue<R> outputs = mOutputs;
        outputs.addAll(results);
        if (outputs.size() >= mMinBatchSize) {
          toAdd = outputs;
          mOutputs = new SimpleQueue<R>();

        } else {
          toAdd = null;
        }
      }

      if (toAdd != null) {
        next.addAll(toAdd);
      }
    }

    private void flush(final PromiseChain<R, ?> next) {
      final SimpleQueue<R> outputs;
      synchronized (mMutex) {
        outputs = mOutputs;
        mOutputs = new SimpleQueue<R>();
      }

      try {
        next.addAll(outputs);

      } catch (final Throwable t) {
        InterruptedExecutionException.throwIfInterrupt(t);
        getLogger().wrn("Suppressed rejection with reason: %s", t);
      }
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ChainProxy<O, R>(mPropagationType, mOutputMapper, mErrorMapper, mMinBatchSize);
    }

    private static class ChainProxy<O, R> extends SerializableProxy {

      private ChainProxy(final PropagationType propagationType, final Mapper<O, R> outputMapper,
          final Mapper<Throwable, R> errorMapper, final int minBatchSize) {
        super(propagationType, outputMapper, errorMapper, minBatchSize);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ChainMap<O, R>((PropagationType) args[0], (Mapper<O, R>) args[1],
              (Mapper<Throwable, R>) args[2], (Integer) args[3]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    @Override
    void add(final PromiseChain<R, ?> next, final O input) {
      mExecutor.execute(new Runnable() {

        public void run() {
          try {
            flush(next, mOutputMapper.apply(input));

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            getLogger().err(t, "Error while processing input: %s", input);
            flush(next);
            next.addRejection(t);
          }
        }
      });
    }

    @Override
    void addAll(final PromiseChain<R, ?> next, final Iterable<O> inputs) {
      mExecutor.execute(new Runnable() {

        @SuppressWarnings("WhileLoopReplaceableByForEach")
        public void run() {
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

            } catch (final Throwable reason) {
              InterruptedExecutionException.throwIfInterrupt(t);
              getLogger().wrn("Suppressed rejection with reason: %s", reason);
            }

            flush(next);
            next.addRejection(t);
          }
        }
      });
    }

    @NotNull
    @Override
    PromiseChain<O, R> copy() {
      return new ChainMap<O, R>(mPropagationType, mOutputMapper, mErrorMapper, mMinBatchSize);
    }

    @Override
    void addRejection(final PromiseChain<R, ?> next, final Throwable reason) {
      mExecutor.execute(new Runnable() {

        public void run() {
          try {
            flush(next, mErrorMapper.apply(reason));

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            getLogger().err(t, "Error while processing rejection with reason: %s", reason);
            flush(next);
            next.addRejection(t);
          }
        }
      });
    }

    @Override
    void resolve(final PromiseChain<R, ?> next) {
      mExecutor.execute(new Runnable() {

        public void run() {
          final SimpleQueue<R> outputs;
          synchronized (mMutex) {
            outputs = mOutputs;
            mOutputs = new SimpleQueue<R>();
          }

          try {
            next.addAll(outputs);
            next.resolve();

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            getLogger().err(t, "Error while propagating resolution");
            next.reject(t);
          }
        }
      });
    }
  }

  private static class ChainStateful<O, R, S> extends PromiseChain<O, R> {

    private final ScheduledExecutor mExecutor;

    private final StatefulHandler<O, R, S> mHandler;

    private final PropagationType mPropagationType;

    private boolean mIsCreated;

    private boolean mIsRejected;

    private S mState;

    private ChainStateful(@NotNull final PropagationType propagationType,
        @NotNull final StatefulHandler<O, R, S> handler) {
      mHandler = ConstantConditions.notNull("handler", handler);
      mPropagationType = propagationType;
      mExecutor = ScheduledExecutors.withThrottling(propagationType.executor(), 1);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ChainProxy<O, R, S>(mPropagationType, mHandler);
    }

    private static class ChainProxy<O, R, S> extends SerializableProxy {

      private ChainProxy(final PropagationType propagationType,
          final StatefulHandler<O, R, S> handler) {
        super(propagationType, handler);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ChainStateful<O, R, S>((PropagationType) args[0],
              (StatefulHandler<O, R, S>) args[1]);

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
            return;
          }

          try {
            final StatefulHandler<O, R, S> handler = mHandler;
            if (!mIsCreated) {
              mIsCreated = true;
              mState = handler.create(next);
            }

            mState = handler.fulfill(mState, input, next);

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            getLogger().err(t, "Error while processing input: %s", input);
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

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            getLogger().err(t, "Error while processing input: %s", inputs);
            mIsRejected = true;
            next.reject(t);
          }
        }
      });
    }

    @NotNull
    @Override
    PromiseChain<O, R> copy() {
      return new ChainStateful<O, R, S>(mPropagationType, mHandler);
    }

    @Override
    void addRejection(final PromiseChain<R, ?> next, final Throwable reason) {
      innerReject(reason);
      mExecutor.execute(new Runnable() {

        public void run() {
          if (mIsRejected) {
            return;
          }

          try {
            final StatefulHandler<O, R, S> handler = mHandler;
            if (!mIsCreated) {
              mIsCreated = true;
              mState = handler.create(next);
            }

            mState = handler.reject(mState, reason, next);

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            getLogger().err(t, "Error while processing rejection with reason: %s", reason);
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
            return;
          }

          try {
            final StatefulHandler<O, R, S> handler = mHandler;
            if (!mIsCreated) {
              mIsCreated = true;
              mState = handler.create(next);
            }

            handler.resolve(mState, next);

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

    private final PropagationType mPropagationType;

    private final NestedQueue<Resolution<R>> mQueue = new NestedQueue<Resolution<R>>();

    private boolean mIsCreated;

    private boolean mIsRejected;

    private S mState;

    private ChainStatefulSorted(@NotNull final PropagationType propagationType,
        @NotNull final StatefulHandler<O, R, S> handler) {
      mHandler = ConstantConditions.notNull("handler", handler);
      mPropagationType = propagationType;
      mExecutor = ScheduledExecutors.withThrottling(propagationType.executor(), 1);
    }

    // TODO: 31/07/2017 find tradeoff and switch between transferTo and removeFirst
    private void flushQueue(final PromiseChain<R, ?> next) {
      Resolution<R> resolution = removeFirst();
      try {
        while (resolution != null) {
          resolution.consume(next);
          resolution = removeFirst();
        }

      } catch (final Throwable t) {
        InterruptedExecutionException.throwIfInterrupt(t);
        getLogger().err(t, "Error while propagating resolutions");
        next.reject(t);
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

        } catch (final Throwable t) {
          InterruptedExecutionException.throwIfInterrupt(t);
          getLogger().err(t, "Error while propagating resolution");
          next.reject(t);
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
      return new ChainProxy<O, R, S>(mPropagationType, mHandler);
    }

    private static class ChainProxy<O, R, S> extends SerializableProxy {

      private ChainProxy(final PropagationType propagationType,
          final StatefulHandler<O, R, S> handler) {
        super(propagationType, handler);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ChainStatefulSorted<O, R, S>((PropagationType) args[0],
              (StatefulHandler<O, R, S>) args[1]);

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

      public void add(final R output) {
        synchronized (mMutex) {
          mQueue.add(new ResolutionResolved<R>(output));
        }

        flushQueue(mNext);
      }

      public void defer(@NotNull final Promise<R> promise) {
        addDeferred(promise);
        resolve();
      }

      public void reject(final Throwable reason) {
        synchronized (mMutex) {
          final NestedQueue<Resolution<R>> queue = mQueue;
          queue.add(new ResolutionRejected<R>(reason));
          queue.close();
        }

        flushQueue(mNext);
      }

      @SuppressWarnings("unchecked")
      public void addAllDeferred(@NotNull final Promise<? extends Iterable<R>> promise) {
        final NestedQueue<Resolution<R>> queue;
        synchronized (mMutex) {
          queue = mQueue.addNested();
        }

        if (promise instanceof PromiseIterable) {
          ((PromiseIterable<R>) promise).then(new ChainStatefulHandler(mNext, queue));

        } else {
          ((Promise<Iterable<R>>) promise).then(new ChainHandlerIterable(mNext, queue));
        }
      }

      public void addAll(@Nullable final Iterable<R> outputs) {
        synchronized (mMutex) {
          mQueue.add(new ResolutionFulfilled<R>(outputs));
        }

        flushQueue(mNext);
      }

      public void addDeferred(@NotNull final Promise<R> promise) {
        final NestedQueue<Resolution<R>> queue;
        synchronized (mMutex) {
          queue = mQueue.addNested();
        }

        promise.then(new ChainHandler(mNext, queue));
      }

      public void addRejection(final Throwable reason) {
        synchronized (mMutex) {
          mQueue.add(new ResolutionRejected<R>(reason));
        }

        flushQueue(mNext);
      }

      public void resolve() {
        synchronized (mMutex) {
          mQueue.close();
        }

        flushQueue(mNext);
      }

      public void resolve(final R output) {
        synchronized (mMutex) {
          final NestedQueue<Resolution<R>> queue = mQueue;
          queue.add(new ResolutionResolved<R>(output));
          queue.close();
        }

        flushQueue(mNext);
      }
    }

    private class ChainHandler implements Handler<R, Void> {

      private final PromiseChain<R, ?> mNext;

      private final NestedQueue<Resolution<R>> mQueue;

      private ChainHandler(final PromiseChain<R, ?> next, final NestedQueue<Resolution<R>> queue) {
        mNext = next;
        mQueue = queue;
      }

      public void reject(final Throwable reason, @NotNull final Callback<Void> callback) {
        synchronized (mMutex) {
          final NestedQueue<Resolution<R>> queue = mQueue;
          queue.add(new ResolutionRejected<R>(reason));
          queue.close();
        }

        flushQueue(mNext);
      }

      public void resolve(final R input, @NotNull final Callback<Void> callback) {
        synchronized (mMutex) {
          final NestedQueue<Resolution<R>> queue = mQueue;
          queue.add(new ResolutionResolved<R>(input));
          queue.close();
        }

        flushQueue(mNext);
      }
    }

    private class ChainHandlerIterable implements Handler<Iterable<R>, Void> {

      private final PromiseChain<R, ?> mNext;

      private final NestedQueue<Resolution<R>> mQueue;

      private ChainHandlerIterable(final PromiseChain<R, ?> next,
          final NestedQueue<Resolution<R>> queue) {
        mNext = next;
        mQueue = queue;
      }

      public void reject(final Throwable reason, @NotNull final Callback<Void> callback) {
        synchronized (mMutex) {
          final NestedQueue<Resolution<R>> queue = mQueue;
          queue.add(new ResolutionRejected<R>(reason));
          queue.close();
        }

        flushQueue(mNext);
      }

      public void resolve(final Iterable<R> input, @NotNull final Callback<Void> callback) {
        synchronized (mMutex) {
          final NestedQueue<Resolution<R>> queue = mQueue;
          queue.add(new ResolutionFulfilled<R>(input));
          queue.close();
        }

        flushQueue(mNext);
      }
    }

    private class ChainStatefulHandler implements StatefulHandler<R, Void, Void> {

      private final PromiseChain<R, ?> mNext;

      private final NestedQueue<Resolution<R>> mQueue;

      private ChainStatefulHandler(final PromiseChain<R, ?> next,
          final NestedQueue<Resolution<R>> queue) {
        mNext = next;
        mQueue = queue;
      }

      public Void create(@NotNull final CallbackIterable<Void> callback) {
        return null;
      }

      public Void fulfill(final Void state, final R input,
          @NotNull final CallbackIterable<Void> callback) {
        synchronized (mMutex) {
          mQueue.add(new ResolutionResolved<R>(input));
        }

        flushQueue(mNext);
        return null;
      }

      public Void reject(final Void state, final Throwable reason,
          @NotNull final CallbackIterable<Void> callback) {
        synchronized (mMutex) {
          mQueue.add(new ResolutionRejected<R>(reason));
        }

        flushQueue(mNext);
        return null;
      }

      public void resolve(final Void state, @NotNull final CallbackIterable<Void> callback) {
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

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            getLogger().err(t, "Error while processing input: %s", input);
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

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            getLogger().err(t, "Error while processing input: %s", inputs);
            mIsRejected = true;
            next.reject(t);
          }
        }
      });
    }

    @NotNull
    @Override
    PromiseChain<O, R> copy() {
      return new ChainStateful<O, R, S>(mPropagationType, mHandler);
    }

    @Override
    void addRejection(final PromiseChain<R, ?> next, final Throwable reason) {
      innerReject(reason);
      mExecutor.execute(new Runnable() {

        public void run() {
          if (mIsRejected) {
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

  private static class ChainStateless<O, R> extends PromiseChain<O, R> {

    private final AtomicLong mCallbackCount = new AtomicLong(1);

    private final StatelessHandler<O, R> mHandler;

    private final PropagationType mPropagationType;

    private ChainStateless(@NotNull final PropagationType propagationType,
        @NotNull final StatelessHandler<O, R> handler) {
      mHandler = ConstantConditions.notNull("handler", handler);
      mPropagationType = propagationType;
    }

    private void innerResolve(final PromiseChain<R, ?> next) {
      if (mCallbackCount.decrementAndGet() <= 0) {
        try {
          next.resolve();

        } catch (final Throwable t) {
          InterruptedExecutionException.throwIfInterrupt(t);
          getLogger().err(t, "Error while propagating resolution");
          next.reject(t);
        }
      }
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ChainProxy<O, R>(mPropagationType, mHandler);
    }

    private static class ChainProxy<O, R> extends SerializableProxy {

      private ChainProxy(final PropagationType propagationType,
          final StatelessHandler<O, R> handler) {
        super(propagationType, handler);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ChainStateless<O, R>((PropagationType) args[0],
              (StatelessHandler<O, R>) args[1]);

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
        try {
          mNext.add(output);

        } catch (final Throwable t) {
          InterruptedExecutionException.throwIfInterrupt(t);
          getLogger().err(t, "Error while propagating resolution: %s", output);
          mNext.reject(t);
        }
      }

      @SuppressWarnings("unchecked")
      public void addAllDeferred(@NotNull final Promise<? extends Iterable<R>> promise) {
        if (promise instanceof PromiseIterable) {
          ((PromiseIterable<R>) promise).then(new ChainStatefulHandler(mNext));

        } else {
          ((Promise<Iterable<R>>) promise).then(new ChainHandlerIterable(mNext));
        }
      }

      public void defer(@NotNull final Promise<R> promise) {
        addDeferred(promise);
        innerResolve(mNext);
      }

      public void reject(final Throwable reason) {
        addRejection(reason);
        innerResolve(mNext);
      }

      public void addAll(@Nullable final Iterable<R> outputs) {
        mNext.addAll(outputs);
      }

      public void addDeferred(@NotNull final Promise<R> promise) {
        promise.then(new ChainHandler(mNext));
      }

      public void addRejection(final Throwable reason) {
        try {
          mNext.addRejection(reason);

        } catch (final Throwable t) {
          InterruptedExecutionException.throwIfInterrupt(t);
          getLogger().err(t, "Error while propagating rejection with reason: %s", reason);
          mNext.reject(t);
        }
      }

      public void resolve() {
        innerResolve(mNext);
      }

      public void resolve(final R output) {
        add(output);
        innerResolve(mNext);
      }
    }

    private class ChainHandler implements Handler<R, Void> {

      private final PromiseChain<R, ?> mNext;

      private ChainHandler(final PromiseChain<R, ?> next) {
        mCallbackCount.incrementAndGet();
        mNext = next;
      }

      public void reject(final Throwable reason, @NotNull final Callback<Void> callback) {
        mNext.addRejection(reason);
        innerResolve(mNext);
      }

      public void resolve(final R input, @NotNull final Callback<Void> callback) {
        try {
          mNext.add(input);

        } catch (final Throwable t) {
          InterruptedExecutionException.throwIfInterrupt(t);
          getLogger().err(t, "Error while propagating resolution: %s", input);
          mNext.reject(t);
        }

        innerResolve(mNext);
      }
    }

    private class ChainHandlerIterable implements Handler<Iterable<R>, Void> {

      private final PromiseChain<R, ?> mNext;

      private ChainHandlerIterable(final PromiseChain<R, ?> next) {
        mCallbackCount.incrementAndGet();
        mNext = next;
      }

      public void reject(final Throwable reason, @NotNull final Callback<Void> callback) {
        mNext.addRejection(reason);
        innerResolve(mNext);
      }

      public void resolve(final Iterable<R> inputs, @NotNull final Callback<Void> callback) {
        try {
          mNext.addAll(inputs);

        } catch (final Throwable t) {
          InterruptedExecutionException.throwIfInterrupt(t);
          getLogger().err(t, "Error while propagating resolutions: %s", inputs);
          mNext.reject(t);
        }

        innerResolve(mNext);
      }
    }

    private class ChainStatefulHandler implements StatefulHandler<R, Void, Void> {

      private final PromiseChain<R, ?> mNext;

      private ChainStatefulHandler(final PromiseChain<R, ?> next) {
        mCallbackCount.incrementAndGet();
        mNext = next;
      }

      public Void create(@NotNull final CallbackIterable<Void> callback) {
        return null;
      }

      public Void fulfill(final Void state, final R input,
          @NotNull final CallbackIterable<Void> callback) {
        try {
          mNext.add(input);

        } catch (final Throwable t) {
          InterruptedExecutionException.throwIfInterrupt(t);
          getLogger().err(t, "Error while propagating resolution: %s", input);
          mNext.reject(t);
        }

        return null;
      }

      public Void reject(final Void state, final Throwable reason,
          @NotNull final CallbackIterable<Void> callback) {
        mNext.addRejection(reason);
        return null;
      }

      public void resolve(final Void state, @NotNull final CallbackIterable<Void> callback) {
        innerResolve(mNext);
      }
    }

    @Override
    void add(final PromiseChain<R, ?> next, final O input) {
      final ChainCallback callback = new ChainCallback(next);
      mPropagationType.execute(new Runnable() {

        public void run() {
          try {
            mHandler.resolve(input, callback);

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            getLogger().err(t, "Error while processing input: %s", input);
            callback.reject(t);
          }
        }
      });
    }

    @Override
    void addAll(final PromiseChain<R, ?> next, final Iterable<O> inputs) {
      int size;
      if (inputs instanceof Collection) {
        size = ((Collection) inputs).size();

      } else {
        size = 0;
        for (final O ignored : inputs) {
          ++size;
        }
      }

      final ChainCallback callback = new ChainCallback(next, size);
      mPropagationType.execute(new Runnable() {

        public void run() {
          for (final O input : inputs) {
            try {
              mHandler.resolve(input, callback);

            } catch (final Throwable t) {
              InterruptedExecutionException.throwIfInterrupt(t);
              getLogger().err(t, "Error while processing input: %s", input);
              callback.reject(t);
            }
          }
        }
      });
    }

    @NotNull
    @Override
    PromiseChain<O, R> copy() {
      return new ChainStateless<O, R>(mPropagationType, mHandler);
    }

    @Override
    void addRejection(final PromiseChain<R, ?> next, final Throwable reason) {
      final ChainCallback callback = new ChainCallback(next);
      mPropagationType.execute(new Runnable() {

        public void run() {
          try {
            mHandler.reject(reason, callback);

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            getLogger().err(t, "Error while processing rejection with reason: %s", reason);
            callback.reject(t);
          }
        }
      });
    }

    @Override
    void resolve(final PromiseChain<R, ?> next) {
      mPropagationType.execute(new Runnable() {

        public void run() {
          innerResolve(next);
        }
      });
    }
  }

  private static class ChainStatelessSorted<O, R> extends PromiseChain<O, R> {

    private final AtomicLong mCallbackCount = new AtomicLong(1);

    private final StatelessHandler<O, R> mHandler;

    private final Object mMutex = new Object();

    private final PropagationType mPropagationType;

    private final NestedQueue<Resolution<R>> mQueue = new NestedQueue<Resolution<R>>();

    private ChainStatelessSorted(@NotNull final PropagationType propagationType,
        @NotNull final StatelessHandler<O, R> handler) {
      mHandler = ConstantConditions.notNull("handler", handler);
      mPropagationType = propagationType;
    }

    // TODO: 31/07/2017 find tradeoff and switch between transferTo and removeFirst
    private void flushQueue(final PromiseChain<R, ?> next) {
      Resolution<R> resolution = removeFirst();
      try {
        while (resolution != null) {
          resolution.consume(next);
          resolution = removeFirst();
        }

      } catch (final Throwable t) {
        InterruptedExecutionException.throwIfInterrupt(t);
        getLogger().err(t, "Error while propagating resolutions");
        next.reject(t);
        synchronized (mMutex) {
          mQueue.clear();
        }
      }
    }

    private void innerResolve(final PromiseChain<R, ?> next) {
      if (mCallbackCount.decrementAndGet() <= 0) {
        try {
          flushQueue(next);
          next.resolve();

        } catch (final Throwable t) {
          InterruptedExecutionException.throwIfInterrupt(t);
          getLogger().err(t, "Error while propagating resolution");
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
      return new ChainProxy<O, R>(mPropagationType, mHandler);
    }

    private static class ChainProxy<O, R> extends SerializableProxy {

      private ChainProxy(final PropagationType propagationType,
          final StatelessHandler<O, R> handler) {
        super(propagationType, handler);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ChainStateless<O, R>((PropagationType) args[0],
              (StatelessHandler<O, R>) args[1]);

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
          mQueue.add(new ResolutionResolved<R>(output));
        }

        flushQueue(mNext);
      }

      @SuppressWarnings("unchecked")
      public void addAllDeferred(@NotNull final Promise<? extends Iterable<R>> promise) {
        final NestedQueue<Resolution<R>> queue;
        synchronized (mMutex) {
          queue = mQueue.addNested();
        }

        if (promise instanceof PromiseIterable) {
          ((PromiseIterable<R>) promise).then(new ChainStatefulHandler(mNext, queue));

        } else {
          ((Promise<Iterable<R>>) promise).then(new ChainHandlerIterable(mNext, queue));
        }
      }

      public void defer(@NotNull final Promise<R> promise) {
        addDeferred(promise);
        resolve();
      }

      public void reject(final Throwable reason) {
        addRejection(reason);
        innerResolve(mNext);
      }

      public void addAll(@Nullable final Iterable<R> outputs) {
        synchronized (mMutex) {
          mQueue.add(new ResolutionFulfilled<R>(outputs));
        }

        flushQueue(mNext);
      }

      public void addDeferred(@NotNull final Promise<R> promise) {
        final NestedQueue<Resolution<R>> queue;
        synchronized (mMutex) {
          queue = mQueue.addNested();
        }

        promise.then(new ChainHandler(mNext, queue));
      }

      public void addRejection(final Throwable reason) {
        synchronized (mMutex) {
          mQueue.add(new ResolutionRejected<R>(reason));
        }

        flushQueue(mNext);
      }

      public void resolve() {
        final PromiseChain<R, ?> next = mNext;
        flushQueue(next);
        innerResolve(next);
      }

      public void resolve(final R output) {
        add(output);
        innerResolve(mNext);
      }
    }

    private class ChainHandler implements Handler<R, Void> {

      private final PromiseChain<R, ?> mNext;

      private final NestedQueue<Resolution<R>> mQueue;

      private ChainHandler(final PromiseChain<R, ?> next, final NestedQueue<Resolution<R>> queue) {
        mCallbackCount.incrementAndGet();
        mNext = next;
        mQueue = queue;
      }

      public void reject(final Throwable reason, @NotNull final Callback<Void> callback) {
        synchronized (mMutex) {
          final NestedQueue<Resolution<R>> queue = mQueue;
          queue.add(new ResolutionRejected<R>(reason));
          queue.close();
        }

        final PromiseChain<R, ?> next = mNext;
        flushQueue(next);
        innerResolve(next);
      }

      public void resolve(final R input, @NotNull final Callback<Void> callback) {
        synchronized (mMutex) {
          final NestedQueue<Resolution<R>> queue = mQueue;
          queue.add(new ResolutionResolved<R>(input));
          queue.close();
        }

        final PromiseChain<R, ?> next = mNext;
        flushQueue(next);
        innerResolve(next);
      }
    }

    private class ChainHandlerIterable implements Handler<Iterable<R>, Void> {

      private final PromiseChain<R, ?> mNext;

      private final NestedQueue<Resolution<R>> mQueue;

      private ChainHandlerIterable(final PromiseChain<R, ?> next,
          final NestedQueue<Resolution<R>> queue) {
        mCallbackCount.incrementAndGet();
        mNext = next;
        mQueue = queue;
      }

      public void reject(final Throwable reason, @NotNull final Callback<Void> callback) {
        synchronized (mMutex) {
          final NestedQueue<Resolution<R>> queue = mQueue;
          queue.add(new ResolutionRejected<R>(reason));
          queue.close();
        }

        final PromiseChain<R, ?> next = mNext;
        flushQueue(next);
        innerResolve(next);
      }

      public void resolve(final Iterable<R> input, @NotNull final Callback<Void> callback) {
        synchronized (mMutex) {
          final NestedQueue<Resolution<R>> queue = mQueue;
          queue.add(new ResolutionFulfilled<R>(input));
          queue.close();
        }

        final PromiseChain<R, ?> next = mNext;
        flushQueue(next);
        innerResolve(next);
      }
    }

    private class ChainStatefulHandler implements StatefulHandler<R, Void, Void> {

      private final PromiseChain<R, ?> mNext;

      private final NestedQueue<Resolution<R>> mQueue;

      private ChainStatefulHandler(final PromiseChain<R, ?> next,
          final NestedQueue<Resolution<R>> queue) {
        mCallbackCount.incrementAndGet();
        mNext = next;
        mQueue = queue;
      }

      public Void create(@NotNull final CallbackIterable<Void> callback) {
        return null;
      }

      public Void fulfill(final Void state, final R input,
          @NotNull final CallbackIterable<Void> callback) {
        synchronized (mMutex) {
          mQueue.add(new ResolutionResolved<R>(input));
        }

        flushQueue(mNext);
        return null;
      }

      public Void reject(final Void state, final Throwable reason,
          @NotNull final CallbackIterable<Void> callback) {
        synchronized (mMutex) {
          mQueue.add(new ResolutionRejected<R>(reason));
        }

        flushQueue(mNext);
        return null;
      }

      public void resolve(final Void state, @NotNull final CallbackIterable<Void> callback) {
        synchronized (mMutex) {
          mQueue.close();
        }

        final PromiseChain<R, ?> next = mNext;
        flushQueue(next);
        innerResolve(next);
      }
    }

    @Override
    void add(final PromiseChain<R, ?> next, final O input) {
      final ChainCallback callback = new ChainCallback(next);
      mPropagationType.execute(new Runnable() {

        public void run() {
          try {
            mHandler.resolve(input, callback);

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            getLogger().err(t, "Error while processing input: %s", input);
            callback.reject(t);
          }
        }
      });
    }

    @Override
    void addAll(final PromiseChain<R, ?> next, final Iterable<O> inputs) {
      int size;
      if (inputs instanceof Collection) {
        size = ((Collection<?>) inputs).size();

      } else {
        size = 0;
        for (final O ignored : inputs) {
          ++size;
        }
      }

      final ChainCallback callback = new ChainCallback(next, size);
      mPropagationType.execute(new Runnable() {

        public void run() {
          for (final O input : inputs) {
            try {
              mHandler.resolve(input, callback);

            } catch (final Throwable t) {
              InterruptedExecutionException.throwIfInterrupt(t);
              getLogger().err(t, "Error while processing input: %s", input);
              callback.reject(t);
            }
          }
        }
      });
    }

    @NotNull
    @Override
    PromiseChain<O, R> copy() {
      return new ChainStateless<O, R>(mPropagationType, mHandler);
    }

    @Override
    void addRejection(final PromiseChain<R, ?> next, final Throwable reason) {
      final ChainCallback callback = new ChainCallback(next);
      mPropagationType.execute(new Runnable() {

        public void run() {
          try {
            mHandler.reject(reason, callback);

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            getLogger().err(t, "Error while processing rejection with reason: %s", reason);
            callback.reject(t);
          }
        }
      });
    }

    @Override
    void resolve(final PromiseChain<R, ?> next) {
      mPropagationType.execute(new Runnable() {

        public void run() {
          innerResolve(next);
        }
      });
    }
  }

  private static class DefaultStatelessHandler<I, O> implements StatelessHandler<I, O> {

    public void reject(final Throwable reason, @NotNull final CallbackIterable<O> callback) throws
        Exception {
      callback.reject(reason);
    }

    @SuppressWarnings("unchecked")
    public void resolve(final I input, @NotNull final CallbackIterable<O> callback) throws
        Exception {
      callback.resolve((O) input);
    }
  }

  private static class HandlerAll<O, R>
      implements StatefulHandler<O, R, ArrayList<O>>, Serializable {

    private final StatelessHandler<Iterable<O>, R> mHandler;

    private HandlerAll(final StatelessHandler<Iterable<O>, R> handler) {
      mHandler = ConstantConditions.notNull("handler", handler);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new HandlerProxy<O, R>(mHandler);
    }

    private static class HandlerProxy<O, R> extends SerializableProxy {

      private HandlerProxy(final StatelessHandler<Iterable<O>, R> handler) {
        super(handler);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new HandlerAll<O, R>((StatelessHandler<Iterable<O>, R>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public ArrayList<O> create(@NotNull final CallbackIterable<R> callback) {
      return new ArrayList<O>();
    }

    public ArrayList<O> fulfill(final ArrayList<O> state, final O input,
        @NotNull final CallbackIterable<R> callback) {
      state.add(input);
      return state;
    }

    public ArrayList<O> reject(final ArrayList<O> state, final Throwable reason,
        @NotNull final CallbackIterable<R> callback) throws Exception {
      mHandler.reject(reason, callback);
      return state;
    }

    public void resolve(final ArrayList<O> state,
        @NotNull final CallbackIterable<R> callback) throws Exception {
      mHandler.resolve(state, callback);
    }
  }

  private static class HandlerAny<O, R> implements StatefulHandler<O, R, Boolean>, Serializable {

    private final StatelessHandler<O, R> mHandler;

    private HandlerAny(@NotNull final StatelessHandler<O, R> handler) {
      mHandler = ConstantConditions.notNull("handler", handler);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new HandlerProxy<O, R>(mHandler);
    }

    private static class HandlerProxy<O, R> extends SerializableProxy {

      private HandlerProxy(final StatelessHandler<O, R> handler) {
        super(handler);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new HandlerAny<O, R>((StatelessHandler<O, R>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public Boolean create(@NotNull final CallbackIterable<R> callback) {
      return Boolean.TRUE;
    }

    public Boolean fulfill(final Boolean state, final O input,
        @NotNull final CallbackIterable<R> callback) throws Exception {
      if (state) {
        mHandler.resolve(input, callback);
        return Boolean.FALSE;
      }

      return state;
    }

    public Boolean reject(final Boolean state, final Throwable reason,
        @NotNull final CallbackIterable<R> callback) throws Exception {
      mHandler.reject(reason, callback);
      return state;
    }

    public void resolve(final Boolean state, @NotNull final CallbackIterable<R> callback) {
      if (state) {
        callback.resolve();
      }
    }
  }

  private static class HandlerApplyEach<O, R> implements StatelessHandler<O, R>, Serializable {

    private final Log mLog;

    private final Level mLogLevel;

    private final Mapper<Promise<O>, Promise<R>> mMapper;

    private final PropagationType mPropagationType;

    private HandlerApplyEach(final Mapper<Promise<O>, Promise<R>> mapper,
        final PropagationType propagationType, final Log log, final Level logLevel) {
      mMapper = ConstantConditions.notNull("mapper", mapper);
      mPropagationType = propagationType;
      mLog = log;
      mLogLevel = logLevel;
    }

    private Object writeReplace() throws ObjectStreamException {
      return new HandlerProxy<O, R>(mMapper, mPropagationType, mLog, mLogLevel);
    }

    private static class HandlerProxy<O, R> extends SerializableProxy {

      private HandlerProxy(final Mapper<Promise<O>, Promise<R>> mapper,
          final PropagationType propagationType, final Log log, final Level logLevel) {
        super(mapper, propagationType, log, logLevel);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new HandlerApplyEach<O, R>((Mapper<Promise<O>, Promise<R>>) args[0],
              (PropagationType) args[1], (Log) args[2], (Level) args[3]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void reject(final Throwable reason, @NotNull final CallbackIterable<R> callback) throws
        Exception {
      callback.defer(mMapper.apply(
          new DefaultPromise<O>(new RejectedObserver<O>(reason), mPropagationType, mLog,
              mLogLevel)));
    }

    public void resolve(final O input, @NotNull final CallbackIterable<R> callback) throws
        Exception {
      callback.defer(mMapper.apply(
          new DefaultPromise<O>(new ResolvedObserver<O>(input), mPropagationType, mLog,
              mLogLevel)));
    }
  }

  private static class HandlerCatchAll<O> extends IterableStatelessHandler<Iterable<O>, O>
      implements Serializable {

    private final Mapper<Throwable, Iterable<O>> mMapper;

    private HandlerCatchAll(@NotNull final Mapper<Throwable, Iterable<O>> mapper) {
      mMapper = ConstantConditions.notNull("mapper", mapper);
    }

    @Override
    public void reject(final Throwable reason, @NotNull final CallbackIterable<O> callback) throws
        Exception {
      callback.addAll(mMapper.apply(reason));
      callback.resolve();
    }

    private Object writeReplace() throws ObjectStreamException {
      return new HandlerProxy<O>(mMapper);
    }

    private static class HandlerProxy<O> extends SerializableProxy {

      private HandlerProxy(final Mapper<Throwable, Iterable<O>> mapper) {
        super(mapper);
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
  }

  private static class HandlerFulfilledAll<O> extends IterableStatelessHandler<Iterable<O>, O>
      implements Serializable {

    private final Observer<Iterable<O>> mObserver;

    private HandlerFulfilledAll(@NotNull final Observer<Iterable<O>> observer) {
      mObserver = ConstantConditions.notNull("observer", observer);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new HandlerProxy<O>(mObserver);
    }

    private static class HandlerProxy<O> extends SerializableProxy {

      private HandlerProxy(final Observer<Iterable<O>> observer) {
        super(observer);
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

    @Override
    public void resolve(final Iterable<O> input, @NotNull final CallbackIterable<O> callback) throws
        Exception {
      mObserver.accept(input);
      super.resolve(input, callback);
    }
  }

  private static class HandlerFulfilledEach<O> extends DefaultStatelessHandler<O, O>
      implements Serializable {

    private final Observer<O> mObserver;

    private HandlerFulfilledEach(@NotNull final Observer<O> observer) {
      mObserver = ConstantConditions.notNull("observer", observer);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new HandlerProxy<O>(mObserver);
    }

    private static class HandlerProxy<O> extends SerializableProxy {

      private HandlerProxy(final Observer<O> observer) {
        super(observer);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new HandlerFulfilledEach<O>((Observer<O>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    @Override
    public void resolve(final O input, @NotNull final CallbackIterable<O> callback) throws
        Exception {
      mObserver.accept(input);
      super.resolve(input, callback);
    }
  }

  private static class HandlerMapAll<O, R> extends IterableStatelessHandler<Iterable<O>, R>
      implements Serializable {

    private final Mapper<Iterable<O>, Iterable<R>> mMapper;

    private HandlerMapAll(final Mapper<Iterable<O>, Iterable<R>> mapper) {
      mMapper = ConstantConditions.notNull("mapper", mapper);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new HandlerProxy<O, R>(mMapper);
    }

    private static class HandlerProxy<O, R> extends SerializableProxy {

      private HandlerProxy(final Mapper<Iterable<O>, Iterable<R>> mapper) {
        super(mapper);
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

    @Override
    public void resolve(final Iterable<O> input, @NotNull final CallbackIterable<R> callback) throws
        Exception {
      callback.addAll(mMapper.apply(input));
      callback.resolve();
    }
  }

  private static class HandlerMapEach<O, R> extends DefaultStatelessHandler<O, R>
      implements Serializable {

    private final Mapper<O, R> mMapper;

    private HandlerMapEach(final Mapper<O, R> mapper) {
      mMapper = ConstantConditions.notNull("mapper", mapper);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new HandlerProxy<O, R>(mMapper);
    }

    private static class HandlerProxy<O, R> extends SerializableProxy {

      private HandlerProxy(final Mapper<O, R> mapper) {
        super(mapper);
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

    @Override
    public void resolve(final O input, @NotNull final CallbackIterable<R> callback) throws
        Exception {
      callback.resolve(mMapper.apply(input));
    }
  }

  private static class HandlerObserve<O, R> implements StatelessHandler<O, R>, Serializable {

    private final ObserverHandler<Throwable, ? super CallbackIterable<R>> mReject;

    private final ObserverHandler<O, ? super CallbackIterable<R>> mResolve;

    @SuppressWarnings("unchecked")
    private HandlerObserve(@Nullable final ObserverHandler<O, ? super CallbackIterable<R>> resolve,
        @Nullable final ObserverHandler<Throwable, ? super CallbackIterable<R>> reject) {
      mResolve = (ObserverHandler<O, ? super CallbackIterable<R>>) ((resolve != null) ? resolve
          : new PassThroughOutputHandler<O, R>());
      mReject = (ObserverHandler<Throwable, ? super CallbackIterable<R>>) ((reject != null) ? reject
          : new PassThroughErrorHandler<R>());
    }

    private Object writeReplace() throws ObjectStreamException {
      return new HandlerProxy<O, R>(mResolve, mReject);
    }

    private static class HandlerProxy<O, R> extends SerializableProxy {

      private HandlerProxy(final ObserverHandler<O, ? super CallbackIterable<R>> resolve,
          final ObserverHandler<Throwable, ? super CallbackIterable<R>> reject) {
        super(resolve, reject);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new HandlerObserve<O, R>((ObserverHandler<O, ? super Callback<R>>) args[0],
              (ObserverHandler<Throwable, ? super Callback<R>>) args[1]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void reject(final Throwable reason, @NotNull final CallbackIterable<R> callback) throws
        Exception {
      mReject.accept(reason, callback);
    }

    public void resolve(final O input, @NotNull final CallbackIterable<R> callback) throws
        Exception {
      mResolve.accept(input, callback);
    }
  }

  private static class HandlerRejectedAll<O> extends IterableStatelessHandler<Iterable<O>, O>
      implements Serializable {

    private final Observer<Throwable> mObserver;

    private HandlerRejectedAll(@NotNull final Observer<Throwable> observer) {
      mObserver = ConstantConditions.notNull("observer", observer);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new HandlerProxy<O>(mObserver);
    }

    private static class HandlerProxy<O> extends SerializableProxy {

      private HandlerProxy(final Observer<Throwable> observer) {
        super(observer);
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

    @Override
    public void reject(final Throwable reason, @NotNull final CallbackIterable<O> callback) throws
        Exception {
      mObserver.accept(reason);
      super.reject(reason, callback);
    }
  }

  private static class HandlerRejectedEach<O> extends DefaultStatelessHandler<O, O>
      implements Serializable {

    private final Observer<Throwable> mObserver;

    private HandlerRejectedEach(@NotNull final Observer<Throwable> observer) {
      mObserver = ConstantConditions.notNull("observer", observer);
    }

    @Override
    public void reject(final Throwable reason, @NotNull final CallbackIterable<O> callback) throws
        Exception {
      mObserver.accept(reason);
      super.reject(reason, callback);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new HandlerProxy<O>(mObserver);
    }

    private static class HandlerProxy<O> extends SerializableProxy {

      private HandlerProxy(final Observer<Throwable> observer) {
        super(observer);
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
  }

  private static class HandlerResolvedAll<O>
      implements StatelessHandler<Iterable<O>, O>, Serializable {

    private final Action mAction;

    private HandlerResolvedAll(@NotNull final Action action) {
      mAction = ConstantConditions.notNull("action", action);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new HandlerProxy<O>(mAction);
    }

    private static class HandlerProxy<O> extends SerializableProxy {

      private HandlerProxy(final Action action) {
        super(action);
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

    public void reject(final Throwable reason, @NotNull final CallbackIterable<O> callback) throws
        Exception {
      mAction.perform();
      callback.reject(reason);
    }

    public void resolve(final Iterable<O> input, @NotNull final CallbackIterable<O> callback) throws
        Exception {
      mAction.perform();
      callback.addAll(input);
      callback.resolve();
    }
  }

  private static class HandlerStateless<O, R> implements StatelessHandler<O, R>, Serializable {

    private final Handler<O, R> mHandler;

    private HandlerStateless(@NotNull final Handler<O, R> handler) {
      mHandler = ConstantConditions.notNull("handler", handler);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new HandlerProxy<O, R>(mHandler);
    }

    private static class HandlerProxy<O, R> extends SerializableProxy {

      private HandlerProxy(final Handler<O, R> handler) {
        super(handler);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new HandlerStateless<O, R>((Handler<O, R>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void reject(final Throwable reason, @NotNull final CallbackIterable<R> callback) throws
        Exception {
      mHandler.reject(reason, callback);
    }

    public void resolve(final O input, @NotNull final CallbackIterable<R> callback) throws
        Exception {
      mHandler.resolve(input, callback);
    }
  }

  private static class HandlerStatelessIterable<O, R>
      implements StatelessHandler<Iterable<O>, R>, Serializable {

    private final Handler<Iterable<O>, Iterable<R>> mHandler;

    private HandlerStatelessIterable(@NotNull final Handler<Iterable<O>, Iterable<R>> handler) {
      mHandler = ConstantConditions.notNull("handler", handler);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new HandlerProxy<O, R>(mHandler);
    }

    private static class HandlerCallback<O> implements Callback<Iterable<O>> {

      private final CallbackIterable<O> mCallback;

      private HandlerCallback(@NotNull final CallbackIterable<O> callback) {
        mCallback = callback;
      }

      public void defer(@NotNull final Promise<Iterable<O>> promise) {
        promise.then(new Handler<Iterable<O>, Void>() {

          public void reject(final Throwable reason, @NotNull final Callback<Void> callback) {
            mCallback.reject(reason);
          }

          public void resolve(final Iterable<O> input, @NotNull final Callback<Void> callback) {
            mCallback.addAll(input);
            mCallback.resolve();
          }
        });
      }

      public void reject(final Throwable reason) {
        mCallback.reject(reason);
      }

      public void resolve(final Iterable<O> outputs) {
        mCallback.addAll(outputs);
        mCallback.resolve();
      }
    }

    private static class HandlerProxy<O, R> extends SerializableProxy {

      private HandlerProxy(final Handler<Iterable<O>, Iterable<R>> handler) {
        super(handler);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new HandlerStatelessIterable<O, R>((Handler<Iterable<O>, Iterable<R>>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void reject(final Throwable reason, @NotNull final CallbackIterable<R> callback) throws
        Exception {
      mHandler.reject(reason, new HandlerCallback<R>(callback));
    }

    public void resolve(final Iterable<O> input, @NotNull final CallbackIterable<R> callback) throws
        Exception {
      mHandler.resolve(input, new HandlerCallback<R>(callback));
    }
  }

  private static class IterableStatelessHandler<I extends Iterable<?>, O>
      implements StatelessHandler<I, O> {

    public void reject(final Throwable reason, @NotNull final CallbackIterable<O> callback) throws
        Exception {
      callback.reject(reason);
    }

    @SuppressWarnings("unchecked")
    public void resolve(final I input, @NotNull final CallbackIterable<O> callback) throws
        Exception {
      callback.addAll((Iterable<O>) input);
      callback.resolve();
    }
  }

  private static class PassThroughErrorHandler<R>
      implements ObserverHandler<Throwable, Callback<R>>, Serializable {

    public void accept(final Throwable input, @NotNull final Callback<R> callback) {
      callback.reject(input);
    }
  }

  private static class PassThroughOutputHandler<O, R>
      implements ObserverHandler<O, Callback<R>>, Serializable {

    @SuppressWarnings("unchecked")
    public void accept(final O input, @NotNull final Callback<R> callback) {
      callback.resolve((R) input);
    }
  }

  private static abstract class PromiseChain<I, O> implements CallbackIterable<I>, Serializable {

    private transient final Object mMutex = new Object();

    private transient long mDeferredCount = 1;

    private transient volatile StatePending mInnerState = new StatePending();

    private transient Logger mLogger;

    private transient volatile PromiseChain<O, ?> mNext;

    abstract void add(PromiseChain<O, ?> next, I input);

    abstract void addAll(PromiseChain<O, ?> next, Iterable<I> inputs);

    abstract void addRejection(PromiseChain<O, ?> next, Throwable reason);

    @NotNull
    abstract PromiseChain<I, O> copy();

    Logger getLogger() {
      return mLogger;
    }

    void setLogger(@NotNull final Logger logger) {
      mLogger = logger.subContextLogger(this);
    }

    void innerReject(final Throwable reason) {
      final Runnable command;
      synchronized (mMutex) {
        command = mInnerState.reject(reason);
      }

      if (command != null) {
        command.run();
      }

      innerResolve();
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
        needResolve = (--mDeferredCount <= 0);
      }

      if (needResolve) {
        resolve(mNext);
      }
    }

    private class StatePending {

      @Nullable
      Runnable add() {
        return null;
      }

      @Nullable
      Runnable reject(final Throwable reason) {
        mInnerState = new StateRejected(reason);
        return null;
      }

      @Nullable
      Runnable resolve() {
        mInnerState = new StateResolved();
        return null;
      }
    }

    private class StatePrepending extends StatePending implements Runnable {

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
        try {
          for (final Resolution<I> resolution : mResolutions) {
            resolution.consume(PromiseChain.this);
          }

        } catch (final Throwable t) {
          InterruptedExecutionException.throwIfInterrupt(t);
          getLogger().err(t, "Error while propagating resolutions");
          reject(t);
        }
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

      private final Throwable mReason;

      private StateRejected(final Throwable reason) {
        mReason = reason;
      }

      @Nullable
      @Override
      Runnable add() {
        final Throwable reason = mReason;
        mLogger.wrn("Chain has been already rejected with reason: %s", reason);
        throw RejectionException.wrapIfNotRejectionException(reason);
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
        final Throwable reason = mReason;
        mLogger.wrn("Chain has been already rejected with reason: %s", reason);
        throw RejectionException.wrapIfNotRejectionException(reason);
      }
    }

    private class StateResolved extends StatePending {

      @Nullable
      @Override
      Runnable add() {
        mLogger.wrn("Chain has been already resolved");
        throw new IllegalStateException("chain has been already resolved");
      }

      @Nullable
      @Override
      Runnable resolve() {
        mLogger.wrn("Chain has been already resolved");
        throw new IllegalStateException("chain has been already resolved");
      }
    }

    public final void addRejection(final Throwable reason) {
      final Runnable command;
      synchronized (mMutex) {
        command = mInnerState.add();
      }

      if (command != null) {
        command.run();
      }

      addRejection(mNext, reason);
    }

    public final void add(final I output) {
      final Runnable command;
      synchronized (mMutex) {
        command = mInnerState.add();
      }

      if (command != null) {
        command.run();
      }

      add(mNext, output);
    }

    public final void addAll(@Nullable final Iterable<I> outputs) {
      final Runnable command;
      synchronized (mMutex) {
        command = mInnerState.add();
      }

      if (command != null) {
        command.run();
      }

      if (outputs != null) {
        addAll(mNext, outputs);
      }
    }

    @SuppressWarnings("unchecked")
    public final void addAllDeferred(@NotNull final Promise<? extends Iterable<I>> promise) {
      final Runnable command;
      synchronized (mMutex) {
        command = mInnerState.add();
        ++mDeferredCount;
      }

      if (command != null) {
        command.run();
      }

      if (promise instanceof PromiseIterable) {
        ((PromiseIterable<I>) promise).then(new StatefulHandler<I, Void, Void>() {

          public Void fulfill(final Void state, final I input,
              @NotNull final CallbackIterable<Void> callback) {
            PromiseChain.this.add(mNext, input);
            return null;
          }

          public Void create(@NotNull final CallbackIterable<Void> callback) {
            return null;
          }

          public Void reject(final Void state, final Throwable reason,
              @NotNull final CallbackIterable<Void> callback) {
            PromiseChain.this.addRejection(mNext, reason);
            return null;
          }

          public void resolve(final Void state, @NotNull final CallbackIterable<Void> callback) {
            PromiseChain.this.innerResolve();
          }
        });

      } else {
        ((Promise<Iterable<I>>) promise).then(new Handler<Iterable<I>, Void>() {

          public void reject(final Throwable reason, @NotNull final Callback<Void> callback) {
            PromiseChain.this.addRejection(mNext, reason);
          }

          public void resolve(final Iterable<I> inputs, @NotNull final Callback<Void> callback) {
            if (inputs != null) {
              PromiseChain.this.addAll(mNext, inputs);
            }

            PromiseChain.this.innerResolve();
          }
        });
      }
    }

    public final void addDeferred(@NotNull final Promise<I> promise) {
      final Runnable command;
      synchronized (mMutex) {
        command = mInnerState.add();
        ++mDeferredCount;
      }

      if (command != null) {
        command.run();
      }

      promise.then(new Handler<I, Void>() {

        public void reject(final Throwable reason, @NotNull final Callback<Void> callback) {
          PromiseChain.this.addRejection(mNext, reason);
          PromiseChain.this.innerResolve();
        }

        public void resolve(final I input, @NotNull final Callback<Void> callback) {
          PromiseChain.this.add(mNext, input);
          PromiseChain.this.innerResolve();
        }
      });
    }

    public final void resolve() {
      final Runnable command;
      synchronized (mMutex) {
        command = mInnerState.resolve();
      }

      if (command != null) {
        command.run();
      }

      innerResolve();
    }

    public final void defer(@NotNull final Promise<I> promise) {
      final Runnable command;
      synchronized (mMutex) {
        command = mInnerState.add();
        ++mDeferredCount;
      }

      if (command != null) {
        command.run();
      }

      promise.then(new Handler<I, Void>() {

        public void reject(final Throwable reason, @NotNull final Callback<Void> callback) {
          PromiseChain.this.addRejection(mNext, reason);
          PromiseChain.this.innerResolve();
        }

        public void resolve(final I input, @NotNull final Callback<Void> callback) {
          PromiseChain.this.add(mNext, input);
          PromiseChain.this.innerResolve();
        }
      });

      resolve();
    }

    public final void reject(final Throwable reason) {
      final Runnable command;
      synchronized (mMutex) {
        command = mInnerState.reject(reason);
      }

      if (command != null) {
        command.run();
      }

      addRejection(mNext, reason);
      innerResolve();
    }

    public final void resolve(final I output) {
      final Runnable command;
      synchronized (mMutex) {
        command = mInnerState.resolve();
      }

      if (command != null) {
        command.run();
      }

      add(mNext, output);
      innerResolve();
    }
  }

  private static class PromiseProxy extends SerializableProxy {

    private PromiseProxy(final Observer<? extends CallbackIterable<?>> observer,
        final PropagationType propagationType, final Log log, final Level logLevel,
        final List<PromiseChain<?, ?>> chains) {
      super(observer, propagationType, log, logLevel, chains);
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

        return new DefaultPromiseIterable<Object>((Observer<CallbackIterable<?>>) args[0],
            (PropagationType) args[1], (Log) args[2], (Level) args[3], head,
            (PromiseChain<?, Object>) tail);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }

  private static class ResolutionFulfilled<O> implements Resolution<O> {

    private final SimpleQueue<O> mOutputs = new SimpleQueue<O>();

    private ResolutionFulfilled() {
    }

    private ResolutionFulfilled(final Iterable<O> inputs) {
      @SuppressWarnings("UnnecessaryLocalVariable") final SimpleQueue<O> outputs = mOutputs;
      if (inputs != null) {
        for (final O input : inputs) {
          outputs.add(input);
        }
      }
    }

    public void consume(@NotNull final PromiseChain<O, ?> chain) {
      chain.addAll(mOutputs);
    }

    void add(final O input) {
      mOutputs.add(input);
    }

    boolean isEmpty() {
      return mOutputs.isEmpty();
    }

    @Nullable
    public RejectionException getError() {
      return null;
    }

    @NotNull
    public SimpleQueue<O> getOutputs() {
      return mOutputs;
    }

    public void throwError() {
    }
  }

  private static class ResolutionIterable<O> implements Iterable<O> {

    private final Iterable<O> mIterable;

    private ResolutionIterable(@NotNull final Iterable<O> wrapped) {
      mIterable = wrapped;
    }

    public Iterator<O> iterator() {
      return mIterable.iterator();
    }
  }

  private static class ResolutionRejected<O> implements Resolution<O> {

    private final Throwable mReason;

    private ResolutionRejected(final Throwable reason) {
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
    public SimpleQueue<O> getOutputs() {
      throw getError();
    }

    public void throwError() {
      throw getError();
    }
  }

  private static class ResolutionResolved<O> implements Resolution<O> {

    private final O mOutput;

    private ResolutionResolved(final O output) {
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
    public SimpleQueue<O> getOutputs() {
      final SimpleQueue<O> outputs = new SimpleQueue<O>();
      outputs.add(mOutput);
      return outputs;
    }

    public void throwError() {
    }
  }

  private class ChainTail extends PromiseChain<O, Object> {

    private final ChainHead<O> mHead;

    private ChainTail(@NotNull final ChainHead<O> head) {
      mHead = head;
    }

    @Override
    void add(final PromiseChain<Object, ?> next, final O input) {
      final PromiseChain<O, ?> bond;
      synchronized (mMutex) {
        try {
          mState = PromiseState.Fulfilled;
          if ((bond = mBond) == null) {
            mHead.innerAdd(input);
            return;
          }

        } finally {
          mMutex.notifyAll();
        }
      }

      bond.add(input);
    }

    @Override
    void addAll(final PromiseChain<Object, ?> next, final Iterable<O> inputs) {
      final PromiseChain<O, ?> bond;
      synchronized (mMutex) {
        try {
          mState = PromiseState.Fulfilled;
          if ((bond = mBond) == null) {
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

      bond.addAll(inputs);
    }

    @NotNull
    @Override
    PromiseChain<O, Object> copy() {
      return ConstantConditions.unsupported();
    }

    @Override
    void addRejection(final PromiseChain<Object, ?> next, final Throwable reason) {
      final PromiseChain<O, ?> bond;
      synchronized (mMutex) {
        try {
          mState = PromiseState.Rejected;
          if ((bond = mBond) == null) {
            mHead.innerAddRejection(reason);
            return;
          }

        } finally {
          mMutex.notifyAll();
        }
      }

      bond.addRejection(reason);
    }

    @Override
    void resolve(final PromiseChain<Object, ?> next) {
      final PromiseChain<O, ?> bond;
      synchronized (mMutex) {
        try {
          mState = PromiseState.Fulfilled;
          if ((bond = mBond) == null) {
            mHead.innerResolve();
            return;
          }

        } finally {
          mMutex.notifyAll();
        }
      }

      bond.resolve();
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
            final SimpleQueue<O> outputs = (SimpleQueue<O>) head.getOutputs();
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
            final SimpleQueue<O> outputs = (SimpleQueue<O>) head.getOutputs();
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

        final SimpleQueue<O> outputs = (SimpleQueue<O>) mHead.getOutputs();
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
}

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
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import dm.james.executor.InterruptedExecutionException;
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
import dm.james.util.SerializableProxy;
import dm.james.util.TimeUtils;
import dm.james.util.TimeUtils.Condition;

/**
 * Created by davide-maestroni on 07/23/2017.
 */
class DefaultPromiseIterable<O> implements PromiseIterable<O>, Serializable {

  // TODO: 26/07/2017 test rejection propagation

  private final ScheduledExecutor mExecutor;

  private final ChainHead<?> mHead;

  private final Logger mLogger;

  private final Object mMutex;

  private final Observer<? extends CallbackIterable<?>> mObserver;

  private final PropagationType mPropagationType;

  private final PromiseChain<?, O> mTail;

  private boolean mIsBound;

  private PromiseState mState = PromiseState.Pending;

  DefaultPromiseIterable(@NotNull final Observer<CallbackIterable<O>> observer,
      @Nullable final PropagationType propagationType, @Nullable final Log log,
      @Nullable final Level level) {
    mObserver = ConstantConditions.notNull("observer", observer);
    mLogger = Logger.newLogger(log, level, this);
    mPropagationType = (propagationType != null) ? propagationType : PropagationType.LOOP;
    mExecutor = ScheduledExecutors.throttlingExecutor(mPropagationType.executor(), 1);
    final ChainHead<O> head = new ChainHead<O>();
    head.setLogger(mLogger);
    head.setNext(new ChainTail(mExecutor, head));
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
  private DefaultPromiseIterable(@NotNull final Observer<? extends CallbackIterable<?>> observer,
      @NotNull final PropagationType propagationType, @Nullable final Log log,
      @Nullable final Level level, @NotNull final ChainHead<?> head,
      @NotNull final PromiseChain<?, O> tail) {
    // serialization
    mObserver = observer;
    mPropagationType = propagationType;
    mExecutor = ScheduledExecutors.throttlingExecutor(propagationType.executor(), 1);
    mLogger = Logger.newLogger(log, level, this);
    mMutex = head.getMutex();
    mHead = head;
    mTail = tail;
    tail.setNext(new ChainTail(mExecutor, (ChainHead<O>) head));
    PromiseChain<?, ?> chain = head;
    while (chain != tail) {
      chain.setLogger(mLogger);
      chain = chain.mNext;
    }

    chain.setLogger(mLogger);
    try {
      ((Observer<CallbackIterable<?>>) observer).accept(head);

    } catch (final Throwable t) {
      InterruptedExecutionException.throwIfInterrupt(t);
      head.reject(t);
    }
  }

  @SuppressWarnings("unchecked")
  private DefaultPromiseIterable(@NotNull final Observer<? extends CallbackIterable<?>> observer,
      @NotNull final PropagationType propagationType, @NotNull final ScheduledExecutor executor,
      @NotNull final Logger logger, @NotNull final ChainHead<?> head,
      @NotNull final PromiseChain<?, O> chain) {
    // bind
    mObserver = observer;
    mPropagationType = propagationType;
    mExecutor = executor;
    mLogger = logger;
    mMutex = head.getMutex();
    mHead = head;
    mTail = chain;
    chain.setNext(new ChainTail(mExecutor, (ChainHead<O>) head));
  }

  @SuppressWarnings("unchecked")
  private DefaultPromiseIterable(@NotNull final Observer<? extends CallbackIterable<?>> observer,
      @NotNull final PropagationType propagationType, @NotNull final Logger logger,
      @NotNull final ChainHead<?> head, @NotNull final PromiseChain<?, O> tail) {
    // copy
    mObserver = observer;
    mPropagationType = propagationType;
    mExecutor = ScheduledExecutors.throttlingExecutor(propagationType.executor(), 1);
    mLogger = logger;
    mMutex = head.getMutex();
    mHead = head;
    mTail = tail;
    tail.setNext(new ChainTail(mExecutor, (ChainHead<O>) head));
    try {
      ((Observer<CallbackIterable<?>>) observer).accept(head);

    } catch (final Throwable t) {
      InterruptedExecutionException.throwIfInterrupt(t);
      head.reject(t);
    }
  }

  @NotNull
  private static RejectionException wrapException(@Nullable final Throwable t) {
    return (t instanceof RejectionException) ? (RejectionException) t : new RejectionException(t);
  }

  @NotNull
  public <R> PromiseIterable<R> all(
      @Nullable final Handler<Iterable<O>, R, CallbackIterable<R>> outputHandler,
      @Nullable final Handler<Throwable, R, CallbackIterable<R>> errorHandler) {
    return all(new ProcessorHandle<Iterable<O>, R>(outputHandler, errorHandler));
  }

  @NotNull
  public <R> PromiseIterable<R> all(@NotNull final Mapper<Iterable<O>, Iterable<R>> mapper) {
    return all(new ProcessorMapAll<O, R>(mapper));
  }

  @NotNull
  public <R> PromiseIterable<R> all(@NotNull final StatelessProcessor<Iterable<O>, R> processor) {
    return then(new ProcessorAll<O, R>(processor));
  }

  @NotNull
  public <R> PromiseIterable<R> allSorted(
      @Nullable final Handler<Iterable<O>, R, CallbackIterable<R>> outputHandler,
      @Nullable final Handler<Throwable, R, CallbackIterable<R>> errorHandler) {
    return allSorted(new ProcessorHandle<Iterable<O>, R>(outputHandler, errorHandler));
  }

  @NotNull
  public <R> PromiseIterable<R> allSorted(
      @NotNull final StatelessProcessor<Iterable<O>, R> processor) {
    return thenSorted(new ProcessorAll<O, R>(processor));
  }

  @NotNull
  public <R> PromiseIterable<R> any(
      @Nullable final Handler<O, R, CallbackIterable<R>> outputHandler,
      @Nullable final Handler<Throwable, R, CallbackIterable<R>> errorHandler) {
    return any(new ProcessorHandle<O, R>(outputHandler, errorHandler));
  }

  @NotNull
  public <R> PromiseIterable<R> any(@NotNull final Mapper<O, R> mapper) {
    return any(new ProcessorMapEach<O, R>(mapper));
  }

  @NotNull
  public <R> PromiseIterable<R> any(@NotNull final StatelessProcessor<O, R> processor) {
    return then(new ProcessorAny<O, R>(processor));
  }

  @NotNull
  public <R> PromiseIterable<R> anySorted(
      @Nullable final Handler<O, R, CallbackIterable<R>> outputHandler,
      @Nullable final Handler<Throwable, R, CallbackIterable<R>> errorHandler) {
    return anySorted(new ProcessorHandle<O, R>(outputHandler, errorHandler));
  }

  @NotNull
  public <R> PromiseIterable<R> anySorted(@NotNull final StatelessProcessor<O, R> processor) {
    return thenSorted(new ProcessorAny<O, R>(processor));
  }

  @NotNull
  public <R> PromiseIterable<R> applyAll(
      @NotNull final Mapper<PromiseIterable<O>, PromiseIterable<R>> mapper) {
    try {
      return ConstantConditions.notNull("promise", mapper.apply(this));

    } catch (final Throwable t) {
      InterruptedExecutionException.throwIfInterrupt(t);
      mLogger.err(t, "Error while applying promise transformation");
      throw wrapException(t);
    }
  }

  @NotNull
  public <R> PromiseIterable<R> applyAny(@NotNull final Mapper<Promise<O>, Promise<R>> mapper) {
    final Logger logger = mLogger;
    return any(new ProcessorApplyEach<O, R>(mapper, mPropagationType, logger.getLog(),
        logger.getLogLevel()));
  }

  @NotNull
  public <R> PromiseIterable<R> applyEach(@NotNull final Mapper<Promise<O>, Promise<R>> mapper) {
    final Logger logger = mLogger;
    return each(new ProcessorApplyEach<O, R>(mapper, mPropagationType, logger.getLog(),
        logger.getLogLevel()));
  }

  @NotNull
  public PromiseIterable<O> catchAny(@NotNull final Mapper<Throwable, Iterable<O>> mapper) {
    return all(new ProcessorCatchAll<O>(mapper));
  }

  @NotNull
  public PromiseIterable<O> whenFulfilled(@NotNull final Observer<Iterable<O>> observer) {
    return all(new ProcessorFulfilledAll<O>(observer));
  }

  @NotNull
  public PromiseIterable<O> whenRejected(@NotNull final Observer<Throwable> observer) {
    return all(new ProcessorRejectedAll<O>(observer));
  }

  @NotNull
  public PromiseIterable<O> whenResolved(@NotNull final Action action) {
    return all(new ProcessorResolvedAll<O>(action));
  }

  @NotNull
  public PromiseIterable<O> catchEach(@NotNull final Mapper<Throwable, O> mapper) {
    return each(new ProcessorCatchEach<O>(mapper));
  }

  @NotNull
  public <R> PromiseIterable<R> each(
      @Nullable final Handler<O, R, CallbackIterable<R>> outputHandler,
      @Nullable final Handler<Throwable, R, CallbackIterable<R>> errorHandler) {
    return each(new ProcessorHandle<O, R>(outputHandler, errorHandler));
  }

  @NotNull
  public <R> PromiseIterable<R> each(@NotNull final Mapper<O, R> mapper) {
    return each(new ProcessorMapEach<O, R>(mapper));
  }

  @NotNull
  public <R> PromiseIterable<R> each(@NotNull final StatelessProcessor<O, R> processor) {
    return chain(new ChainStateless<O, R>(mPropagationType, processor));
  }

  @NotNull
  public <R> PromiseIterable<R> eachSorted(
      @Nullable final Handler<O, R, CallbackIterable<R>> outputHandler,
      @Nullable final Handler<Throwable, R, CallbackIterable<R>> errorHandler) {
    return eachSorted(new ProcessorHandle<O, R>(outputHandler, errorHandler));
  }

  @NotNull
  public <R> PromiseIterable<R> eachSorted(@NotNull final StatelessProcessor<O, R> processor) {
    return chain(new ChainStatelessSorted<O, R>(mPropagationType, processor));
  }

  @NotNull
  public List<O> get(final int maxSize) {
    return get(maxSize, -1, TimeUnit.MILLISECONDS);
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public List<O> get(final int maxSize, final long timeout, @NotNull final TimeUnit timeUnit) {
    final ChainHead<?> head = mHead;
    synchronized (mMutex) {
      try {
        if (TimeUtils.waitUntil(mMutex, new Condition() {

          public boolean isTrue() {
            checkBound();
            return ((head.getOutputs().size() >= maxSize) || head.getState().isResolved());
          }
        }, timeout, timeUnit)) {
          final List<O> outputs = (List<O>) head.getOutputs();
          return new ArrayList<O>(outputs.subList(0, Math.min(maxSize, outputs.size())));
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
    final ChainHead<?> head = mHead;
    synchronized (mMutex) {
      try {
        if (TimeUtils.waitUntil(mMutex, new Condition() {

          public boolean isTrue() {
            checkBound();
            return (!head.getOutputs().isEmpty() || head.getState().isResolved());
          }
        }, timeout, timeUnit)) {
          return ((List<O>) head.getOutputs()).get(0);
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
    final ChainHead<?> head = mHead;
    synchronized (mMutex) {
      try {
        if (TimeUtils.waitUntil(mMutex, new Condition() {

          public boolean isTrue() {
            checkBound();
            return (!head.getOutputs().isEmpty() || head.getState().isResolved());
          }
        }, timeout, timeUnit)) {
          return ((List<O>) head.getOutputs()).get(0);
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
    final ChainHead<?> head = mHead;
    synchronized (mMutex) {
      try {
        if (TimeUtils.waitUntil(mMutex, new Condition() {

          public boolean isTrue() {
            checkBound();
            return (!head.getOutputs().isEmpty() || head.getState().isResolved());
          }
        }, timeout, timeUnit)) {
          return ((List<O>) head.getOutputs()).remove(0);
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
    final ChainHead<?> head = mHead;
    synchronized (mMutex) {
      try {
        if (TimeUtils.waitUntil(mMutex, new Condition() {

          public boolean isTrue() {
            checkBound();
            return ((head.getOutputs().size() >= maxSize) || head.getState().isResolved());
          }
        }, timeout, timeUnit)) {
          final List<O> outputs = (List<O>) head.getOutputs();
          final ArrayList<O> removed = new ArrayList<O>();
          for (int i = 0; (i < maxSize) && !outputs.isEmpty(); --i) {
            removed.add(outputs.remove(0));
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
    final ChainHead<?> head = mHead;
    synchronized (mMutex) {
      try {
        if (TimeUtils.waitUntil(mMutex, new Condition() {

          public boolean isTrue() {
            checkBound();
            return (!head.getOutputs().isEmpty() || head.getState().isResolved());
          }
        }, timeout, timeUnit)) {
          return ((List<O>) head.getOutputs()).remove(0);
        }

      } catch (final InterruptedException e) {
        throw new InterruptedExecutionException(e);
      }
    }

    return other;
  }

  @NotNull
  public <R, S> PromiseIterable<R> then(@NotNull final StatefulProcessor<O, R, S> processor) {
    return chain(new ChainStateful<O, R, S>(mPropagationType, processor));
  }

  @NotNull
  public <R, S> PromiseIterable<R> thenSorted(@NotNull final StatefulProcessor<O, R, S> processor) {
    return chain(new ChainStatefulSorted<O, R, S>(mPropagationType, processor));
  }

  public void waitCompleted() {
    waitCompleted(-1, TimeUnit.MILLISECONDS);
  }

  public boolean waitCompleted(final long timeout, @NotNull final TimeUnit timeUnit) {
    synchronized (mMutex) {
      try {
        if (TimeUtils.waitUntil(mMutex, new Condition() {

          public boolean isTrue() {
            return mHead.isCompleted();
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
    return any(new ProcessorFulfilledEach<O>(observer));
  }

  @NotNull
  public PromiseIterable<O> whenFulfilledEach(@NotNull final Observer<O> observer) {
    return each(new ProcessorFulfilledEach<O>(observer));
  }

  @NotNull
  public PromiseIterable<O> whenRejectedEach(@NotNull final Observer<Throwable> observer) {
    return each(new ProcessorRejectedEach<O>(observer));
  }

  @NotNull
  public <R> Promise<R> apply(@NotNull final Mapper<Promise<Iterable<O>>, Promise<R>> mapper) {
    try {
      return ConstantConditions.notNull("promise", mapper.apply(this));

    } catch (final Throwable t) {
      InterruptedExecutionException.throwIfInterrupt(t);
      mLogger.err(t, "Error while applying promise transformation");
      throw wrapException(t);
    }
  }

  public Iterable<O> get() {
    return get(-1, TimeUnit.MILLISECONDS);
  }

  @SuppressWarnings("unchecked")
  public Iterable<O> get(final long timeout, @NotNull final TimeUnit timeUnit) {
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
      return mIsBound;
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
  public <R> Promise<R> then(@Nullable final Handler<Iterable<O>, R, Callback<R>> outputHandler,
      @Nullable final Handler<Throwable, R, Callback<R>> errorHandler) {
    return toPromise().then(outputHandler, errorHandler);
  }

  @NotNull
  public <R> Promise<R> then(@NotNull final Mapper<Iterable<O>, R> mapper) {
    return toPromise().then(mapper);
  }

  @NotNull
  public <R> Promise<R> then(@NotNull final Processor<Iterable<O>, R> processor) {
    return toPromise().then(processor);
  }

  public void waitResolved() {
    waitResolved(-1, TimeUnit.MILLISECONDS);
  }

  public boolean waitResolved(final long timeout, @NotNull final TimeUnit timeUnit) {
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
    final boolean isBound;
    synchronized (mMutex) {
      if (mIsBound) {
        isBound = true;

      } else {
        isBound = false;
        chain.setLogger(logger);
        mIsBound = true;
        mMutex.notifyAll();
      }
    }

    if (isBound) {
      return copy().chain(chain);
    }

    final DefaultPromiseIterable<R> promise =
        new DefaultPromiseIterable<R>(mObserver, mPropagationType, mExecutor, logger, mHead, chain);
    final PromiseChain<?, O> tail = mTail;
    ((ChainTail) tail.mNext).bind(tail, chain);
    return promise;
  }

  private void checkBound() {
    if (mIsBound) {
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
        (PromiseChain<?, O>) newTail);
  }

  @NotNull
  private Promise<Iterable<O>> toPromise() {
    final Logger logger = mLogger;
    return SinglePromise.create(this,
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
    List<O> getOutputs();

    void throwError();
  }

  private static class ChainHead<O> extends PromiseChain<O, O> {

    private final Object mMutex = new Object();

    private StatePending mInnerState = new StatePending();

    private ArrayList<Resolution<O>> mOutputs = new ArrayList<Resolution<O>>();

    private ResolutionFulfilled<O> mResolution = new ResolutionFulfilled<O>();

    private PromiseState mState = PromiseState.Pending;

    @Nullable
    public RejectionException getException() {
      return mInnerState.getException();
    }

    @Override
    void add(final PromiseChain<O, ?> next, final O input) {
      next.add(input);
    }

    ArrayList<Resolution<O>> consumeOutputs() {
      final ArrayList<Resolution<O>> outputs = mOutputs;
      mOutputs = new ArrayList<Resolution<O>>();
      return outputs;
    }

    @NotNull
    Object getMutex() {
      return mMutex;
    }

    @NotNull
    List<O> getOutputs() {
      return mInnerState.getOutputs();
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
      outputs.add(mResolution);
      outputs.add(new ResolutionRejected<O>(reason));
      mResolution = new ResolutionFulfilled<O>();
      mState = PromiseState.Rejected;
    }

    void innerReject(@Nullable final Throwable reason) {
      mInnerState.innerReject(reason);
      mState = PromiseState.Rejected;
    }

    void innerResolve() {
      mInnerState.innerResolve();
      mState = PromiseState.Fulfilled;
    }

    boolean isCompleted() {
      return mInnerState.isCompleted();
    }

    private class StatePending {

      @Nullable
      public RejectionException getException() {
        return null;
      }

      @NotNull
      IllegalStateException exception(@NotNull final PromiseState state) {
        return new IllegalStateException("invalid state: " + state);
      }

      @NotNull
      List<O> getOutputs() {
        final ArrayList<Resolution<O>> outputs = mOutputs;
        final int size = outputs.size();
        if (size == 1) {
          return outputs.get(0).getOutputs();

        } else if (size > 1) {
          for (final Resolution<O> output : outputs) {
            output.throwError();
          }
        }

        return Collections.emptyList();
      }

      void innerReject(@Nullable final Throwable reason) {
        innerAddRejection(reason);
        mInnerState = new StateRejected();
      }

      void innerResolve() {
        getLogger().dbg("Resolving promise with resolution [%s => %s]: %s", PromiseState.Pending,
            PromiseState.Fulfilled, mOutputs);
        mInnerState = new StateResolved();
      }

      boolean isCompleted() {
        return false;
      }
    }

    private class StateRejected extends StatePending {

      @Nullable
      @Override
      public RejectionException getException() {
        for (final Resolution<O> output : mOutputs) {
          final RejectionException exception = output.getError();
          if (exception != null) {
            return exception;
          }
        }

        return null;
      }

      @NotNull
      @Override
      List<O> getOutputs() {
        for (final Resolution<O> output : mOutputs) {
          output.throwError();
        }

        return Collections.emptyList();
      }

      @Override
      void innerReject(@Nullable final Throwable reason) {
        throw exception(PromiseState.Rejected);
      }

      @Override
      void innerResolve() {
        throw exception(PromiseState.Rejected);
      }

      @Override
      boolean isCompleted() {
        return true;
      }
    }

    private class StateResolved extends StatePending {

      @Override
      void innerReject(@Nullable final Throwable reason) {
        throw exception(PromiseState.Fulfilled);
      }

      @Override
      void innerResolve() {
        throw exception(PromiseState.Fulfilled);
      }

      @Override
      boolean isCompleted() {
        return true;
      }
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
    void reject(final PromiseChain<O, ?> next, final Throwable reason) {
      next.reject(reason);
    }

    @Override
    void resolve(final PromiseChain<O, ?> next) {
      next.resolve();
    }
  }

  private static class ChainStateful<O, R, S> extends PromiseChain<O, R> {

    private final ScheduledExecutor mExecutor;

    private final StatefulProcessor<O, R, S> mProcessor;

    private final PropagationType mPropagationType;

    private boolean mIsCreated;

    private boolean mIsFailed;

    private boolean mIsRejected;

    private S mState;

    private ChainStateful(@NotNull final PropagationType propagationType,
        @NotNull final StatefulProcessor<O, R, S> processor) {
      mProcessor = ConstantConditions.notNull("processor", processor);
      mPropagationType = propagationType;
      mExecutor = ScheduledExecutors.throttlingExecutor(propagationType.executor(), 1);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ChainProxy<O, R, S>(mPropagationType, mProcessor);
    }

    private static class ChainProxy<O, R, S> extends SerializableProxy {

      private ChainProxy(final PropagationType propagationType,
          final StatefulProcessor<O, R, S> processor) {
        super(propagationType, processor);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ChainStateful<O, R, S>((PropagationType) args[0],
              (StatefulProcessor<O, R, S>) args[1]);

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
            final StatefulProcessor<O, R, S> processor = mProcessor;
            if (!mIsCreated) {
              mIsCreated = true;
              try {
                mState = processor.create(next);

              } catch (final Throwable t) {
                mIsFailed = true;
                throw t;
              }
            }

            mState = processor.process(mState, input, next);

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            getLogger().err(t, "Error while processing input: %s", input);
            mIsRejected = true;
            reject(t);
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
            final StatefulProcessor<O, R, S> processor = mProcessor;
            if (!mIsCreated) {
              mIsCreated = true;
              try {
                mState = processor.create(next);

              } catch (final Throwable t) {
                mIsFailed = true;
                throw t;
              }
            }

            for (final O input : inputs) {
              mState = processor.process(mState, input, next);
            }

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            getLogger().err(t, "Error while processing input: %s", inputs);
            mIsRejected = true;
            reject(t);
          }
        }
      });
    }

    @NotNull
    @Override
    PromiseChain<O, R> copy() {
      return new ChainStateful<O, R, S>(mPropagationType, mProcessor);
    }

    @Override
    void reject(final PromiseChain<R, ?> next, final Throwable reason) {
      mExecutor.execute(new Runnable() {

        public void run() {
          if (mIsFailed) {
            next.reject(reason);
            return;
          }

          try {
            final StatefulProcessor<O, R, S> processor = mProcessor;
            if (!mIsCreated) {
              mIsCreated = true;
              try {
                mState = processor.create(next);

              } catch (final Throwable t) {
                mIsFailed = true;
                throw t;
              }
            }

            processor.reject(mState, reason, next);

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
            final StatefulProcessor<O, R, S> processor = mProcessor;
            if (!mIsCreated) {
              mIsCreated = true;
              try {
                mState = processor.create(next);

              } catch (final Throwable t) {
                mIsFailed = true;
                throw t;
              }
            }

            processor.resolve(mState, next);

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            getLogger().err(t, "Error while processing resolution");
            mIsRejected = true;
            reject(t);
          }
        }
      });
    }
  }

  private static class ChainStatefulSorted<O, R, S> extends PromiseChain<O, R> {

    private final ScheduledExecutor mExecutor;

    private final Object mMutex = new Object();

    private final StatefulProcessor<O, R, S> mProcessor;

    private final PropagationType mPropagationType;

    private final NestedQueue<Resolution<R>> mQueue = new NestedQueue<Resolution<R>>();

    private boolean mIsCreated;

    private boolean mIsFailed;

    private boolean mIsRejected;

    private S mState;

    private ChainStatefulSorted(@NotNull final PropagationType propagationType,
        @NotNull final StatefulProcessor<O, R, S> processor) {
      mProcessor = ConstantConditions.notNull("processor", processor);
      mPropagationType = propagationType;
      mExecutor = ScheduledExecutors.throttlingExecutor(propagationType.executor(), 1);
    }

    private void flushQueue(final PromiseChain<R, ?> next) {
      Resolution<R> resolution = removeFirst();
      while (resolution != null) {
        resolution.consume(next);
      }

      final boolean closed;
      synchronized (mMutex) {
        final NestedQueue<Resolution<R>> queue = mQueue;
        closed = queue.isEmpty() && queue.isClosed();
      }

      if (closed) {
        next.resolve();
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
      return new ChainProxy<O, R, S>(mPropagationType, mProcessor);
    }

    private static class ChainProxy<O, R, S> extends SerializableProxy {

      private ChainProxy(final PropagationType propagationType,
          final StatefulProcessor<O, R, S> processor) {
        super(propagationType, processor);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ChainStatefulSorted<O, R, S>((PropagationType) args[0],
              (StatefulProcessor<O, R, S>) args[1]);

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

      @SuppressWarnings("unchecked")
      public void addAllDeferred(@NotNull final Promise<? extends Iterable<R>> promise) {
        final NestedQueue<Resolution<R>> queue;
        synchronized (mMutex) {
          queue = mQueue.addNested();
        }

        if (promise instanceof PromiseIterable) {
          ((PromiseIterable<R>) promise).then(new ChainStatefulProcessor(mNext, queue));

        } else {
          ((Promise<Iterable<R>>) promise).then(new ChainProcessorIterable(mNext, queue));
        }
      }

      public void defer(@NotNull final Promise<R> promise) {
        addDeferred(promise);
        resolve();
      }

      public void reject(final Throwable reason) {
        synchronized (mMutex) {
          mQueue.add(new ResolutionRejected<R>(reason));
        }

        flushQueue(mNext);
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

        promise.then(new ChainProcessor(mNext, queue));
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

    private class ChainProcessor implements Processor<R, Void> {

      private final PromiseChain<R, ?> mNext;

      private final NestedQueue<Resolution<R>> mQueue;

      private ChainProcessor(final PromiseChain<R, ?> next,
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

      public void resolve(final R input, @NotNull final Callback<Void> callback) {
        synchronized (mMutex) {
          final NestedQueue<Resolution<R>> queue = mQueue;
          queue.add(new ResolutionResolved<R>(input));
          queue.close();
        }

        flushQueue(mNext);
      }
    }

    private class ChainProcessorIterable implements Processor<Iterable<R>, Void> {

      private final PromiseChain<R, ?> mNext;

      private final NestedQueue<Resolution<R>> mQueue;

      private ChainProcessorIterable(final PromiseChain<R, ?> next,
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

    private class ChainStatefulProcessor implements StatefulProcessor<R, Void, Void> {

      private final PromiseChain<R, ?> mNext;

      private final NestedQueue<Resolution<R>> mQueue;

      private ChainStatefulProcessor(final PromiseChain<R, ?> next,
          final NestedQueue<Resolution<R>> queue) {
        mNext = next;
        mQueue = queue;
      }

      public Void create(@NotNull final CallbackIterable<Void> callback) {
        return null;
      }

      public Void process(final Void state, final R input,
          @NotNull final CallbackIterable<Void> callback) {
        synchronized (mMutex) {
          mQueue.add(new ResolutionResolved<R>(input));
        }

        flushQueue(mNext);
        return null;
      }

      public void reject(final Void state, final Throwable reason,
          @NotNull final CallbackIterable<Void> callback) {
        synchronized (mMutex) {
          mQueue.add(new ResolutionRejected<R>(reason));
        }

        flushQueue(mNext);
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
            final StatefulProcessor<O, R, S> processor = mProcessor;
            if (!mIsCreated) {
              mIsCreated = true;
              try {
                mState = processor.create(callback);

              } catch (final Throwable t) {
                mIsFailed = true;
                throw t;
              }
            }

            mState = processor.process(mState, input, callback);

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            getLogger().err(t, "Error while processing input: %s", input);
            mIsRejected = true;
            reject(t);
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
            final StatefulProcessor<O, R, S> processor = mProcessor;
            if (!mIsCreated) {
              mIsCreated = true;
              try {
                mState = processor.create(callback);

              } catch (final Throwable t) {
                mIsFailed = true;
                throw t;
              }
            }

            for (final O input : inputs) {
              mState = processor.process(mState, input, callback);
            }

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            getLogger().err(t, "Error while processing input: %s", inputs);
            mIsRejected = true;
            reject(t);
          }
        }
      });
    }

    @NotNull
    @Override
    PromiseChain<O, R> copy() {
      return new ChainStateful<O, R, S>(mPropagationType, mProcessor);
    }

    @Override
    void reject(final PromiseChain<R, ?> next, final Throwable reason) {
      mExecutor.execute(new Runnable() {

        public void run() {
          if (mIsFailed) {
            next.reject(reason);
            return;
          }

          try {
            final ChainCallback callback = new ChainCallback(next);
            final StatefulProcessor<O, R, S> processor = mProcessor;
            if (!mIsCreated) {
              mIsCreated = true;
              try {
                mState = processor.create(callback);

              } catch (final Throwable t) {
                mIsFailed = true;
                throw t;
              }
            }

            processor.reject(mState, reason, callback);

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
            final ChainCallback callback = new ChainCallback(next);
            final StatefulProcessor<O, R, S> processor = mProcessor;
            if (!mIsCreated) {
              mIsCreated = true;
              try {
                mState = processor.create(callback);

              } catch (final Throwable t) {
                mIsFailed = true;
                throw t;
              }
            }

            processor.resolve(mState, callback);

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            getLogger().err(t, "Error while processing resolution");
            mIsRejected = true;
            reject(t);
          }
        }
      });
    }
  }

  private static class ChainStateless<O, R> extends PromiseChain<O, R> {

    private final AtomicLong mCallbackCount = new AtomicLong(1);

    private final StatelessProcessor<O, R> mProcessor;

    private final PropagationType mPropagationType;

    private ChainStateless(@NotNull final PropagationType propagationType,
        @NotNull final StatelessProcessor<O, R> processor) {
      mProcessor = ConstantConditions.notNull("processor", processor);
      mPropagationType = propagationType;
    }

    private void innerReject(final PromiseChain<R, ?> next, final Throwable reason) {
      next.addRejection(reason);
      if (mCallbackCount.decrementAndGet() <= 0) {
        next.resolve();
      }
    }

    private void innerResolve(final PromiseChain<R, ?> next) {
      if (mCallbackCount.decrementAndGet() <= 0) {
        next.resolve();
      }
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ChainProxy<O, R>(mPropagationType, mProcessor);
    }

    private static class ChainProxy<O, R> extends SerializableProxy {

      private ChainProxy(final PropagationType propagationType,
          final StatelessProcessor<O, R> processor) {
        super(propagationType, processor);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ChainStateless<O, R>((PropagationType) args[0],
              (StatelessProcessor<O, R>) args[1]);

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

      public void add(final R output) {
        mNext.add(output);
      }

      @SuppressWarnings("unchecked")
      public void addAllDeferred(@NotNull final Promise<? extends Iterable<R>> promise) {
        if (promise instanceof PromiseIterable) {
          ((PromiseIterable<R>) promise).then(new ChainStatefulProcessor(mNext));

        } else {
          ((Promise<Iterable<R>>) promise).then(new ChainProcessorIterable(mNext));
        }
      }

      public void defer(@NotNull final Promise<R> promise) {
        addDeferred(promise);
        resolve();
      }

      public void reject(final Throwable reason) {
        innerReject(mNext, reason);
      }

      public void addAll(@Nullable final Iterable<R> outputs) {
        mNext.addAll(outputs);
      }

      public void addDeferred(@NotNull final Promise<R> promise) {
        promise.then(new ChainProcessor(mNext));
      }

      public void resolve() {
        innerResolve(mNext);
      }

      public void resolve(final R output) {
        add(output);
        resolve();
      }
    }

    private class ChainProcessor implements Processor<R, Void> {

      private final PromiseChain<R, ?> mNext;

      private ChainProcessor(final PromiseChain<R, ?> next) {
        mCallbackCount.incrementAndGet();
        mNext = next;
      }

      public void reject(final Throwable reason, @NotNull final Callback<Void> callback) {
        innerReject(mNext, reason);
      }

      public void resolve(final R input, @NotNull final Callback<Void> callback) {
        mNext.add(input);
        innerResolve(mNext);
      }
    }

    private class ChainProcessorIterable implements Processor<Iterable<R>, Void> {

      private final PromiseChain<R, ?> mNext;

      private ChainProcessorIterable(final PromiseChain<R, ?> next) {
        mCallbackCount.incrementAndGet();
        mNext = next;
      }

      public void reject(final Throwable reason, @NotNull final Callback<Void> callback) {
        innerReject(mNext, reason);
      }

      public void resolve(final Iterable<R> input, @NotNull final Callback<Void> callback) {
        mNext.addAll(input);
        innerResolve(mNext);
      }
    }

    private class ChainStatefulProcessor implements StatefulProcessor<R, Void, Void> {

      private final PromiseChain<R, ?> mNext;

      private ChainStatefulProcessor(final PromiseChain<R, ?> next) {
        mCallbackCount.incrementAndGet();
        mNext = next;
      }

      public Void create(@NotNull final CallbackIterable<Void> callback) {
        return null;
      }

      public Void process(final Void state, final R input,
          @NotNull final CallbackIterable<Void> callback) {
        mNext.add(input);
        return null;
      }

      public void reject(final Void state, final Throwable reason,
          @NotNull final CallbackIterable<Void> callback) {
        innerReject(mNext, reason);
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
            mProcessor.resolve(input, callback);

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
      final ArrayList<ChainCallback> callbacks = new ArrayList<ChainCallback>();
      for (final O ignored : inputs) {
        callbacks.add(new ChainCallback(next));
      }

      mPropagationType.execute(new Runnable() {

        public void run() {
          int i = 0;
          for (final O input : inputs) {
            final ChainCallback callback = callbacks.get(i++);
            try {
              mProcessor.resolve(input, callback);

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
      return new ChainStateless<O, R>(mPropagationType, mProcessor);
    }

    @Override
    void addRejection(final PromiseChain<R, ?> next, final Throwable reason) {
      reject(next, reason);
    }

    @Override
    void reject(final PromiseChain<R, ?> next, final Throwable reason) {
      final ChainCallback callback = new ChainCallback(next);
      mPropagationType.execute(new Runnable() {

        public void run() {
          try {
            mProcessor.reject(reason, callback);

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

    private final Object mMutex = new Object();

    private final StatelessProcessor<O, R> mProcessor;

    private final PropagationType mPropagationType;

    private final NestedQueue<Resolution<R>> mQueue = new NestedQueue<Resolution<R>>();

    private ChainStatelessSorted(@NotNull final PropagationType propagationType,
        @NotNull final StatelessProcessor<O, R> processor) {
      mProcessor = ConstantConditions.notNull("processor", processor);
      mPropagationType = propagationType;
    }

    private void flushQueue(final PromiseChain<R, ?> next) {
      Resolution<R> resolution = removeFirst();
      while (resolution != null) {
        resolution.consume(next);
      }

      final boolean closed;
      synchronized (mMutex) {
        final NestedQueue<Resolution<R>> queue = mQueue;
        closed = queue.isEmpty() && queue.isClosed();
      }

      if (closed) {
        next.resolve();
      }
    }

    private void innerReject(final PromiseChain<R, ?> next, final Throwable reason) {
      next.addRejection(reason);
      if (mCallbackCount.decrementAndGet() <= 0) {
        next.resolve();
      }
    }

    private void innerResolve(final PromiseChain<R, ?> next) {
      if (mCallbackCount.decrementAndGet() <= 0) {
        next.resolve();
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
      return new ChainProxy<O, R>(mPropagationType, mProcessor);
    }

    private static class ChainProxy<O, R> extends SerializableProxy {

      private ChainProxy(final PropagationType propagationType,
          final StatelessProcessor<O, R> processor) {
        super(propagationType, processor);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ChainStateless<O, R>((PropagationType) args[0],
              (StatelessProcessor<O, R>) args[1]);

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
          ((PromiseIterable<R>) promise).then(new ChainStatefulProcessor(mNext, queue));

        } else {
          ((Promise<Iterable<R>>) promise).then(new ChainProcessorIterable(mNext, queue));
        }
      }

      public void defer(@NotNull final Promise<R> promise) {
        addDeferred(promise);
        resolve();
      }

      public void reject(final Throwable reason) {
        synchronized (mMutex) {
          mQueue.add(new ResolutionRejected<R>(reason));
        }

        final PromiseChain<R, ?> next = mNext;
        flushQueue(next);
        innerResolve(next);
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

        promise.then(new ChainProcessor(mNext, queue));
      }

      public void resolve() {
        synchronized (mMutex) {
          mQueue.close();
        }

        final PromiseChain<R, ?> next = mNext;
        flushQueue(next);
        innerResolve(next);
      }

      public void resolve(final R output) {
        synchronized (mMutex) {
          final NestedQueue<Resolution<R>> queue = mQueue;
          queue.add(new ResolutionResolved<R>(output));
          queue.close();
        }

        final PromiseChain<R, ?> next = mNext;
        flushQueue(next);
        innerResolve(next);
      }
    }

    private class ChainProcessor implements Processor<R, Void> {

      private final PromiseChain<R, ?> mNext;

      private final NestedQueue<Resolution<R>> mQueue;

      private ChainProcessor(final PromiseChain<R, ?> next,
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

    private class ChainProcessorIterable implements Processor<Iterable<R>, Void> {

      private final PromiseChain<R, ?> mNext;

      private final NestedQueue<Resolution<R>> mQueue;

      private ChainProcessorIterable(final PromiseChain<R, ?> next,
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

    private class ChainStatefulProcessor implements StatefulProcessor<R, Void, Void> {

      private final PromiseChain<R, ?> mNext;

      private final NestedQueue<Resolution<R>> mQueue;

      private ChainStatefulProcessor(final PromiseChain<R, ?> next,
          final NestedQueue<Resolution<R>> queue) {
        mCallbackCount.incrementAndGet();
        mNext = next;
        mQueue = queue;
      }

      public Void create(@NotNull final CallbackIterable<Void> callback) {
        return null;
      }

      public Void process(final Void state, final R input,
          @NotNull final CallbackIterable<Void> callback) {
        synchronized (mMutex) {
          mQueue.add(new ResolutionResolved<R>(input));
        }

        flushQueue(mNext);
        return null;
      }

      public void reject(final Void state, final Throwable reason,
          @NotNull final CallbackIterable<Void> callback) {
        synchronized (mMutex) {
          mQueue.add(new ResolutionRejected<R>(reason));
        }

        final PromiseChain<R, ?> next = mNext;
        flushQueue(next);
        innerResolve(next);
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
            mProcessor.resolve(input, callback);

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
      final ArrayList<ChainCallback> callbacks = new ArrayList<ChainCallback>();
      for (final O ignored : inputs) {
        callbacks.add(new ChainCallback(next));
      }

      mPropagationType.execute(new Runnable() {

        public void run() {
          int i = 0;
          for (final O input : inputs) {
            final ChainCallback callback = callbacks.get(i++);
            try {
              mProcessor.resolve(input, callback);

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
      return new ChainStateless<O, R>(mPropagationType, mProcessor);
    }

    @Override
    void addRejection(final PromiseChain<R, ?> next, final Throwable reason) {
      reject(next, reason);
    }

    @Override
    void reject(final PromiseChain<R, ?> next, final Throwable reason) {
      final ChainCallback callback = new ChainCallback(next);
      mPropagationType.execute(new Runnable() {

        public void run() {
          try {
            mProcessor.reject(reason, callback);

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

  private static class DefaultStatelessProcessor<I, O> implements StatelessProcessor<I, O> {

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

  private static class IterableStatelessProcessor<I extends Iterable<?>, O>
      implements StatelessProcessor<I, O> {

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
      implements Handler<Throwable, R, CallbackIterable<R>>, Serializable {

    public void accept(final Throwable input, @NotNull final CallbackIterable<R> callback) {
      callback.reject(input);
    }
  }

  private static class PassThroughOutputHandler<O, R>
      implements Handler<O, R, CallbackIterable<R>>, Serializable {

    @SuppressWarnings("unchecked")
    public void accept(final O input, @NotNull final CallbackIterable<R> callback) {
      callback.resolve((R) input);
    }
  }

  private static class ProcessorAll<O, R>
      implements StatefulProcessor<O, R, ArrayList<O>>, Serializable {

    private final StatelessProcessor<Iterable<O>, R> mProcessor;

    private ProcessorAll(final StatelessProcessor<Iterable<O>, R> processor) {
      mProcessor = ConstantConditions.notNull("processor", processor);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ProcessorProxy<O, R>(mProcessor);
    }

    private static class ProcessorProxy<O, R> extends SerializableProxy {

      private ProcessorProxy(final StatelessProcessor<Iterable<O>, R> processor) {
        super(processor);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ProcessorAll<O, R>((StatelessProcessor<Iterable<O>, R>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public ArrayList<O> create(@NotNull final CallbackIterable<R> callback) {
      return new ArrayList<O>();
    }

    public ArrayList<O> process(final ArrayList<O> state, final O input,
        @NotNull final CallbackIterable<R> callback) {
      state.add(input);
      return state;
    }

    public void reject(final ArrayList<O> state, final Throwable reason,
        @NotNull final CallbackIterable<R> callback) throws Exception {
      mProcessor.reject(reason, callback);
    }

    public void resolve(final ArrayList<O> state,
        @NotNull final CallbackIterable<R> callback) throws Exception {
      mProcessor.resolve(state, callback);
    }
  }

  private static class ProcessorAny<O, R>
      implements StatefulProcessor<O, R, Boolean>, Serializable {

    private final StatelessProcessor<O, R> mProcessor;

    private ProcessorAny(@NotNull final StatelessProcessor<O, R> processor) {
      mProcessor = ConstantConditions.notNull("processor", processor);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ProcessorProxy<O, R>(mProcessor);
    }

    private static class ProcessorProxy<O, R> extends SerializableProxy {

      private ProcessorProxy(final StatelessProcessor<O, R> processor) {
        super(processor);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ProcessorAny<O, R>((StatelessProcessor<O, R>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public Boolean create(@NotNull final CallbackIterable<R> callback) {
      return Boolean.TRUE;
    }

    public Boolean process(final Boolean state, final O input,
        @NotNull final CallbackIterable<R> callback) throws Exception {
      if (state) {
        mProcessor.resolve(input, callback);
        return Boolean.FALSE;
      }

      return state;
    }

    public void reject(final Boolean state, final Throwable reason,
        @NotNull final CallbackIterable<R> callback) throws Exception {
      mProcessor.reject(reason, callback);
    }

    public void resolve(final Boolean state, @NotNull final CallbackIterable<R> callback) {
    }
  }

  private static class ProcessorApplyEach<O, R> implements StatelessProcessor<O, R>, Serializable {

    private final Log mLog;

    private final Level mLogLevel;

    private final Mapper<Promise<O>, Promise<R>> mMapper;

    private final PropagationType mPropagationType;

    private ProcessorApplyEach(final Mapper<Promise<O>, Promise<R>> mapper,
        final PropagationType propagationType, final Log log, final Level logLevel) {
      mMapper = ConstantConditions.notNull("mapper", mapper);
      mPropagationType = propagationType;
      mLog = log;
      mLogLevel = logLevel;
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ProcessorProxy<O, R>(mMapper, mPropagationType, mLog, mLogLevel);
    }

    private static class ProcessorProxy<O, R> extends SerializableProxy {

      private ProcessorProxy(final Mapper<Promise<O>, Promise<R>> mapper,
          final PropagationType propagationType, final Log log, final Level logLevel) {
        super(mapper, propagationType, log, logLevel);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ProcessorApplyEach<O, R>((Mapper<Promise<O>, Promise<R>>) args[0],
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

  private static class ProcessorCatchAll<O> extends IterableStatelessProcessor<Iterable<O>, O>
      implements Serializable {

    private final Mapper<Throwable, Iterable<O>> mMapper;

    private ProcessorCatchAll(@NotNull final Mapper<Throwable, Iterable<O>> mapper) {
      mMapper = ConstantConditions.notNull("mapper", mapper);
    }

    @Override
    public void reject(final Throwable reason, @NotNull final CallbackIterable<O> callback) throws
        Exception {
      callback.addAll(mMapper.apply(reason));
      callback.resolve();
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ProcessorProxy<O>(mMapper);
    }

    private static class ProcessorProxy<O> extends SerializableProxy {

      private ProcessorProxy(final Mapper<Throwable, Iterable<O>> mapper) {
        super(mapper);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ProcessorCatchAll<O>((Mapper<Throwable, Iterable<O>>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }
  }

  private static class ProcessorCatchEach<O> extends DefaultStatelessProcessor<O, O>
      implements Serializable {

    private final Mapper<Throwable, O> mMapper;

    private ProcessorCatchEach(@NotNull final Mapper<Throwable, O> mapper) {
      mMapper = ConstantConditions.notNull("mapper", mapper);
    }

    @Override
    public void reject(final Throwable reason, @NotNull final CallbackIterable<O> callback) throws
        Exception {
      callback.resolve(mMapper.apply(reason));
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ProcessorProxy<O>(mMapper);
    }

    private static class ProcessorProxy<O> extends SerializableProxy {

      private ProcessorProxy(final Mapper<Throwable, O> mapper) {
        super(mapper);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ProcessorCatchEach<O>((Mapper<Throwable, O>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }
  }

  private static class ProcessorFulfilledAll<O> extends IterableStatelessProcessor<Iterable<O>, O>
      implements Serializable {

    private final Observer<Iterable<O>> mObserver;

    private ProcessorFulfilledAll(@NotNull final Observer<Iterable<O>> observer) {
      mObserver = ConstantConditions.notNull("observer", observer);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ProcessorProxy<O>(mObserver);
    }

    private static class ProcessorProxy<O> extends SerializableProxy {

      private ProcessorProxy(final Observer<Iterable<O>> observer) {
        super(observer);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ProcessorFulfilledAll<O>((Observer<Iterable<O>>) args[0]);

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

  private static class ProcessorFulfilledEach<O> extends DefaultStatelessProcessor<O, O>
      implements Serializable {

    private final Observer<O> mObserver;

    private ProcessorFulfilledEach(@NotNull final Observer<O> observer) {
      mObserver = ConstantConditions.notNull("observer", observer);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ProcessorProxy<O>(mObserver);
    }

    private static class ProcessorProxy<O> extends SerializableProxy {

      private ProcessorProxy(final Observer<O> observer) {
        super(observer);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ProcessorFulfilledEach<O>((Observer<O>) args[0]);

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

  private static class ProcessorHandle<O, R> implements StatelessProcessor<O, R>, Serializable {

    private final Handler<Throwable, R, CallbackIterable<R>> mErrorHandler;

    private final Handler<O, R, CallbackIterable<R>> mOutputHandler;

    private ProcessorHandle(@Nullable final Handler<O, R, CallbackIterable<R>> outputHandler,
        @Nullable final Handler<Throwable, R, CallbackIterable<R>> errorHandler) {
      mOutputHandler =
          (outputHandler != null) ? outputHandler : new PassThroughOutputHandler<O, R>();
      mErrorHandler = (errorHandler != null) ? errorHandler : new PassThroughErrorHandler<R>();
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ProcessorProxy<O, R>(mOutputHandler, mErrorHandler);
    }

    private static class ProcessorProxy<O, R> extends SerializableProxy {

      private ProcessorProxy(final Handler<O, R, CallbackIterable<R>> outputHandler,
          final Handler<Throwable, R, CallbackIterable<R>> errorHandler) {
        super(outputHandler, errorHandler);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ProcessorHandle<O, R>((Handler<O, R, CallbackIterable<R>>) args[0],
              (Handler<Throwable, R, CallbackIterable<R>>) args[1]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void reject(final Throwable reason, @NotNull final CallbackIterable<R> callback) throws
        Exception {
      mErrorHandler.accept(reason, callback);
    }

    public void resolve(final O input, @NotNull final CallbackIterable<R> callback) throws
        Exception {
      mOutputHandler.accept(input, callback);
    }
  }

  private static class ProcessorMapAll<O, R> extends IterableStatelessProcessor<Iterable<O>, R>
      implements Serializable {

    private final Mapper<Iterable<O>, Iterable<R>> mMapper;

    private ProcessorMapAll(final Mapper<Iterable<O>, Iterable<R>> mapper) {
      mMapper = ConstantConditions.notNull("mapper", mapper);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ProcessorProxy<O, R>(mMapper);
    }

    private static class ProcessorProxy<O, R> extends SerializableProxy {

      private ProcessorProxy(final Mapper<Iterable<O>, Iterable<R>> mapper) {
        super(mapper);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ProcessorMapAll<O, R>((Mapper<Iterable<O>, Iterable<R>>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    @Override
    public void resolve(final Iterable<O> input, @NotNull final CallbackIterable<R> callback) throws
        Exception {
      callback.addAll(mMapper.apply(input));
    }
  }

  private static class ProcessorMapEach<O, R> extends DefaultStatelessProcessor<O, R>
      implements Serializable {

    private final Mapper<O, R> mMapper;

    private ProcessorMapEach(final Mapper<O, R> mapper) {
      mMapper = ConstantConditions.notNull("mapper", mapper);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ProcessorProxy<O, R>(mMapper);
    }

    private static class ProcessorProxy<O, R> extends SerializableProxy {

      private ProcessorProxy(final Mapper<O, R> mapper) {
        super(mapper);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ProcessorMapEach<O, R>((Mapper<O, R>) args[0]);

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

  private static class ProcessorRejectedAll<O> extends IterableStatelessProcessor<Iterable<O>, O>
      implements Serializable {

    private final Observer<Throwable> mObserver;

    private ProcessorRejectedAll(@NotNull final Observer<Throwable> observer) {
      mObserver = ConstantConditions.notNull("observer", observer);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ProcessorProxy<O>(mObserver);
    }

    private static class ProcessorProxy<O> extends SerializableProxy {

      private ProcessorProxy(final Observer<Throwable> observer) {
        super(observer);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ProcessorRejectedAll<O>((Observer<Throwable>) args[0]);

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

  private static class ProcessorRejectedEach<O> extends DefaultStatelessProcessor<O, O>
      implements Serializable {

    private final Observer<Throwable> mObserver;

    private ProcessorRejectedEach(@NotNull final Observer<Throwable> observer) {
      mObserver = ConstantConditions.notNull("observer", observer);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ProcessorProxy<O>(mObserver);
    }

    private static class ProcessorProxy<O> extends SerializableProxy {

      private ProcessorProxy(final Observer<Throwable> observer) {
        super(observer);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ProcessorRejectedEach<O>((Observer<Throwable>) args[0]);

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

  private static class ProcessorResolvedAll<O>
      implements StatelessProcessor<Iterable<O>, O>, Serializable {

    private final Action mAction;

    private ProcessorResolvedAll(@NotNull final Action action) {
      mAction = ConstantConditions.notNull("action", action);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ProcessorProxy<O>(mAction);
    }

    private static class ProcessorProxy<O> extends SerializableProxy {

      private ProcessorProxy(final Action action) {
        super(action);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ProcessorResolvedAll<O>((Action) args[0]);

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

  private static abstract class PromiseChain<I, O> implements CallbackIterable<I>, Serializable {

    private transient final Object mMutex = new Object();

    private transient long mDeferredCount = 1;

    private transient volatile StatePending mInnerState = new StatePending();

    private transient Logger mLogger;

    private transient volatile PromiseChain<O, ?> mNext;

    abstract void add(PromiseChain<O, ?> next, I input);

    abstract void addAll(PromiseChain<O, ?> next, Iterable<I> inputs);

    void addRejection(PromiseChain<O, ?> next, Throwable reason) {
      reject(reason);
    }

    final void addRejection(final Throwable reason) {
      addRejection(mNext, reason);
    }

    @NotNull
    abstract PromiseChain<I, O> copy();

    Logger getLogger() {
      return mLogger;
    }

    void setLogger(@NotNull final Logger logger) {
      mLogger = logger.subContextLogger(this);
    }

    abstract void reject(PromiseChain<O, ?> next, Throwable reason);

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

      void add() {
      }

      boolean reject(final Throwable reason) {
        mInnerState = new StateRejected(reason);
        return true;
      }

      void resolve() {
        mInnerState = new StateResolved();
      }
    }

    private class StateRejected extends StatePending {

      private final Throwable mReason;

      private StateRejected(final Throwable reason) {
        mReason = reason;
      }

      @Override
      void add() {
        final Throwable reason = mReason;
        mLogger.wrn("Chain has been already rejected with reason: %s", reason);
        throw wrapException(reason);
      }

      @Override
      boolean reject(final Throwable reason) {
        mLogger.wrn("Suppressed rejection with reason: %s", reason);
        return false;
      }

      @Override
      void resolve() {
        final Throwable reason = mReason;
        mLogger.wrn("Chain has been already rejected with reason: %s", reason);
        throw wrapException(reason);
      }
    }

    private class StateResolved extends StatePending {

      @Override
      void add() {
        mLogger.wrn("Chain has been already resolved");
        throw wrapException(new IllegalStateException("chain has been already resolved"));
      }

      @Override
      void resolve() {
        mLogger.wrn("Chain has been already resolved");
        throw wrapException(new IllegalStateException("chain has been already resolved"));
      }

      @Override
      boolean reject(final Throwable reason) {
        mInnerState = new StateRejected(reason);
        return true;
      }
    }

    public final void add(final I output) {
      synchronized (mMutex) {
        mInnerState.add();
      }

      add(mNext, output);
    }

    public final void addAll(@Nullable final Iterable<I> outputs) {
      synchronized (mMutex) {
        mInnerState.add();
      }

      if (outputs != null) {
        addAll(mNext, outputs);
      }
    }

    @SuppressWarnings("unchecked")
    public final void addAllDeferred(@NotNull final Promise<? extends Iterable<I>> promise) {
      synchronized (mMutex) {
        mInnerState.add();
        ++mDeferredCount;
      }

      if (promise instanceof PromiseIterable) {
        ((PromiseIterable<I>) promise).then(new StatefulProcessor<I, Void, Void>() {

          public Void process(final Void state, final I input,
              @NotNull final CallbackIterable<Void> callback) {
            PromiseChain.this.add(mNext, input);
            return null;
          }

          public Void create(@NotNull final CallbackIterable<Void> callback) {
            return null;
          }

          public void reject(final Void state, final Throwable reason,
              @NotNull final CallbackIterable<Void> callback) {
            PromiseChain.this.reject(mNext, reason);
          }

          public void resolve(final Void state, @NotNull final CallbackIterable<Void> callback) {
            PromiseChain.this.innerResolve();
          }
        });

      } else {
        ((Promise<Iterable<I>>) promise).then(new Processor<Iterable<I>, Void>() {

          public void reject(final Throwable reason, @NotNull final Callback<Void> callback) {
            PromiseChain.this.reject(mNext, reason);
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
      synchronized (mMutex) {
        mInnerState.add();
        ++mDeferredCount;
      }

      promise.then(new Processor<I, Void>() {

        public void reject(final Throwable reason, @NotNull final Callback<Void> callback) {
          PromiseChain.this.reject(mNext, reason);
        }

        public void resolve(final I input, @NotNull final Callback<Void> callback) {
          PromiseChain.this.add(mNext, input);
          PromiseChain.this.innerResolve();
        }
      });
    }

    public final void resolve() {
      synchronized (mMutex) {
        mInnerState.resolve();
      }

      innerResolve();
    }

    public final void defer(@NotNull final Promise<I> promise) {
      synchronized (mMutex) {
        mInnerState.add();
        ++mDeferredCount;
      }

      promise.then(new Processor<I, Void>() {

        public void reject(final Throwable reason, @NotNull final Callback<Void> callback) {
          PromiseChain.this.reject(mNext, reason);
        }

        public void resolve(final I input, @NotNull final Callback<Void> callback) {
          PromiseChain.this.add(mNext, input);
          PromiseChain.this.innerResolve();
        }
      });
    }

    public final void reject(final Throwable reason) {
      synchronized (mMutex) {
        mInnerState.reject(reason);
      }

      reject(mNext, reason);
    }

    public final void resolve(final I output) {
      synchronized (mMutex) {
        mInnerState.resolve();
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

        return new DefaultPromiseIterable<Object>((Observer<CallbackIterable<Object>>) args[0],
            (PropagationType) args[1], (Log) args[2], (Level) args[3], head,
            (PromiseChain<?, Object>) tail);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }

  private static class ResolutionFulfilled<O> implements Resolution<O> {

    private final ArrayList<O> mOutputs = new ArrayList<O>();

    private ResolutionFulfilled() {
    }

    private ResolutionFulfilled(final Iterable<O> inputs) {
      @SuppressWarnings("UnnecessaryLocalVariable") final ArrayList<O> outputs = mOutputs;
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

    @Nullable
    public RejectionException getError() {
      return null;
    }

    @NotNull
    public List<O> getOutputs() {
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
      return wrapException(mReason);
    }

    @NotNull
    public List<O> getOutputs() {
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
    public List<O> getOutputs() {
      return Collections.singletonList(mOutput);
    }

    public void throwError() {
    }
  }

  private class ChainTail extends PromiseChain<O, Object> {

    private final ScheduledExecutor mExecutor;

    private final ChainHead<O> mHead;

    private PromiseChain<O, ?> mBond;

    private ChainTail(@NotNull final ScheduledExecutor executor, @NotNull final ChainHead<O> head) {
      mExecutor = executor;
      mHead = head;
    }

    void bind(@NotNull final PromiseChain<?, O> tail, @NotNull final PromiseChain<O, ?> bond) {
      mExecutor.execute(new Runnable() {

        public void run() {
          mBond = bond;
          final ArrayList<Resolution<O>> outputs;
          synchronized (mMutex) {
            outputs = mHead.consumeOutputs();
          }

          try {
            for (final Resolution<O> output : outputs) {
              output.consume(bond);
            }

            final PromiseState state = mState;
            if (state.isResolved()) {
              bond.resolve();
            }

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
          }

          tail.setNext(bond);
        }
      });
    }

    @Override
    void add(final PromiseChain<Object, ?> next, final O input) {
      mExecutor.execute(new Runnable() {

        public void run() {
          final PromiseChain<O, ?> bond = mBond;
          if (bond == null) {
            synchronized (mMutex) {
              try {
                mHead.innerAdd(input);

              } finally {
                mMutex.notifyAll();
              }
            }

          } else {
            bond.add(input);
          }
        }
      });
    }

    @Override
    void addAll(final PromiseChain<Object, ?> next, final Iterable<O> inputs) {
      mExecutor.execute(new Runnable() {

        public void run() {
          final PromiseChain<O, ?> bond = mBond;
          if (bond == null) {
            synchronized (mMutex) {
              try {
                @SuppressWarnings("UnnecessaryLocalVariable") final ChainHead<O> head = mHead;
                for (final O input : inputs) {
                  head.innerAdd(input);
                }

              } finally {
                mMutex.notifyAll();
              }
            }

          } else {
            bond.addAll(inputs);
          }
        }
      });
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
    void reject(final PromiseChain<Object, ?> next, final Throwable reason) {
      final PromiseChain<O, ?> bond;
      synchronized (mMutex) {
        try {
          mState = PromiseState.Rejected;
          if ((bond = mBond) == null) {
            mHead.innerReject(reason);
            return;
          }

        } finally {
          mMutex.notifyAll();
        }
      }

      bond.reject(reason);
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
      mTimeUnit = ((timeUnit.toNanos(timeout) % TimeUnit.MILLISECONDS.toNanos(1)) == 0)
          ? TimeUnit.MILLISECONDS : TimeUnit.NANOSECONDS;
      mTimeout = mTimeUnit.convert(timeout, timeUnit) + TimeUtils.currentTimeIn(mTimeUnit);
    }

    @SuppressWarnings("unchecked")
    public boolean hasNext() {
      final ChainHead<?> head = mHead;
      final long timeout = remainingTime();
      synchronized (mMutex) {
        final int index = mIndex;
        try {
          if (TimeUtils.waitUntil(mMutex, new Condition() {

            public boolean isTrue() {
              checkBound();
              return ((head.getOutputs().size() > index) || head.getState().isResolved());
            }
          }, timeout, mTimeUnit)) {
            final List<O> outputs = (List<O>) head.getOutputs();
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
      synchronized (mMutex) {
        final int index = mIndex;
        try {
          if (TimeUtils.waitUntil(mMutex, new Condition() {

            public boolean isTrue() {
              checkBound();
              return ((head.getOutputs().size() > index) || head.getState().isResolved());
            }
          }, timeout, mTimeUnit)) {
            final List<O> outputs = (List<O>) head.getOutputs();
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

        final List<O> outputs = (List<O>) mHead.getOutputs();
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

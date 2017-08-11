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
import java.util.concurrent.TimeUnit;

import dm.james.log.Log;
import dm.james.log.Log.Level;
import dm.james.log.Logger;
import dm.james.promise.Action;
import dm.james.promise.Mapper;
import dm.james.promise.Observer;
import dm.james.promise.Promise;
import dm.james.promise.RejectionException;
import dm.james.promise.TimeoutException;
import dm.james.util.ConstantConditions;
import dm.james.util.InterruptedExecutionException;
import dm.james.util.SerializableProxy;
import dm.james.util.ThreadUtils;
import dm.james.util.TimeUtils;
import dm.james.util.TimeUtils.Condition;

/**
 * Created by davide-maestroni on 07/03/2017.
 */
class DefaultPromise<O> implements Promise<O> {

  private final ChainHead<?> mHead;

  private final Logger mLogger;

  private final Object mMutex;

  private final Observer<Callback<?>> mObserver;

  private final PropagationType mPropagationType;

  private final PromiseChain<?, O> mTail;

  private PromiseChain<O, ?> mBond;

  private PromiseState mState = PromiseState.Pending;

  @SuppressWarnings("unchecked")
  DefaultPromise(@NotNull final Observer<? super Callback<O>> observer,
      @Nullable final PropagationType propagationType, @Nullable final Log log,
      @Nullable final Level level) {
    mObserver = (Observer<Callback<?>>) ConstantConditions.notNull("observer", observer);
    mLogger = Logger.newLogger(log, level, this);
    mPropagationType = (propagationType != null) ? propagationType : PropagationType.LOOP;
    final ChainHead<O> head = new ChainHead<O>();
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
  private DefaultPromise(@NotNull final Observer<Callback<?>> observer,
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
    tail.setNext(new ChainTail());
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
  private DefaultPromise(@NotNull final Observer<Callback<?>> observer,
      @NotNull final PropagationType propagationType, @NotNull final Logger logger,
      @NotNull final ChainHead<?> head, @NotNull final PromiseChain<?, ?> tail,
      @NotNull final PromiseChain<?, O> chain) {
    // bind
    mObserver = observer;
    mPropagationType = propagationType;
    mLogger = logger;
    mMutex = head.getMutex();
    mHead = head;
    mTail = chain;
    chain.setNext(new ChainTail());
    ((PromiseChain<?, Object>) tail).setNext((PromiseChain<Object, O>) chain);
  }

  @SuppressWarnings("unchecked")
  private DefaultPromise(@NotNull final Observer<Callback<?>> observer,
      @NotNull final PropagationType propagationType, @NotNull final Logger logger,
      @NotNull final ChainHead<?> head, @NotNull final PromiseChain<?, O> tail) {
    // copy
    mObserver = observer;
    mPropagationType = propagationType;
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
  public <R> Promise<R> apply(@NotNull final Mapper<Promise<O>, Promise<R>> mapper) {
    try {
      return ConstantConditions.notNull("promise", mapper.apply(this));

    } catch (final Throwable t) {
      InterruptedExecutionException.throwIfInterrupt(t);
      mLogger.err(t, "Error while applying promise transformation");
      throw RejectionException.wrapIfNotRejectionException(t);
    }
  }

  @NotNull
  public Promise<O> catchAny(@NotNull final Mapper<Throwable, O> mapper) {
    return then(null, new HandlerCatch<O>(mapper));
  }

  public O get() {
    return get(-1, TimeUnit.MILLISECONDS);
  }

  @SuppressWarnings("unchecked")
  public O get(final long timeout, @NotNull final TimeUnit timeUnit) {
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
          return (O) head.getOutput();
        }

      } catch (final InterruptedException e) {
        throw new InterruptedExecutionException(e);
      }
    }

    throw new TimeoutException(
        "timeout while waiting for promise resolution [" + timeout + " " + timeUnit + "]");
  }

  @SuppressWarnings("unchecked")
  public O getOr(final O other, final long timeout, @NotNull final TimeUnit timeUnit) {
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
          return (O) head.getOutput();
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
  public <R> Promise<R> then(@Nullable final Handler<O, ? super Callback<R>> fulfill,
      @Nullable final Handler<Throwable, ? super Callback<R>> reject) {
    return chain(new ChainHandler<O, R>(mPropagationType, fulfill, reject));
  }

  @NotNull
  public <R> Promise<R> then(@NotNull final Mapper<O, R> mapper) {
    return then(new HandlerMap<O, R>(mapper), null);
  }

  @NotNull
  public <R> Promise<R> thenTry(@Nullable Handler<O, ? super Callback<R>> fulfill,
      @Nullable Handler<Throwable, ? super Callback<R>> reject) {
    final Logger logger = mLogger;
    return then(
        (fulfill != null) ? new HandlerTry<O, R>(fulfill, logger.getLog(), logger.getLogLevel())
            : null, (reject != null) ? new HandlerTry<Throwable, R>(reject, logger.getLog(),
            logger.getLogLevel()) : null);
  }

  @NotNull
  public <R> Promise<R> thenTry(@NotNull final Mapper<O, R> mapper) {
    return thenTry(new HandlerMap<O, R>(mapper), null);
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

  @NotNull
  public Promise<O> whenFulfilled(@NotNull final Observer<O> observer) {
    return then(new HandlerFulfilled<O>(observer), null);
  }

  @NotNull
  public Promise<O> whenRejected(@NotNull final Observer<Throwable> observer) {
    return then(null, new HandlerRejected<O>(observer));
  }

  @NotNull
  public Promise<O> whenResolved(@NotNull final Action action) {
    return then(new HandlerResolvedFulfill<O>(action), new HandlerResolvedReject<O>(action));
  }

  @NotNull
  private <R> Promise<R> chain(@NotNull final PromiseChain<O, R> chain) {
    final ChainHead<?> head = mHead;
    final PropagationType propagationType = mPropagationType;
    final Logger logger = mLogger;
    final boolean isBound;
    final Runnable binding;
    synchronized (mMutex) {
      if (mBond != null) {
        isBound = true;
        binding = null;

      } else {
        isBound = false;
        chain.setLogger(logger);
        binding = head.bind(chain);
        mBond = chain;
        mMutex.notifyAll();
      }
    }

    if (isBound) {
      return copy().chain(chain);

    }

    final DefaultPromise<R> promise =
        new DefaultPromise<R>(mObserver, propagationType, logger, head, mTail, chain);
    if (binding != null) {
      propagationType.execute(binding);
    }

    return promise;
  }

  private void checkBound() {
    if (mBond != null) {
      throw new IllegalStateException("the promise has been bound");
    }
  }

  @SuppressWarnings("unchecked")
  private DefaultPromise<O> copy() {
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

    return new DefaultPromise<O>(mObserver, mPropagationType, logger, newHead,
        (PromiseChain<?, O>) newTail);
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

  private static class ChainHandler<O, R> extends PromiseChain<O, R> {

    private final Handler<O, ? super Callback<R>> mFulfill;

    private final PropagationType mPropagationType;

    private final Handler<Throwable, ? super Callback<R>> mReject;

    @SuppressWarnings("unchecked")
    private ChainHandler(@NotNull final PropagationType propagationType,
        @Nullable final Handler<O, ? super Callback<R>> fulfill,
        @Nullable final Handler<Throwable, ? super Callback<R>> reject) {
      mFulfill = (Handler<O, ? super Callback<R>>) ((fulfill != null) ? fulfill
          : new PassThroughOutputHandler<O, R>());
      mReject = (Handler<Throwable, ? super Callback<R>>) ((reject != null) ? reject
          : new PassThroughErrorHandler<R>());
      mPropagationType = propagationType;
    }

    @NotNull
    PromiseChain<O, R> copy() {
      return new ChainHandler<O, R>(mPropagationType, mFulfill, mReject);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ChainProxy<O, R>(mFulfill, mReject, mPropagationType);
    }

    private static class ChainProxy<O, R> extends SerializableProxy {

      private ChainProxy(final Handler<O, ? super Callback<R>> fulfill,
          final Handler<Throwable, ? super Callback<R>> reject,
          final PropagationType propagationType) {
        super(proxy(fulfill), proxy(reject), propagationType);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ChainHandler<O, R>((PropagationType) args[2],
              (Handler<O, ? super Callback<R>>) args[0],
              (Handler<Throwable, ? super Callback<R>>) args[1]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    @Override
    void reject(final PromiseChain<R, ?> next, final Throwable reason) {
      mPropagationType.execute(new Runnable() {

        public void run() {
          try {
            getLogger().dbg("Processing rejection with reason: %s", reason);
            mReject.accept(reason, next);

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            getLogger().err(t, "Error while processing rejection with reason: %s", reason);
            next.reject(t);
          }
        }
      });
    }

    @Override
    void resolve(final PromiseChain<R, ?> next, final O input) {
      mPropagationType.execute(new Runnable() {

        public void run() {
          try {
            getLogger().dbg("Processing resolution: %s", input);
            mFulfill.accept(input, next);

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            getLogger().err(t, "Error while processing resolution: %s", input);
            next.reject(t);
          }
        }
      });
    }
  }

  private static class ChainHead<O> extends PromiseChain<O, O> {

    private final Object mMutex = new Object();

    private Throwable mException;

    private StatePending mInnerState = new StatePending();

    private Object mOutput;

    private PromiseState mState = PromiseState.Pending;

    @Nullable
    Runnable bind(@NotNull final PromiseChain<?, ?> bond) {
      final Runnable binding = mInnerState.bind(bond);
      mState = PromiseState.Pending;
      return binding;
    }

    @Nullable
    RejectionException getException() {
      return mInnerState.getException();
    }

    @NotNull
    Object getMutex() {
      return mMutex;
    }

    Object getOutput() {
      return mInnerState.getOutput();
    }

    @NotNull
    PromiseState getState() {
      return mState;
    }

    void innerReject(@Nullable final Throwable reason) {
      mInnerState.innerReject(reason);
      mState = PromiseState.Rejected;
    }

    void innerResolve(final Object output) {
      mInnerState.innerResolve(output);
      mState = PromiseState.Fulfilled;
    }

    private class StateFulfilled extends StatePending {

      @Nullable
      @Override
      Runnable bind(@NotNull final PromiseChain<?, ?> bond) {
        getLogger().dbg("Binding promise [%s => %s]", PromiseState.Fulfilled, PromiseState.Pending);
        final Object output = mOutput;
        mOutput = null;
        mInnerState = new StatePending();
        return new Runnable() {

          @SuppressWarnings("unchecked")
          public void run() {
            ((PromiseChain<Object, ?>) bond).resolve(output);
          }
        };
      }

      @Override
      Object getOutput() {
        return mOutput;
      }

      @Override
      void innerReject(@Nullable final Throwable reason) {
        throw exception(PromiseState.Fulfilled);
      }

      @Override
      void innerResolve(final Object output) {
        throw exception(PromiseState.Fulfilled);
      }
    }

    private class StatePending {

      @Nullable
      Runnable bind(@NotNull final PromiseChain<?, ?> bond) {
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

      Object getOutput() {
        throw exception(PromiseState.Pending);
      }

      void innerReject(@Nullable final Throwable reason) {
        getLogger().dbg("Rejecting promise with reason [%s => %s]: %s", PromiseState.Pending,
            PromiseState.Rejected, reason);
        mException = reason;
        mInnerState = new StateRejected();
      }

      void innerResolve(final Object output) {
        getLogger().dbg("Resolving promise with resolution [%s => %s]: %s", PromiseState.Pending,
            PromiseState.Fulfilled, output);
        mOutput = output;
        mInnerState = new StateFulfilled();
      }
    }

    private class StateRejected extends StatePending {

      @Nullable
      @Override
      Runnable bind(@NotNull final PromiseChain<?, ?> bond) {
        getLogger().dbg("Binding promise [%s => %s]", PromiseState.Rejected, PromiseState.Pending);
        final Throwable exception = mException;
        mException = null;
        mInnerState = new StatePending();
        return new Runnable() {

          public void run() {
            bond.reject(exception);
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
      void innerResolve(final Object output) {
        throw exception(PromiseState.Rejected);
      }
    }

    @NotNull
    ChainHead<O> copy() {
      return new ChainHead<O>();
    }

    @Override
    public void resolve(final PromiseChain<O, ?> next, final O input) {
      try {
        next.resolve(input);

      } catch (final Throwable t) {
        InterruptedExecutionException.throwIfInterrupt(t);
        getLogger().err(t, "Error while propagating resolution: %s", input);
        next.reject(t);
      }
    }

    @Override
    public void reject(final PromiseChain<O, ?> next, final Throwable reason) {
      next.reject(reason);
    }
  }

  private static class HandlerCatch<O> implements Handler<Throwable, Callback<O>>, Serializable {

    private final Mapper<Throwable, O> mMapper;

    private HandlerCatch(@NotNull final Mapper<Throwable, O> mapper) {
      mMapper = ConstantConditions.notNull("mapper", mapper);
    }

    public void accept(final Throwable reason, final Callback<O> callback) throws Exception {
      callback.resolve(mMapper.apply(reason));
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
          return new HandlerCatch<O>((Mapper<Throwable, O>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }
  }

  private static class HandlerFulfilled<O> implements Handler<O, Callback<O>>, Serializable {

    private final Observer<O> mObserver;

    private HandlerFulfilled(@NotNull final Observer<O> observer) {
      mObserver = ConstantConditions.notNull("observer", observer);
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
          return new HandlerFulfilled<O>((Observer<O>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void accept(final O input, final Callback<O> callback) throws Exception {
      mObserver.accept(input);
      callback.resolve(input);
    }
  }

  private static class HandlerMap<O, R> implements Handler<O, Callback<R>>, Serializable {

    private final Mapper<O, R> mMapper;

    private HandlerMap(@NotNull final Mapper<O, R> mapper) {
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
          return new HandlerMap<O, R>((Mapper<O, R>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void accept(final O input, final Callback<R> callback) throws Exception {
      callback.resolve(mMapper.apply(input));
    }
  }

  private static class HandlerRejected<O> implements Handler<Throwable, Callback<O>>, Serializable {

    private final Observer<Throwable> mObserver;

    private HandlerRejected(@NotNull final Observer<Throwable> observer) {
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
          return new HandlerRejected<O>((Observer<Throwable>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void accept(final Throwable reason, final Callback<O> callback) throws Exception {
      mObserver.accept(reason);
      callback.reject(reason);
    }
  }

  private static class HandlerResolvedFulfill<O> implements Handler<O, Callback<O>>, Serializable {

    private final Action mAction;

    private HandlerResolvedFulfill(@NotNull final Action action) {
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
          return new HandlerResolvedFulfill<O>((Action) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void accept(final O input, final Callback<O> callback) throws Exception {
      mAction.perform();
      callback.resolve(input);
    }
  }

  private static class HandlerResolvedReject<O>
      implements Handler<Throwable, Callback<O>>, Serializable {

    private final Action mAction;

    private HandlerResolvedReject(@NotNull final Action action) {
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
          return new HandlerResolvedReject<O>((Action) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void accept(final Throwable reason, final Callback<O> callback) throws Exception {
      mAction.perform();
      callback.reject(reason);
    }
  }

  private static class HandlerTry<O, R> implements Handler<O, Callback<R>>, Serializable {

    private final Handler<O, ? super Callback<R>> mHandler;

    private final Logger mLogger;

    @SuppressWarnings("unchecked")
    private HandlerTry(@NotNull final Handler<O, ? super Callback<R>> handler,
        @Nullable final Log log, @Nullable final Level level) {
      mHandler = ConstantConditions.notNull("handler", handler);
      mLogger = Logger.newLogger(log, level, this);
    }

    private Object writeReplace() throws ObjectStreamException {
      final Logger logger = mLogger;
      return new HandlerProxy<O, R>(mHandler, logger.getLog(), logger.getLogLevel());
    }

    private static class HandlerProxy<O, R> extends SerializableProxy {

      private HandlerProxy(final Handler<O, ? super Callback<R>> handler, final Log log,
          final Level level) {
        super(proxy(handler), log, level);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new HandlerTry<O, R>((Handler<O, ? super Callback<R>>) args[0], (Log) args[1],
              (Level) args[2]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void accept(final O input, final Callback<R> callback) {
      try {
        mHandler.accept(input, new Callback<R>() {

          public void defer(@NotNull final Promise<R> promise) {
            safeClose(input, mLogger);
            callback.defer(promise);
          }

          public void reject(final Throwable reason) {
            safeClose(input, mLogger);
            callback.reject(reason);
          }

          public void resolve(final R output) {
            safeClose(input, mLogger);
            callback.resolve(output);
          }
        });

      } catch (final Throwable t) {
        safeClose(input, mLogger);
        InterruptedExecutionException.throwIfInterrupt(t);
        throw RejectionException.wrapIfNot(RuntimeException.class, t);
      }
    }
  }

  private static class PassThroughErrorHandler<R>
      implements Handler<Throwable, Callback<R>>, Serializable {

    public void accept(final Throwable input, @NotNull final Callback<R> callback) {
      callback.reject(input);
    }
  }

  private static class PassThroughOutputHandler<O, R>
      implements Handler<O, Callback<R>>, Serializable {

    @SuppressWarnings("unchecked")
    public void accept(final O input, @NotNull final Callback<R> callback) {
      callback.resolve((R) input);
    }
  }

  private static abstract class PromiseChain<I, O> implements Callback<I>, Serializable {

    private transient volatile StatePending mInnerState = new StatePending();

    private transient Logger mLogger;

    private transient volatile PromiseChain<O, ?> mNext;

    @NotNull
    abstract PromiseChain<I, O> copy();

    Logger getLogger() {
      return mLogger;
    }

    void setLogger(@NotNull final Logger logger) {
      mLogger = logger.subContextLogger(this);
    }

    abstract void reject(PromiseChain<O, ?> next, Throwable reason);

    abstract void resolve(PromiseChain<O, ?> next, I input);

    void setNext(@NotNull PromiseChain<O, ?> next) {
      mNext = next;
    }

    private class StatePending {

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
      boolean reject(final Throwable reason) {
        mLogger.wrn(reason, "Suppressed rejection");
        return false;
      }

      @Override
      void resolve() {
        final Throwable reason = mReason;
        mLogger.wrn("Chain has been already rejected with reason: %s", reason);
        throw RejectionException.wrapIfNotRejectionException(reason);
      }
    }

    private class StateResolved extends StatePending {

      @Override
      void resolve() {
        mLogger.wrn("Chain has been already resolved");
        throw new IllegalStateException("chain has been already resolved");
      }

      @Override
      boolean reject(final Throwable reason) {
        mInnerState = new StateRejected(reason);
        return true;
      }
    }

    public final void defer(@NotNull final Promise<I> promise) {
      mInnerState.resolve();
      promise.then(new Handler<I, Callback<Void>>() {

        public void accept(final I input, final Callback<Void> callback) {
          resolve(mNext, input);
        }
      }, new Handler<Throwable, Callback<Void>>() {

        public void accept(final Throwable reason, final Callback<Void> callback) {
          reject(mNext, reason);
        }
      });
    }

    public final void reject(final Throwable reason) {
      if (mInnerState.reject(reason)) {
        reject(mNext, reason);
      }
    }

    public final void resolve(final I output) {
      mInnerState.resolve();
      resolve(mNext, output);
    }
  }

  private static class PromiseProxy extends SerializableProxy {

    private PromiseProxy(final Observer<? extends Callback<?>> observer,
        final PropagationType propagationType, final Log log, final Level logLevel,
        final List<PromiseChain<?, ?>> chains) {
      super(proxy(observer), propagationType, log, logLevel, chains);
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

        return new DefaultPromise<Object>((Observer<Callback<?>>) args[0],
            (PropagationType) args[1], (Log) args[2], (Level) args[3], head,
            (PromiseChain<?, Object>) tail);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }

  private class ChainTail extends PromiseChain<O, Object> {

    @NotNull
    @Override
    PromiseChain<O, Object> copy() {
      return ConstantConditions.unsupported();
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
    void resolve(final PromiseChain<Object, ?> next, final O input) {
      final PromiseChain<O, ?> bond;
      synchronized (mMutex) {
        try {
          mState = PromiseState.Fulfilled;
          if ((bond = mBond) == null) {
            mHead.innerResolve(input);
            return;
          }

        } finally {
          mMutex.notifyAll();
        }
      }

      bond.resolve(input);
    }
  }
}

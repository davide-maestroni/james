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

import dm.james.executor.ScheduledExecutor;
import dm.james.executor.ScheduledExecutors;
import dm.james.log.Log;
import dm.james.log.Log.Level;
import dm.james.log.Logger;
import dm.james.promise.Action;
import dm.james.promise.CancellationException;
import dm.james.promise.Chainable;
import dm.james.promise.Mapper;
import dm.james.promise.Observer;
import dm.james.promise.Promise;
import dm.james.promise.PromiseInspection;
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

  private final ScheduledExecutor mExecutor;

  private final ChainHead<?> mHead;

  private final Logger mLogger;

  private final Object mMutex;

  private final Observer<Callback<?>> mObserver;

  private final PromiseChain<?, O> mTail;

  private PromiseChain<O, ?> mChain;

  private PromiseState mState = PromiseState.Pending;

  @SuppressWarnings("unchecked")
  DefaultPromise(@NotNull final Observer<? super Callback<O>> observer, @Nullable final Log log,
      @Nullable final Level level) {
    mObserver = (Observer<Callback<?>>) ConstantConditions.notNull("observer", observer);
    mExecutor = ScheduledExecutors.immediateExecutor();
    mLogger = Logger.newLogger(log, level, this);
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
      @NotNull final ScheduledExecutor executor, @Nullable final Log log,
      @Nullable final Level level, @NotNull final ChainHead<?> head,
      @NotNull final PromiseChain<?, O> tail) {
    // serialization
    mObserver = observer;
    mExecutor = executor;
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
  private DefaultPromise(@NotNull final Observer<Callback<?>> observer,
      @NotNull final ScheduledExecutor executor, @NotNull final Logger logger,
      @NotNull final ChainHead<?> head, @NotNull final PromiseChain<?, ?> tail,
      @NotNull final PromiseChain<?, O> chain) {
    // bind
    mObserver = observer;
    mExecutor = executor;
    mLogger = logger;
    mMutex = head.getMutex();
    mHead = head;
    mTail = chain;
    chain.setNext(new ChainTail());
    ((PromiseChain<?, Object>) tail).setNext((PromiseChain<Object, O>) chain);
  }

  @SuppressWarnings("unchecked")
  private DefaultPromise(@NotNull final Observer<Callback<?>> observer,
      @NotNull final ScheduledExecutor executor, @NotNull final Logger logger,
      @NotNull final ChainHead<?> head, @NotNull final PromiseChain<?, O> tail) {
    // copy
    mObserver = observer;
    mExecutor = executor;
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
  public Promise<O> catchAll(@NotNull final Iterable<Class<? extends Throwable>> errors,
      @NotNull final Mapper<Throwable, O> mapper) {
    return then(new HandlerCatchFiltered<O>(errors, mapper));
  }

  @NotNull
  public Promise<O> catchAll(@NotNull final Mapper<Throwable, O> mapper) {
    return then(new HandlerCatch<O>(mapper));
  }

  @NotNull
  public Promise<O> catchAllFlat(@NotNull final Iterable<Class<? extends Throwable>> errors,
      @NotNull final Mapper<Throwable, Chainable<? extends O>> mapper) {
    return then(new HandlerCatchFilteredTrusted<O>(errors, mapper));
  }

  @NotNull
  public Promise<O> catchAllFlat(
      @NotNull final Mapper<Throwable, Chainable<? extends O>> mapper) {
    return then(new HandlerCatchTrusted<O>(mapper));
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

  @NotNull
  public Promise<PromiseInspection<O>> inspect() {
    return chain(new ChainHandler<O, PromiseInspection<O>>(new HandlerInspect<O>()),
        ScheduledExecutors.immediateExecutor(), ScheduledExecutors.immediateExecutor());
  }

  public boolean isChained() {
    synchronized (mMutex) {
      return (mChain != null);
    }
  }

  @NotNull
  public Promise<O> renew() {
    return copy();
  }

  @NotNull
  public Promise<O> scheduleAll(@NotNull final ScheduledExecutor executor) {
    return chain(new ChainHandler<O, O>(new HandlerSchedule<O>(executor)), mExecutor, executor);
  }

  @NotNull
  public <R> Promise<R> then(@NotNull final Handler<O, ? super Callback<R>> handler) {
    return chain(new ChainHandler<O, R>(handler));
  }

  @NotNull
  public <R> Promise<R> then(@NotNull final Mapper<O, R> mapper) {
    return then(new HandlerMap<O, R>(mapper));
  }

  @NotNull
  public <R> Promise<R> thenFlat(@NotNull final Mapper<O, Chainable<? extends R>> mapper) {
    return then(new HandlerMapTrusted<O, R>(mapper));
  }

  @NotNull
  public <R> Promise<R> thenTry(@NotNull final Handler<O, ? super Callback<R>> handler) {
    final Logger logger = mLogger;
    return then(new HandlerTry<O, R>(handler, logger.getLog(), logger.getLogLevel()));
  }

  @NotNull
  public <R> Promise<R> thenTry(@NotNull final Mapper<O, R> mapper) {
    return thenTry(new HandlerMap<O, R>(mapper));
  }

  @NotNull
  public <R> Promise<R> thenTryFlat(@NotNull final Mapper<O, Chainable<? extends R>> mapper) {
    return thenTry(new HandlerMapTrusted<O, R>(mapper));
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
  public Promise<O> onFulfill(@NotNull final Observer<O> observer) {
    return then(new HandlerFulfilled<O>(observer));
  }

  @NotNull
  public Promise<O> onReject(@NotNull final Observer<Throwable> observer) {
    return then(new HandlerRejected<O>(observer));
  }

  @NotNull
  public Promise<O> onResolve(@NotNull final Action action) {
    return then(new HandlerResolved<O>(action));
  }

  public boolean isFulfilled() {
    synchronized (mMutex) {
      return (mState == PromiseState.Fulfilled) || (mHead.getState() == PromiseState.Fulfilled);
    }
  }

  @NotNull
  private <R> Promise<R> chain(@NotNull final PromiseChain<O, R> chain) {
    return chain(chain, mExecutor, mExecutor);
  }

  @NotNull
  private <R> Promise<R> chain(@NotNull final PromiseChain<O, R> chain,
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
      return copy().chain(chain);

    }

    final DefaultPromise<R> promise =
        new DefaultPromise<R>(mObserver, newExecutor, logger, head, mTail, chain);
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

    return new DefaultPromise<O>(mObserver, mExecutor, logger, newHead,
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
    return new PromiseProxy(mObserver, mExecutor, logger.getLog(), logger.getLogLevel(), chains);
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

    private final Handler<O, ? super Callback<R>> mHandler;

    @SuppressWarnings("unchecked")
    private ChainHandler(@NotNull final Handler<O, ? super Callback<R>> handler) {
      mHandler = ConstantConditions.notNull("handler", handler);
    }

    @NotNull
    PromiseChain<O, R> copy() {
      return new ChainHandler<O, R>(mHandler);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ChainProxy<O, R>(mHandler);
    }

    private static class ChainProxy<O, R> extends SerializableProxy {

      private ChainProxy(Handler<O, ? super Callback<R>> handler) {
        super(proxy(handler));
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ChainHandler<O, R>((Handler<O, ? super Callback<R>>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    @Override
    void reject(final PromiseChain<R, ?> next, final Throwable reason) {
      try {
        getLogger().dbg("Processing rejection with reason: %s", reason);
        mHandler.reject(reason, next);

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
    void resolve(final PromiseChain<R, ?> next, final O input) {
      try {
        getLogger().dbg("Processing fulfillment: %s", input);
        mHandler.fulfill(input, next);

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Promise has been cancelled");
        next.reject(e);

      } catch (final Throwable t) {
        InterruptedExecutionException.throwIfInterrupt(t);
        getLogger().err(t, "Error while processing fulfillment: %s", input);
        next.reject(t);
      }
    }
  }

  private static class ChainHead<O> extends PromiseChain<O, O> {

    private final Object mMutex = new Object();

    private Throwable mException;

    private StatePending mInnerState = new StatePending();

    private Object mOutput;

    private PromiseState mState = PromiseState.Pending;

    @Nullable
    Runnable bind(@NotNull final PromiseChain<?, ?> chain,
        @NotNull final ScheduledExecutor executor) {
      final Runnable binding = mInnerState.bind(chain, executor);
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
      Runnable bind(@NotNull final PromiseChain<?, ?> chain,
          @NotNull final ScheduledExecutor executor) {
        getLogger().dbg("Binding promise [%s => %s]", PromiseState.Fulfilled, PromiseState.Pending);
        final Object output = mOutput;
        mOutput = null;
        mInnerState = new StatePending();
        return new Runnable() {

          public void run() {
            executor.execute(new Runnable() {

              @SuppressWarnings("unchecked")
              public void run() {
                ((PromiseChain<Object, ?>) chain).resolve(output);
              }
            });
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
      next.resolve(input);
    }

    @Override
    public void reject(final PromiseChain<O, ?> next, final Throwable reason) {
      next.reject(reason);
    }
  }

  private static class HandlerCatch<O> implements Handler<O, Callback<O>>, Serializable {

    private final Mapper<Throwable, O> mMapper;

    private HandlerCatch(@NotNull final Mapper<Throwable, O> mapper) {
      mMapper = ConstantConditions.notNull("mapper", mapper);
    }

    public void fulfill(final O input, @NotNull final Callback<O> callback) {
      callback.resolve(input);
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

    public void reject(final Throwable reason, @NotNull final Callback<O> callback) throws
        Exception {
      callback.resolve(mMapper.apply(reason));
    }
  }

  private static class HandlerCatchFiltered<O> implements Handler<O, Callback<O>>, Serializable {

    private final Iterable<Class<? extends Throwable>> mErrors;

    private final Mapper<Throwable, O> mMapper;

    private HandlerCatchFiltered(@NotNull final Iterable<Class<? extends Throwable>> errors,
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
          return new HandlerCatchFiltered<O>((Iterable<Class<? extends Throwable>>) args[0],
              (Mapper<Throwable, O>) args[1]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void fulfill(final O input, @NotNull final Callback<O> callback) {
      callback.resolve(input);
    }

    public void reject(final Throwable reason, @NotNull final Callback<O> callback) throws
        Exception {
      for (final Class<? extends Throwable> error : mErrors) {
        if (error.isInstance(reason)) {
          callback.resolve(mMapper.apply(reason));
          return;
        }
      }

      callback.reject(reason);
    }
  }

  private static class HandlerCatchFilteredTrusted<O>
      implements Handler<O, Callback<O>>, Serializable {

    private final Iterable<Class<? extends Throwable>> mErrors;

    private final Mapper<Throwable, Chainable<? extends O>> mMapper;

    private HandlerCatchFilteredTrusted(@NotNull final Iterable<Class<? extends Throwable>> errors,
        @NotNull final Mapper<Throwable, Chainable<? extends O>> mapper) {
      mErrors = ConstantConditions.notNull("errors", errors);
      mMapper = ConstantConditions.notNull("mapper", mapper);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new HandlerProxy<O>(mErrors, mMapper);
    }

    private static class HandlerProxy<O> extends SerializableProxy {

      private HandlerProxy(final Iterable<Class<? extends Throwable>> errors,
          final Mapper<Throwable, Chainable<? extends O>> mapper) {
        super(errors, proxy(mapper));
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new HandlerCatchFilteredTrusted<O>((Iterable<Class<? extends Throwable>>) args[0],
              (Mapper<Throwable, Chainable<? extends O>>) args[1]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void fulfill(final O input, @NotNull final Callback<O> callback) {
      callback.resolve(input);
    }

    @SuppressWarnings("unchecked")
    public void reject(final Throwable reason, @NotNull final Callback<O> callback) throws
        Exception {
      for (final Class<? extends Throwable> error : mErrors) {
        if (error.isInstance(reason)) {
          callback.defer((Chainable<O>) mMapper.apply(reason));
          return;
        }
      }

      callback.reject(reason);
    }
  }

  private static class HandlerCatchTrusted<O> implements Handler<O, Callback<O>>, Serializable {

    private final Mapper<Throwable, Chainable<? extends O>> mMapper;

    private HandlerCatchTrusted(@NotNull final Mapper<Throwable, Chainable<? extends O>> mapper) {
      mMapper = ConstantConditions.notNull("mapper", mapper);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new HandlerProxy<O>(mMapper);
    }

    private static class HandlerProxy<O> extends SerializableProxy {

      private HandlerProxy(final Mapper<Throwable, Chainable<? extends O>> mapper) {
        super(proxy(mapper));
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new HandlerCatchTrusted<O>((Mapper<Throwable, Chainable<? extends O>>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void fulfill(final O input, @NotNull final Callback<O> callback) {
      callback.resolve(input);
    }

    @SuppressWarnings("unchecked")
    public void reject(final Throwable reason, @NotNull final Callback<O> callback) throws
        Exception {
      callback.defer((Chainable<O>) mMapper.apply(reason));
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

    public void fulfill(final O input, @NotNull final Callback<O> callback) throws Exception {
      mObserver.accept(input);
      callback.resolve(input);
    }

    public void reject(final Throwable reason, @NotNull final Callback<O> callback) {
      callback.reject(reason);
    }
  }

  private static class HandlerInspect<O>
      implements Handler<O, Callback<PromiseInspection<O>>>, Serializable {

    public void fulfill(final O input, @NotNull final Callback<PromiseInspection<O>> callback) {
      callback.resolve(new PromiseInspectionFulfilled<O>(input));
    }

    public void reject(final Throwable reason,
        @NotNull final Callback<PromiseInspection<O>> callback) {
      callback.resolve(new PromiseInspectionRejected<O>(reason));
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

    public void fulfill(final O input, @NotNull final Callback<R> callback) throws Exception {
      callback.resolve(mMapper.apply(input));
    }

    public void reject(final Throwable reason, @NotNull final Callback<R> callback) {
      callback.reject(reason);
    }
  }

  private static class HandlerMapTrusted<O, R> implements Handler<O, Callback<R>>, Serializable {

    private final Mapper<O, Chainable<? extends R>> mMapper;

    private HandlerMapTrusted(@NotNull final Mapper<O, Chainable<? extends R>> mapper) {
      mMapper = ConstantConditions.notNull("mapper", mapper);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new HandlerProxy<O, R>(mMapper);
    }

    private static class HandlerProxy<O, R> extends SerializableProxy {

      private HandlerProxy(final Mapper<O, Chainable<? extends R>> mapper) {
        super(proxy(mapper));
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new HandlerMapTrusted<O, R>((Mapper<O, Chainable<? extends R>>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    @SuppressWarnings("unchecked")
    public void fulfill(final O input, @NotNull final Callback<R> callback) throws Exception {
      callback.defer((Chainable<R>) mMapper.apply(input));
    }

    public void reject(final Throwable reason, @NotNull final Callback<R> callback) {
      callback.reject(reason);
    }
  }

  private static class HandlerRejected<O> implements Handler<O, Callback<O>>, Serializable {

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

    public void fulfill(final O input, @NotNull final Callback<O> callback) {
      callback.resolve(input);
    }

    public void reject(final Throwable reason, @NotNull final Callback<O> callback) throws
        Exception {
      mObserver.accept(reason);
      callback.reject(reason);
    }
  }

  private static class HandlerResolved<O> implements Handler<O, Callback<O>>, Serializable {

    private final Action mAction;

    private HandlerResolved(@NotNull final Action action) {
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
          return new HandlerResolved<O>((Action) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void fulfill(final O input, @NotNull final Callback<O> callback) throws Exception {
      mAction.perform();
      callback.resolve(input);
    }

    public void reject(final Throwable reason, @NotNull final Callback<O> callback) throws
        Exception {
      mAction.perform();
      callback.reject(reason);
    }
  }

  private static class HandlerSchedule<I> implements Handler<I, Callback<I>>, Serializable {

    private final ScheduledExecutor mExecutor;

    private HandlerSchedule(@NotNull final ScheduledExecutor executor) {
      mExecutor = ConstantConditions.notNull("executor", executor);
    }

    public void fulfill(final I input, @NotNull final Callback<I> callback) {
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

    public void reject(final Throwable reason, @NotNull final Callback<I> callback) {
      if (reason instanceof CancellationException) {
        callback.reject(reason);

      } else {
        mExecutor.execute(new Runnable() {

          public void run() {
            callback.reject(reason);
          }
        });
      }
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

    public void fulfill(final O input, @NotNull final Callback<R> callback) throws Exception {
      try {
        mHandler.fulfill(input, new Callback<R>() {

          public void defer(@NotNull final Chainable<R> chainable) {
            close(input, mLogger);
            callback.defer(chainable);
          }

          public void reject(final Throwable reason) {
            close(input, mLogger);
            callback.reject(reason);
          }

          public void resolve(final R output) {
            close(input, mLogger);
            callback.resolve(output);
          }
        });

      } catch (final Throwable t) {
        safeClose(input, mLogger);
        InterruptedExecutionException.throwIfInterrupt(t);
        throw RejectionException.wrapIfNotException(t);
      }
    }

    public void reject(final Throwable reason, @NotNull final Callback<R> callback) throws
        Exception {
      try {
        mHandler.reject(reason, callback);

      } catch (final Throwable t) {
        InterruptedExecutionException.throwIfInterrupt(t);
        throw RejectionException.wrapIfNotException(t);
      }
    }
  }

  private static abstract class PromiseChain<I, O> implements Callback<I>, Serializable {

    private transient volatile StatePending mInnerState = new StatePending();

    private transient Logger mLogger;

    private transient volatile PromiseChain<O, ?> mNext;

    boolean cancel(@Nullable final Throwable reason) {
      if (mInnerState.reject(reason)) {
        reject(mNext, reason);
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
        mLogger.wrn("Promise has been already rejected with reason: %s", reason);
        throw RejectionException.wrapIfNotRejectionException(reason);
      }
    }

    private class StateResolved extends StatePending {

      @Override
      void resolve() {
        mLogger.wrn("Promise has been already resolved");
        throw new IllegalStateException("promise has been already resolved");
      }

      @Override
      boolean reject(final Throwable reason) {
        mLogger.wrn(reason, "Suppressed rejection");
        return false;
      }
    }

    public final void defer(@NotNull final Chainable<I> chainable) {
      mInnerState.resolve();
      chainable.then(new Handler<I, Callback<Void>>() {

        public void fulfill(final I input, @NotNull final Callback<Void> callback) {
          resolve(mNext, input);
        }

        public void reject(final Throwable reason, @NotNull final Callback<Void> callback) {
          PromiseChain.this.reject(mNext, reason);
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

    private PromiseProxy(final Observer<? extends Callback<?>> observer,
        final ScheduledExecutor executor, final Log log, final Level logLevel,
        final List<PromiseChain<?, ?>> chains) {
      super(proxy(observer), executor, log, logLevel, chains);
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
            (ScheduledExecutor) args[1], (Log) args[2], (Level) args[3], head,
            (PromiseChain<?, Object>) tail);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }

  private class ChainTail extends PromiseChain<O, Object> {

    private ChainTail() {
      setLogger(mLogger);
    }

    @Override
    boolean isTail() {
      return true;
    }

    @NotNull
    @Override
    PromiseChain<O, Object> copy() {
      return ConstantConditions.unsupported();
    }

    @Override
    void reject(final PromiseChain<Object, ?> next, final Throwable reason) {
      final PromiseChain<O, ?> chain;
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
    void resolve(final PromiseChain<Object, ?> next, final O input) {
      final PromiseChain<O, ?> chain;
      synchronized (mMutex) {
        try {
          mState = PromiseState.Fulfilled;
          if ((chain = mChain) == null) {
            mHead.innerResolve(input);
            return;
          }

        } finally {
          mMutex.notifyAll();
        }
      }

      chain.resolve(input);
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

  public O value() {
    synchronized (mMutex) {
      if (!isFulfilled()) {
        throw new IllegalStateException("the promise is not fulfilled");
      }

      return get(0, TimeUnit.MILLISECONDS);
    }
  }
}

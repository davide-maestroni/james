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
import java.util.List;
import java.util.concurrent.TimeUnit;

import dm.james.executor.InterruptedExecutionException;
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
import dm.james.util.SerializableProxy;
import dm.james.util.TimeUtils;
import dm.james.util.TimeUtils.Condition;

/**
 * Created by davide-maestroni on 07/03/2017.
 */
class DefaultPromise<O> implements Promise<O> {

  private final ChainHead<?> mHead;

  private final Logger mLogger;

  private final Object mMutex;

  private final Observer<? extends Callback<?>> mObserver;

  private final PropagationType mPropagationType;

  private final PromiseChain<?, O> mTail;

  private PromiseChain<O, ?> mBond;

  private PromiseState mState = PromiseState.Pending;

  DefaultPromise(@NotNull final Observer<Callback<O>> observer,
      @Nullable final PropagationType propagationType, @Nullable final Log log,
      @Nullable final Level level) {
    mObserver = ConstantConditions.notNull("observer", observer);
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
  private DefaultPromise(@NotNull final Observer<? extends Callback<?>> observer,
      @NotNull final PropagationType propagationType, @NotNull final Logger logger,
      @NotNull final ChainHead<?> head, @NotNull final PromiseChain<?, ?> tail,
      @NotNull final PromiseChain<?, O> chain) {
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
  private DefaultPromise(@NotNull final Observer<? extends Callback<?>> observer,
      @NotNull final PropagationType propagationType, @NotNull final Logger logger,
      @NotNull final ChainHead<?> head, @NotNull final PromiseChain<?, O> tail) {
    mObserver = observer;
    mPropagationType = propagationType;
    mLogger = logger;
    mMutex = head.getMutex();
    mHead = head;
    mTail = tail;
    tail.setNext(new ChainTail());
    try {
      ((Observer<Callback<?>>) observer).accept(head);

    } catch (final Throwable t) {
      InterruptedExecutionException.throwIfInterrupt(t);
      head.reject(t);
    }
  }

  @SuppressWarnings("unchecked")
  private DefaultPromise(@NotNull final Observer<? extends Callback<?>> observer,
      @NotNull final PropagationType propagationType, @Nullable final Log log,
      @Nullable final Level level, @NotNull final ChainHead<?> head,
      @NotNull final PromiseChain<?, O> tail) {
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
      ((Observer<Callback<?>>) observer).accept(head);

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
  public <R> Promise<R> apply(@NotNull final Mapper<Promise<O>, Promise<R>> mapper) {
    try {
      return ConstantConditions.notNull("promise", mapper.apply(this));

    } catch (final Throwable t) {
      InterruptedExecutionException.throwIfInterrupt(t);
      throw new RejectionException(t);
    }
  }

  public O get() {
    return get(-1, TimeUnit.MILLISECONDS);
  }

  @SuppressWarnings("unchecked")
  public O get(final long timeout, @NotNull final TimeUnit timeUnit) {
    final ChainHead<?> head = mHead;
    synchronized (mMutex) {
      try {
        if (TimeUtils.waitUntil(mMutex, new Condition() {

          public boolean isTrue() {
            checkBound();
            return head.isTerminated();
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
            return head.isTerminated();
          }
        }, timeout, timeUnit)) {
          return wrapException(head.getException());
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
            return head.isTerminated();
          }
        }, timeout, timeUnit)) {
          return wrapException(head.getException());
        }

      } catch (final InterruptedException e) {
        throw new InterruptedExecutionException(e);
      }
    }

    return other;
  }

  @SuppressWarnings("unchecked")
  public O getOr(final O other, final long timeout, @NotNull final TimeUnit timeUnit) {
    final ChainHead<?> head = mHead;
    synchronized (mMutex) {
      try {
        if (TimeUtils.waitUntil(mMutex, new Condition() {

          public boolean isTrue() {
            checkBound();
            return head.isTerminated();
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
  public <R> Promise<R> then(@Nullable final Handler<O, R, Callback<R>> outputHandler,
      @Nullable final Handler<Throwable, R, Callback<R>> errorHandler) {
    return then(new ProcessorHandle<O, R>(outputHandler, errorHandler));
  }

  @NotNull
  public <R> Promise<R> then(@NotNull final Mapper<O, R> mapper) {
    return then(new ProcessorMap<O, R>(mapper));
  }

  @NotNull
  public <R> Promise<R> then(@NotNull final Processor<O, R> processor) {
    return chain(new ChainProcessor<O, R>(mPropagationType, processor));
  }

  @NotNull
  public Promise<O> thenCatch(@NotNull final Mapper<Throwable, O> mapper) {
    return then(new ProcessorCatch<O>(mapper));
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

  @NotNull
  public Promise<O> whenFulfilled(@NotNull final Observer<O> observer) {
    return then(new ProcessorFulfilled<O>(observer));
  }

  @NotNull
  public Promise<O> whenRejected(@NotNull final Observer<Throwable> observer) {
    return then(new ProcessorRejected<O>(observer));
  }

  @NotNull
  public Promise<O> whenResolved(@NotNull final Action action) {
    return then(new ProcessorResolved<O>(action));
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

    @NotNull
    ChainHead<O> copy() {
      return new ChainHead<O>();
    }

    @Nullable
    Throwable getException() {
      return mException;
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

    boolean isTerminated() {
      return mInnerState.isTerminated();
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
        mInnerState = new StateResolved();
      }

      boolean isTerminated() {
        return false;
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

      @Override
      void innerReject(@Nullable final Throwable reason) {
        throw exception(PromiseState.Rejected);
      }

      @Override
      void innerResolve(final Object output) {
        throw exception(PromiseState.Rejected);
      }

      @Override
      boolean isTerminated() {
        return true;
      }
    }

    private class StateResolved extends StatePending {

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

      @Override
      boolean isTerminated() {
        return true;
      }

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
    }

    public void resolve(final PromiseChain<O, ?> next, final O input) {
      next.resolve(input);
    }

    public void reject(final PromiseChain<O, ?> next, final Throwable reason) {
      next.reject(reason);
    }
  }

  private static class ChainProcessor<O, R> extends PromiseChain<O, R> {

    private final Processor<O, R> mProcessor;

    private final PropagationType mPropagationType;

    private ChainProcessor(@NotNull final PropagationType propagationType,
        @NotNull final Processor<O, R> processor) {
      mProcessor = ConstantConditions.notNull("processor", processor);
      mPropagationType = propagationType;
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ChainProxy<O, R>(mPropagationType, mProcessor);
    }

    private static class ChainProxy<O, R> extends SerializableProxy {

      private ChainProxy(final PropagationType propagationType, final Processor<O, R> processor) {
        super(propagationType, processor);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ChainProcessor<O, R>((PropagationType) args[0], (Processor<O, R>) args[1]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    @NotNull
    PromiseChain<O, R> copy() {
      return new ChainProcessor<O, R>(mPropagationType, mProcessor);
    }

    void reject(final PromiseChain<R, ?> next, final Throwable reason) {
      mPropagationType.execute(new Runnable() {

        public void run() {
          try {
            getLogger().dbg("Processing rejection with reason: %s", reason);
            mProcessor.reject(reason, next);

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            getLogger().err(t, "Error while processing rejection with reason: %s", reason);
            next.reject(t);
          }
        }
      });
    }

    void resolve(final PromiseChain<R, ?> next, final O input) {
      mPropagationType.execute(new Runnable() {

        public void run() {
          try {
            getLogger().dbg("Processing resolution: %s", input);
            mProcessor.resolve(input, next);

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            getLogger().err(t, "Error while processing resolution: %s", input);
            next.reject(t);
          }
        }
      });
    }
  }

  private static class DefaultProcessor<I, O> implements Processor<I, O> {

    public void reject(final Throwable reason, @NotNull final Callback<O> callback) throws
        Exception {
      callback.reject(reason);
    }

    @SuppressWarnings("unchecked")
    public void resolve(final I input, @NotNull final Callback<O> callback) throws Exception {
      callback.resolve((O) input);
    }
  }

  private static class PassThroughErrorHandler<R>
      implements Handler<Throwable, R, Callback<R>>, Serializable {

    public void accept(final Throwable input, @NotNull final Callback<R> callback) {
      callback.reject(input);
    }
  }

  private static class PassThroughOutputHandler<O, R>
      implements Handler<O, R, Callback<R>>, Serializable {

    @SuppressWarnings("unchecked")
    public void accept(final O input, @NotNull final Callback<R> callback) {
      callback.resolve((R) input);
    }
  }

  private static class ProcessorCatch<O> extends DefaultProcessor<O, O> implements Serializable {

    private final Mapper<Throwable, O> mMapper;

    private ProcessorCatch(@NotNull final Mapper<Throwable, O> mapper) {
      mMapper = ConstantConditions.notNull("mapper", mapper);
    }

    public void reject(final Throwable reason, @NotNull final Callback<O> callback) throws
        Exception {
      callback.resolve(mMapper.apply(reason));
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ChainProxy<O>(mMapper);
    }

    private static class ChainProxy<O> extends SerializableProxy {

      private ChainProxy(final Mapper<Throwable, O> mapper) {
        super(mapper);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ProcessorCatch<O>((Mapper<Throwable, O>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }
  }

  private static class ProcessorFulfilled<O> extends DefaultProcessor<O, O>
      implements Serializable {

    private final Observer<O> mObserver;

    private ProcessorFulfilled(@NotNull final Observer<O> observer) {
      mObserver = ConstantConditions.notNull("observer", observer);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ChainProxy<O>(mObserver);
    }

    private static class ChainProxy<O> extends SerializableProxy {

      private ChainProxy(final Observer<O> observer) {
        super(observer);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ProcessorFulfilled<O>((Observer<O>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    @Override
    public void resolve(final O input, @NotNull final Callback<O> callback) throws Exception {
      mObserver.accept(input);
      super.resolve(input, callback);
    }
  }

  private static class ProcessorHandle<I, O> implements Processor<I, O>, Serializable {

    private final Handler<Throwable, O, Callback<O>> mErrorHandler;

    private final Handler<I, O, Callback<O>> mOutputHandler;

    private ProcessorHandle(@Nullable final Handler<I, O, Callback<O>> outputHandler,
        @Nullable final Handler<Throwable, O, Callback<O>> errorHandler) {
      mOutputHandler =
          (outputHandler != null) ? outputHandler : new PassThroughOutputHandler<I, O>();
      mErrorHandler = (errorHandler != null) ? errorHandler : new PassThroughErrorHandler<O>();
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ChainProxy<I, O>(mOutputHandler, mErrorHandler);
    }

    private static class ChainProxy<I, O> extends SerializableProxy {

      private ChainProxy(final Handler<I, O, Callback<O>> outputHandler,
          final Handler<Throwable, O, Callback<O>> errorHandler) {
        super(outputHandler, errorHandler);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ProcessorHandle<I, O>((Handler<I, O, Callback<O>>) args[0],
              (Handler<Throwable, O, Callback<O>>) args[1]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void reject(final Throwable reason, @NotNull final Callback<O> callback) throws
        Exception {
      mErrorHandler.accept(reason, callback);
    }

    public void resolve(final I input, @NotNull final Callback<O> callback) throws Exception {
      mOutputHandler.accept(input, callback);
    }
  }

  private static class ProcessorMap<I, O> extends DefaultProcessor<I, O> implements Serializable {

    private final Mapper<I, O> mMapper;

    private ProcessorMap(final Mapper<I, O> mapper) {
      mMapper = ConstantConditions.notNull("mapper", mapper);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ChainProxy<I, O>(mMapper);
    }

    private static class ChainProxy<I, O> extends SerializableProxy {

      private ChainProxy(final Mapper<I, O> mapper) {
        super(mapper);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ProcessorMap<I, O>((Mapper<I, O>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void resolve(final I input, @NotNull final Callback<O> callback) throws Exception {
      callback.resolve(mMapper.apply(input));
    }
  }

  private static class ProcessorRejected<O> extends DefaultProcessor<O, O> implements Serializable {

    private final Observer<Throwable> mObserver;

    private ProcessorRejected(@NotNull final Observer<Throwable> observer) {
      mObserver = ConstantConditions.notNull("observer", observer);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ChainProxy<O>(mObserver);
    }

    private static class ChainProxy<O> extends SerializableProxy {

      private ChainProxy(final Observer<Throwable> observer) {
        super(observer);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ProcessorRejected<O>((Observer<Throwable>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    @Override
    public void reject(final Throwable reason, @NotNull final Callback<O> callback) throws
        Exception {
      mObserver.accept(reason);
      super.reject(reason, callback);
    }
  }

  private static class ProcessorResolved<O> implements Processor<O, O>, Serializable {

    private final Action mAction;

    private ProcessorResolved(@NotNull final Action action) {
      mAction = ConstantConditions.notNull("action", action);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ChainProxy<O>(mAction);
    }

    private static class ChainProxy<O> extends SerializableProxy {

      private ChainProxy(final Action action) {
        super(action);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ProcessorResolved<O>((Action) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void reject(final Throwable reason, @NotNull final Callback<O> callback) throws
        Exception {
      mAction.perform();
      callback.reject(reason);
    }

    public void resolve(final O input, @NotNull final Callback<O> callback) throws Exception {
      mAction.perform();
      callback.resolve(input);
    }
  }

  private static abstract class PromiseChain<I, O>
      implements Processor<I, Void>, Callback<I>, Serializable {

    private transient volatile Throwable mException;

    private transient Logger mLogger;

    private transient volatile PromiseChain<O, ?> mNext;

    public final void defer(@NotNull final Promise<I> promise) {
      final Throwable exception = mException;
      if (exception != null) {
        mLogger.wrn("Chain has been already rejected with reason: %s", exception);
        throw wrapException(exception);
      }

      mException = new IllegalStateException("chain has been already resolved");
      promise.then(this);
    }

    public final void reject(final Throwable reason) {
      mException = reason;
      reject(mNext, reason);
    }

    public final void resolve(final I input) {
      final Throwable exception = mException;
      if (exception != null) {
        mLogger.wrn("Chain has been already rejected with reason: %s", exception);
        throw wrapException(exception);
      }

      mException = new IllegalStateException("chain has been already resolved");
      resolve(mNext, input);
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

    abstract void resolve(PromiseChain<O, ?> next, I input);

    void setNext(@NotNull PromiseChain<O, ?> next) {
      mNext = next;
    }

    public void reject(final Throwable reason, @NotNull final Callback<Void> callback) {
      reject(mNext, reason);
    }

    public void resolve(final I input, @NotNull final Callback<Void> callback) {
      resolve(mNext, input);
    }
  }

  private static class PromiseProxy extends SerializableProxy {

    private PromiseProxy(final Observer<? extends Callback<?>> observer,
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

        return new DefaultPromise<Object>((Observer<Callback<Object>>) args[0],
            (PropagationType) args[1], (Log) args[2], (Level) args[3], head,
            (PromiseChain<?, Object>) tail);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }

  private class ChainTail extends PromiseChain<O, Object> {

    @NotNull
    PromiseChain<O, Object> copy() {
      return ConstantConditions.unsupported();
    }

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

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
import java.util.Iterator;
import java.util.List;
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
import dm.james.util.ConstantConditions;
import dm.james.util.SerializableProxy;

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
    return null;
  }

  public Iterable<O> get(final long timeout, @NotNull final TimeUnit timeUnit) {
    return null;
  }

  @Nullable
  public RejectionException getError() {
    return null;
  }

  @Nullable
  public RejectionException getError(final long timeout, @NotNull final TimeUnit timeUnit) {
    return null;
  }

  public RejectionException getErrorOr(final RejectionException other, final long timeout,
      @NotNull final TimeUnit timeUnit) {
    return null;
  }

  public Iterable<O> getOr(final Iterable<O> other, final long timeout,
      @NotNull final TimeUnit timeUnit) {
    return null;
  }

  public boolean isBound() {
    return false;
  }

  public boolean isFulfilled() {
    return false;
  }

  public boolean isPending() {
    return false;
  }

  public boolean isRejected() {
    return false;
  }

  public boolean isResolved() {
    return false;
  }

  @NotNull
  public <R> Promise<R> then(@Nullable final Handler<Iterable<O>, R, Callback<R>> outputHandler,
      @Nullable final Handler<Throwable, R, Callback<R>> errorHandler) {
    // TODO: 27/07/2017 implement n:1 => deferred?
    return null;
  }

  @NotNull
  public <R> Promise<R> then(@NotNull final Mapper<Iterable<O>, R> mapper) {
    // TODO: 27/07/2017 n:1
    return null;
  }

  @NotNull
  public <R> Promise<R> then(@NotNull final Processor<Iterable<O>, R> processor) {
    // TODO: 27/07/2017 n:1
    return null;
  }

  public void waitResolved() {

  }

  public boolean waitResolved(final long timeout, @NotNull final TimeUnit timeUnit) {
    return false;
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
  public <R> PromiseIterable<R> applyEach(@NotNull final Mapper<Promise<O>, Promise<R>> mapper) {
    final Logger logger = mLogger;
    return thenEach(new ProcessorApplyEach<O, R>(mapper, mPropagationType, logger.getLog(),
        logger.getLogLevel()));
  }

  @NotNull
  public List<O> getAll() {
    return null;
  }

  @NotNull
  public List<O> getAll(final long timeout, @NotNull final TimeUnit timeUnit) {
    return null;
  }

  @NotNull
  public Iterator<O> iterator(final long timeout, @NotNull final TimeUnit timeUnit) {
    return null;
  }

  public O remove() {
    return null;
  }

  public O remove(final long timeout, @NotNull final TimeUnit timeUnit) {
    return null;
  }

  @NotNull
  public List<O> remove(final int maxSize) {
    return null;
  }

  @NotNull
  public List<O> remove(final int maxSize, final long timeout, @NotNull final TimeUnit timeUnit) {
    return null;
  }

  @NotNull
  public List<O> removeAll() {
    return null;
  }

  @NotNull
  public List<O> removeAll(final long timeout, @NotNull final TimeUnit timeUnit) {
    return null;
  }

  public O removeOr(final O other, final long timeout, @NotNull final TimeUnit timeUnit) {
    return null;
  }

  @NotNull
  public <R, S> PromiseIterable<R> then(@NotNull final StatefulProcessor<O, R, S> processor) {
    return chain(new ChainStateful<O, R, S>(mPropagationType, processor));
  }

  @NotNull
  public <R> PromiseIterable<R> thenAll(
      @Nullable final Handler<Iterable<O>, R, CallbackIterable<R>> outputHandler,
      @Nullable final Handler<Throwable, R, CallbackIterable<R>> errorHandler) {
    return thenAll(new ProcessorHandle<Iterable<O>, R>(outputHandler, errorHandler));
  }

  @NotNull
  public <R> PromiseIterable<R> thenAll(@NotNull final Mapper<Iterable<O>, Iterable<R>> mapper) {
    // TODO: 25/07/2017 n:n => thenAll(StatelessProcessor)
    return null;
  }

  @NotNull
  public <R> PromiseIterable<R> thenAll(
      @NotNull final StatelessProcessor<Iterable<O>, R> processor) {
    // TODO: 25/07/2017 n:n => then(StatefulProcessor)
    return null;
  }

  @NotNull
  public PromiseIterable<O> thenCatch(@NotNull final Mapper<Throwable, Iterable<O>> mapper) {
    // TODO: 25/07/2017 n:n => thenAll(StatelessProcessor)
    return null;
  }

  @NotNull
  public PromiseIterable<O> whenFulfilled(@NotNull final Observer<Iterable<O>> observer) {
    // TODO: 25/07/2017 n:n => thenAll(StatelessProcessor)
    return null;
  }

  @NotNull
  public PromiseIterable<O> whenRejected(@NotNull final Observer<Throwable> observer) {
    // TODO: 25/07/2017 n:n => thenAll(StatelessProcessor)
    return null;
  }

  @NotNull
  public PromiseIterable<O> whenResolved(@NotNull final Action action) {
    // TODO: 25/07/2017 n:n => thenAll(StatelessProcessor)
    return null;
  }

  @NotNull
  public PromiseIterable<O> thenCatchEach(@NotNull final Mapper<Throwable, O> mapper) {
    return thenEach(new ProcessorCatchEach<O>(mapper));
  }

  @NotNull
  public <R> PromiseIterable<R> thenEach(
      @Nullable final Handler<O, R, CallbackIterable<R>> outputHandler,
      @Nullable final Handler<Throwable, R, CallbackIterable<R>> errorHandler) {
    return thenEach(new ProcessorHandle<O, R>(outputHandler, errorHandler));
  }

  @NotNull
  public <R> PromiseIterable<R> thenEach(@NotNull final Mapper<O, R> mapper) {
    return thenEach(new ProcessorMapEach<O, R>(mapper));
  }

  @NotNull
  public <R> PromiseIterable<R> thenEach(@NotNull final StatelessProcessor<O, R> processor) {
    return chain(new ChainStateless<O, R>(mPropagationType, processor));
  }

  @NotNull
  public PromiseIterable<O> whenFulfilledEach(@NotNull final Observer<O> observer) {
    return thenEach(new ProcessorFulfilledEach<O>(observer));
  }

  @NotNull
  public PromiseIterable<O> whenRejectedEach(@NotNull final Observer<Throwable> observer) {
    return thenEach(new ProcessorRejectedEach<O>(observer));
  }

  public Iterator<O> iterator() {
    return null;
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

    void consume(PromiseChain<O, ?> chain);
  }

  private static class ChainHead<O> extends PromiseChain<O, O> {

    private final Object mMutex = new Object();

    private StatePending mInnerState = new StatePending();

    private ArrayList<Resolution<O>> mOutputs = new ArrayList<Resolution<O>>();

    private ResolutionFulfilled<O> mResolution = new ResolutionFulfilled<O>();

    private PromiseState mState = PromiseState.Pending;

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
    PromiseState getState() {
      return mState;
    }

    void innerAdd(final O output) {
      mInnerState.innerAdd(output);
    }

    void innerReject(@Nullable final Throwable reason) {
      mInnerState.innerAddRejection(reason);
      mState = PromiseState.Rejected;
    }

    void innerResolve() {
      mInnerState.innerResolve();
      mState = PromiseState.Fulfilled;
    }

    boolean isTerminated() {
      return mInnerState.isTerminated();
    }

    private class StatePending {

      @NotNull
      IllegalStateException exception(@NotNull final PromiseState state) {
        return new IllegalStateException("invalid state: " + state);
      }

      void innerAdd(final O output) {
        getLogger().dbg("Adding promise resolution [%s => %s]: %s", PromiseState.Pending,
            PromiseState.Pending, output);
        mResolution.add(output);
        mInnerState = new StateRejected();
      }

      void innerAddRejection(@Nullable final Throwable reason) {
        getLogger().dbg("Rejecting promise with reason [%s => %s]: %s", PromiseState.Pending,
            PromiseState.Rejected, reason);
        final ArrayList<Resolution<O>> outputs = mOutputs;
        outputs.add(mResolution);
        outputs.add(new ResolutionRejected<O>(reason));
        mResolution = new ResolutionFulfilled<O>();
        mInnerState = new StateRejected();
      }

      void innerResolve() {
        getLogger().dbg("Resolving promise with resolution [%s => %s]: %s", PromiseState.Pending,
            PromiseState.Fulfilled, mOutputs);
        mInnerState = new StateResolved();
      }

      boolean isTerminated() {
        return false;
      }
    }

    private class StateRejected extends StatePending {

      @Override
      void innerAdd(final O output) {
        throw exception(PromiseState.Rejected);
      }

      @Override
      void innerAddRejection(@Nullable final Throwable reason) {
        throw exception(PromiseState.Rejected);
      }

      @Override
      void innerResolve() {
        throw exception(PromiseState.Rejected);
      }

      @Override
      boolean isTerminated() {
        return true;
      }
    }

    private class StateResolved extends StatePending {

      @Override
      void innerAdd(final O output) {
        throw exception(PromiseState.Fulfilled);
      }

      @Override
      void innerAddRejection(@Nullable final Throwable reason) {
        throw exception(PromiseState.Fulfilled);
      }

      @Override
      void innerResolve() {
        throw exception(PromiseState.Fulfilled);
      }

      @Override
      boolean isTerminated() {
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

            mState = processor.add(mState, input, next);

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
              mState = processor.add(mState, input, next);
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

      public Void add(final Void state, final R input,
          @NotNull final CallbackIterable<Void> callback) {
        mNext.add(input);
        return null;
      }

      public Void create(@NotNull final CallbackIterable<Void> callback) {
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
      return new ChainProxy<O, R>(mMapper, mPropagationType, mLog, mLogLevel);
    }

    private static class ChainProxy<O, R> extends SerializableProxy {

      private ChainProxy(final Mapper<Promise<O>, Promise<R>> mapper,
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
          return new ProcessorCatchEach<O>((Mapper<Throwable, O>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }
  }

  private static class ProcessorFulfilledEach<O> extends DefaultStatelessProcessor<O, O>
      implements Serializable {

    private final Observer<O> mObserver;

    private ProcessorFulfilledEach(@NotNull final Observer<O> observer) {
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
      return new ChainProxy<O, R>(mOutputHandler, mErrorHandler);
    }

    private static class ChainProxy<I, O> extends SerializableProxy {

      private ChainProxy(final Handler<I, O, CallbackIterable<O>> outputHandler,
          final Handler<Throwable, O, CallbackIterable<O>> errorHandler) {
        super(outputHandler, errorHandler);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ProcessorHandle<I, O>((Handler<I, O, CallbackIterable<O>>) args[0],
              (Handler<Throwable, O, CallbackIterable<O>>) args[1]);

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

  private static class ProcessorMapEach<O, R> extends DefaultStatelessProcessor<O, R>
      implements Serializable {

    private final Mapper<O, R> mMapper;

    private ProcessorMapEach(final Mapper<O, R> mapper) {
      mMapper = ConstantConditions.notNull("mapper", mapper);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ChainProxy<O, R>(mMapper);
    }

    private static class ChainProxy<I, O> extends SerializableProxy {

      private ChainProxy(final Mapper<I, O> mapper) {
        super(mapper);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ProcessorMapEach<I, O>((Mapper<I, O>) args[0]);

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

  private static class ProcessorRejectedEach<O> extends DefaultStatelessProcessor<O, O>
      implements Serializable {

    private final Observer<Throwable> mObserver;

    private ProcessorRejectedEach(@NotNull final Observer<Throwable> observer) {
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

          public Void add(final Void state, final I input,
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

    public void consume(final PromiseChain<O, ?> chain) {
      chain.addAll(mOutputs);
    }

    void add(final O input) {
      mOutputs.add(input);
    }
  }

  private static class ResolutionRejected<O> implements Resolution<O> {

    private final Throwable mReason;

    private ResolutionRejected(final Throwable reason) {
      mReason = reason;
    }

    public void consume(final PromiseChain<O, ?> chain) {
      chain.addRejection(mReason);
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
              mHead.innerAdd(input);
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
              @SuppressWarnings("UnnecessaryLocalVariable") final ChainHead<O> head = mHead;
              for (final O input : inputs) {
                head.innerAdd(input);
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
}

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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

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

/**
 * Created by davide-maestroni on 07/23/2017.
 */
class DefaultPromiseIterable<O> implements PromiseIterable<O> {

  private final ScheduledExecutor mExecutor;

  private final ChainHead<O> mHead;

  private final Logger mLogger;

  // TODO: 26/07/2017 rejection propagation

  private final ArrayList<Mapper<? extends Promise<?>, ? extends Promise<?>>> mMappers =
      new ArrayList<Mapper<? extends Promise<?>, ? extends Promise<?>>>();

  private final Object mMutex;

  private final Observer<CallbackIterable<O>> mObserver;

  private final PropagationType mPropagationType;

  private final ChainHead<O> mTail;

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
    head.setNext(new ChainTail(mExecutor));
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

  @NotNull
  private static RejectionException wrapException(@Nullable final Throwable t) {
    return (t instanceof RejectionException) ? (RejectionException) t : new RejectionException(t);
  }

  @NotNull
  public <R> Promise<R> apply(@NotNull final Mapper<Promise<Iterable<O>>, Promise<R>> mapper) {
    return null;
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
    return null;
  }

  @NotNull
  public <R> Promise<R> then(@NotNull final Mapper<Iterable<O>, R> mapper) {
    return null;
  }

  @NotNull
  public <R> Promise<R> then(@NotNull final Processor<Iterable<O>, R> processor) {
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
    return null;
  }

  @NotNull
  public <R> PromiseIterable<R> applyEach(@NotNull final Mapper<Promise<O>, Promise<R>> mapper) {
    return null;
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
    // TODO: 25/07/2017 implement
    new Mapper<Promise<?>, PromiseIterable<R>>() {

      public PromiseIterable<R> apply(final Promise<?> input) throws Exception {
        return null;
      }
    };
    return null;
  }

  @NotNull
  public <R> PromiseIterable<R> thenAll(
      @Nullable final Handler<Iterable<O>, R, CallbackIterable<R>> outputHandler,
      @Nullable final Handler<Throwable, R, CallbackIterable<R>> errorHandler) {
    return null;
  }

  @NotNull
  public <R> PromiseIterable<R> thenAll(@NotNull final Mapper<Iterable<O>, Iterable<R>> mapper) {
    return null;
  }

  @NotNull
  public <R> PromiseIterable<R> thenAll(
      @NotNull final StatelessProcessor<Iterable<O>, R> processor) {
    return null;
  }

  @NotNull
  public PromiseIterable<O> thenCatch(@NotNull final Mapper<Throwable, Iterable<O>> mapper) {
    return null;
  }

  @NotNull
  public PromiseIterable<O> whenFulfilled(@NotNull final Observer<Iterable<O>> observer) {
    return null;
  }

  @NotNull
  public PromiseIterable<O> whenRejected(@NotNull final Observer<Throwable> observer) {
    return null;
  }

  @NotNull
  public PromiseIterable<O> whenResolved(@NotNull final Action action) {
    return null;
  }

  @NotNull
  public PromiseIterable<O> thenCatchEach(@NotNull final Mapper<Throwable, O> mapper) {
    return null;
  }

  @NotNull
  public <R> PromiseIterable<R> thenEach(
      @Nullable final Handler<O, R, CallbackIterable<R>> outputHandler,
      @Nullable final Handler<Throwable, R, CallbackIterable<R>> errorHandler) {
    return null;
  }

  @NotNull
  public <R> PromiseIterable<R> thenEach(@NotNull final Mapper<O, R> mapper) {
    return null;
  }

  @NotNull
  public <R> PromiseIterable<R> thenEach(@NotNull final StatelessProcessor<O, R> processor) {
    // TODO: 25/07/2017 implement
    return null;
  }

  @NotNull
  public PromiseIterable<O> whenFulfilledEach(@NotNull final Observer<O> observer) {
    return null;
  }

  @NotNull
  public PromiseIterable<O> whenRejectedEach(@NotNull final Observer<Throwable> observer) {
    return null;
  }

  public Iterator<O> iterator() {
    return null;
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

    private ArrayList<O> mOutputs = new ArrayList<O>();

    private PromiseState mState = PromiseState.Pending;

    void add(final PromiseChain<O, ?> next, final O input) {
      next.add(input);
    }

    @Nullable
    Throwable getException() {
      return mException;
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
      mInnerState.innerReject(reason);
      mState = PromiseState.Rejected;
    }

    void innerResolve() {
      mInnerState.innerResolve();
      mState = PromiseState.Fulfilled;
    }

    boolean isTerminated() {
      return mInnerState.isTerminated();
    }

    ArrayList<O> resetOutputs() {
      final ArrayList<O> outputs = mOutputs;
      mOutputs = new ArrayList<O>();
      return outputs;
    }

    private class StatePending {

      @NotNull
      IllegalStateException exception(@NotNull final PromiseState state) {
        return new IllegalStateException("invalid state: " + state);
      }

      void innerAdd(final O output) {
        getLogger().dbg("Adding promise resolution [%s => %s]: %s", PromiseState.Pending,
            PromiseState.Pending, output);
        mOutputs.add(output);
        mInnerState = new StateRejected();
      }

      void innerReject(@Nullable final Throwable reason) {
        getLogger().dbg("Rejecting promise with reason [%s => %s]: %s", PromiseState.Pending,
            PromiseState.Rejected, reason);
        mException = reason;
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
      void innerReject(@Nullable final Throwable reason) {
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
      void innerReject(@Nullable final Throwable reason) {
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

    void addAll(final PromiseChain<O, ?> next, final Iterable<O> inputs) {
      next.addAll(inputs);
    }

    @NotNull
    ChainHead<O> copy() {
      return new ChainHead<O>();
    }

    void reject(final PromiseChain<O, ?> next, final Throwable reason) {
      next.reject(reason);
    }

    void resolve(final PromiseChain<O, ?> next) {
      next.resolve();
    }
  }

  private static class ChainProcessor<I, O, S> extends PromiseChain<I, O> {

    private final ScheduledExecutor mExecutor;

    private final StatefulProcessor<I, O, S> mProcessor;

    private final PropagationType mPropagationType;

    private boolean mIsCreated;

    private S mState;

    private ChainProcessor(@NotNull final PropagationType propagationType,
        @NotNull final StatefulProcessor<I, O, S> processor) {
      mProcessor = ConstantConditions.notNull("processor", processor);
      mPropagationType = propagationType;
      mExecutor = ScheduledExecutors.throttlingExecutor(propagationType.executor(), 1);
    }

    void add(final PromiseChain<O, ?> next, final I input) {
      mExecutor.execute(new Runnable() {

        public void run() {
          try {
            final StatefulProcessor<I, O, S> processor = mProcessor;
            if (!mIsCreated) {
              mIsCreated = true;
              mState = processor.create(next);
            }

            mState = processor.add(mState, input, next);

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            getLogger().err(t, "Error while processing input: %s", input);
            next.reject(t);
          }
        }
      });
    }

    void addAll(final PromiseChain<O, ?> next, final Iterable<I> inputs) {
      mExecutor.execute(new Runnable() {

        public void run() {
          try {
            final StatefulProcessor<I, O, S> processor = mProcessor;
            if (!mIsCreated) {
              mIsCreated = true;
              mState = processor.create(next);
            }

            for (final I input : inputs) {
              mState = processor.add(mState, input, next);
            }

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            getLogger().err(t, "Error while processing input: %s", inputs);
            next.reject(t);
          }
        }
      });
    }

    @NotNull
    PromiseChain<I, O> copy() {
      return new ChainProcessor<I, O, S>(mPropagationType, mProcessor);
    }

    void reject(final PromiseChain<O, ?> next, final Throwable reason) {
      mExecutor.execute(new Runnable() {

        public void run() {
          try {
            final StatefulProcessor<I, O, S> processor = mProcessor;
            if (!mIsCreated) {
              mIsCreated = true;
              mState = processor.create(next);
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

    void resolve(final PromiseChain<O, ?> next) {
      mExecutor.execute(new Runnable() {

        public void run() {
          try {
            final StatefulProcessor<I, O, S> processor = mProcessor;
            if (!mIsCreated) {
              mIsCreated = true;
              mState = processor.create(next);
            }

            processor.resolve(mState, next);

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            getLogger().err(t, "Error while processing resolution");
            next.reject(t);
          }
        }
      });
    }
  }

  private static abstract class PromiseChain<I, O> implements CallbackIterable<I>, Serializable {

    private transient final Object mMutex = new Object();

    private transient long mDeferredCount;

    private transient volatile Throwable mException;

    private transient Logger mLogger;

    private transient volatile PromiseChain<O, ?> mNext;

    private transient PromiseState mState = PromiseState.Pending;

    public final void add(final I output) {
      synchronized (mMutex) {
        if (mState != PromiseState.Pending) {
          throw wrapException(mException);
        }
      }

      add(mNext, output);
    }

    public final void addAll(@Nullable final Iterable<I> outputs) {
      synchronized (mMutex) {
        if (mState != PromiseState.Pending) {
          throw wrapException(mException);
        }
      }

      if (outputs != null) {
        addAll(mNext, outputs);
      }
    }

    public final void addAllDeferred(@NotNull final Promise<? extends Iterable<I>> promise) {
      synchronized (mMutex) {
        if (mState != PromiseState.Pending) {
          throw wrapException(mException);
        }

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
        if (mState != PromiseState.Pending) {
          throw wrapException(mException);
        }

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
        if (mState != PromiseState.Pending) {
          throw wrapException(mException);
        }

        mState = PromiseState.Fulfilled;
        mException = new IllegalStateException("chain has been already resolved");
      }

      innerResolve();
    }

    public final void defer(@NotNull final Promise<I> promise) {
      synchronized (mMutex) {
        if (mState != PromiseState.Pending) {
          throw wrapException(mException);
        }

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
        if (mState == PromiseState.Rejected) {
          throw wrapException(mException);
        }

        mState = PromiseState.Rejected;
        mException = reason;
      }

      reject(mNext, reason);
    }

    public final void resolve(final I input) {
      synchronized (mMutex) {
        if (mState != PromiseState.Pending) {
          throw wrapException(mException);
        }

        mState = PromiseState.Fulfilled;
        mException = new IllegalStateException("chain has been already resolved");
      }

      add(mNext, input);
      innerResolve();
    }

    abstract void add(PromiseChain<O, ?> next, I input);

    abstract void addAll(PromiseChain<O, ?> next, Iterable<I> inputs);

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
  }

  private class ChainTail extends PromiseChain<O, Object> {

    private final ScheduledExecutor mExecutor;

    private PromiseChain<O, ?> mBond;

    private ChainTail(@NotNull final ScheduledExecutor executor) {
      mExecutor = executor;
    }

    void bind(@NotNull final PromiseChain<?, O> tail, @NotNull final PromiseChain<O, ?> bond) {
      mExecutor.execute(new Runnable() {

        public void run() {
          mBond = bond;
          final ArrayList<O> outputs;
          final Throwable exception;
          synchronized (mMutex) {
            final ChainHead<O> head = mHead;
            outputs = head.resetOutputs();
            exception = head.getException();
          }

          bond.addAll(outputs);
          final PromiseState state = mState;
          if (state == PromiseState.Fulfilled) {
            bond.resolve();

          } else if (state == PromiseState.Rejected) {
            bond.reject(exception);
          }

          tail.setNext(ChainTail.this);
        }
      });
    }

    void add(final PromiseChain<Object, ?> next, final O input) {
      mExecutor.execute(new Runnable() {

        public void run() {
          final PromiseChain<O, ?> bond = mBond;
          if (bond == null) {
            synchronized (mMutex) {
              mHead.add(input);
            }

          } else {
            bond.add(input);
          }
        }
      });
    }

    void addAll(final PromiseChain<Object, ?> next, final Iterable<O> inputs) {
      mExecutor.execute(new Runnable() {

        public void run() {
          final PromiseChain<O, ?> bond = mBond;
          if (bond == null) {
            synchronized (mMutex) {
              final ChainHead<O> head = mHead;
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

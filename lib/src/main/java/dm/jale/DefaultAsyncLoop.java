/*
 * Copyright 2018 Davide Maestroni
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

package dm.jale;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import dm.jale.async.Action;
import dm.jale.async.AsyncEvaluation;
import dm.jale.async.AsyncEvaluations;
import dm.jale.async.AsyncLoop;
import dm.jale.async.AsyncState;
import dm.jale.async.AsyncStatement;
import dm.jale.async.Completer;
import dm.jale.async.FailureException;
import dm.jale.async.Mapper;
import dm.jale.async.Observer;
import dm.jale.async.Provider;
import dm.jale.async.RuntimeInterruptedException;
import dm.jale.async.RuntimeTimeoutException;
import dm.jale.async.Settler;
import dm.jale.async.SimpleState;
import dm.jale.async.Updater;
import dm.jale.config.BuildConfig;
import dm.jale.executor.ExecutorPool;
import dm.jale.log.Logger;
import dm.jale.util.ConstantConditions;
import dm.jale.util.DoubleQueue;
import dm.jale.util.Iterables;
import dm.jale.util.SerializableProxy;
import dm.jale.util.TimeUnits;
import dm.jale.util.TimeUnits.Condition;

import static dm.jale.executor.ExecutorPool.immediateExecutor;
import static dm.jale.executor.ExecutorPool.withThrottling;

/**
 * Created by davide-maestroni on 02/01/2018.
 */
class DefaultAsyncLoop<V> implements AsyncLoop<V>, Serializable {

  private static final AsyncEvaluations<?> VOID_EVALUATIONS = new AsyncEvaluations<Object>() {

    @NotNull
    public AsyncEvaluations<Object> addFailure(@NotNull final Throwable failure) {
      return this;
    }

    @NotNull
    public AsyncEvaluations<Object> addFailures(
        @Nullable final Iterable<? extends Throwable> failures) {
      return this;
    }

    @NotNull
    public AsyncEvaluations<Object> addValue(final Object value) {
      return this;
    }

    @NotNull
    public AsyncEvaluations<Object> addValues(@Nullable final Iterable<? extends Object> values) {
      return this;
    }

    public void set() {
    }
  };

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final CopyOnWriteArrayList<AsyncLoop<?>> mForked =
      new CopyOnWriteArrayList<AsyncLoop<?>>();

  private final ChainHead<?> mHead;

  private final boolean mIsEvaluated;

  private final boolean mIsFork;

  private final Logger mLogger;

  private final Object mMutex;

  private final Observer<AsyncEvaluations<?>> mObserver;

  private final LoopChain<?, V> mTail;

  private LoopChain<V, ?> mChain;

  private StatementState mState = StatementState.Evaluating;

  @SuppressWarnings("unchecked")
  DefaultAsyncLoop(@NotNull final Observer<? super AsyncEvaluations<V>> observer,
      final boolean isEvaluated, @Nullable final String loggerName) {
    mObserver = (Observer<AsyncEvaluations<?>>) observer;
    mIsEvaluated = isEvaluated;
    mIsFork = false;
    mLogger = Logger.newLogger(this, loggerName, Locale.ENGLISH);
    final ChainHead<V> head = new ChainHead<V>();
    head.setLogger(mLogger);
    head.setNext(new ChainTail(head));
    mMutex = head.getMutex();
    mHead = head;
    mTail = head;
    try {
      observer.accept(head);

    } catch (final Throwable t) {
      head.failSafe(RuntimeInterruptedException.wrapIfInterrupt(t));
    }
  }

  @SuppressWarnings("unchecked")
  private DefaultAsyncLoop(@NotNull final Observer<? super AsyncEvaluations<V>> observer,
      final boolean isEvaluated, @NotNull final Logger logger) {
    // forking
    mObserver = (Observer<AsyncEvaluations<?>>) observer;
    mIsEvaluated = isEvaluated;
    mIsFork = (observer instanceof ForkObserver);
    mLogger = logger;
    final ChainHead<V> head = new ChainHead<V>();
    head.setLogger(mLogger);
    head.setNext(new ChainTail(head));
    mMutex = head.getMutex();
    mHead = head;
    mTail = head;
    try {
      observer.accept(head);

    } catch (final Throwable t) {
      head.failSafe(RuntimeInterruptedException.wrapIfInterrupt(t));
    }
  }

  @SuppressWarnings("unchecked")
  private DefaultAsyncLoop(@NotNull final Observer<AsyncEvaluations<?>> observer,
      final boolean isEvaluated, @Nullable final String loggerName,
      @NotNull final ChainHead<?> head, @NotNull final LoopChain<?, V> tail) {
    // serialization
    mObserver = observer;
    mIsEvaluated = isEvaluated;
    mIsFork = (observer instanceof ForkObserver);
    mLogger = Logger.newLogger(this, loggerName, Locale.ENGLISH);
    mMutex = head.getMutex();
    mHead = head;
    mTail = tail;
    tail.setNext(new ChainTail((ChainHead<V>) head));
    LoopChain<?, ?> chain = head;
    while (!chain.isTail()) {
      chain.setLogger(mLogger);
      chain = chain.mNext;
    }

    try {
      observer.accept(head);

    } catch (final Throwable t) {
      head.failSafe(RuntimeInterruptedException.wrapIfInterrupt(t));
    }
  }

  @SuppressWarnings("unchecked")
  private DefaultAsyncLoop(@NotNull final Observer<AsyncEvaluations<?>> observer,
      final boolean isEvaluated, @NotNull final Logger logger, @NotNull final ChainHead<?> head,
      @NotNull final LoopChain<?, V> tail, final boolean observe) {
    // copy/chain
    mObserver = observer;
    mIsEvaluated = isEvaluated;
    mIsFork = (observer instanceof ForkObserver);
    mLogger = logger;
    mMutex = head.getMutex();
    mHead = head;
    mTail = tail;
    tail.setNext(new ChainTail((ChainHead<V>) head));
    if (observe) {
      try {
        observer.accept(head);

      } catch (final Throwable t) {
        head.failSafe(RuntimeInterruptedException.wrapIfInterrupt(t));
      }
    }
  }

  @SuppressWarnings(
      {"unchecked", "ConstantConditions", "SynchronizationOnLocalVariableOrMethodParameter"})
  private static <V> void addTo(@NotNull final Object mutex,
      @NotNull final NestedQueue<SimpleState<V>> queue, @NotNull final LoopChain<V, ?> chain) {
    boolean isValue;
    SimpleState<V> state;
    ArrayList<?> inputs = null;
    synchronized (mutex) {
      if (queue.isEmpty()) {
        return;
      }

      state = queue.removeFirst();
      isValue = state.isSet();
      if (isValue) {
        inputs = new ArrayList<V>();
      }
    }

    while (state != null) {
      if (isValue) {
        if (state.isSet()) {
          ((ArrayList<V>) inputs).add(state.value());

        } else {
          chain.addValues((Iterable<V>) inputs);
          final ArrayList<Throwable> failures = new ArrayList<Throwable>();
          failures.add(state.failure());
          inputs = failures;
        }

      } else {
        if (state.isFailed()) {
          ((ArrayList<Throwable>) inputs).add(state.failure());

        } else {
          chain.addFailures((Iterable<Throwable>) inputs);
          final ArrayList<V> values = new ArrayList<V>();
          values.add(state.value());
          inputs = values;
        }
      }

      synchronized (mutex) {
        if (queue.isEmpty()) {
          return;
        }

        state = queue.removeFirst();
      }
    }
  }

  public boolean cancel(final boolean mayInterruptIfRunning) {
    final Observer<? extends AsyncEvaluations<?>> observer = mObserver;
    if (mIsFork) {
      if (((ForkObserver<?, ?>) observer).cancel(mayInterruptIfRunning)) {
        return true;
      }

      boolean isCancelled = false;
      for (final AsyncLoop<?> forked : mForked) {
        if (forked.cancel(mayInterruptIfRunning)) {
          isCancelled = true;
        }
      }

      return isCancelled;
    }

    LoopChain<?, ?> chain = mHead;
    final CancellationException exception = new CancellationException();
    if (mayInterruptIfRunning && (observer instanceof InterruptibleObserver)) {
      if (chain.cancel(exception)) {
        ((InterruptibleObserver<?>) observer).interrupt();
        return true;
      }

      chain = chain.mNext;
    }

    while (!chain.isTail()) {
      if (chain.cancel(exception)) {
        return true;
      }

      chain = chain.mNext;
    }

    return false;
  }

  public boolean isDone() {
    synchronized (mMutex) {
      return mHead.getState().isDone();
    }
  }

  public Iterable<V> get() throws InterruptedException, ExecutionException {
    try {
      return getValue();

    } catch (final RuntimeInterruptedException e) {
      throw e.toInterruptedException();

    } catch (final FailureException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof CancellationException) {
        throw (CancellationException) cause;
      }

      throw new ExecutionException(e);
    }
  }

  public Iterable<V> get(final long timeout, @NotNull final TimeUnit timeUnit) throws
      InterruptedException, ExecutionException, TimeoutException {
    try {
      return getValue(timeout, timeUnit);

    } catch (final RuntimeInterruptedException e) {
      throw e.toInterruptedException();

    } catch (final RuntimeTimeoutException e) {
      throw e.toTimeoutException();

    } catch (final FailureException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof CancellationException) {
        throw (CancellationException) cause;
      }

      throw new ExecutionException(e);
    }
  }

  @SuppressWarnings("unchecked")
  public void consume() {
    to((AsyncEvaluations<V>) VOID_EVALUATIONS);
  }

  public boolean getDone() {
    return getDone(-1, TimeUnit.MILLISECONDS);
  }

  public boolean getDone(final long timeout, @NotNull final TimeUnit timeUnit) {
    checkSupported();
    deadLockWarning(timeout);
    @SuppressWarnings("UnnecessaryLocalVariable") final ChainHead<?> head = mHead;
    synchronized (mMutex) {
      try {
        if (TimeUnits.waitUntil(mMutex, new Condition() {

          public boolean isTrue() {
            return head.getState().isDone();
          }
        }, timeout, timeUnit)) {
          return true;
        }

      } catch (final InterruptedException e) {
        throw new RuntimeInterruptedException(e);
      }
    }

    return false;
  }

  @Nullable
  public FailureException getFailure() {
    return getFailure(-1, TimeUnit.MILLISECONDS);
  }

  @Nullable
  public FailureException getFailure(final long timeout, @NotNull final TimeUnit timeUnit) {
    checkSupported();
    deadLockWarning(timeout);
    final ChainHead<?> head = mHead;
    synchronized (mMutex) {
      try {
        if (TimeUnits.waitUntil(mMutex, new Condition() {

          public boolean isTrue() {
            checkFinal();
            return head.getState().isDone();
          }
        }, timeout, timeUnit)) {
          return head.getFailure();
        }

      } catch (final InterruptedException e) {
        throw new RuntimeInterruptedException(e);
      }
    }

    throw new RuntimeTimeoutException(
        "timeout while waiting for statement failure [" + timeout + " " + timeUnit + "]");
  }

  public Iterable<V> getValue() {
    return getValue(-1, TimeUnit.MILLISECONDS);
  }

  @SuppressWarnings("unchecked")
  public Iterable<V> getValue(final long timeout, @NotNull final TimeUnit timeUnit) {
    checkSupported();
    deadLockWarning(timeout);
    final ChainHead<?> head = mHead;
    synchronized (mMutex) {
      try {
        if (TimeUnits.waitUntil(mMutex, new Condition() {

          public boolean isTrue() {
            checkFinal();
            return head.getState().isDone();
          }
        }, timeout, timeUnit)) {
          return (Iterable<V>) head.getValues();
        }

      } catch (final InterruptedException e) {
        throw new RuntimeInterruptedException(e);
      }
    }

    throw new RuntimeTimeoutException(
        "timeout while waiting for statement value [" + timeout + " " + timeUnit + "]");
  }

  public boolean isFinal() {
    synchronized (mMutex) {
      return (mChain == null);
    }
  }

  @NotNull
  public <R> AsyncStatement<R> then(@NotNull final Mapper<? super Iterable<V>, R> mapper) {
    return toStatement().then(mapper);
  }

  @NotNull
  public AsyncStatement<Iterable<V>> thenDo(@NotNull final Observer<? super Iterable<V>> observer) {
    return toStatement().thenDo(observer);
  }

  @NotNull
  public <R> AsyncStatement<R> thenIf(
      @NotNull final Mapper<? super Iterable<V>, ? extends AsyncStatement<R>> mapper) {
    return toStatement().thenIf(mapper);
  }

  @NotNull
  public <R> AsyncStatement<R> thenTry(
      @NotNull final Mapper<? super Iterable<V>, ? extends Closeable> closeable,
      @NotNull final Mapper<? super Iterable<V>, R> mapper) {
    return toStatement().thenTry(closeable, mapper);
  }

  @NotNull
  public AsyncStatement<Iterable<V>> thenTryDo(
      @NotNull final Mapper<? super Iterable<V>, ? extends Closeable> closeable,
      @NotNull final Observer<? super Iterable<V>> observer) {
    return toStatement().thenTryDo(closeable, observer);
  }

  @NotNull
  public <R> AsyncStatement<R> thenTryIf(
      @NotNull final Mapper<? super Iterable<V>, ? extends Closeable> closeable,
      @NotNull final Mapper<? super Iterable<V>, ? extends AsyncStatement<R>> mapper) {
    return toStatement().thenTryIf(closeable, mapper);
  }

  @NotNull
  public AsyncStatement<Iterable<V>> whenDone(@NotNull final Action action) {
    return toStatement().whenDone(action);
  }

  @NotNull
  public AsyncLoop<V> elseCatch(
      @NotNull final Mapper<? super Throwable, ? extends Iterable<V>> mapper,
      @Nullable final Class<?>[] exceptionTypes) {
    return yield(new ElseCatchYielder<V>(mapper, Asyncs.cloneExceptionTypes(exceptionTypes)));
  }

  @NotNull
  public AsyncLoop<V> elseDo(@NotNull final Observer<? super Throwable> observer,
      @Nullable final Class<?>[] exceptionTypes) {
    return yield(new ElseDoYielder<V>(observer, Asyncs.cloneExceptionTypes(exceptionTypes)));
  }

  @NotNull
  public AsyncLoop<V> elseIf(
      @NotNull final Mapper<? super Throwable, ? extends AsyncStatement<? extends Iterable<V>>>
          mapper,
      @Nullable final Class<?>[] exceptionTypes) {
    return yield(new ElseIfYielder<V>(mapper, Asyncs.cloneExceptionTypes(exceptionTypes)));
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public DefaultAsyncLoop<V> evaluate() {
    final Logger logger = mLogger;
    final ChainHead<?> head = mHead;
    final ChainHead<?> newHead = head.copy();
    newHead.setLogger(logger);
    LoopChain<?, ?> newTail = newHead;
    LoopChain<?, ?> next = head;
    while (next != mTail) {
      next = next.mNext;
      LoopChain<?, ?> chain = next.copy();
      chain.setLogger(logger);
      ((LoopChain<?, Object>) newTail).setNext((LoopChain<Object, ?>) chain);
      newTail = chain;
    }

    return new DefaultAsyncLoop<V>(renewObserver(), true, logger, newHead,
        (LoopChain<?, V>) newTail, true);
  }

  @NotNull
  public AsyncLoop<V> evaluated() {
    return (mIsEvaluated) ? this : evaluate();
  }

  @NotNull
  public <S> AsyncLoop<V> fork(
      @NotNull final Forker<S, ? super Iterable<V>, ? super AsyncEvaluation<Iterable<V>>, ? super
          AsyncStatement<Iterable<V>>> forker) {
    return forkLoop(new StatementLoopForker<S, V>(forker));
  }

  @NotNull
  public <S> AsyncLoop<V> fork(@Nullable final Mapper<? super AsyncStatement<Iterable<V>>, S> init,
      @Nullable final Updater<S, ? super Iterable<V>, ? super AsyncStatement<Iterable<V>>> value,
      @Nullable final Updater<S, ? super Throwable, ? super AsyncStatement<Iterable<V>>> failure,
      @Nullable final Completer<S, ? super AsyncStatement<Iterable<V>>> done,
      @Nullable final Updater<S, ? super AsyncEvaluation<Iterable<V>>, ? super
          AsyncStatement<Iterable<V>>> evaluation) {
    return fork(
        new ComposedStatementForker<S, Iterable<V>>(init, value, failure, done, evaluation));
  }

  @NotNull
  public AsyncLoop<V> forkOn(@NotNull final Executor executor) {
    return forkLoop(new ExecutorLoopForker<V>(withThrottling(1, executor), mLogger.getName()));
  }

  @NotNull
  public AsyncLoop<V> elseForEach(@NotNull final Mapper<? super Throwable, V> mapper,
      @Nullable Class<?>... exceptionTypes) {
    return chain(new ElseCatchLoopHandler<V>(mapper, Asyncs.cloneExceptionTypes(exceptionTypes)));
  }

  @NotNull
  public AsyncLoop<V> elseForEachDo(@NotNull final Observer<? super Throwable> observer,
      @Nullable Class<?>... exceptionTypes) {
    return chain(new ElseDoLoopHandler<V>(observer, Asyncs.cloneExceptionTypes(exceptionTypes)));
  }

  @NotNull
  public AsyncLoop<V> elseForEachIf(
      @NotNull final Mapper<? super Throwable, ? extends AsyncStatement<? extends V>> mapper,
      @Nullable Class<?>... exceptionTypes) {
    return chain(new ElseIfStatementHandler<V>(mapper, Asyncs.cloneExceptionTypes(exceptionTypes)));
  }

  @NotNull
  public AsyncLoop<V> elseForEachLoop(
      @NotNull final Mapper<? super Throwable, ? extends Iterable<? extends V>> mapper,
      @Nullable Class<?>... exceptionTypes) {
    return chain(new ElseLoopLoopHandler<V>(mapper, Asyncs.cloneExceptionTypes(exceptionTypes)));
  }

  @NotNull
  public AsyncLoop<V> elseForEachLoopIf(
      @NotNull final Mapper<? super Throwable, ? extends AsyncLoop<? extends V>> mapper,
      @Nullable Class<?>... exceptionTypes) {
    return chain(
        new ElseLoopIfStatementHandler<V>(mapper, Asyncs.cloneExceptionTypes(exceptionTypes)));
  }

  @NotNull
  public AsyncLoop<V> elseForEachOrderedIf(
      @NotNull final Mapper<? super Throwable, ? extends AsyncStatement<? extends V>> mapper,
      @Nullable Class<?>... exceptionTypes) {
    return chainOrdered(
        new ElseIfStatementHandler<V>(mapper, Asyncs.cloneExceptionTypes(exceptionTypes)));
  }

  @NotNull
  public AsyncLoop<V> elseForEachOrderedLoopIf(
      @NotNull final Mapper<? super Throwable, ? extends AsyncLoop<? extends V>> mapper,
      @Nullable Class<?>... exceptionTypes) {
    return chainOrdered(
        new ElseLoopIfStatementHandler<V>(mapper, Asyncs.cloneExceptionTypes(exceptionTypes)));
  }

  @NotNull
  public <R> AsyncLoop<R> forEach(@NotNull final Mapper<? super V, R> mapper) {
    return chain(new ThenLoopHandler<V, R>(mapper));
  }

  @NotNull
  public AsyncLoop<V> forEachDo(@NotNull final Observer<? super V> observer) {
    return chain(new ThenDoLoopHandler<V, V>(observer));
  }

  @NotNull
  public AsyncLoop<V> forEachDone(@NotNull final Action action) {
    return chain(new DoneLoopHandler<V>(action));
  }

  @NotNull
  public <R> AsyncLoop<R> forEachIf(
      @NotNull final Mapper<? super V, ? extends AsyncStatement<R>> mapper) {
    return chain(new ThenIfStatementHandler<V, R>(mapper));
  }

  @NotNull
  public <R> AsyncLoop<R> forEachLoop(
      @NotNull final Mapper<? super V, ? extends Iterable<R>> mapper) {
    return chain(new ThenLoopLoopHandler<V, R>(mapper));
  }

  @NotNull
  public <R> AsyncLoop<R> forEachLoopIf(
      @NotNull final Mapper<? super V, ? extends AsyncLoop<R>> mapper) {
    return chain(new ThenLoopIfStatementHandler<V, R>(mapper));
  }

  @NotNull
  public <R> AsyncLoop<R> forEachOrderedIf(
      @NotNull final Mapper<? super V, ? extends AsyncStatement<R>> mapper) {
    return chainOrdered(new ThenIfStatementHandler<V, R>(mapper));
  }

  @NotNull
  public <R> AsyncLoop<R> forEachOrderedLoopIf(
      @NotNull final Mapper<? super V, ? extends AsyncLoop<R>> mapper) {
    return chainOrdered(new ThenLoopIfStatementHandler<V, R>(mapper));
  }

  @NotNull
  public <R> AsyncLoop<R> forEachOrderedTryIf(
      @NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final Mapper<? super V, ? extends AsyncStatement<R>> mapper) {
    return chainOrdered(new TryIfStatementHandler<V, R>(closeable, mapper, mLogger.getName()));
  }

  @NotNull
  public <R> AsyncLoop<R> forEachOrderedTryLoopIf(
      @NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final Mapper<? super V, ? extends AsyncLoop<R>> mapper) {
    return chainOrdered(
        new TryStatementLoopHandler<V, R>(closeable, new ThenLoopIfStatementHandler<V, R>(mapper),
            mLogger.getName()));
  }

  @NotNull
  public <R> AsyncLoop<R> forEachTry(
      @NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final Mapper<? super V, R> mapper) {
    return chain(new TryStatementHandler<V, R>(closeable, new ThenStatementHandler<V, R>(mapper),
        mLogger.getName()));
  }

  @NotNull
  public AsyncLoop<V> forEachTryDo(@NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final Observer<? super V> observer) {
    return chain(
        new TryStatementHandler<V, V>(closeable, new ThenDoStatementHandler<V, V>(observer),
            mLogger.getName()));
  }

  @NotNull
  public <R> AsyncLoop<R> forEachTryIf(
      @NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final Mapper<? super V, ? extends AsyncStatement<R>> mapper) {
    return chain(new TryIfStatementHandler<V, R>(closeable, mapper, mLogger.getName()));
  }

  @NotNull
  public <R> AsyncLoop<R> forEachTryLoop(
      @NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final Mapper<? super V, ? extends Iterable<R>> mapper) {
    return chain(
        new TryStatementLoopHandler<V, R>(closeable, new ThenLoopStatementHandler<V, R>(mapper),
            mLogger.getName()));
  }

  @NotNull
  public <R> AsyncLoop<R> forEachTryLoopIf(
      @NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final Mapper<? super V, ? extends AsyncLoop<R>> mapper) {
    return chain(
        new TryStatementLoopHandler<V, R>(closeable, new ThenLoopIfStatementHandler<V, R>(mapper),
            mLogger.getName()));
  }

  @NotNull
  public <S> AsyncLoop<V> forkLoop(
      @NotNull final Forker<S, ? super V, ? super AsyncEvaluations<V>, ? super AsyncLoop<V>>
          forker) {
    return new DefaultAsyncLoop<V>(new ForkObserver<S, V>(this, forker), mIsEvaluated, mLogger);
  }

  @NotNull
  public <S> AsyncLoop<V> forkLoop(@Nullable final Mapper<? super AsyncLoop<V>, S> init,
      @Nullable final Updater<S, ? super V, ? super AsyncLoop<V>> value,
      @Nullable final Updater<S, ? super Throwable, ? super AsyncLoop<V>> failure,
      @Nullable final Completer<S, ? super AsyncLoop<V>> done,
      @Nullable final Updater<S, ? super AsyncEvaluations<V>, ? super AsyncLoop<V>> evaluation) {
    return forkLoop(new ComposedLoopForker<S, V>(init, value, failure, done, evaluation));
  }

  @NotNull
  public AsyncLoop<V> forkOn(@NotNull final Executor executor, final int maxValues,
      final int maxFailures) {
    return forkLoop(
        new ExecutorLoopBatchForker<V>(withThrottling(1, executor), maxValues, maxFailures,
            mLogger.getName()));
  }

  @NotNull
  public AsyncLoop<V> forkOnParallel(@NotNull final Executor executor, final int maxValues,
      final int maxFailures) {
    return forkLoop(
        new ExecutorLoopBatchForker<V>(executor, maxValues, maxFailures, mLogger.getName()));
  }

  @NotNull
  public AsyncLoop<V> forkOnParallel(@NotNull final Executor executor) {
    return forkLoop(new ExecutorLoopForker<V>(executor, mLogger.getName()));
  }

  @NotNull
  public List<AsyncState<V>> getStates(final int maxCount) {
    return getStates(maxCount, -1, TimeUnit.MILLISECONDS);
  }

  @NotNull
  public List<AsyncState<V>> getStates(final int maxCount, final long timeout,
      @NotNull final TimeUnit timeUnit) {
    checkSupported();
    deadLockWarning(timeout);
    final ChainHead<?> head = mHead;
    final ArrayList<AsyncState<V>> outputs = new ArrayList<AsyncState<V>>();
    synchronized (mMutex) {
      try {
        if (TimeUnits.waitUntil(mMutex, new Condition() {

          public boolean isTrue() {
            checkFinal();
            return ((head.getStates().size() >= maxCount) || head.getState().isDone());
          }
        }, timeout, timeUnit)) {
          @SuppressWarnings("unchecked") final DoubleQueue<SimpleState<V>> states =
              ((ChainHead<V>) head).getStates();
          while (!states.isEmpty() && (outputs.size() < maxCount)) {
            outputs.add(states.removeFirst());
          }
        }

      } catch (final InterruptedException e) {
        throw new RuntimeInterruptedException(e);
      }
    }

    return outputs;
  }

  @NotNull
  public List<V> getValues(final int maxCount) {
    return getValues(maxCount, -1, TimeUnit.MILLISECONDS);
  }

  @NotNull
  public List<V> getValues(final int maxCount, final long timeout,
      @NotNull final TimeUnit timeUnit) {
    checkSupported();
    deadLockWarning(timeout);
    final ChainHead<?> head = mHead;
    final ArrayList<V> outputs = new ArrayList<V>();
    synchronized (mMutex) {
      try {
        if (TimeUnits.waitUntil(mMutex, new Condition() {

          public boolean isTrue() {
            checkFinal();
            return ((head.getStates().size() >= maxCount) || head.getState().isDone());
          }
        }, timeout, timeUnit)) {
          @SuppressWarnings("unchecked") final DoubleQueue<SimpleState<V>> states =
              ((ChainHead<V>) head).getStates();
          while (!states.isEmpty() && (outputs.size() < maxCount)) {
            final SimpleState<V> state = states.removeFirst();
            if (state.isFailed()) {
              throw FailureException.wrap(state.failure());
            }

            outputs.add(state.value());
          }
        }

      } catch (final InterruptedException e) {
        throw new RuntimeInterruptedException(e);
      }
    }

    return outputs;
  }

  @NotNull
  public AsyncGenerator<AsyncState<V>> stateGenerator() {
    return stateGenerator(-1, TimeUnit.MILLISECONDS);
  }

  @NotNull
  public AsyncGenerator<AsyncState<V>> stateGenerator(final long timeout,
      @NotNull final TimeUnit timeUnit) {
    checkSupported();
    return new StateGenerator<V>(this, timeout, timeUnit);
  }

  public void to(@NotNull final AsyncEvaluations<? super V> evaluations) {
    checkEvaluated();
    chain(new ToEvaluationLoopHandler<V>(evaluations));
  }

  @NotNull
  public AsyncGenerator<V> valueGenerator() {
    return valueGenerator(-1, TimeUnit.MILLISECONDS);
  }

  @NotNull
  public AsyncGenerator<V> valueGenerator(final long timeout, @NotNull final TimeUnit timeUnit) {
    checkSupported();
    return new ValueGenerator<V>(this, timeout, timeUnit);
  }

  @NotNull
  public <S, R> AsyncLoop<R> yield(@NotNull final Yielder<S, ? super V, R> yielder) {
    return chain(new YieldLoopHandler<S, V, R>(yielder, mLogger.getName()));
  }

  @NotNull
  public <S, R> AsyncLoop<R> yield(@Nullable final Provider<S> init,
      @Nullable final Mapper<S, ? extends Boolean> loop,
      @Nullable final Updater<S, ? super V, ? super YieldOutputs<R>> value,
      @Nullable final Updater<S, ? super Throwable, ? super YieldOutputs<R>> failure,
      @Nullable final Settler<S, ? super YieldOutputs<R>> done) {
    return yield(new ComposedYielder<S, V, R>(init, loop, value, failure, done));
  }

  @NotNull
  public <S, R> AsyncLoop<R> yieldOrdered(@NotNull final Yielder<S, ? super V, R> yielder) {
    return chainOrdered(new YieldLoopHandler<S, V, R>(yielder, mLogger.getName()));
  }

  @NotNull
  public <S, R> AsyncLoop<R> yieldOrdered(@Nullable final Provider<S> init,
      @Nullable final Mapper<S, ? extends Boolean> loop,
      @Nullable final Updater<S, ? super V, ? super YieldOutputs<R>> value,
      @Nullable final Updater<S, ? super Throwable, ? super YieldOutputs<R>> failure,
      @Nullable final Settler<S, ? super YieldOutputs<R>> done) {
    return yieldOrdered(new ComposedYielder<S, V, R>(init, loop, value, failure, done));
  }

  @NotNull
  @SuppressWarnings("ConstantConditions")
  public Throwable failure() {
    synchronized (mMutex) {
      if (!isFailed()) {
        throw new IllegalStateException("the statement is not failed");
      }

      return mHead.getFailure().getCause();
    }
  }

  public boolean isCancelled() {
    synchronized (mMutex) {
      final FailureException failure = mHead.getFailure();
      return (failure != null) && failure.isCancelled();
    }
  }

  public boolean isEvaluating() {
    synchronized (mMutex) {
      return mIsEvaluated && (mState == StatementState.Evaluating);
    }
  }

  public boolean isFailed() {
    synchronized (mMutex) {
      return (mState == StatementState.Failed);
    }
  }

  public boolean isSet() {
    synchronized (mMutex) {
      return (mState == StatementState.Set);
    }
  }

  public void to(@NotNull final AsyncEvaluation<? super Iterable<V>> evaluation) {
    checkEvaluated();
    yield(new CollectionYielder<V>() {

      @Override
      public void done(final ArrayList<V> stack, @NotNull final YieldOutputs<V> outputs) {
        evaluation.set(stack);
      }

      @Override
      public ArrayList<V> failure(final ArrayList<V> stack, @NotNull final Throwable failure,
          @NotNull final YieldOutputs<V> outputs) throws Exception {
        evaluation.fail(failure);
        return null;
      }
    });
  }

  @SuppressWarnings("unchecked")
  public Iterable<V> value() {
    synchronized (mMutex) {
      if (!isSet()) {
        throw new IllegalStateException("the statement is not set");
      }

      return (Iterable<V>) mHead.getValues();
    }
  }

  @NotNull
  private <R> AsyncLoop<R> chain(@NotNull final AsyncStatementHandler<V, R> handler) {
    return chain(new ChainStatementHandler<V, R>(handler));
  }

  @NotNull
  private <R> AsyncLoop<R> chain(@NotNull final AsyncStatementLoopHandler<V, R> handler) {
    return chain(new ChainStatementLoopHandler<V, R>(handler));
  }

  @NotNull
  private <R> AsyncLoop<R> chain(@NotNull final AsyncLoopHandler<V, R> handler) {
    return chain(new ChainLoopHandler<V, R>(handler));
  }

  @NotNull
  @SuppressWarnings("unchecked")
  private <R> AsyncLoop<R> chain(@NotNull final LoopChain<V, R> chain) {
    final Logger logger = mLogger;
    final ChainHead<?> head = mHead;
    final Runnable chaining;
    final Observer<? extends AsyncEvaluations<?>> observer = mObserver;
    if (mIsFork) {
      final AsyncLoop<R> forked =
          new DefaultAsyncLoop<V>(((ForkObserver<?, V>) observer).newObserver(), mIsEvaluated,
              mLogger).chain(chain);
      mForked.add(forked);
      return forked;
    }

    synchronized (mMutex) {
      if (mChain != null) {
        throw new IllegalStateException("the statement is already chained");

      } else {
        chain.setLogger(logger);
        chaining = ((ChainHead<V>) head).chain(chain);
        mChain = chain;
        mMutex.notifyAll();
      }
    }

    final DefaultAsyncLoop<R> loop =
        new DefaultAsyncLoop<R>((Observer<AsyncEvaluations<?>>) observer, mIsEvaluated, logger,
            head, chain, false);
    if (chaining != null) {
      chaining.run();
    }

    ((LoopChain<?, Object>) mTail).setNext((LoopChain<Object, V>) chain);
    return loop;
  }

  @NotNull
  private <R> AsyncLoop<R> chainOrdered(@NotNull final AsyncLoopHandler<V, R> handler) {
    return chain(new ChainLoopHandlerOrdered<V, R>(handler));
  }

  @NotNull
  private <R> AsyncLoop<R> chainOrdered(@NotNull final AsyncStatementHandler<V, R> handler) {
    return chain(new ChainStatementHandlerOrdered<V, R>(handler));
  }

  @NotNull
  private <R> AsyncLoop<R> chainOrdered(@NotNull final AsyncStatementLoopHandler<V, R> handler) {
    return chain(new ChainStatementLoopHandlerOrdered<V, R>(handler));
  }

  private void checkEvaluated() {
    if (!mIsEvaluated) {
      ConstantConditions.unsupported("the loop has not been evaluated", "checkEvaluated");
    }
  }

  private void checkFinal() {
    if (mChain != null) {
      throw new IllegalStateException("the loop is not final");
    }
  }

  private void checkSupported() {
    checkEvaluated();
    if (mIsFork) {
      ConstantConditions.unsupported("the loop has been forked", "checkSupported");
    }
  }

  private void deadLockWarning(final long waitTime) {
    if (waitTime == 0) {
      return;
    }

    final Logger logger = mLogger;
    if (logger.getPrinter().canLogWrn() && ExecutorPool.isOwnedThread()) {
      logger.wrn("ATTENTION: possible deadlock detected! Try to avoid waiting on managed threads");
    }
  }

  @NotNull
  private Observer<AsyncEvaluations<?>> renewObserver() {
    final Observer<AsyncEvaluations<?>> observer = mObserver;
    if (observer instanceof RenewableObserver) {
      return ((RenewableObserver<AsyncEvaluations<?>>) observer).renew();
    }

    return observer;
  }

  @NotNull
  private AsyncStatement<Iterable<V>> toStatement() {
    return new DefaultAsyncStatement<Iterable<V>>(new ToStatementObserver<V>(this), mIsEvaluated,
        mLogger.getName());
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    final ArrayList<LoopChain<?, ?>> chains = new ArrayList<LoopChain<?, ?>>();
    final ChainHead<?> head = mHead;
    LoopChain<?, ?> chain = head;
    while (chain != mTail) {
      if (chain != head) {
        chains.add(chain);
      }

      chain = chain.mNext;
    }

    if (chain != head) {
      chains.add(chain);
    }

    return new LoopProxy(mObserver, mIsEvaluated, mLogger.getName(), chains);
  }

  private static class ChainForkObserver<S, V>
      implements RenewableObserver<AsyncEvaluations<V>>, Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final ForkObserver<S, V> mObserver;

    private ChainForkObserver(@NotNull final ForkObserver<S, V> observer) {
      mObserver = observer;
    }

    public void accept(final AsyncEvaluations<V> evaluation) {
      mObserver.chain(evaluation);
    }

    @NotNull
    public ChainForkObserver<S, V> renew() {
      final ForkObserver<S, V> observer = mObserver.renew();
      observer.accept(null);
      return new ChainForkObserver<S, V>(observer);
    }

    @NotNull
    private Object writeReplace() throws ObjectStreamException {
      return new ObserverProxy<S, V>(mObserver);
    }

    private static class ObserverProxy<S, V> implements Serializable {

      private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

      private final ForkObserver<S, V> mObserver;

      private ObserverProxy(@NotNull final ForkObserver<S, V> observer) {
        mObserver = observer;
      }

      @NotNull
      private Object readResolve() throws ObjectStreamException {
        try {
          final ForkObserver<S, V> observer = mObserver;
          observer.accept(null);
          return new ChainForkObserver<S, V>(observer);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }
  }

  private static class ChainHead<V> extends LoopChain<V, V> {

    private final Object mMutex = new Object();

    private StateEvaluating mInnerState = new StateEvaluating();

    private StatementState mState = StatementState.Evaluating;

    private DoubleQueue<SimpleState<V>> mStates = new DoubleQueue<SimpleState<V>>();

    @Override
    void addFailure(final LoopChain<V, ?> next, @NotNull final Throwable failure) {
      next.addFailure(failure);
    }

    @Nullable
    Runnable chain(final LoopChain<V, ?> chain) {
      final Runnable binding = mInnerState.chain(chain);
      mState = StatementState.Chained;
      return binding;
    }

    @NotNull
    List<SimpleState<V>> consumeStates() {
      final ArrayList<SimpleState<V>> states = new ArrayList<SimpleState<V>>(mStates);
      mStates.clear();
      return states;
    }

    @Nullable
    FailureException getFailure() {
      for (final SimpleState<V> state : mStates) {
        if (state.isFailed()) {
          return FailureException.wrap(state.failure());
        }
      }

      return null;
    }

    @NotNull
    Object getMutex() {
      return mMutex;
    }

    @NotNull
    StatementState getState() {
      return mState;
    }

    @NotNull
    DoubleQueue<SimpleState<V>> getStates() {
      return mStates;
    }

    @NotNull
    List<V> getValues() {
      final ArrayList<V> values = new ArrayList<V>();
      for (final SimpleState<V> state : mStates) {
        if (state.isFailed()) {
          throw FailureException.wrap(state.failure());
        }

        values.add(state.value());
      }

      return values;
    }

    void innerAddFailure(@NotNull final Throwable failure) {
      getLogger().dbg("Loop failed with reason [%s => %s]: %s", StatementState.Evaluating,
          StatementState.Failed, failure);
      final DoubleQueue<SimpleState<V>> states = mStates;
      states.add(SimpleState.<V>ofFailure(failure));
      mState = StatementState.Failed;
    }

    void innerAddValue(final V value) {
      getLogger().dbg("Adding value [%s => %s]: %s", StatementState.Evaluating,
          StatementState.Evaluating, value);
      mStates.add(SimpleState.ofValue(value));
    }

    void innerSet() {
      mInnerState.innerSet();
      if (mState == StatementState.Evaluating) {
        mState = StatementState.Set;
      }
    }

    boolean isSet() {
      return mInnerState.isSet();
    }

    private class StateEvaluating {

      @Nullable
      Runnable chain(final LoopChain<V, ?> chain) {
        chain.prepend(consumeStates());
        return null;
      }

      @NotNull
      IllegalStateException exception(@NotNull final StatementState state) {
        return new IllegalStateException("invalid state: " + state);
      }

      void innerSet() {
        getLogger().dbg("Setting loop with values [%s => %s]: %s", StatementState.Evaluating,
            StatementState.Set, mStates);
        mInnerState = new StateSet();
      }

      boolean isSet() {
        return false;
      }
    }

    private class StateSet extends StateEvaluating {

      @Nullable
      @Override
      Runnable chain(final LoopChain<V, ?> chain) {
        getLogger().dbg("Binding loop [%s => %s]", StatementState.Set, StatementState.Chained);
        mInnerState = new StateEvaluating();
        chain.prepend(consumeStates());
        return new Runnable() {

          public void run() {
            chain.set();
          }
        };
      }

      @Override
      void innerSet() {
        throw exception(StatementState.Set);
      }

      @Override
      boolean isSet() {
        return true;
      }
    }

    @Override
    void addFailures(final LoopChain<V, ?> next,
        @NotNull  final Iterable<? extends Throwable> failures) {
      next.addFailures(failures);
    }

    @Override
    void addValue(final LoopChain<V, ?> next, final V value) {
      next.addValue(value);
    }

    @Override
    void addValues(final LoopChain<V, ?> next, @NotNull final Iterable<? extends V> values) {
      next.addValues(values);
    }

    @NotNull
    @Override
    ChainHead<V> copy() {
      return new ChainHead<V>();
    }

    @Override
    void set(final LoopChain<V, ?> next) {
      next.set();
    }
  }

  private static class ChainLoopHandler<V, R> extends LoopChain<V, R> implements Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final ChainEvaluations mEvaluations = new ChainEvaluations();

    private final AsyncLoopHandler<V, R> mHandler;

    private final AtomicLong mPendingCount = new AtomicLong(1);

    private ChainLoopHandler(@Nullable final AsyncLoopHandler<V, R> handler) {
      mHandler = ConstantConditions.notNull("handler", handler);
    }

    private void innerFailSafe(final LoopChain<R, ?> next, @NotNull final Throwable failure) {
      mPendingCount.set(0);
      next.failSafe(failure);
    }

    private void innerSet(final LoopChain<R, ?> next) {
      if (mPendingCount.decrementAndGet() > 0) {
        return;
      }

      next.set();
    }

    @NotNull
    private Object writeReplace() throws ObjectStreamException {
      return new ChainProxy<V, R>(mHandler);
    }

    private static class ChainProxy<V, R> implements Serializable {

      private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

      private final AsyncLoopHandler<V, R> mHandler;

      private ChainProxy(final AsyncLoopHandler<V, R> handler) {
        mHandler = handler;
      }

      @NotNull
      private Object readResolve() throws ObjectStreamException {
        try {
          return new ChainLoopHandler<V, R>(mHandler);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    private class ChainEvaluations implements AsyncEvaluations<R> {

      private volatile LoopChain<R, ?> mNext;

      @NotNull
      ChainEvaluations withNext(final LoopChain<R, ?> next) {
        mNext = next;
        return this;
      }

      @NotNull
      public AsyncEvaluations<R> addFailure(@NotNull final Throwable failure) {
        mNext.addFailure(failure);
        return this;
      }

      @NotNull
      public AsyncEvaluations<R> addFailures(
          @Nullable final Iterable<? extends Throwable> failures) {
        mNext.addFailures(failures);
        return this;
      }

      @NotNull
      public AsyncEvaluations<R> addValue(final R value) {
        mNext.addValue(value);
        return this;
      }

      @NotNull
      public AsyncEvaluations<R> addValues(@Nullable final Iterable<? extends R> values) {
        mNext.addValues(values);
        return this;
      }

      public void set() {
        innerSet(mNext);
      }
    }

    @Override
    void addFailure(final LoopChain<R, ?> next, @NotNull final Throwable failure) {
      mPendingCount.incrementAndGet();
      try {
        mHandler.addFailure(failure, mEvaluations.withNext(next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing failure with reason: %s", failure);
        innerFailSafe(next, RuntimeInterruptedException.wrapIfInterrupt(t));
      }
    }

    @Override
    void addValue(final LoopChain<R, ?> next, final V value) {
      mPendingCount.incrementAndGet();
      try {
        mHandler.addValue(value, mEvaluations.withNext(next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing value: %s", value);
        innerFailSafe(next, RuntimeInterruptedException.wrapIfInterrupt(t));
      }
    }

    @Override
    void addValues(final LoopChain<R, ?> next, @NotNull final Iterable<? extends V> values) {
      mPendingCount.incrementAndGet();
      try {
        mHandler.addValues(values, mEvaluations.withNext(next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing values: %s", Iterables.toString(values));
        innerFailSafe(next, RuntimeInterruptedException.wrapIfInterrupt(t));
      }
    }

    @NotNull
    @Override
    LoopChain<V, R> copy() {
      return new ChainLoopHandler<V, R>(mHandler.renew());
    }

    @Override
    void addFailures(final LoopChain<R, ?> next,
        @NotNull final Iterable<? extends Throwable> failures) {
      mPendingCount.incrementAndGet();
      try {
        mHandler.addFailures(failures, mEvaluations.withNext(next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing value: %s", Iterables.toString(failures));
        innerFailSafe(next, RuntimeInterruptedException.wrapIfInterrupt(t));
      }
    }

    @Override
    void set(final LoopChain<R, ?> next) {
      try {
        mHandler.set(mEvaluations.withNext(next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while completing loop");
        innerFailSafe(next, RuntimeInterruptedException.wrapIfInterrupt(t));
      }
    }
  }

  private static class ChainLoopHandlerOrdered<V, R> extends LoopChain<V, R>
      implements Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final AsyncLoopHandler<V, R> mHandler;

    private final Object mMutex = new Object();

    private final AtomicLong mPendingCount = new AtomicLong(1);

    private final NestedQueue<SimpleState<R>> mQueue = new NestedQueue<SimpleState<R>>();

    private ChainLoopHandlerOrdered(@Nullable final AsyncLoopHandler<V, R> handler) {
      mHandler = ConstantConditions.notNull("handler", handler);
    }

    private void flushQueue(final LoopChain<R, ?> next) {
      addTo(mMutex, mQueue, next);
    }

    private void innerFailSafe(final LoopChain<R, ?> next, @NotNull final Throwable failure) {
      mPendingCount.set(0);
      synchronized (mMutex) {
        mQueue.clear();
      }

      next.failSafe(failure);
    }

    private void innerSet(final LoopChain<R, ?> next) {
      if (mPendingCount.decrementAndGet() > 0) {
        return;
      }

      next.set();
    }

    @NotNull
    private Object writeReplace() throws ObjectStreamException {
      return new ChainProxy<V, R>(mHandler);
    }

    private static class ChainProxy<V, R> implements Serializable {

      private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

      private final AsyncLoopHandler<V, R> mHandler;

      private ChainProxy(final AsyncLoopHandler<V, R> handler) {
        mHandler = handler;
      }

      @NotNull
      private Object readResolve() throws ObjectStreamException {
        try {
          return new ChainLoopHandlerOrdered<V, R>(mHandler);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    private class ChainEvaluations implements AsyncEvaluations<R> {

      private final LoopChain<R, ?> mNext;

      private final NestedQueue<SimpleState<R>> mQueue;

      private ChainEvaluations(@NotNull final NestedQueue<SimpleState<R>> queue,
          final LoopChain<R, ?> next) {
        mQueue = queue;
        mNext = next;
      }

      @NotNull
      public AsyncEvaluations<R> addFailure(@NotNull final Throwable failure) {
        synchronized (mMutex) {
          mQueue.add(SimpleState.<R>ofFailure(failure));
        }

        flushQueue(mNext);
        return this;
      }

      @NotNull
      public AsyncEvaluations<R> addFailures(
          @Nullable final Iterable<? extends Throwable> failures) {
        if (failures != null) {
          synchronized (mMutex) {
            @SuppressWarnings("UnnecessaryLocalVariable") final NestedQueue<SimpleState<R>> queue =
                mQueue;
            for (final Throwable failure : failures) {
              queue.add(SimpleState.<R>ofFailure(failure));
            }
          }

          flushQueue(mNext);
        }

        return this;
      }

      @NotNull
      public AsyncEvaluations<R> addValue(final R value) {
        synchronized (mMutex) {
          mQueue.add(SimpleState.ofValue(value));
        }

        flushQueue(mNext);
        return this;
      }

      @NotNull
      public AsyncEvaluations<R> addValues(@Nullable final Iterable<? extends R> values) {
        if (values != null) {
          synchronized (mMutex) {
            @SuppressWarnings("UnnecessaryLocalVariable") final NestedQueue<SimpleState<R>> queue =
                mQueue;
            for (final R value : values) {
              queue.add(SimpleState.ofValue(value));
            }
          }

          flushQueue(mNext);
        }

        return this;
      }

      public void set() {
        synchronized (mMutex) {
          mQueue.close();
        }

        final LoopChain<R, ?> next = mNext;
        flushQueue(next);
        innerSet(next);
      }
    }

    @Override
    void addFailure(final LoopChain<R, ?> next, @NotNull final Throwable failure) {
      mPendingCount.incrementAndGet();
      try {
        mHandler.addFailure(failure, new ChainEvaluations(mQueue.addNested(), next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing failure with reason: %s", failure);
        innerFailSafe(next, RuntimeInterruptedException.wrapIfInterrupt(t));
      }
    }

    @Override
    void addValue(final LoopChain<R, ?> next, final V value) {
      mPendingCount.incrementAndGet();
      try {
        mHandler.addValue(value, new ChainEvaluations(mQueue.addNested(), next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing value: %s", value);
        innerFailSafe(next, RuntimeInterruptedException.wrapIfInterrupt(t));
      }
    }

    @Override
    void addValues(final LoopChain<R, ?> next, @NotNull final Iterable<? extends V> values) {
      mPendingCount.incrementAndGet();
      try {
        mHandler.addValues(values, new ChainEvaluations(mQueue.addNested(), next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing values: %s", Iterables.toString(values));
        innerFailSafe(next, RuntimeInterruptedException.wrapIfInterrupt(t));
      }
    }

    @NotNull
    @Override
    LoopChain<V, R> copy() {
      return new ChainLoopHandlerOrdered<V, R>(mHandler.renew());
    }

    @Override
    void addFailures(final LoopChain<R, ?> next,
        @NotNull final Iterable<? extends Throwable> failures) {
      mPendingCount.incrementAndGet();
      try {
        mHandler.addFailures(failures, new ChainEvaluations(mQueue.addNested(), next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing failures: %s", Iterables.toString(failures));
        innerFailSafe(next, RuntimeInterruptedException.wrapIfInterrupt(t));
      }
    }

    @Override
    void set(final LoopChain<R, ?> next) {
      try {
        mHandler.set(new ChainEvaluations(mQueue.addNested(), next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while completing loop");
        innerFailSafe(next, RuntimeInterruptedException.wrapIfInterrupt(t));
      }
    }
  }

  private static class ChainStatementHandler<V, R> extends LoopChain<V, R> implements Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final ChainEvaluation mEvaluation = new ChainEvaluation();

    private final AsyncStatementHandler<V, R> mHandler;

    private final AtomicLong mPendingCount = new AtomicLong(1);

    private ChainStatementHandler(@Nullable final AsyncStatementHandler<V, R> handler) {
      mHandler = ConstantConditions.notNull("handler", handler);
    }

    private void innerFailSafe(final LoopChain<R, ?> next, @NotNull final Throwable failure) {
      mPendingCount.set(0);
      next.failSafe(failure);
    }

    private void innerSet(final LoopChain<R, ?> next) {
      if (mPendingCount.decrementAndGet() > 0) {
        return;
      }

      next.set();
    }

    @NotNull
    private Object writeReplace() throws ObjectStreamException {
      return new ChainProxy<V, R>(mHandler);
    }

    private static class ChainProxy<V, R> implements Serializable {

      private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

      private final AsyncStatementHandler<V, R> mHandler;

      private ChainProxy(final AsyncStatementHandler<V, R> handler) {
        mHandler = handler;
      }

      @NotNull
      private Object readResolve() throws ObjectStreamException {
        try {
          return new ChainStatementHandler<V, R>(mHandler);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    private class ChainEvaluation implements AsyncEvaluation<R> {

      private volatile LoopChain<R, ?> mNext;

      public void fail(@NotNull final Throwable failure) {
        final LoopChain<R, ?> next = mNext;
        next.addFailureSafe(failure);
        innerSet(next);
      }

      @NotNull
      ChainEvaluation withNext(final LoopChain<R, ?> next) {
        mNext = next;
        return this;
      }

      public void set(final R value) {
        final LoopChain<R, ?> next = mNext;
        next.addValue(value);
        innerSet(next);
      }
    }

    @Override
    void addFailure(final LoopChain<R, ?> next, @NotNull final Throwable failure) {
      mPendingCount.incrementAndGet();
      try {
        mHandler.failure(failure, mEvaluation.withNext(next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing failure with reason: %s", failure);
        innerFailSafe(next, RuntimeInterruptedException.wrapIfInterrupt(t));
      }
    }

    @Override
    void addValue(final LoopChain<R, ?> next, final V value) {
      mPendingCount.incrementAndGet();
      try {
        mHandler.value(value, mEvaluation.withNext(next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing value: %s", value);
        innerFailSafe(next, RuntimeInterruptedException.wrapIfInterrupt(t));
      }
    }

    @Override
    void addValues(final LoopChain<R, ?> next, @NotNull final Iterable<? extends V> values) {
      mPendingCount.addAndGet(Iterables.size(values));
      final ChainEvaluation evaluation = mEvaluation.withNext(next);
      for (final V value : values) {
        try {
          mHandler.value(value, evaluation);

        } catch (final CancellationException e) {
          getLogger().wrn(e, "Loop has been cancelled");
          innerFailSafe(next, e);
          break;

        } catch (final Throwable t) {
          getLogger().err(t, "Error while processing value: %s", value);
          innerFailSafe(next, RuntimeInterruptedException.wrapIfInterrupt(t));
          break;
        }
      }
    }

    @NotNull
    @Override
    LoopChain<V, R> copy() {
      return new ChainStatementHandler<V, R>(mHandler.renew());
    }

    @Override
    void addFailures(final LoopChain<R, ?> next,
        @NotNull final Iterable<? extends Throwable> failures) {
      mPendingCount.addAndGet(Iterables.size(failures));
      final ChainEvaluation evaluation = mEvaluation.withNext(next);
      for (final Throwable failure : failures) {
        try {
          mHandler.failure(failure, evaluation);

        } catch (final CancellationException e) {
          getLogger().wrn(e, "Loop has been cancelled");
          innerFailSafe(next, e);
          break;

        } catch (final Throwable t) {
          getLogger().err(t, "Error while processing failure with reason: %s", failure);
          innerFailSafe(next, RuntimeInterruptedException.wrapIfInterrupt(t));
          break;
        }
      }
    }

    @Override
    void set(final LoopChain<R, ?> next) {
      innerSet(next);
    }
  }

  private static class ChainStatementHandlerOrdered<V, R> extends LoopChain<V, R>
      implements Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final AsyncStatementHandler<V, R> mHandler;

    private final Object mMutex = new Object();

    private final AtomicLong mPendingCount = new AtomicLong(1);

    private final NestedQueue<SimpleState<R>> mQueue = new NestedQueue<SimpleState<R>>();

    private ChainStatementHandlerOrdered(@Nullable final AsyncStatementHandler<V, R> handler) {
      mHandler = ConstantConditions.notNull("handler", handler);
    }

    private void flushQueue(final LoopChain<R, ?> next) {
      addTo(mMutex, mQueue, next);
    }

    private void flushQueueSafe(final LoopChain<R, ?> next) {
      try {
        flushQueue(next);

      } catch (final Throwable t) {
        getLogger().wrn(t, "Suppressed failure");
      }
    }

    private void innerFailSafe(final LoopChain<R, ?> next, @NotNull final Throwable failure) {
      mPendingCount.set(0);
      synchronized (mMutex) {
        mQueue.clear();
      }

      next.failSafe(failure);
    }

    private void innerSet(final LoopChain<R, ?> next) {
      if (mPendingCount.decrementAndGet() > 0) {
        return;
      }

      next.set();
    }

    @NotNull
    private Object writeReplace() throws ObjectStreamException {
      return new ChainProxy<V, R>(mHandler);
    }

    private static class ChainProxy<V, R> implements Serializable {

      private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

      private final AsyncStatementHandler<V, R> mHandler;

      private ChainProxy(final AsyncStatementHandler<V, R> handler) {
        mHandler = handler;
      }

      @NotNull
      private Object readResolve() throws ObjectStreamException {
        try {
          return new ChainStatementHandlerOrdered<V, R>(mHandler);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    private class ChainEvaluation implements AsyncEvaluation<R> {

      private final LoopChain<R, ?> mNext;

      private final NestedQueue<SimpleState<R>> mQueue;

      private ChainEvaluation(@NotNull final NestedQueue<SimpleState<R>> queue,
          final LoopChain<R, ?> next) {
        mQueue = queue;
        mNext = next;
      }

      public void fail(@NotNull final Throwable failure) {
        synchronized (mMutex) {
          final NestedQueue<SimpleState<R>> queue = mQueue;
          queue.add(SimpleState.<R>ofFailure(failure));
          queue.close();
        }

        flushQueueSafe(mNext);
      }

      public void set(final R value) {
        synchronized (mMutex) {
          final NestedQueue<SimpleState<R>> queue = mQueue;
          queue.add(SimpleState.ofValue(value));
          queue.close();
        }

        flushQueue(mNext);
      }
    }

    @Override
    void addFailure(final LoopChain<R, ?> next, @NotNull final Throwable failure) {
      mPendingCount.incrementAndGet();
      try {
        mHandler.failure(failure, new ChainEvaluation(mQueue.addNested(), next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing failure with reason: %s", failure);
        innerFailSafe(next, RuntimeInterruptedException.wrapIfInterrupt(t));
      }
    }

    @Override
    void addValue(final LoopChain<R, ?> next, final V value) {
      mPendingCount.incrementAndGet();
      try {
        mHandler.value(value, new ChainEvaluation(mQueue.addNested(), next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing value: %s", value);
        innerFailSafe(next, RuntimeInterruptedException.wrapIfInterrupt(t));
      }
    }

    @Override
    void addValues(final LoopChain<R, ?> next, @NotNull final Iterable<? extends V> values) {
      mPendingCount.addAndGet(Iterables.size(values));
      for (final V value : values) {
        try {
          mHandler.value(value, new ChainEvaluation(mQueue.addNested(), next));

        } catch (final CancellationException e) {
          getLogger().wrn(e, "Loop has been cancelled");
          innerFailSafe(next, e);
          break;

        } catch (final Throwable t) {
          getLogger().err(t, "Error while processing value: %s", value);
          innerFailSafe(next, RuntimeInterruptedException.wrapIfInterrupt(t));
          break;
        }
      }
    }

    @NotNull
    @Override
    LoopChain<V, R> copy() {
      return new ChainStatementHandlerOrdered<V, R>(mHandler.renew());
    }

    @Override
    void addFailures(final LoopChain<R, ?> next,
        @NotNull final Iterable<? extends Throwable> failures) {
      mPendingCount.addAndGet(Iterables.size(failures));
      for (final Throwable failure : failures) {
        try {
          mHandler.failure(failure, new ChainEvaluation(mQueue.addNested(), next));

        } catch (final CancellationException e) {
          getLogger().wrn(e, "Loop has been cancelled");
          innerFailSafe(next, e);
          break;

        } catch (final Throwable t) {
          getLogger().err(t, "Error while processing value: %s", failure);
          innerFailSafe(next, RuntimeInterruptedException.wrapIfInterrupt(t));
          break;
        }
      }
    }

    @Override
    void set(final LoopChain<R, ?> next) {
      flushQueue(next);
      innerSet(next);
    }
  }

  private static class ChainStatementLoopHandler<V, R> extends LoopChain<V, R>
      implements Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final ChainEvaluations mEvaluation = new ChainEvaluations();

    private final AsyncStatementLoopHandler<V, R> mHandler;

    private final AtomicLong mPendingCount = new AtomicLong(1);

    private ChainStatementLoopHandler(@Nullable final AsyncStatementLoopHandler<V, R> handler) {
      mHandler = ConstantConditions.notNull("handler", handler);
    }

    private void innerFailSafe(final LoopChain<R, ?> next, @NotNull final Throwable failure) {
      mPendingCount.set(0);
      next.failSafe(failure);
    }

    private void innerSet(final LoopChain<R, ?> next) {
      if (mPendingCount.decrementAndGet() > 0) {
        return;
      }

      next.set();
    }

    @NotNull
    private Object writeReplace() throws ObjectStreamException {
      return new ChainProxy<V, R>(mHandler);
    }

    private static class ChainProxy<V, R> implements Serializable {

      private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

      private final AsyncStatementLoopHandler<V, R> mHandler;

      private ChainProxy(final AsyncStatementLoopHandler<V, R> handler) {
        mHandler = handler;
      }

      @NotNull
      private Object readResolve() throws ObjectStreamException {
        try {
          return new ChainStatementLoopHandler<V, R>(mHandler);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    private class ChainEvaluations implements AsyncEvaluations<R> {

      private volatile LoopChain<R, ?> mNext;

      @NotNull
      ChainEvaluations withNext(final LoopChain<R, ?> next) {
        mNext = next;
        return this;
      }

      @NotNull
      public AsyncEvaluations<R> addFailure(@NotNull final Throwable failure) {
        mNext.addFailure(failure);
        return this;
      }

      @NotNull
      public AsyncEvaluations<R> addFailures(
          @Nullable final Iterable<? extends Throwable> failures) {
        mNext.addFailures(failures);
        return this;
      }

      @NotNull
      public AsyncEvaluations<R> addValue(final R value) {
        mNext.addValue(value);
        return this;
      }

      @NotNull
      public AsyncEvaluations<R> addValues(@Nullable final Iterable<? extends R> values) {
        mNext.addValues(values);
        return this;
      }

      public void set() {
        innerSet(mNext);
      }
    }

    @Override
    void addFailure(final LoopChain<R, ?> next, @NotNull final Throwable failure) {
      mPendingCount.incrementAndGet();
      try {
        mHandler.failure(failure, mEvaluation.withNext(next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing failure with reason: %s", failure);
        innerFailSafe(next, RuntimeInterruptedException.wrapIfInterrupt(t));
      }
    }

    @Override
    void addValue(final LoopChain<R, ?> next, final V value) {
      mPendingCount.incrementAndGet();
      try {
        mHandler.value(value, mEvaluation.withNext(next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing value: %s", value);
        innerFailSafe(next, RuntimeInterruptedException.wrapIfInterrupt(t));
      }
    }

    @Override
    void addValues(final LoopChain<R, ?> next, @NotNull final Iterable<? extends V> values) {
      mPendingCount.addAndGet(Iterables.size(values));
      final ChainEvaluations evaluation = mEvaluation.withNext(next);
      for (final V value : values) {
        try {
          mHandler.value(value, evaluation);

        } catch (final CancellationException e) {
          getLogger().wrn(e, "Loop has been cancelled");
          innerFailSafe(next, e);
          break;

        } catch (final Throwable t) {
          getLogger().err(t, "Error while processing value: %s", value);
          innerFailSafe(next, RuntimeInterruptedException.wrapIfInterrupt(t));
          break;
        }
      }
    }

    @NotNull
    @Override
    LoopChain<V, R> copy() {
      return new ChainStatementLoopHandler<V, R>(mHandler.renew());
    }

    @Override
    void addFailures(final LoopChain<R, ?> next,
        @NotNull final Iterable<? extends Throwable> failures) {
      mPendingCount.addAndGet(Iterables.size(failures));
      final ChainEvaluations evaluation = mEvaluation.withNext(next);
      for (final Throwable failure : failures) {
        try {
          mHandler.failure(failure, evaluation);

        } catch (final CancellationException e) {
          getLogger().wrn(e, "Loop has been cancelled");
          innerFailSafe(next, e);
          break;

        } catch (final Throwable t) {
          getLogger().err(t, "Error while processing failure with reason: %s", failure);
          innerFailSafe(next, RuntimeInterruptedException.wrapIfInterrupt(t));
          break;
        }
      }
    }

    @Override
    void set(final LoopChain<R, ?> next) {
      innerSet(next);
    }
  }

  private static class ChainStatementLoopHandlerOrdered<V, R> extends LoopChain<V, R>
      implements Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final AsyncStatementLoopHandler<V, R> mHandler;

    private final Object mMutex = new Object();

    private final AtomicLong mPendingCount = new AtomicLong(1);

    private final NestedQueue<SimpleState<R>> mQueue = new NestedQueue<SimpleState<R>>();

    private ChainStatementLoopHandlerOrdered(
        @Nullable final AsyncStatementLoopHandler<V, R> handler) {
      mHandler = ConstantConditions.notNull("handler", handler);
    }

    private void flushQueue(final LoopChain<R, ?> next) {
      addTo(mMutex, mQueue, next);
    }

    private void innerFailSafe(final LoopChain<R, ?> next, @NotNull final Throwable failure) {
      mPendingCount.set(0);
      synchronized (mMutex) {
        mQueue.clear();
      }

      next.failSafe(failure);
    }

    private void innerSet(final LoopChain<R, ?> next) {
      if (mPendingCount.decrementAndGet() > 0) {
        return;
      }

      next.set();
    }

    @NotNull
    private Object writeReplace() throws ObjectStreamException {
      return new ChainProxy<V, R>(mHandler);
    }

    private static class ChainProxy<V, R> implements Serializable {

      private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

      private final AsyncStatementLoopHandler<V, R> mHandler;

      private ChainProxy(final AsyncStatementLoopHandler<V, R> handler) {
        mHandler = handler;
      }

      @NotNull
      private Object readResolve() throws ObjectStreamException {
        try {
          return new ChainStatementLoopHandlerOrdered<V, R>(mHandler);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    private class ChainEvaluations implements AsyncEvaluations<R> {

      private final LoopChain<R, ?> mNext;

      private final NestedQueue<SimpleState<R>> mQueue;

      private ChainEvaluations(@NotNull final NestedQueue<SimpleState<R>> queue,
          final LoopChain<R, ?> next) {
        mQueue = queue;
        mNext = next;
      }

      @NotNull
      public AsyncEvaluations<R> addFailure(@NotNull final Throwable failure) {
        synchronized (mMutex) {
          mQueue.add(SimpleState.<R>ofFailure(failure));
        }

        flushQueue(mNext);
        return this;
      }

      @NotNull
      public AsyncEvaluations<R> addFailures(
          @Nullable final Iterable<? extends Throwable> failures) {
        if (failures != null) {
          synchronized (mMutex) {
            @SuppressWarnings("UnnecessaryLocalVariable") final NestedQueue<SimpleState<R>> queue =
                mQueue;
            for (final Throwable failure : failures) {
              queue.add(SimpleState.<R>ofFailure(failure));
            }
          }

          flushQueue(mNext);
        }

        return this;
      }

      @NotNull
      public AsyncEvaluations<R> addValue(final R value) {
        synchronized (mMutex) {
          mQueue.add(SimpleState.ofValue(value));
        }

        flushQueue(mNext);
        return this;
      }

      @NotNull
      public AsyncEvaluations<R> addValues(@Nullable final Iterable<? extends R> values) {
        if (values != null) {
          synchronized (mMutex) {
            @SuppressWarnings("UnnecessaryLocalVariable") final NestedQueue<SimpleState<R>> queue =
                mQueue;
            for (final R value : values) {
              queue.add(SimpleState.ofValue(value));
            }
          }

          flushQueue(mNext);
        }

        return this;
      }

      public void set() {
        synchronized (mMutex) {
          mQueue.close();
        }

        final LoopChain<R, ?> next = mNext;
        flushQueue(next);
        innerSet(next);
      }
    }

    @Override
    void addFailure(final LoopChain<R, ?> next, @NotNull final Throwable failure) {
      mPendingCount.incrementAndGet();
      try {
        mHandler.failure(failure, new ChainEvaluations(mQueue.addNested(), next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing failure with reason: %s", failure);
        innerFailSafe(next, RuntimeInterruptedException.wrapIfInterrupt(t));
      }
    }

    @Override
    void addValue(final LoopChain<R, ?> next, final V value) {
      mPendingCount.incrementAndGet();
      try {
        mHandler.value(value, new ChainEvaluations(mQueue.addNested(), next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing value: %s", value);
        innerFailSafe(next, RuntimeInterruptedException.wrapIfInterrupt(t));
      }
    }

    @Override
    void addValues(final LoopChain<R, ?> next, @NotNull final Iterable<? extends V> values) {
      mPendingCount.addAndGet(Iterables.size(values));
      for (final V value : values) {
        try {
          mHandler.value(value, new ChainEvaluations(mQueue.addNested(), next));

        } catch (final CancellationException e) {
          getLogger().wrn(e, "Loop has been cancelled");
          innerFailSafe(next, e);
          break;

        } catch (final Throwable t) {
          getLogger().err(t, "Error while processing value: %s", value);
          innerFailSafe(next, RuntimeInterruptedException.wrapIfInterrupt(t));
          break;
        }
      }
    }

    @NotNull
    @Override
    LoopChain<V, R> copy() {
      return new ChainStatementLoopHandlerOrdered<V, R>(mHandler.renew());
    }

    @Override
    void addFailures(final LoopChain<R, ?> next,
        @NotNull final Iterable<? extends Throwable> failures) {
      mPendingCount.addAndGet(Iterables.size(failures));
      for (final Throwable failure : failures) {
        try {
          mHandler.failure(failure, new ChainEvaluations(mQueue.addNested(), next));

        } catch (final CancellationException e) {
          getLogger().wrn(e, "Loop has been cancelled");
          innerFailSafe(next, e);
          break;

        } catch (final Throwable t) {
          getLogger().err(t, "Error while processing failure with reason: %s", failure);
          innerFailSafe(next, RuntimeInterruptedException.wrapIfInterrupt(t));
          break;
        }
      }
    }

    @Override
    void set(final LoopChain<R, ?> next) {
      innerSet(next);
    }
  }

  private static class ForkObserver<S, V> extends AsyncLoopHandler<V, V>
      implements RenewableObserver<AsyncEvaluations<V>>, Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final List<AsyncEvaluations<V>> mEvaluations = new ArrayList<AsyncEvaluations<V>>();

    private final Executor mExecutor;

    private final Forker<S, V, AsyncEvaluations<V>, AsyncLoop<V>> mForker;

    private Throwable mFailure;

    private DefaultAsyncLoop<V> mLoop;

    private S mStack;

    @SuppressWarnings("unchecked")
    private ForkObserver(@NotNull final DefaultAsyncLoop<V> loop,
        @NotNull final Forker<S, ? super V, ? super AsyncEvaluations<V>, ? super AsyncLoop<V>>
            forker) {
      mForker =
          (Forker<S, V, AsyncEvaluations<V>, AsyncLoop<V>>) ConstantConditions.notNull("forker",
              forker);
      mExecutor = withThrottling(1, immediateExecutor());
      mLoop = loop;
    }

    @Override
    void addFailure(@NotNull final Throwable throwable,
        @NotNull final AsyncEvaluations<V> evaluations) {
      mExecutor.execute(new Runnable() {

        public void run() {
          final Throwable failure = mFailure;
          if (failure != null) {
            clearEvaluations(failure);
            return;
          }

          try {
            mStack = mForker.failure(mStack, throwable, mLoop);

          } catch (final Throwable t) {
            final Throwable throwable = RuntimeInterruptedException.wrapIfInterrupt(t);
            mFailure = throwable;
            clearEvaluations(throwable);
          }
        }
      });
    }

    @Override
    void addFailures(@Nullable final Iterable<? extends Throwable> failures,
        @NotNull final AsyncEvaluations<V> evaluations) {
      mExecutor.execute(new Runnable() {

        public void run() {
          final Throwable failure = mFailure;
          if (failure != null) {
            clearEvaluations(failure);
            return;
          }

          if (failures != null) {
            try {
              @SuppressWarnings(
                  "UnnecessaryLocalVariable") final Forker<S, V, AsyncEvaluations<V>, AsyncLoop<V>>
                  forker = mForker;
              for (final Throwable throwable : failures) {
                mStack = forker.failure(mStack, throwable, mLoop);
              }

            } catch (final Throwable t) {
              final Throwable throwable = RuntimeInterruptedException.wrapIfInterrupt(t);
              mFailure = throwable;
              clearEvaluations(throwable);
            }
          }
        }
      });
    }

    @Override
    void addValue(final V value, @NotNull final AsyncEvaluations<V> evaluations) {
      mExecutor.execute(new Runnable() {

        public void run() {
          final Throwable failure = mFailure;
          if (failure != null) {
            clearEvaluations(failure);
            return;
          }

          try {
            mStack = mForker.value(mStack, value, mLoop);

          } catch (final Throwable t) {
            final Throwable throwable = RuntimeInterruptedException.wrapIfInterrupt(t);
            mFailure = throwable;
            clearEvaluations(throwable);
          }
        }
      });
    }

    @Override
    void addValues(@Nullable final Iterable<? extends V> values,
        @NotNull final AsyncEvaluations<V> evaluations) {
      mExecutor.execute(new Runnable() {

        public void run() {
          final Throwable failure = mFailure;
          if (failure != null) {
            clearEvaluations(failure);
            return;
          }

          if (values != null) {
            try {
              @SuppressWarnings(
                  "UnnecessaryLocalVariable") final Forker<S, V, AsyncEvaluations<V>, AsyncLoop<V>>
                  forker = mForker;
              for (final V value : values) {
                mStack = forker.value(mStack, value, mLoop);
              }

            } catch (final Throwable t) {
              final Throwable throwable = RuntimeInterruptedException.wrapIfInterrupt(t);
              mFailure = throwable;
              clearEvaluations(throwable);
            }
          }
        }
      });
    }

    @NotNull
    public ForkObserver<S, V> renew() {
      return new ForkObserver<S, V>(mLoop.evaluate(), mForker);
    }

    @Override
    void set(@NotNull final AsyncEvaluations<V> evaluations) {
      mExecutor.execute(new Runnable() {

        public void run() {
          final Throwable failure = mFailure;
          if (failure != null) {
            clearEvaluations(failure);
            return;
          }

          try {
            mStack = mForker.done(mStack, mLoop);

          } catch (final Throwable t) {
            final Throwable throwable = RuntimeInterruptedException.wrapIfInterrupt(t);
            mFailure = throwable;
            clearEvaluations(throwable);
          }
        }
      });
    }

    boolean cancel(final boolean mayInterruptIfRunning) {
      return mLoop.cancel(mayInterruptIfRunning);
    }

    void chain(final AsyncEvaluations<V> evaluations) {
      mExecutor.execute(new Runnable() {

        public void run() {
          final Throwable failure = mFailure;
          if (failure != null) {
            evaluations.addFailure(failure).set();
            return;
          }

          mEvaluations.add(evaluations);
          try {
            mStack = mForker.evaluation(mStack, evaluations, mLoop);

          } catch (final Throwable t) {
            final Throwable throwable = RuntimeInterruptedException.wrapIfInterrupt(t);
            mFailure = throwable;
            clearEvaluations(throwable);
          }
        }
      });
    }

    @NotNull
    Observer<AsyncEvaluations<V>> newObserver() {
      return new ChainForkObserver<S, V>(this);
    }

    private void clearEvaluations(@NotNull final Throwable failure) {
      final List<AsyncEvaluations<V>> evaluations = mEvaluations;
      for (final AsyncEvaluations<V> evaluation : evaluations) {
        evaluation.addFailure(failure).set();
      }

      evaluations.clear();
    }

    @NotNull
    private Object writeReplace() throws ObjectStreamException {
      return new ObserverProxy<S, V>(mLoop.evaluate(), mForker);
    }

    private static class ObserverProxy<S, V> extends SerializableProxy {

      private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

      private ObserverProxy(final DefaultAsyncLoop<V> loop,
          final Forker<S, ? super V, ? super AsyncEvaluations<V>, ? super AsyncLoop<V>> forker) {
        super(loop, proxy(forker));
      }

      @NotNull
      @SuppressWarnings("unchecked")
      private Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ForkObserver<S, V>((DefaultAsyncLoop<V>) args[0],
              (Forker<S, ? super V, ? super AsyncEvaluations<V>, ? super AsyncLoop<V>>) args[1]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void accept(final AsyncEvaluations<V> evaluations) {
      mExecutor.execute(new Runnable() {

        public void run() {
          try {
            final DefaultAsyncLoop<V> loop = mLoop;
            mStack = mForker.init(loop);
            loop.chain(ForkObserver.this);

          } catch (final Throwable t) {
            mFailure = RuntimeInterruptedException.wrapIfInterrupt(t);
          }
        }
      });
    }
  }

  private static abstract class LoopChain<V, R> implements AsyncEvaluations<V> {

    private static final Runnable NO_OP = new Runnable() {

      public void run() {
      }
    };

    private final Object mMutex = new Object();

    private volatile StateEvaluating mInnerState = new StateEvaluating();

    private volatile Logger mLogger;

    private volatile LoopChain<R, ?> mNext;

    abstract void addFailure(LoopChain<R, ?> next, @NotNull Throwable failure);

    final void addFailureSafe(@NotNull final Throwable failure) {
      final Runnable command;
      synchronized (mMutex) {
        command = mInnerState.addFailureSafe(failure);
      }

      if (command != null) {
        command.run();
        addFailure(mNext, failure);
      }
    }

    abstract void addFailures(LoopChain<R, ?> next,
        @NotNull Iterable<? extends Throwable> failures);

    abstract void addValue(LoopChain<R, ?> next, V input);

    abstract void addValues(LoopChain<R, ?> next, @NotNull Iterable<? extends V> inputs);

    boolean cancel(@NotNull final Throwable exception) {
      final Runnable command;
      synchronized (mMutex) {
        command = mInnerState.cancel(exception);
      }

      if (command != null) {
        command.run();
        final LoopChain<R, ?> next = mNext;
        addFailure(next, exception);
        set(next);
        return true;
      }

      return false;
    }

    @NotNull
    abstract LoopChain<V, R> copy();

    final void failSafe(@NotNull final Throwable failure) {
      final Runnable command;
      synchronized (mMutex) {
        command = mInnerState.failSafe(failure);
      }

      if (command != null) {
        command.run();
        final LoopChain<R, ?> next = mNext;
        addFailure(next, failure);
        set(next);
      }
    }

    Logger getLogger() {
      return mLogger;
    }

    void setLogger(@NotNull final Logger logger) {
      mLogger = logger.newChildLogger(this);
    }

    boolean isTail() {
      return false;
    }

    void prepend(@NotNull final List<SimpleState<V>> states) {
      if (!states.isEmpty()) {
        mInnerState = new StatePrepending(states);
      }
    }

    final void set(final V value) {
      final Runnable command;
      synchronized (mMutex) {
        command = mInnerState.set();
      }

      if (command != null) {
        command.run();
        final LoopChain<R, ?> next = mNext;
        addValue(next, value);
        set(next);
      }
    }

    abstract void set(LoopChain<R, ?> next);

    void setNext(@NotNull LoopChain<R, ?> next) {
      mNext = next;
    }

    private class StateEvaluating {

      @Nullable
      Runnable add() {
        return NO_OP;
      }

      @Nullable
      Runnable addFailureSafe(@NotNull final Throwable failure) {
        return add();
      }

      @Nullable
      Runnable cancel(@NotNull final Throwable failure) {
        mInnerState = new StateFailed(failure);
        return NO_OP;
      }

      @Nullable
      Runnable failSafe(@NotNull final Throwable failure) {
        mInnerState = new StateFailed(failure);
        return NO_OP;
      }

      @Nullable
      Runnable set() {
        mInnerState = new StateSet();
        return NO_OP;
      }
    }

    private class StateFailed extends StateEvaluating {

      private final Throwable mFailure;

      private StateFailed(@NotNull final Throwable failure) {
        mFailure = failure;
      }

      @Nullable
      @Override
      Runnable add() {
        mLogger.wrn("Loop has already failed with reason: %s", mFailure);
        throw FailureException.wrap(new IllegalStateException("loop has already complete"));
      }

      @Nullable
      @Override
      Runnable addFailureSafe(@NotNull final Throwable failure) {
        mLogger.wrn("Loop has already failed with reason: %s", mFailure);
        return null;
      }

      @Nullable
      @Override
      Runnable cancel(@NotNull final Throwable failure) {
        return null;
      }

      @Nullable
      @Override
      Runnable failSafe(@NotNull final Throwable failure) {
        mLogger.wrn(failure, "Suppressed failure");
        return null;
      }

      @Nullable
      @Override
      Runnable set() {
        mLogger.wrn("Loop has already failed with reason: %s", mFailure);
        throw new IllegalStateException("loop has already complete");
      }
    }

    private class StatePrepending extends StateEvaluating implements Runnable {

      private final List<SimpleState<V>> mStates;

      private StatePrepending(@NotNull final List<SimpleState<V>> states) {
        mStates = states;
      }

      @Nullable
      @Override
      Runnable add() {
        mInnerState = new StateEvaluating();
        return this;
      }

      public void run() {
        try {
          for (final SimpleState<V> state : mStates) {
            state.addTo(LoopChain.this);
          }

        } catch (final CancellationException e) {
          mLogger.wrn(e, "Loop has been cancelled");
          failSafe(e);

        } catch (final Throwable t) {
          mLogger.wrn(t, "Error while processing values");
          failSafe(t);
        }
      }

      @Nullable
      @Override
      Runnable failSafe(@NotNull final Throwable failure) {
        mInnerState = new StateEvaluating();
        return new Runnable() {

          public void run() {
            StatePrepending.this.run();
            synchronized (mMutex) {
              mInnerState = new StateFailed(failure);
            }
          }
        };
      }

      @Nullable
      @Override
      Runnable set() {
        mInnerState = new StateEvaluating();
        return new Runnable() {

          public void run() {
            StatePrepending.this.run();
            synchronized (mMutex) {
              mInnerState = new StateSet();
            }
          }
        };
      }
    }

    private class StateSet extends StateEvaluating {

      @Nullable
      @Override
      Runnable cancel(@NotNull final Throwable failure) {
        return null;
      }

      @Nullable
      @Override
      Runnable add() {
        mLogger.wrn("Loop has already complete");
        throw new IllegalStateException("loop has already complete");
      }

      @Nullable
      @Override
      Runnable addFailureSafe(@NotNull final Throwable failure) {
        mLogger.wrn("Loop has already complete");
        return null;
      }

      @Nullable
      @Override
      Runnable failSafe(@NotNull final Throwable failure) {
        mLogger.wrn(failure, "Suppressed rejection");
        return null;
      }

      @Nullable
      @Override
      Runnable set() {
        mLogger.wrn("Loop has already complete");
        throw new IllegalStateException("loop has already complete");
      }
    }

    @NotNull
    public final AsyncEvaluations<V> addFailure(@NotNull final Throwable failure) {
      ConstantConditions.notNull("failure", failure);
      final Runnable command;
      synchronized (mMutex) {
        command = mInnerState.add();
      }

      if (command != null) {
        command.run();
        addFailure(mNext, failure);
      }

      return this;
    }

    @NotNull
    public AsyncEvaluations<V> addFailures(@Nullable final Iterable<? extends Throwable> failures) {
      final Runnable command;
      synchronized (mMutex) {
        command = mInnerState.add();
      }

      if (command != null) {
        command.run();
        if (failures != null) {
          if (Iterables.contains(failures, null)) {
            throw new NullPointerException("failures cannot contain null objects");
          }

          addFailures(mNext, failures);
        }
      }

      return this;
    }

    @NotNull
    public final AsyncEvaluations<V> addValue(final V value) {
      final Runnable command;
      synchronized (mMutex) {
        command = mInnerState.add();
      }

      if (command != null) {
        command.run();
        addValue(mNext, value);
      }

      return this;
    }

    @NotNull
    public final AsyncEvaluations<V> addValues(@Nullable final Iterable<? extends V> values) {
      final Runnable command;
      synchronized (mMutex) {
        command = mInnerState.add();
      }

      if (command != null) {
        command.run();
        if (values != null) {
          addValues(mNext, values);
        }
      }

      return this;
    }

    public final void set() {
      final Runnable command;
      synchronized (mMutex) {
        command = mInnerState.set();
      }

      if (command != null) {
        command.run();
        set(mNext);
      }
    }
  }

  private static class LoopProxy extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private LoopProxy(final Observer<AsyncEvaluations<?>> observer, final boolean isEvaluated,
        final String loggerName, final List<LoopChain<?, ?>> chains) {
      super(proxy(observer), isEvaluated, loggerName, chains);
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        final ChainHead<Object> head = new ChainHead<Object>();
        LoopChain<?, ?> tail = head;
        for (final LoopChain<?, ?> chain : (List<LoopChain<?, ?>>) args[3]) {
          ((LoopChain<?, Object>) tail).setNext((LoopChain<Object, ?>) chain);
          tail = chain;
        }

        return new DefaultAsyncLoop<Object>((Observer<AsyncEvaluations<?>>) args[0],
            (Boolean) args[1], (String) args[2], head, (LoopChain<?, Object>) tail);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }

  private static class StateGenerator<V> implements AsyncGenerator<AsyncState<V>> {

    private final DefaultAsyncLoop<V> mLoop;

    private final TimeUnit mTimeUnit;

    private final long mTimeout;

    private StateGenerator(@NotNull final DefaultAsyncLoop<V> loop, final long timeout,
        @NotNull final TimeUnit timeUnit) {
      mTimeUnit = ConstantConditions.notNull("timeUnit", timeUnit);
      mTimeout = timeout;
      mLoop = loop;
    }

    @NotNull
    public AsyncIterator<AsyncState<V>> iterator() {
      return new StateIterator<V>(mLoop.evaluate(), mTimeout, mTimeUnit);
    }
  }

  private static class StateIterator<V> implements AsyncIterator<AsyncState<V>> {

    private final DefaultAsyncLoop<V> mLoop;

    private final TimeUnit mOriginalTimeUnit;

    private final long mOriginalTimeout;

    private final TimeUnit mTimeUnit;

    private final long mTimeout;

    private StateIterator(@NotNull final DefaultAsyncLoop<V> loop, final long timeout,
        @NotNull final TimeUnit timeUnit) {
      mOriginalTimeout = timeout;
      mOriginalTimeUnit = timeUnit;
      if (timeout >= 0) {
        mTimeUnit = ((timeUnit.toNanos(timeout) % TimeUnit.MILLISECONDS.toNanos(1)) == 0)
            ? TimeUnit.MILLISECONDS : TimeUnit.NANOSECONDS;
        mTimeout = mTimeUnit.convert(timeout, timeUnit) + TimeUnits.currentTimeIn(mTimeUnit);

      } else {
        mTimeUnit = TimeUnit.MILLISECONDS;
        mTimeout = -1;
      }

      mLoop = loop;
    }

    public boolean hasNext() {
      final DefaultAsyncLoop<V> loop = mLoop;
      final ChainHead<?> head = loop.mHead;
      final long timeout = remainingTime();
      loop.deadLockWarning(timeout);
      synchronized (loop.mMutex) {
        try {
          if (TimeUnits.waitUntil(loop.mMutex, new Condition() {

            public boolean isTrue() {
              loop.checkFinal();
              return (!head.getStates().isEmpty() || head.getState().isDone());
            }
          }, timeout, mTimeUnit)) {
            return !head.getStates().isEmpty();
          }

        } catch (final InterruptedException e) {
          throw new RuntimeInterruptedException(e);
        }
      }

      throw timeoutException();
    }

    @NotNull
    public List<AsyncState<V>> next(final int maxCount) {
      final ArrayList<AsyncState<V>> outputs = new ArrayList<AsyncState<V>>();
      readNext(outputs, maxCount);
      return outputs;
    }

    private long remainingTime() {
      final long timeout = mTimeout;
      if (timeout < 0) {
        return -1;
      }

      final long remainingTime = timeout - TimeUnits.currentTimeIn(mTimeUnit);
      if (remainingTime < 0) {
        throw timeoutException();
      }

      return remainingTime;
    }

    @NotNull
    private RuntimeTimeoutException timeoutException() {
      return new RuntimeTimeoutException(
          "timeout while iterating promise resolutions [" + mOriginalTimeout + " "
              + mOriginalTimeUnit + "]");
    }

    public int readNext(@NotNull final Collection<? super AsyncState<V>> collection,
        final int maxCount) {
      return readNext(collection, maxCount, -1, TimeUnit.MILLISECONDS);
    }

    @NotNull
    public List<AsyncState<V>> next(final int maxCount, final long timeout,
        @NotNull final TimeUnit timeUnit) {
      final ArrayList<AsyncState<V>> outputs = new ArrayList<AsyncState<V>>();
      readNext(outputs, maxCount, timeout, timeUnit);
      return outputs;
    }

    public int readNext(@NotNull final Collection<? super AsyncState<V>> collection,
        final int maxCount, final long timeout, @NotNull final TimeUnit timeUnit) {
      final DefaultAsyncLoop<V> loop = mLoop;
      final ChainHead<?> head = loop.mHead;
      final TimeUnit waitTimeUnit = mTimeUnit;
      final long waitTimeout = Math.min(remainingTime(),
          (timeout >= 0) ? waitTimeUnit.convert(timeout, timeUnit) : Long.MAX_VALUE);
      loop.deadLockWarning(waitTimeout);
      synchronized (loop.mMutex) {
        try {
          if (TimeUnits.waitUntil(loop.mMutex, new Condition() {

            public boolean isTrue() {
              loop.checkFinal();
              return ((head.getStates().size() >= maxCount) || head.getState().isDone());
            }
          }, waitTimeout, waitTimeUnit)) {
            int count = 0;
            @SuppressWarnings("unchecked") final DoubleQueue<SimpleState<V>> states =
                ((ChainHead<V>) head).getStates();
            while (!states.isEmpty() && (count < maxCount)) {
              collection.add(states.removeFirst());
              ++count;
            }

            return count;
          }

        } catch (final InterruptedException e) {
          throw new RuntimeInterruptedException(e);
        }
      }

      throw timeoutException();
    }

    public AsyncState<V> next() {
      final DefaultAsyncLoop<V> loop = mLoop;
      final ChainHead<?> head = loop.mHead;
      final long timeout = remainingTime();
      loop.deadLockWarning(timeout);
      synchronized (loop.mMutex) {
        try {
          if (TimeUnits.waitUntil(loop.mMutex, new Condition() {

            public boolean isTrue() {
              loop.checkFinal();
              return (!head.getStates().isEmpty() || head.getState().isDone());
            }
          }, timeout, mTimeUnit)) {
            @SuppressWarnings("unchecked") final DoubleQueue<SimpleState<V>> states =
                ((ChainHead<V>) head).getStates();
            if (states.isEmpty()) {
              throw new NoSuchElementException();
            }

            return states.removeFirst();
          }

        } catch (final InterruptedException e) {
          throw new RuntimeInterruptedException(e);
        }
      }

      throw timeoutException();
    }

    public void remove() {
      ConstantConditions.unsupported();
    }
  }

  private static class ValueGenerator<V> implements AsyncGenerator<V> {

    private final DefaultAsyncLoop<V> mLoop;

    private final TimeUnit mTimeUnit;

    private final long mTimeout;

    private ValueGenerator(@NotNull final DefaultAsyncLoop<V> loop, final long timeout,
        @NotNull final TimeUnit timeUnit) {
      mTimeUnit = ConstantConditions.notNull("timeUnit", timeUnit);
      mTimeout = timeout;
      mLoop = loop;
    }

    @NotNull
    public AsyncIterator<V> iterator() {
      return new ValueIterator<V>(mLoop.evaluate(), mTimeout, mTimeUnit);
    }
  }

  private static class ValueIterator<V> implements AsyncIterator<V> {

    private final DefaultAsyncLoop<V> mLoop;

    private final TimeUnit mOriginalTimeUnit;

    private final long mOriginalTimeout;

    private final TimeUnit mTimeUnit;

    private final long mTimeout;

    private ValueIterator(@NotNull final DefaultAsyncLoop<V> loop, final long timeout,
        @NotNull final TimeUnit timeUnit) {
      mOriginalTimeout = timeout;
      mOriginalTimeUnit = timeUnit;
      if (timeout >= 0) {
        mTimeUnit = ((timeUnit.toNanos(timeout) % TimeUnit.MILLISECONDS.toNanos(1)) == 0)
            ? TimeUnit.MILLISECONDS : TimeUnit.NANOSECONDS;
        mTimeout = mTimeUnit.convert(timeout, timeUnit) + TimeUnits.currentTimeIn(mTimeUnit);

      } else {
        mTimeUnit = TimeUnit.MILLISECONDS;
        mTimeout = -1;
      }

      mLoop = loop;
    }

    private long remainingTime() {
      final long timeout = mTimeout;
      if (timeout < 0) {
        return -1;
      }

      final long remainingTime = timeout - TimeUnits.currentTimeIn(mTimeUnit);
      if (remainingTime < 0) {
        throw timeoutException();
      }

      return remainingTime;
    }

    @NotNull
    private RuntimeTimeoutException timeoutException() {
      return new RuntimeTimeoutException(
          "timeout while iterating promise resolutions [" + mOriginalTimeout + " "
              + mOriginalTimeUnit + "]");
    }

    public boolean hasNext() {
      final DefaultAsyncLoop<V> loop = mLoop;
      final ChainHead<?> head = loop.mHead;
      final long timeout = remainingTime();
      loop.deadLockWarning(timeout);
      synchronized (loop.mMutex) {
        try {
          if (TimeUnits.waitUntil(loop.mMutex, new Condition() {

            public boolean isTrue() {
              loop.checkFinal();
              return (!head.getStates().isEmpty() || head.getState().isDone());
            }
          }, timeout, mTimeUnit)) {
            if (!head.getStates().isEmpty()) {
              final SimpleState<?> state = head.getStates().peekFirst();
              if (state.isFailed()) {
                throw FailureException.wrap(state.failure());
              }

              return true;
            }

            return false;
          }

        } catch (final InterruptedException e) {
          throw new RuntimeInterruptedException(e);
        }
      }

      throw timeoutException();
    }

    public V next() {
      final DefaultAsyncLoop<V> loop = mLoop;
      final ChainHead<?> head = loop.mHead;
      final long timeout = remainingTime();
      loop.deadLockWarning(timeout);
      synchronized (loop.mMutex) {
        try {
          if (TimeUnits.waitUntil(loop.mMutex, new Condition() {

            public boolean isTrue() {
              loop.checkFinal();
              return (!head.getStates().isEmpty() || head.getState().isDone());
            }
          }, timeout, mTimeUnit)) {
            @SuppressWarnings("unchecked") final DoubleQueue<SimpleState<V>> states =
                ((ChainHead<V>) head).getStates();
            if (states.isEmpty()) {
              throw new NoSuchElementException();
            }

            final SimpleState<V> state = states.removeFirst();
            if (state.isFailed()) {
              throw FailureException.wrap(state.failure());
            }

            return state.value();
          }

        } catch (final InterruptedException e) {
          throw new RuntimeInterruptedException(e);
        }
      }

      throw timeoutException();
    }

    public void remove() {
      ConstantConditions.unsupported();
    }

    @NotNull
    public List<V> next(final int maxCount) {
      final ArrayList<V> outputs = new ArrayList<V>();
      readNext(outputs, maxCount);
      return outputs;
    }

    public int readNext(@NotNull final Collection<? super V> collection, final int maxCount) {
      return readNext(collection, maxCount, -1, TimeUnit.MILLISECONDS);
    }

    @NotNull
    public List<V> next(final int maxCount, final long timeout, @NotNull final TimeUnit timeUnit) {
      final ArrayList<V> outputs = new ArrayList<V>();
      readNext(outputs, maxCount, timeout, timeUnit);
      return outputs;
    }

    public int readNext(@NotNull final Collection<? super V> collection, final int maxCount,
        final long timeout, @NotNull final TimeUnit timeUnit) {
      final DefaultAsyncLoop<V> loop = mLoop;
      final ChainHead<?> head = loop.mHead;
      final TimeUnit waitTimeUnit = mTimeUnit;
      final long waitTimeout = Math.min(remainingTime(),
          (timeout >= 0) ? waitTimeUnit.convert(timeout, timeUnit) : Long.MAX_VALUE);
      loop.deadLockWarning(waitTimeout);
      synchronized (loop.mMutex) {
        try {
          if (TimeUnits.waitUntil(loop.mMutex, new Condition() {

            public boolean isTrue() {
              loop.checkFinal();
              return ((head.getStates().size() >= maxCount) || head.getState().isDone());
            }
          }, waitTimeout, waitTimeUnit)) {
            int count = 0;
            @SuppressWarnings("unchecked") final DoubleQueue<SimpleState<V>> states =
                ((ChainHead<V>) head).getStates();
            while (!states.isEmpty() && (count < maxCount)) {
              final SimpleState<V> state = states.removeFirst();
              if (state.isFailed()) {
                throw FailureException.wrap(state.failure());
              }

              collection.add(state.value());
              ++count;
            }

            return count;
          }

        } catch (final InterruptedException e) {
          throw new RuntimeInterruptedException(e);
        }
      }

      throw timeoutException();
    }
  }

  private class ChainTail extends LoopChain<V, Object> {

    private final ChainHead<V> mHead;

    private ChainTail(@NotNull final ChainHead<V> head) {
      mHead = head;
      setLogger(mLogger);
    }

    @Override
    void addFailure(final LoopChain<Object, ?> next, @NotNull final Throwable failure) {
      final LoopChain<V, ?> chain;
      synchronized (mMutex) {
        try {
          mState = StatementState.Failed;
          if ((chain = mChain) == null) {
            mHead.innerAddFailure(failure);
            return;
          }

        } finally {
          mMutex.notifyAll();
        }
      }

      chain.addFailure(failure);
    }

    @Override
    void addFailures(final LoopChain<Object, ?> next,
        @NotNull final Iterable<? extends Throwable> failures) {
      final LoopChain<V, ?> chain;
      synchronized (mMutex) {
        try {
          if ((chain = mChain) == null) {
            @SuppressWarnings("UnnecessaryLocalVariable") final ChainHead<V> head = mHead;
            for (final Throwable failure : failures) {
              head.innerAddFailure(failure);
            }

            return;
          }

        } finally {
          mMutex.notifyAll();
        }
      }

      chain.addFailures(failures);
    }

    @Override
    void set(final LoopChain<Object, ?> next) {
      final LoopChain<V, ?> chain;
      synchronized (mMutex) {
        try {
          if (mState == StatementState.Evaluating) {
            mState = StatementState.Set;
          }

          if ((chain = mChain) == null) {
            mHead.innerSet();
            return;
          }

        } finally {
          mMutex.notifyAll();
        }
      }

      chain.set();
    }

    @Override
    boolean isTail() {
      return true;
    }

    @Override
    void addValue(final LoopChain<Object, ?> next, final V value) {
      final LoopChain<V, ?> chain;
      synchronized (mMutex) {
        try {
          if ((chain = mChain) == null) {
            mHead.innerAddValue(value);
            return;
          }

        } finally {
          mMutex.notifyAll();
        }
      }

      chain.addValue(value);
    }

    @Override
    void addValues(final LoopChain<Object, ?> next, @NotNull final Iterable<? extends V> values) {
      final LoopChain<V, ?> chain;
      synchronized (mMutex) {
        try {
          if ((chain = mChain) == null) {
            @SuppressWarnings("UnnecessaryLocalVariable") final ChainHead<V> head = mHead;
            for (final V value : values) {
              head.innerAddValue(value);
            }

            return;
          }

        } finally {
          mMutex.notifyAll();
        }
      }

      chain.addValues(values);
    }

    @NotNull
    @Override
    LoopChain<V, Object> copy() {
      return ConstantConditions.unsupported();
    }
  }
}

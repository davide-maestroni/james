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

package dm.jail;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import dm.jail.async.Action;
import dm.jail.async.AsyncLoop;
import dm.jail.async.AsyncResult;
import dm.jail.async.AsyncResultCollection;
import dm.jail.async.AsyncState;
import dm.jail.async.AsyncStatement;
import dm.jail.async.FailureException;
import dm.jail.async.Mapper;
import dm.jail.async.Observer;
import dm.jail.async.Provider;
import dm.jail.async.RuntimeInterruptedException;
import dm.jail.async.RuntimeTimeoutException;
import dm.jail.async.SimpleState;
import dm.jail.config.BuildConfig;
import dm.jail.executor.ScheduledExecutor;
import dm.jail.executor.ScheduledExecutors;
import dm.jail.log.LogPrinter;
import dm.jail.log.LogPrinter.Level;
import dm.jail.log.Logger;
import dm.jail.util.ConstantConditions;
import dm.jail.util.DoubleQueue;
import dm.jail.util.Iterables;
import dm.jail.util.SerializableProxy;
import dm.jail.util.Threads;

/**
 * Created by davide-maestroni on 02/01/2018.
 */
class DefaultAsyncLoop<V> implements AsyncLoop<V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final ScheduledExecutor mExecutor;

  private final CopyOnWriteArrayList<AsyncLoop<?>> mForked =
      new CopyOnWriteArrayList<AsyncLoop<?>>();

  private final ChainHead<?> mHead;

  private final boolean mIsEvaluated;

  private final boolean mIsFork;

  private final Logger mLogger;

  private final Object mMutex;

  private final Observer<AsyncResultCollection<?>> mObserver;

  private final LoopChain<?, V> mTail;

  private LoopChain<V, ?> mChain;

  private StatementState mState = StatementState.Evaluating;

  DefaultAsyncLoop(@NotNull final Observer<? super AsyncResultCollection<V>> observer,
      final boolean isEvaluated, @Nullable final LogPrinter printer, @Nullable final Level level) {
    this(ConstantConditions.notNull("observer", observer), isEvaluated,
        ScheduledExecutors.immediateExecutor(), printer, level);
  }

  @SuppressWarnings("unchecked")
  private DefaultAsyncLoop(@NotNull final Observer<? super AsyncResultCollection<V>> observer,
      final boolean isEvaluated, @NotNull final ScheduledExecutor executor,
      @Nullable final LogPrinter printer, @Nullable final Level level) {
    // forking
    mObserver = (Observer<AsyncResultCollection<?>>) observer;
    mExecutor = executor;
    mIsEvaluated = isEvaluated;
    mIsFork = (observer instanceof ForkObserver);
    mLogger = Logger.newLogger(printer, level, this);
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
  private DefaultAsyncLoop(@NotNull final Observer<AsyncResultCollection<?>> observer,
      final boolean isEvaluated, @NotNull final ScheduledExecutor executor,
      @Nullable final LogPrinter printer, @Nullable final Level level,
      @NotNull final ChainHead<?> head, @NotNull final LoopChain<?, V> tail) {
    // serialization
    mObserver = observer;
    mExecutor = executor;
    mIsEvaluated = isEvaluated;
    mIsFork = (observer instanceof ForkObserver);
    mLogger = Logger.newLogger(printer, level, this);
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
  private DefaultAsyncLoop(@NotNull final Observer<AsyncResultCollection<?>> observer,
      final boolean isEvaluated, @NotNull final ScheduledExecutor executor,
      @NotNull final Logger logger, @NotNull final ChainHead<?> head,
      @NotNull final LoopChain<?, V> tail, final boolean observe) {
    // copy/chain
    mObserver = observer;
    mExecutor = executor;
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

  @NotNull
  public <S> AsyncLoop<V> backoffOn(@NotNull final ScheduledExecutor executor,
      @NotNull final Backoffer<S, V> backoffer) {
    return null;
  }

  @NotNull
  public <S> AsyncLoop<V> backoffOn(@NotNull final ScheduledExecutor executor,
      @Nullable final Provider<S> init,
      @Nullable final YieldUpdater<S, ? super V, ? super PendingState> value,
      @Nullable final YieldUpdater<S, ? super Throwable, ? super PendingState> failure,
      @Nullable final YieldCompleter<S, ? super PendingState> done) {
    return null;
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public AsyncLoop<V> evaluate() {
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

    return new DefaultAsyncLoop<V>(renewObserver(), true, mExecutor, logger, newHead,
        (LoopChain<?, V>) newTail, true);
  }

  @NotNull
  public AsyncLoop<V> evaluated() {
    return (mIsEvaluated) ? this : evaluate();
  }

  @NotNull
  public AsyncLoop<V> on(@NotNull final ScheduledExecutor executor) {
    final ScheduledExecutor throttledExecutor = ScheduledExecutors.withThrottling(executor, 1);
    return chain(new ChainLoopHandler<V, V>(new ExecutorLoopHandler<V>(throttledExecutor)),
        mExecutor, throttledExecutor);
  }

  @NotNull
  public <R> AsyncLoop<R> forEach(@NotNull final Mapper<? super V, R> mapper) {
    return chain(new ThenLoopHandler<V, R>(mapper));
  }

  @NotNull
  public AsyncLoop<V> forEachCatch(@NotNull final Mapper<? super Throwable, V> mapper,
      @Nullable Class<?>... exceptionTypes) {
    return chain(new ElseCatchLoopHandler<V>(mapper, Asyncs.cloneExceptionTypes(exceptionTypes)));
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
  public AsyncLoop<V> forEachElseDo(@NotNull final Observer<? super Throwable> observer,
      @Nullable Class<?>... exceptionTypes) {
    return chain(new ElseDoLoopHandler<V>(observer, Asyncs.cloneExceptionTypes(exceptionTypes)));
  }

  @NotNull
  public AsyncLoop<V> forEachElseIf(
      @NotNull final Mapper<? super Throwable, ? extends AsyncStatement<? extends V>> mapper,
      @Nullable Class<?>... exceptionTypes) {
    return chain(new ElseIfStatementHandler<V>(mapper, Asyncs.cloneExceptionTypes(exceptionTypes)));
  }

  @NotNull
  public AsyncLoop<V> forEachElseLoop(
      @NotNull final Mapper<? super Throwable, ? extends Iterable<? extends V>> mapper,
      @Nullable Class<?>... exceptionTypes) {
    return chain(new ElseLoopLoopHandler<V>(mapper, Asyncs.cloneExceptionTypes(exceptionTypes)));
  }

  @NotNull
  public AsyncLoop<V> forEachElseLoopIf(
      @NotNull final Mapper<? super Throwable, ? extends AsyncLoop<? extends V>> mapper,
      @Nullable Class<?>... exceptionTypes) {
    return chain(
        new ElseLoopIfStatementHandler<V>(mapper, Asyncs.cloneExceptionTypes(exceptionTypes)));
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
  public <R> AsyncLoop<R> forEachTry(
      @NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final Mapper<? super V, R> mapper) {
    final Logger logger = mLogger;
    return chain(new TryStatementHandler<V, R>(closeable, new ThenStatementHandler<V, R>(mapper),
        logger.getLogPrinter(), logger.getLogLevel()));
  }

  @NotNull
  public AsyncLoop<V> forEachTryDo(@NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final Observer<? super V> observer) {
    final Logger logger = mLogger;
    return chain(
        new TryStatementHandler<V, V>(closeable, new ThenDoStatementHandler<V, V>(observer),
            logger.getLogPrinter(), logger.getLogLevel()));
  }

  @NotNull
  public <R> AsyncLoop<R> forEachTryIf(
      @NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final Mapper<? super V, ? extends AsyncStatement<R>> mapper) {
    final Logger logger = mLogger;
    return chain(new TryIfStatementHandler<V, R>(closeable, mapper, logger.getLogPrinter(),
        logger.getLogLevel()));
  }

  @NotNull
  public <R> AsyncLoop<R> forEachTryLoop(
      @NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final Mapper<? super V, ? extends Iterable<R>> mapper) {
    final Logger logger = mLogger;
    return chain(
        new TryStatementLoopHandler<V, R>(closeable, new ThenLoopStatementHandler<V, R>(mapper),
            logger.getLogPrinter(), logger.getLogLevel()));
  }

  @NotNull
  public <R> AsyncLoop<R> forEachTryLoopIf(
      @NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final Mapper<? super V, ? extends AsyncLoop<R>> mapper) {
    final Logger logger = mLogger;
    return chain(
        new TryStatementLoopHandler<V, R>(closeable, new ThenLoopIfStatementHandler<V, R>(mapper),
            logger.getLogPrinter(), logger.getLogLevel()));
  }

  @NotNull
  public <S> AsyncLoop<V> forkLoop(
      @NotNull final Forker<S, ? super AsyncLoop<V>, ? super V, ? super AsyncResultCollection<V>>
          forker) {
    final Logger logger = mLogger;
    return new DefaultAsyncLoop<V>(new ForkObserver<S, V>(this, forker), mIsEvaluated, mExecutor,
        logger.getLogPrinter(), logger.getLogLevel());
  }

  @NotNull
  public <S> AsyncLoop<V> forkLoop(@Nullable final Mapper<? super AsyncLoop<V>, S> init,
      @Nullable final ForkUpdater<S, ? super AsyncLoop<V>, ? super V> value,
      @Nullable final ForkUpdater<S, ? super AsyncLoop<V>, ? super Throwable> failure,
      @Nullable final ForkCompleter<S, ? super AsyncLoop<V>> done,
      @Nullable final ForkUpdater<S, ? super AsyncLoop<V>, ? super AsyncResultCollection<V>>
          statement) {
    return forkLoop(new ComposedLoopForker<S, V>(init, value, failure, done, statement));
  }

  @NotNull
  public AsyncLoop<V> orderedForEachElseIf(
      @NotNull final Mapper<? super Throwable, ? extends AsyncStatement<? extends V>> mapper,
      @Nullable Class<?>... exceptionTypes) {
    return chainOrdered(
        new ElseIfStatementHandler<V>(mapper, Asyncs.cloneExceptionTypes(exceptionTypes)));
  }

  @NotNull
  public AsyncLoop<V> orderedForEachElseLoopIf(
      @NotNull final Mapper<? super Throwable, ? extends AsyncLoop<? extends V>> mapper,
      @Nullable Class<?>... exceptionTypes) {
    return chainOrdered(
        new ElseLoopIfStatementHandler<V>(mapper, Asyncs.cloneExceptionTypes(exceptionTypes)));
  }

  @NotNull
  public <R> AsyncLoop<R> orderedForEachIf(
      @NotNull final Mapper<? super V, ? extends AsyncStatement<R>> mapper) {
    return chainOrdered(new ThenIfStatementHandler<V, R>(mapper));
  }

  @NotNull
  public <R> AsyncLoop<R> orderedForEachLoopIf(
      @NotNull final Mapper<? super V, ? extends AsyncLoop<R>> mapper) {
    return chainOrdered(new ThenLoopIfStatementHandler<V, R>(mapper));
  }

  @NotNull
  public <R> AsyncLoop<R> orderedForEachTryIf(
      @NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final Mapper<? super V, ? extends AsyncStatement<R>> mapper) {
    final Logger logger = mLogger;
    return chainOrdered(new TryIfStatementHandler<V, R>(closeable, mapper, logger.getLogPrinter(),
        logger.getLogLevel()));
  }

  @NotNull
  public <R> AsyncLoop<R> orderedForEachTryLoopIf(
      @NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final Mapper<? super V, ? extends AsyncLoop<R>> mapper) {
    final Logger logger = mLogger;
    return chainOrdered(
        new TryStatementLoopHandler<V, R>(closeable, new ThenLoopIfStatementHandler<V, R>(mapper),
            logger.getLogPrinter(), logger.getLogLevel()));
  }

  @NotNull
  public AsyncLoop<V> orderedOn(@NotNull final ScheduledExecutor executor) {
    return null;
  }

  @NotNull
  public <R, S> AsyncLoop<R> orderedTryYield(
      @NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final Yielder<S, ? super V, R> yielder) {
    return null;
  }

  @NotNull
  public <R, S> AsyncLoop<R> orderedTryYield(
      @NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @Nullable final Provider<S> init, @Nullable final Mapper<S, ? extends Boolean> loop,
      @Nullable final YieldUpdater<S, ? super V, ? super YieldResults<R>> value,
      @Nullable final YieldUpdater<S, ? super Throwable, ? super YieldResults<R>> failure,
      @Nullable final YieldCompleter<S, ? super YieldResults<R>> done) {
    return null;
  }

  @NotNull
  public <R, S> AsyncLoop<R> orderedYield(@NotNull final Yielder<S, ? super V, R> yielder) {
    return null;
  }

  @NotNull
  public <R, S> AsyncLoop<R> orderedYield(@Nullable final Provider<S> init,
      @Nullable final Mapper<S, ? extends Boolean> loop,
      @Nullable final YieldUpdater<S, ? super V, ? super YieldResults<R>> value,
      @Nullable final YieldUpdater<S, ? super Throwable, ? super YieldResults<R>> failure,
      @Nullable final YieldCompleter<S, ? super YieldResults<R>> done) {
    return null;
  }

  @NotNull
  public AsyncLoop<V> parallelOn(@NotNull final ScheduledExecutor executor) {
    return null;
  }

  @NotNull
  public Generator<AsyncState<V>> stateGenerator() {
    return null;
  }

  @NotNull
  public Generator<AsyncState<V>> stateGenerator(final long timeout,
      @NotNull final TimeUnit timeUnit) {
    return null;
  }

  public void to(@NotNull final AsyncResultCollection<? super V> results) {
    ConstantConditions.notNull("results", results);
    checkEvaluated();
    // TODO: 01/02/2018 yielder??
    forEach(new Mapper<V, Void>() {

      public Void apply(final V value) {
        results.addValue(value);
        return null;
      }
    }).forEachCatch(new Mapper<Throwable, Void>() {

      public Void apply(final Throwable failure) {
        results.addFailure(failure);
        return null;
      }
    }).whenDone(new Action() {

      public void perform() {
        results.set();
      }
    });
  }

  @NotNull
  public <R, S> AsyncLoop<R> tryYield(
      @NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final Yielder<S, ? super V, R> yielder) {
    return null;
  }

  @NotNull
  public <R, S> AsyncLoop<R> tryYield(
      @NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @Nullable final Provider<S> init, @Nullable final Mapper<S, ? extends Boolean> loop,
      @Nullable final YieldUpdater<S, ? super V, ? super YieldResults<R>> value,
      @Nullable final YieldUpdater<S, ? super Throwable, ? super YieldResults<R>> failure,
      @Nullable final YieldCompleter<S, ? super YieldResults<R>> done) {
    return null;
  }

  @NotNull
  public Generator<V> valueGenerator() {
    return null;
  }

  @NotNull
  public Generator<V> valueGenerator(final long timeout, @NotNull final TimeUnit timeUnit) {
    return null;
  }

  @NotNull
  public <R, S> AsyncLoop<R> yield(@NotNull final Yielder<S, ? super V, R> yielder) {
    return null;
  }

  @NotNull
  public <R, S> AsyncLoop<R> yield(@Nullable final Provider<S> init,
      @Nullable final Mapper<S, ? extends Boolean> loop,
      @Nullable final YieldUpdater<S, ? super V, ? super YieldResults<R>> value,
      @Nullable final YieldUpdater<S, ? super Throwable, ? super YieldResults<R>> failure,
      @Nullable final YieldCompleter<S, ? super YieldResults<R>> done) {
    return null;
  }

  public boolean cancel(final boolean mayInterruptIfRunning) {
    final Observer<? extends AsyncResultCollection<?>> observer = mObserver;
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

  @NotNull
  public AsyncStatement<Iterable<V>> elseCatch(
      @NotNull final Mapper<? super Throwable, ? extends Iterable<V>> mapper,
      @Nullable final Class<?>[] exceptionTypes) {
    return toStatement().elseCatch(mapper, exceptionTypes);
  }

  @NotNull
  public AsyncStatement<Iterable<V>> elseDo(@NotNull final Observer<? super Throwable> observer,
      @Nullable final Class<?>[] exceptionTypes) {
    return toStatement().elseDo(observer, exceptionTypes);
  }

  @NotNull
  public AsyncStatement<Iterable<V>> elseIf(
      @NotNull final Mapper<? super Throwable, ? extends AsyncStatement<? extends Iterable<V>>>
          mapper,
      @Nullable final Class<?>[] exceptionTypes) {
    return toStatement().elseIf(mapper, exceptionTypes);
  }

  @NotNull
  public <S> AsyncStatement<Iterable<V>> fork(
      @NotNull final Forker<S, ? super AsyncStatement<Iterable<V>>, ? super Iterable<V>, ? super
          AsyncResult<Iterable<V>>> forker) {
    return toStatement().fork(forker);
  }

  @NotNull
  public <S> AsyncStatement<Iterable<V>> fork(
      @Nullable final Mapper<? super AsyncStatement<Iterable<V>>, S> init,
      @Nullable final ForkUpdater<S, ? super AsyncStatement<Iterable<V>>, ? super Iterable<V>>
          value,
      @Nullable final ForkUpdater<S, ? super AsyncStatement<Iterable<V>>, ? super Throwable>
          failure,
      @Nullable final ForkCompleter<S, ? super AsyncStatement<Iterable<V>>> done,
      @Nullable final ForkUpdater<S, ? super AsyncStatement<Iterable<V>>, ? super
          AsyncResult<Iterable<V>>> statement) {
    return toStatement().fork(init, value, failure, done, statement);
  }

  @Nullable
  public FailureException getFailure() {
    return getFailure(-1, TimeUnit.MILLISECONDS);
  }

  @Nullable
  public FailureException getFailure(final long timeout, @NotNull final TimeUnit timeUnit) {
    return null;
  }

  public Iterable<V> getValue() {
    return getValue(-1, TimeUnit.MILLISECONDS);
  }

  public Iterable<V> getValue(final long timeout, @NotNull final TimeUnit timeUnit) {
    checkSupported();
    deadLockWarning(timeout);
    checkFinal();
    return null;
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

  public void waitDone() {
    waitDone(-1, TimeUnit.MILLISECONDS);
  }

  public boolean waitDone(final long timeout, @NotNull final TimeUnit timeUnit) {
    checkSupported();
    deadLockWarning(timeout);
    return false;
  }

  @NotNull
  public AsyncStatement<Iterable<V>> whenDone(@NotNull final Action action) {
    return toStatement().whenDone(action);
  }

  @NotNull
  public Throwable failure() {
    return null;
  }

  public boolean isCancelled() {
    return false;
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

  public void to(@NotNull final AsyncResult<? super Iterable<V>> result) {
    // TODO: 02/02/2018 yielder
  }

  public Iterable<V> value() {
    return null;
  }

  @NotNull
  private <R> AsyncLoop<R> chain(@NotNull final AsyncStatementHandler<V, R> handler) {
    return chain(new ChainStatementHandler<V, R>(handler), mExecutor, mExecutor);
  }

  @NotNull
  private <R> AsyncLoop<R> chain(@NotNull final AsyncStatementLoopHandler<V, R> handler) {
    return chain(new ChainStatementLoopHandler<V, R>(handler), mExecutor, mExecutor);
  }

  @NotNull
  private <R> AsyncLoop<R> chain(@NotNull final AsyncLoopHandler<V, R> handler) {
    return chain(new ChainLoopHandler<V, R>(handler), mExecutor, mExecutor);
  }

  @NotNull
  @SuppressWarnings("unchecked")
  private <R> AsyncLoop<R> chain(@NotNull final LoopChain<V, R> chain,
      @NotNull final ScheduledExecutor chainExecutor,
      @NotNull final ScheduledExecutor newExecutor) {
    final Logger logger = mLogger;
    final ChainHead<?> head = mHead;
    final Runnable chaining;
    final Observer<? extends AsyncResultCollection<?>> observer = mObserver;
    if (mIsFork) {
      final AsyncLoop<R> forked =
          new DefaultAsyncLoop<V>(((ForkObserver<?, V>) observer).newObserver(), mIsEvaluated,
              logger.getLogPrinter(), logger.getLogLevel()).chain(chain, chainExecutor,
              newExecutor);
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
        new DefaultAsyncLoop<R>((Observer<AsyncResultCollection<?>>) observer, mIsEvaluated,
            newExecutor, logger, head, chain, false);
    if (chaining != null) {
      chainExecutor.execute(chaining);
    }

    ((LoopChain<?, Object>) mTail).setNext((LoopChain<Object, V>) chain);
    return loop;
  }

  @NotNull
  private <R> AsyncLoop<R> chainOrdered(@NotNull final AsyncStatementHandler<V, R> handler) {
    return chain(new ChainStatementHandlerOrdered<V, R>(handler), mExecutor, mExecutor);
  }

  @NotNull
  private <R> AsyncLoop<R> chainOrdered(@NotNull final AsyncStatementLoopHandler<V, R> handler) {
    return chain(new ChainStatementLoopHandlerOrdered<V, R>(handler), mExecutor, mExecutor);
  }

  private void checkEvaluated() {
    if (!mIsEvaluated) {
      throw new UnsupportedOperationException("the loop has not been evaluated");
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
      throw new UnsupportedOperationException("the loop has been forked");
    }
  }

  private void deadLockWarning(final long waitTime) {
    if (waitTime == 0) {
      return;
    }

    final Logger logger = mLogger;
    if (logger.willPrint(Level.WARNING) && Threads.isOwnedThread()) {
      logger.wrn("ATTENTION: possible deadlock detected! Try to avoid waiting on managed threads");
    }
  }

  @NotNull
  private Observer<AsyncResultCollection<?>> renewObserver() {
    final Observer<AsyncResultCollection<?>> observer = mObserver;
    if (observer instanceof RenewableObserver) {
      return ((RenewableObserver<AsyncResultCollection<?>>) observer).renew();
    }

    return observer;
  }

  @NotNull
  private AsyncStatement<Iterable<V>> toStatement() {
    final Logger logger = mLogger;
    return new DefaultAsyncStatement<Iterable<V>>(new ToStatementObserver<V>(this), mIsEvaluated,
        logger.getLogPrinter(), logger.getLogLevel());
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

    final Logger logger = mLogger;
    return new LoopProxy(mObserver, mIsEvaluated, mExecutor, logger.getLogPrinter(),
        logger.getLogLevel(), chains);
  }

  private static class ChainForkObserver<S, V>
      implements RenewableObserver<AsyncResultCollection<V>>, Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final ForkObserver<S, V> mObserver;

    private ChainForkObserver(@NotNull final ForkObserver<S, V> observer) {
      mObserver = observer;
    }

    public void accept(final AsyncResultCollection<V> result) {
      mObserver.chain(result);
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
      Object readResolve() throws ObjectStreamException {
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

    private ArrayList<SimpleState<V>> mStates = new ArrayList<SimpleState<V>>();

    @Nullable
    public FailureException getException() {
      for (final SimpleState<V> state : mStates) {
        if (state.isFailed()) {
          return FailureException.wrap(state.failure());
        }
      }

      return null;
    }

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

    @NotNull
    Object getMutex() {
      return mMutex;
    }

    @NotNull
    StatementState getState() {
      return mState;
    }

    @NotNull
    DoubleQueue<V> getStates() {
      final DoubleQueue<V> states = new DoubleQueue<V>();
      for (final SimpleState<V> state : mStates) {
        if (state.isFailed()) {
          throw FailureException.wrap(state.failure());
        }

        states.add(state.value());
      }

      return states;
    }

    void innerAddFailure(@NotNull final Throwable failure) {
      getLogger().dbg("Loop failed with reason [%s => %s]: %s", StatementState.Evaluating,
          StatementState.Failed, failure);
      final ArrayList<SimpleState<V>> states = mStates;
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
    void addFailures(final LoopChain<V, ?> next, @NotNull  final Iterable<Throwable> failures) {
      next.addFailures(failures);
    }

    @Override
    void addValue(final LoopChain<V, ?> next, final V value) {
      next.addValue(value);
    }

    @Override
    void addValues(final LoopChain<V, ?> next, @NotNull final Iterable<V> values) {
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

    private final AsyncLoopHandler<V, R> mHandler;

    private final AtomicLong mPendingCount = new AtomicLong(1);

    private final ChainResults mResults = new ChainResults();

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
      Object readResolve() throws ObjectStreamException {
        try {
          return new ChainLoopHandler<V, R>(mHandler);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    private class ChainResults implements AsyncResultCollection<R> {

      private volatile LoopChain<R, ?> mNext;

      @NotNull
      public AsyncResultCollection<R> addFailure(@NotNull final Throwable failure) {
        mNext.addFailure(failure);
        return this;
      }

      @NotNull
      ChainResults withNext(final LoopChain<R, ?> next) {
        mNext = next;
        return this;
      }

      @NotNull
      public AsyncResultCollection<R> addFailures(@Nullable final Iterable<Throwable> failures) {
        mNext.addFailures(failures);
        return this;
      }

      @NotNull
      public AsyncResultCollection<R> addValue(final R value) {
        mNext.addValue(value);
        return this;
      }

      @NotNull
      public AsyncResultCollection<R> addValues(@Nullable final Iterable<R> values) {
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
        mHandler.addFailure(failure, mResults.withNext(next));

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
        mHandler.addValue(value, mResults.withNext(next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing value: %s", value);
        innerFailSafe(next, RuntimeInterruptedException.wrapIfInterrupt(t));
      }
    }

    @Override
    void addValues(final LoopChain<R, ?> next, @NotNull final Iterable<V> values) {
      mPendingCount.incrementAndGet();
      try {
        mHandler.addValues(values, mResults.withNext(next));

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
      return new ChainLoopHandler<V, R>(mHandler);
    }

    @Override
    void addFailures(final LoopChain<R, ?> next, @NotNull final Iterable<Throwable> failures) {
      mPendingCount.incrementAndGet();
      try {
        mHandler.addFailures(failures, mResults.withNext(next));

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
        mHandler.set(mResults.withNext(next));

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
      Object readResolve() throws ObjectStreamException {
        try {
          return new ChainLoopHandlerOrdered<V, R>(mHandler);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    private class ChainResults implements AsyncResultCollection<R> {

      private final LoopChain<R, ?> mNext;

      private final NestedQueue<SimpleState<R>> mQueue;

      private ChainResults(@NotNull final NestedQueue<SimpleState<R>> queue,
          final LoopChain<R, ?> next) {
        mQueue = queue;
        mNext = next;
      }

      @NotNull
      public AsyncResultCollection<R> addFailure(@NotNull final Throwable failure) {
        synchronized (mMutex) {
          mQueue.add(SimpleState.<R>ofFailure(failure));
        }

        flushQueue(mNext);
        return this;
      }

      @NotNull
      public AsyncResultCollection<R> addFailures(@Nullable final Iterable<Throwable> failures) {
        if (failures != null) {
          synchronized (mMutex) {
            final NestedQueue<SimpleState<R>> queue = mQueue;
            for (final Throwable failure : failures) {
              queue.add(SimpleState.<R>ofFailure(failure));
            }
          }

          flushQueue(mNext);
        }

        return this;
      }

      @NotNull
      public AsyncResultCollection<R> addValue(final R value) {
        synchronized (mMutex) {
          mQueue.add(SimpleState.ofValue(value));
        }

        flushQueue(mNext);
        return this;
      }

      @NotNull
      public AsyncResultCollection<R> addValues(@Nullable final Iterable<R> values) {
        if (values != null) {
          synchronized (mMutex) {
            final NestedQueue<SimpleState<R>> queue = mQueue;
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
        mHandler.addFailure(failure, new ChainResults(mQueue.addNested(), next));

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
        mHandler.addValue(value, new ChainResults(mQueue.addNested(), next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing value: %s", value);
        innerFailSafe(next, RuntimeInterruptedException.wrapIfInterrupt(t));
      }
    }

    @Override
    void addValues(final LoopChain<R, ?> next, @NotNull final Iterable<V> values) {
      mPendingCount.incrementAndGet();
      try {
        mHandler.addValues(values, new ChainResults(mQueue.addNested(), next));

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
      return new ChainLoopHandlerOrdered<V, R>(mHandler);
    }

    @Override
    void addFailures(final LoopChain<R, ?> next, @NotNull final Iterable<Throwable> failures) {
      mPendingCount.incrementAndGet();
      try {
        mHandler.addFailures(failures, new ChainResults(mQueue.addNested(), next));

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
        mHandler.set(new ChainResults(mQueue.addNested(), next));

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

    private final AsyncStatementHandler<V, R> mHandler;

    private final AtomicLong mPendingCount = new AtomicLong(1);

    private final ChainResult mResult = new ChainResult();

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
      Object readResolve() throws ObjectStreamException {
        try {
          return new ChainStatementHandler<V, R>(mHandler);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    private class ChainResult implements AsyncResult<R> {

      private volatile LoopChain<R, ?> mNext;

      public void fail(@NotNull final Throwable failure) {
        final LoopChain<R, ?> next = mNext;
        next.addFailureSafe(failure);
        innerSet(next);
      }

      @NotNull
      ChainResult withNext(final LoopChain<R, ?> next) {
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
        mHandler.failure(failure, mResult.withNext(next));

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
        mHandler.value(value, mResult.withNext(next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing value: %s", value);
        innerFailSafe(next, RuntimeInterruptedException.wrapIfInterrupt(t));
      }
    }

    @Override
    void addValues(final LoopChain<R, ?> next, @NotNull final Iterable<V> values) {
      mPendingCount.addAndGet(Iterables.size(values));
      final ChainResult result = mResult.withNext(next);
      for (final V value : values) {
        try {
          mHandler.value(value, result);

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
      return new ChainStatementHandler<V, R>(mHandler);
    }

    @Override
    void addFailures(final LoopChain<R, ?> next, @NotNull final Iterable<Throwable> failures) {
      mPendingCount.addAndGet(Iterables.size(failures));
      final ChainResult result = mResult.withNext(next);
      for (final Throwable failure : failures) {
        try {
          mHandler.failure(failure, result);

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
      Object readResolve() throws ObjectStreamException {
        try {
          return new ChainStatementHandlerOrdered<V, R>(mHandler);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    private class ChainResult implements AsyncResult<R> {

      private final LoopChain<R, ?> mNext;

      private final NestedQueue<SimpleState<R>> mQueue;

      private ChainResult(@NotNull final NestedQueue<SimpleState<R>> queue,
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
        mHandler.failure(failure, new ChainResult(mQueue.addNested(), next));

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
        mHandler.value(value, new ChainResult(mQueue.addNested(), next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing value: %s", value);
        innerFailSafe(next, RuntimeInterruptedException.wrapIfInterrupt(t));
      }
    }

    @Override
    void addValues(final LoopChain<R, ?> next, @NotNull final Iterable<V> values) {
      mPendingCount.addAndGet(Iterables.size(values));
      for (final V value : values) {
        try {
          mHandler.value(value, new ChainResult(mQueue.addNested(), next));

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
      return new ChainStatementHandlerOrdered<V, R>(mHandler);
    }

    @Override
    void addFailures(final LoopChain<R, ?> next, @NotNull final Iterable<Throwable> failures) {
      mPendingCount.addAndGet(Iterables.size(failures));
      for (final Throwable failure : failures) {
        try {
          mHandler.failure(failure, new ChainResult(mQueue.addNested(), next));

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

    private final AsyncStatementLoopHandler<V, R> mHandler;

    private final AtomicLong mPendingCount = new AtomicLong(1);

    private final ChainResults mResult = new ChainResults();

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
      Object readResolve() throws ObjectStreamException {
        try {
          return new ChainStatementLoopHandler<V, R>(mHandler);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    private class ChainResults implements AsyncResultCollection<R> {

      private volatile LoopChain<R, ?> mNext;

      @NotNull
      ChainResults withNext(final LoopChain<R, ?> next) {
        mNext = next;
        return this;
      }

      @NotNull
      public AsyncResultCollection<R> addFailure(@NotNull final Throwable failure) {
        mNext.addFailure(failure);
        return this;
      }

      @NotNull
      public AsyncResultCollection<R> addFailures(@Nullable final Iterable<Throwable> failures) {
        mNext.addFailures(failures);
        return this;
      }

      @NotNull
      public AsyncResultCollection<R> addValue(final R value) {
        mNext.addValue(value);
        return this;
      }

      @NotNull
      public AsyncResultCollection<R> addValues(@Nullable final Iterable<R> values) {
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
        mHandler.failure(failure, mResult.withNext(next));

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
        mHandler.value(value, mResult.withNext(next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing value: %s", value);
        innerFailSafe(next, RuntimeInterruptedException.wrapIfInterrupt(t));
      }
    }

    @Override
    void addValues(final LoopChain<R, ?> next, @NotNull final Iterable<V> values) {
      mPendingCount.addAndGet(Iterables.size(values));
      final ChainResults result = mResult.withNext(next);
      for (final V value : values) {
        try {
          mHandler.value(value, result);

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
      return new ChainStatementLoopHandler<V, R>(mHandler);
    }

    @Override
    void addFailures(final LoopChain<R, ?> next, @NotNull final Iterable<Throwable> failures) {
      mPendingCount.addAndGet(Iterables.size(failures));
      final ChainResults result = mResult.withNext(next);
      for (final Throwable failure : failures) {
        try {
          mHandler.failure(failure, result);

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
      Object readResolve() throws ObjectStreamException {
        try {
          return new ChainStatementLoopHandlerOrdered<V, R>(mHandler);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    private class ChainResults implements AsyncResultCollection<R> {

      private final LoopChain<R, ?> mNext;

      private final NestedQueue<SimpleState<R>> mQueue;

      private ChainResults(@NotNull final NestedQueue<SimpleState<R>> queue,
          final LoopChain<R, ?> next) {
        mQueue = queue;
        mNext = next;
      }

      @NotNull
      public AsyncResultCollection<R> addFailure(@NotNull final Throwable failure) {
        synchronized (mMutex) {
          mQueue.add(SimpleState.<R>ofFailure(failure));
        }

        flushQueue(mNext);
        return this;
      }

      @NotNull
      public AsyncResultCollection<R> addFailures(@Nullable final Iterable<Throwable> failures) {
        if (failures != null) {
          synchronized (mMutex) {
            final NestedQueue<SimpleState<R>> queue = mQueue;
            for (final Throwable failure : failures) {
              queue.add(SimpleState.<R>ofFailure(failure));
            }
          }

          flushQueue(mNext);
        }

        return this;
      }

      @NotNull
      public AsyncResultCollection<R> addValue(final R value) {
        synchronized (mMutex) {
          mQueue.add(SimpleState.ofValue(value));
        }

        flushQueue(mNext);
        return this;
      }

      @NotNull
      public AsyncResultCollection<R> addValues(@Nullable final Iterable<R> values) {
        if (values != null) {
          synchronized (mMutex) {
            final NestedQueue<SimpleState<R>> queue = mQueue;
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
        mHandler.failure(failure, new ChainResults(mQueue.addNested(), next));

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
        mHandler.value(value, new ChainResults(mQueue.addNested(), next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing value: %s", value);
        innerFailSafe(next, RuntimeInterruptedException.wrapIfInterrupt(t));
      }
    }

    @Override
    void addValues(final LoopChain<R, ?> next, @NotNull final Iterable<V> values) {
      mPendingCount.addAndGet(Iterables.size(values));
      for (final V value : values) {
        try {
          mHandler.value(value, new ChainResults(mQueue.addNested(), next));

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
      return new ChainStatementLoopHandlerOrdered<V, R>(mHandler);
    }

    @Override
    void addFailures(final LoopChain<R, ?> next, @NotNull final Iterable<Throwable> failures) {
      mPendingCount.addAndGet(Iterables.size(failures));
      for (final Throwable failure : failures) {
        try {
          mHandler.failure(failure, new ChainResults(mQueue.addNested(), next));

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

  private static class ChainYielder<S, V, R> extends LoopChain<V, R> implements Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final ScheduledExecutor mExecutor;

    private final YielderGenerator<R> mGenerator = new YielderGenerator<R>();

    private final Yielder<S, V, R> mYielder;

    private boolean mIsFailed;

    private boolean mIsInitialized;

    private S mStack;

    private ChainYielder(@NotNull final Yielder<S, V, R> yielder) {
      mYielder = ConstantConditions.notNull("yielder", yielder);
      mExecutor = ScheduledExecutors.withThrottling(ScheduledExecutors.immediateExecutor(), 1);
    }

    private static class YielderGenerator<V> implements YieldResults<V> {

      private final AtomicBoolean mIsSet = new AtomicBoolean();

      private volatile LoopChain<V, ?> mNext;

      @NotNull
      public YieldResults<V> yieldFailure(@NotNull final Throwable failure) {
        checkSet();
        return null;
      }

      @NotNull
      public YieldResults<V> yieldFailures(@Nullable final Iterable<Throwable> failures) {
        checkSet();
        return null;
      }

      @NotNull
      public YieldResults<V> yieldIf(@NotNull final AsyncStatement<V> statement) {
        checkSet();
        return null;
      }

      @NotNull
      public YieldResults<V> yieldLoop(@NotNull final AsyncLoop<V> loop) {
        checkSet();
        return null;
      }

      @NotNull
      public YieldResults<V> yieldValue(final V value) {
        checkSet();
        return null;
      }

      @NotNull
      public YieldResults<V> yieldValues(@Nullable final Iterable<V> value) {
        checkSet();
        return null;
      }

      private void checkSet() {
        if (mIsSet.get()) {
          throw new IllegalStateException("loop has already complete");
        }
      }

      private void set() {
        mIsSet.set(true);
      }

      @NotNull
      private YielderGenerator<V> withNext(final LoopChain<V, ?> next) {
        mNext = next;
        return this;
      }
    }

    void addFailure(final LoopChain<R, ?> next, @NotNull final Throwable failure) {
      mExecutor.execute(new Runnable() {

        public void run() {
          if (mIsFailed) {
            getLogger().wrn(failure, "Suppressed failure");
            return;
          }

          try {
            final Yielder<S, V, R> yielder = mYielder;
            if (!mIsInitialized) {
              mIsInitialized = true;
              mStack = yielder.init();
            }

            mStack = yielder.failure(mStack, failure, mGenerator.withNext(next));

          } catch (final CancellationException e) {
            getLogger().wrn(e, "Loop has been cancelled");
            mIsInitialized = true;
            next.failSafe(e);

          } catch (final Throwable t) {
            getLogger().err(t, "Error while processing failure with reason: %s", failure);
            mIsInitialized = true;
            next.failSafe(RuntimeInterruptedException.wrapIfInterrupt(t));
          }
        }
      });
    }

    void addFailures(final LoopChain<R, ?> next, @NotNull final Iterable<Throwable> failures) {

    }

    void addValue(final LoopChain<R, ?> next, final V input) {

    }

    void addValues(final LoopChain<R, ?> next, @NotNull final Iterable<V> inputs) {

    }

    @NotNull
    LoopChain<V, R> copy() {
      return new ChainYielder<S, V, R>(mYielder);
    }

    void set(final LoopChain<R, ?> next) {

    }
  }

  private static class ForkObserver<S, V>
      implements RenewableObserver<AsyncResultCollection<V>>, Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final ScheduledExecutor mExecutor;

    private final Forker<S, AsyncLoop<V>, V, AsyncResultCollection<V>> mForker;

    private final List<AsyncResultCollection<V>> mResults =
        new ArrayList<AsyncResultCollection<V>>();

    private Throwable mFailure;

    private AsyncLoop<V> mLoop;

    private S mStack;

    @SuppressWarnings("unchecked")
    private ForkObserver(@NotNull final AsyncLoop<V> loop,
        @NotNull final Forker<S, ? super AsyncLoop<V>, ? super V, ? super
            AsyncResultCollection<V>> forker) {
      mForker = (Forker<S, AsyncLoop<V>, V, AsyncResultCollection<V>>) ConstantConditions.notNull(
          "forker", forker);
      mExecutor = ScheduledExecutors.withThrottling(ScheduledExecutors.immediateExecutor(), 1);
      mLoop = loop;
    }

    boolean cancel(final boolean mayInterruptIfRunning) {
      return mLoop.cancel(mayInterruptIfRunning);
    }

    void chain(final AsyncResultCollection<V> results) {
      mExecutor.execute(new Runnable() {

        public void run() {
          final Throwable failure = mFailure;
          if (failure != null) {
            results.addFailure(failure).set();
            return;
          }

          mResults.add(results);
          try {
            mStack = mForker.statement(mLoop, mStack, results);

          } catch (final Throwable t) {
            final Throwable throwable = RuntimeInterruptedException.wrapIfInterrupt(t);
            mFailure = throwable;
            clearResults(throwable);
          }
        }
      });
    }

    @NotNull
    Observer<AsyncResultCollection<V>> newObserver() {
      return new ChainForkObserver<S, V>(this);
    }

    private void clearResults(@NotNull final Throwable failure) {
      final List<AsyncResultCollection<V>> results = mResults;
      for (final AsyncResultCollection<V> result : results) {
        result.addFailure(failure).set();
      }

      results.clear();
    }

    @NotNull
    private Object writeReplace() throws ObjectStreamException {
      return new ObserverProxy<S, V>(mLoop, mForker);
    }

    private static class ObserverProxy<S, V> extends SerializableProxy {

      private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

      private ObserverProxy(final AsyncLoop<V> loop,
          final Forker<S, ? super AsyncLoop<V>, ? super V, ? super AsyncResultCollection<V>>
              forker) {
        super(loop, proxy(forker));
      }

      @NotNull
      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ForkObserver<S, V>((AsyncLoop<V>) args[0],
              (Forker<S, ? super AsyncLoop<V>, ? super V, ? super AsyncResultCollection<V>>)
                  args[1]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void accept(final AsyncResultCollection<V> results) {
      mExecutor.execute(new Runnable() {

        public void run() {
          try {
            final AsyncLoop<V> loop = mLoop;
            mStack = mForker.init(loop);
            loop.forEach(new Mapper<V, Void>() {

              public Void apply(final V value) {
                mExecutor.execute(new Runnable() {

                  public void run() {
                    final Throwable failure = mFailure;
                    if (failure != null) {
                      clearResults(failure);
                      return;
                    }

                    try {
                      mStack = mForker.value(mLoop, mStack, value);

                    } catch (final Throwable t) {
                      final Throwable throwable = RuntimeInterruptedException.wrapIfInterrupt(t);
                      mFailure = throwable;
                      clearResults(throwable);
                    }
                  }
                });
                return null;
              }
            }).forEachCatch(new Mapper<Throwable, Void>() {

              public Void apply(final Throwable throwable) {
                mExecutor.execute(new Runnable() {

                  public void run() {
                    final Throwable failure = mFailure;
                    if (failure != null) {
                      clearResults(failure);
                      return;
                    }

                    try {
                      mStack = mForker.failure(mLoop, mStack, throwable);

                    } catch (final Throwable t) {
                      final Throwable throwable = RuntimeInterruptedException.wrapIfInterrupt(t);
                      mFailure = throwable;
                      clearResults(throwable);
                    }
                  }
                });
                return null;
              }
            }).whenDone(new Action() {

              public void perform() {
                mExecutor.execute(new Runnable() {

                  public void run() {
                    final Throwable failure = mFailure;
                    if (failure != null) {
                      clearResults(failure);
                      return;
                    }

                    try {
                      mStack = mForker.done(mLoop, mStack);

                    } catch (final Throwable t) {
                      final Throwable throwable = RuntimeInterruptedException.wrapIfInterrupt(t);
                      mFailure = throwable;
                      clearResults(throwable);
                    }
                  }
                });
              }
            });

          } catch (final Throwable t) {
            mFailure = RuntimeInterruptedException.wrapIfInterrupt(t);
          }
        }
      });
    }

    @NotNull
    public ForkObserver<S, V> renew() {
      return new ForkObserver<S, V>(mLoop.evaluate(), mForker);
    }
  }

  private static abstract class LoopChain<V, R> implements AsyncResultCollection<V> {

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

    abstract void addFailures(LoopChain<R, ?> next, @NotNull Iterable<Throwable> failures);

    abstract void addValue(LoopChain<R, ?> next, V input);

    abstract void addValues(LoopChain<R, ?> next, @NotNull Iterable<V> inputs);

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
      mLogger = logger.subContextLogger(this);
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
    public final AsyncResultCollection<V> addFailure(@NotNull final Throwable failure) {
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
    public AsyncResultCollection<V> addFailures(@Nullable final Iterable<Throwable> failures) {
      final Runnable command;
      synchronized (mMutex) {
        command = mInnerState.add();
      }

      if (command != null) {
        command.run();
        if (failures != null) {
          addFailures(mNext, failures);
        }
      }

      return this;
    }

    @NotNull
    public final AsyncResultCollection<V> addValue(final V value) {
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
    public final AsyncResultCollection<V> addValues(@Nullable final Iterable<V> values) {
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

    private LoopProxy(final Observer<AsyncResultCollection<?>> observer, final boolean isEvaluated,
        final ScheduledExecutor executor, final LogPrinter printer, final Level logLevel,
        final List<LoopChain<?, ?>> chains) {
      super(proxy(observer), isEvaluated, executor, printer, logLevel, chains);
    }

    @NotNull
    @SuppressWarnings("unchecked")
    Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        final ChainHead<Object> head = new ChainHead<Object>();
        LoopChain<?, ?> tail = head;
        for (final LoopChain<?, ?> chain : (List<LoopChain<?, ?>>) args[5]) {
          ((LoopChain<?, Object>) tail).setNext((LoopChain<Object, ?>) chain);
          tail = chain;
        }

        return new DefaultAsyncLoop<Object>((Observer<AsyncResultCollection<?>>) args[0],
            (Boolean) args[1], (ScheduledExecutor) args[2], (LogPrinter) args[3], (Level) args[4],
            head, (LoopChain<?, Object>) tail);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
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
    void addFailures(final LoopChain<Object, ?> next, @NotNull final Iterable<Throwable> failures) {
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
    void addValues(final LoopChain<Object, ?> next, @NotNull final Iterable<V> values) {
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

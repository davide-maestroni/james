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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import dm.jale.async.Action;
import dm.jale.async.AsyncEvaluation;
import dm.jale.async.AsyncStatement;
import dm.jale.async.Completer;
import dm.jale.async.FailureException;
import dm.jale.async.Mapper;
import dm.jale.async.Observer;
import dm.jale.async.RuntimeInterruptedException;
import dm.jale.async.RuntimeTimeoutException;
import dm.jale.async.Updater;
import dm.jale.config.BuildConfig;
import dm.jale.executor.ExecutorPool;
import dm.jale.log.Logger;
import dm.jale.util.ConstantConditions;
import dm.jale.util.SerializableProxy;
import dm.jale.util.TimeUnits;
import dm.jale.util.TimeUnits.Condition;

import static dm.jale.executor.ExecutorPool.immediateExecutor;
import static dm.jale.executor.ExecutorPool.withThrottling;

/**
 * Created by davide-maestroni on 01/12/2018.
 */
class DefaultAsyncStatement<V> implements AsyncStatement<V>, Serializable {

  private static final AsyncEvaluation<?> VOID_EVALUATION = new AsyncEvaluation<Object>() {

    public void fail(@NotNull final Throwable failure) {
    }

    public void set(final Object value) {
    }
  };

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final CopyOnWriteArrayList<AsyncStatement<?>> mForked =
      new CopyOnWriteArrayList<AsyncStatement<?>>();

  private final ChainHead<?> mHead;

  private final boolean mIsEvaluated;

  private final boolean mIsFork;

  private final Logger mLogger;

  private final Object mMutex;

  private final Observer<AsyncEvaluation<?>> mObserver;

  private final StatementChain<?, V> mTail;

  private StatementChain<V, ?> mChain;

  private StatementState mState = StatementState.Evaluating;

  @SuppressWarnings("unchecked")
  DefaultAsyncStatement(@NotNull final Observer<? super AsyncEvaluation<V>> observer,
      final boolean isEvaluated, @Nullable final String loggerName) {
    mObserver = (Observer<AsyncEvaluation<?>>) ConstantConditions.notNull("observer", observer);
    mIsEvaluated = isEvaluated;
    mIsFork = (observer instanceof ForkObserver);
    mLogger = Logger.newLogger(this, loggerName, Locale.ENGLISH);
    final ChainHead<V> head = new ChainHead<V>();
    head.setLogger(mLogger);
    head.setNext(new ChainTail());
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
  private DefaultAsyncStatement(@NotNull final Observer<? super AsyncEvaluation<V>> observer,
      final boolean isEvaluated, @NotNull final Logger logger) {
    // forking
    mObserver = (Observer<AsyncEvaluation<?>>) observer;
    mIsEvaluated = isEvaluated;
    mIsFork = (observer instanceof ForkObserver);
    mLogger = logger;
    final ChainHead<V> head = new ChainHead<V>();
    head.setLogger(mLogger);
    head.setNext(new ChainTail());
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
  private DefaultAsyncStatement(@NotNull final Observer<AsyncEvaluation<?>> observer,
      final boolean isEvaluated, @Nullable final String loggerName,
      @NotNull final ChainHead<?> head, @NotNull final StatementChain<?, V> tail) {
    // serialization
    mObserver = observer;
    mIsEvaluated = isEvaluated;
    mIsFork = (observer instanceof ForkObserver);
    mLogger = Logger.newLogger(this, loggerName, Locale.ENGLISH);
    mMutex = head.getMutex();
    mHead = head;
    mTail = tail;
    tail.setNext(new ChainTail());
    StatementChain<?, ?> chain = head;
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
  private DefaultAsyncStatement(@NotNull final Observer<AsyncEvaluation<?>> observer,
      final boolean isEvaluated, @NotNull final Logger logger, @NotNull final ChainHead<?> head,
      @NotNull final StatementChain<?, ?> tail, @NotNull final StatementChain<?, V> chain) {
    // chaining
    mObserver = observer;
    mIsEvaluated = isEvaluated;
    mIsFork = (observer instanceof ForkObserver);
    mLogger = logger;
    mMutex = head.getMutex();
    mHead = head;
    mTail = chain;
    chain.setNext(new ChainTail());
    ((StatementChain<?, Object>) tail).setNext((StatementChain<Object, V>) chain);
  }

  @SuppressWarnings("unchecked")
  private DefaultAsyncStatement(@NotNull final Observer<AsyncEvaluation<?>> observer,
      final boolean isEvaluated, @NotNull final Logger logger, @NotNull final ChainHead<?> head,
      @NotNull final StatementChain<?, V> tail) {
    // copy
    mObserver = observer;
    mIsEvaluated = isEvaluated;
    mIsFork = (observer instanceof ForkObserver);
    mLogger = logger;
    mMutex = head.getMutex();
    mHead = head;
    mTail = tail;
    tail.setNext(new ChainTail());
    try {
      observer.accept(head);

    } catch (final Throwable t) {
      head.failSafe(RuntimeInterruptedException.wrapIfInterrupt(t));
    }
  }

  public boolean cancel(final boolean mayInterruptIfRunning) {
    final Observer<? extends AsyncEvaluation<?>> observer = mObserver;
    if (mIsFork) {
      if (((ForkObserver<?, ?>) observer).cancel(mayInterruptIfRunning)) {
        return true;
      }

      boolean isCancelled = false;
      for (final AsyncStatement<?> forked : mForked) {
        if (forked.cancel(mayInterruptIfRunning)) {
          isCancelled = true;
        }
      }

      return isCancelled;
    }

    StatementChain<?, ?> chain = mHead;
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

  public V get() throws InterruptedException, ExecutionException {
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

  public V get(final long timeout, @NotNull final TimeUnit timeUnit) throws InterruptedException,
      ExecutionException, TimeoutException {
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
    to((AsyncEvaluation<? super V>) VOID_EVALUATION);
  }

  @NotNull
  public AsyncStatement<V> elseCatch(@NotNull final Mapper<? super Throwable, ? extends V> mapper,
      @Nullable final Class<?>[] exceptionTypes) {
    return chain(
        new ElseCatchStatementHandler<V>(mapper, Asyncs.cloneExceptionTypes(exceptionTypes)));
  }

  @NotNull
  public AsyncStatement<V> elseDo(@NotNull final Observer<? super Throwable> observer,
      @Nullable final Class<?>[] exceptionTypes) {
    return chain(
        new ElseDoStatementHandler<V>(observer, Asyncs.cloneExceptionTypes(exceptionTypes)));
  }

  @NotNull
  public AsyncStatement<V> elseIf(
      @NotNull final Mapper<? super Throwable, ? extends AsyncStatement<? extends V>> mapper,
      @Nullable final Class<?>[] exceptionTypes) {
    return chain(new ElseIfStatementHandler<V>(mapper, Asyncs.cloneExceptionTypes(exceptionTypes)));
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public DefaultAsyncStatement<V> evaluate() {
    final Logger logger = mLogger;
    final ChainHead<?> head = mHead;
    final ChainHead<?> newHead = head.copy();
    newHead.setLogger(logger);
    StatementChain<?, ?> newTail = newHead;
    StatementChain<?, ?> next = head;
    while (next != mTail) {
      next = next.mNext;
      StatementChain<?, ?> chain = next.copy();
      chain.setLogger(logger);
      ((StatementChain<?, Object>) newTail).setNext((StatementChain<Object, ?>) chain);
      newTail = chain;
    }

    return new DefaultAsyncStatement<V>(renewObserver(), true, logger, newHead,
        (StatementChain<?, V>) newTail);
  }

  @NotNull
  public AsyncStatement<V> evaluated() {
    return (mIsEvaluated) ? this : evaluate();
  }

  @NotNull
  public <S> AsyncStatement<V> fork(
      @NotNull final Forker<S, ? super V, ? super AsyncEvaluation<V>, ? super AsyncStatement<V>>
          forker) {
    return new DefaultAsyncStatement<V>(new ForkObserver<S, V>(this, forker), mIsEvaluated,
        mLogger);
  }

  @NotNull
  public <S> AsyncStatement<V> fork(@Nullable final Mapper<? super AsyncStatement<V>, S> init,
      @Nullable final Updater<S, ? super V, ? super AsyncStatement<V>> value,
      @Nullable final Updater<S, ? super Throwable, ? super AsyncStatement<V>> failure,
      @Nullable final Completer<S, ? super AsyncStatement<V>> done,
      @Nullable final Updater<S, ? super AsyncEvaluation<V>, ? super AsyncStatement<V>>
          evaluation) {
    return fork(new ComposedStatementForker<S, V>(init, value, failure, done, evaluation));
  }

  @NotNull
  public AsyncStatement<V> forkOn(@NotNull final Executor executor) {
    return fork(new ExecutorStatementForker<V>(executor));
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

  public V getValue() {
    return getValue(-1, TimeUnit.MILLISECONDS);
  }

  @SuppressWarnings("unchecked")
  public V getValue(final long timeout, @NotNull final TimeUnit timeUnit) {
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
          return (V) head.getValue();
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
  public <R> AsyncStatement<R> then(@NotNull final Mapper<? super V, R> mapper) {
    return chain(new ThenStatementHandler<V, R>(mapper));
  }

  @NotNull
  public AsyncStatement<V> thenDo(@NotNull final Observer<? super V> observer) {
    return chain(new ThenDoStatementHandler<V, V>(observer));
  }

  @NotNull
  public <R> AsyncStatement<R> thenIf(
      @NotNull final Mapper<? super V, ? extends AsyncStatement<R>> mapper) {
    return chain(new ThenIfStatementHandler<V, R>(mapper));
  }

  @NotNull
  public <R> AsyncStatement<R> thenTry(
      @NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final Mapper<? super V, R> mapper) {
    return chain(new TryStatementHandler<V, R>(closeable, new ThenStatementHandler<V, R>(mapper),
        mLogger.getName()));
  }

  @NotNull
  public AsyncStatement<V> thenTryDo(
      @NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final Observer<? super V> observer) {
    return chain(
        new TryStatementHandler<V, V>(closeable, new ThenDoStatementHandler<V, V>(observer),
            mLogger.getName()));
  }

  @NotNull
  public <R> AsyncStatement<R> thenTryIf(
      @NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final Mapper<? super V, ? extends AsyncStatement<R>> mapper) {
    return chain(new TryIfStatementHandler<V, R>(closeable, mapper, mLogger.getName()));
  }

  @NotNull
  public AsyncStatement<V> whenDone(@NotNull final Action action) {
    return chain(new DoneStatementHandler<V>(action));
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

  public void to(@NotNull final AsyncEvaluation<? super V> evaluation) {
    ConstantConditions.notNull("evaluation", evaluation);
    checkEvaluated();
    then(new Mapper<V, Void>() {

      public Void apply(final V value) {
        evaluation.set(value);
        return null;
      }
    }).elseCatch(new Mapper<Throwable, Void>() {

      public Void apply(final Throwable failure) {
        evaluation.fail(failure);
        return null;
      }
    });
  }

  @SuppressWarnings("unchecked")
  public V value() {
    synchronized (mMutex) {
      if (!isSet()) {
        throw new IllegalStateException("the statement is not set");
      }

      return (V) mHead.getValue();
    }
  }

  @NotNull
  private <R> AsyncStatement<R> chain(@NotNull final AsyncStatementHandler<V, R> handler) {
    return chain(new ChainHandler<V, R>(handler));
  }

  @NotNull
  @SuppressWarnings("unchecked")
  private <R> AsyncStatement<R> chain(@NotNull final StatementChain<V, R> chain) {
    final ChainHead<?> head = mHead;
    final Logger logger = mLogger;
    final Runnable chaining;
    final Observer<? extends AsyncEvaluation<?>> observer = mObserver;
    if (mIsFork) {
      final AsyncStatement<R> forked =
          new DefaultAsyncStatement<V>(((ForkObserver<?, V>) observer).newObserver(), mIsEvaluated,
              logger).chain(chain);
      mForked.add(forked);
      return forked;
    }

    synchronized (mMutex) {
      if (mChain != null) {
        throw new IllegalStateException("the statement is already chained");

      } else {
        chain.setLogger(logger);
        chaining = head.chain(chain);
        mState = StatementState.Chained;
        mChain = chain;
        mMutex.notifyAll();
      }
    }

    final DefaultAsyncStatement<R> statement =
        new DefaultAsyncStatement<R>((Observer<AsyncEvaluation<?>>) observer, mIsEvaluated, logger,
            head, mTail, chain);
    if (chaining != null) {
      chaining.run();
    }

    return statement;
  }

  private void checkEvaluated() {
    if (!mIsEvaluated) {
      ConstantConditions.unsupported("the statement has not been evaluated", "checkEvaluated");
    }
  }

  private void checkFinal() {
    if (mChain != null) {
      throw new IllegalStateException("the statement is not final");
    }
  }

  private void checkSupported() {
    checkEvaluated();
    if (mIsFork) {
      ConstantConditions.unsupported("the statement has been forked", "checkSupported");
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
  private Observer<AsyncEvaluation<?>> renewObserver() {
    final Observer<AsyncEvaluation<?>> observer = mObserver;
    if (observer instanceof RenewableObserver) {
      return ((RenewableObserver<AsyncEvaluation<?>>) observer).renew();
    }

    return observer;
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    final ArrayList<StatementChain<?, ?>> chains = new ArrayList<StatementChain<?, ?>>();
    final ChainHead<?> head = mHead;
    StatementChain<?, ?> chain = head;
    while (chain != mTail) {
      if (chain != head) {
        chains.add(chain);
      }

      chain = chain.mNext;
    }

    if (chain != head) {
      chains.add(chain);
    }

    return new StatementProxy(mObserver, mIsEvaluated, mLogger.getName(), chains);
  }

  private static class ChainForkObserver<S, V>
      implements RenewableObserver<AsyncEvaluation<V>>, Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final ForkObserver<S, V> mObserver;

    private ChainForkObserver(@NotNull final ForkObserver<S, V> observer) {
      mObserver = observer;
    }

    public void accept(final AsyncEvaluation<V> evaluation) {
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

  private static class ChainHandler<V, R> extends StatementChain<V, R> implements Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final AsyncStatementHandler<V, R> mHandler;

    ChainHandler(@NotNull final AsyncStatementHandler<V, R> handler) {
      mHandler = handler;
    }

    @NotNull
    StatementChain<V, R> copy() {
      return new ChainHandler<V, R>(mHandler.renew());
    }

    @NotNull
    private Object writeReplace() throws ObjectStreamException {
      return new ChainProxy<V, R>(mHandler);
    }

    private static class ChainProxy<V, R> implements Serializable {

      private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

      private final AsyncStatementHandler<V, R> mHandler;

      private ChainProxy(@NotNull final AsyncStatementHandler<V, R> handler) {
        mHandler = handler;
      }

      @NotNull
      Object readResolve() throws ObjectStreamException {
        try {
          return new ChainHandler<V, R>(mHandler);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    void fail(final StatementChain<R, ?> next, final Throwable failure) {
      try {
        getLogger().dbg("Processing failure with reason: %s", failure);
        mHandler.failure(failure, next);

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Statement has been cancelled");
        next.failSafe(e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing failure with reason: %s", failure);
        next.failSafe(RuntimeInterruptedException.wrapIfInterrupt(t));
      }
    }

    void set(final StatementChain<R, ?> next, final V value) {
      try {
        getLogger().dbg("Processing value: %s", value);
        mHandler.value(value, next);

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Statement has been cancelled");
        next.failSafe(e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing value: %s", value);
        next.failSafe(RuntimeInterruptedException.wrapIfInterrupt(t));
      }
    }
  }

  private static class ChainHead<V> extends StatementChain<V, V> {

    private final Object mMutex = new Object();

    private Throwable mException;

    private StateEvaluating mInnerState = new StateEvaluating();

    private StatementState mState = StatementState.Evaluating;

    private Object mValue;

    @Nullable
    Runnable chain(@NotNull final StatementChain<?, ?> chain) {
      final Runnable chaining = mInnerState.chain(chain);
      mState = StatementState.Evaluating;
      return chaining;
    }

    @Nullable
    FailureException getFailure() {
      return mInnerState.getFailure();
    }

    @NotNull
    Object getMutex() {
      return mMutex;
    }

    @NotNull
    StatementState getState() {
      return mState;
    }

    Object getValue() {
      return mInnerState.getValue();
    }

    void innerFail(@Nullable final Throwable failure) {
      mInnerState.innerFail(failure);
      mState = StatementState.Failed;
    }

    void innerSet(final Object value) {
      mInnerState.innerSet(value);
      mState = StatementState.Set;
    }

    private class StateEvaluating {

      @Nullable
      Runnable chain(@NotNull final StatementChain<?, ?> chain) {
        return null;
      }

      @NotNull
      IllegalStateException exception(@NotNull final StatementState state) {
        return new IllegalStateException("invalid state: " + state);
      }

      @Nullable
      FailureException getFailure() {
        return null;
      }

      Object getValue() {
        throw exception(StatementState.Evaluating);
      }

      void innerFail(@Nullable final Throwable failure) {
        getLogger().dbg("Statement failing with reason [%s => %s]: %s", StatementState.Evaluating,
            StatementState.Failed, failure);
        mException = failure;
        mInnerState = new StateFailed();
      }

      void innerSet(final Object value) {
        getLogger().dbg("Setting statement value [%s => %s]: %s", StatementState.Evaluating,
            StatementState.Set, value);
        mValue = value;
        mInnerState = new StateSet();
      }
    }

    private class StateFailed extends StateEvaluating {

      @Nullable
      @Override
      Runnable chain(@NotNull final StatementChain<?, ?> chain) {
        getLogger().dbg("Binding statement [%s => %s]", StatementState.Failed,
            StatementState.Evaluating);
        final Throwable exception = mException;
        mException = null;
        mInnerState = new StateEvaluating();
        return new Runnable() {

          public void run() {
            chain.failSafe(exception);
          }
        };
      }

      Object getValue() {
        throw FailureException.wrap(mException);
      }

      @Nullable
      @Override
      FailureException getFailure() {
        return FailureException.wrap(mException);
      }

      @Override
      void innerFail(@Nullable final Throwable failure) {
        throw exception(StatementState.Failed);
      }

      @Override
      void innerSet(final Object value) {
        throw exception(StatementState.Failed);
      }
    }

    private class StateSet extends StateEvaluating {

      @Nullable
      @Override
      Runnable chain(@NotNull final StatementChain<?, ?> chain) {
        getLogger().dbg("Chaining statement [%s => %s]", StatementState.Set,
            StatementState.Evaluating);
        final Object value = mValue;
        mValue = null;
        mInnerState = new StateEvaluating();
        return new Runnable() {

          @SuppressWarnings("unchecked")
          public void run() {
            ((StatementChain<Object, ?>) chain).set(value);
          }
        };
      }

      @Override
      Object getValue() {
        return mValue;
      }

      @Override
      void innerFail(@Nullable final Throwable failure) {
        throw exception(StatementState.Set);
      }

      @Override
      void innerSet(final Object value) {
        throw exception(StatementState.Set);
      }
    }

    @NotNull
    ChainHead<V> copy() {
      return new ChainHead<V>();
    }

    @Override
    public void fail(final StatementChain<V, ?> next, final Throwable failure) {
      next.fail(failure);
    }

    @Override
    public void set(final StatementChain<V, ?> next, final V value) {
      next.set(value);
    }
  }

  private static class ForkObserver<S, V> extends AsyncStatementHandler<V, V>
      implements RenewableObserver<AsyncEvaluation<V>>, Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final List<AsyncEvaluation<V>> mEvaluations = new ArrayList<AsyncEvaluation<V>>();

    private final Executor mExecutor;

    private final Forker<S, V, AsyncEvaluation<V>, AsyncStatement<V>> mForker;

    private Throwable mFailure;

    private S mStack;

    private DefaultAsyncStatement<V> mStatement;

    @SuppressWarnings("unchecked")
    private ForkObserver(@NotNull final DefaultAsyncStatement<V> statement,
        @NotNull final Forker<S, ? super V, ? super AsyncEvaluation<V>, ? super
            AsyncStatement<V>> forker) {
      mForker =
          (Forker<S, V, AsyncEvaluation<V>, AsyncStatement<V>>) ConstantConditions.notNull("forker",
              forker);
      mExecutor = withThrottling(1, immediateExecutor());
      mStatement = statement;
    }

    boolean cancel(final boolean mayInterruptIfRunning) {
      return mStatement.cancel(mayInterruptIfRunning);
    }

    void chain(final AsyncEvaluation<V> evaluation) {
      mExecutor.execute(new Runnable() {

        public void run() {
          final Throwable failure = mFailure;
          if (failure != null) {
            evaluation.fail(failure);
            return;
          }

          mEvaluations.add(evaluation);
          try {
            mStack = mForker.evaluation(mStack, evaluation, mStatement);

          } catch (final Throwable t) {
            final Throwable throwable = RuntimeInterruptedException.wrapIfInterrupt(t);
            mFailure = throwable;
            clearEvaluations(throwable);
          }
        }
      });
    }

    @Override
    void failure(@NotNull final Throwable throwable, @NotNull final AsyncEvaluation<V> evaluation) {
      mExecutor.execute(new Runnable() {

        public void run() {
          final Throwable failure = mFailure;
          if (failure != null) {
            clearEvaluations(failure);
            return;
          }

          try {
            final Forker<S, V, AsyncEvaluation<V>, AsyncStatement<V>> forker = mForker;
            final DefaultAsyncStatement<V> statement = mStatement;
            final S stack = forker.failure(mStack, throwable, statement);
            mStack = forker.done(stack, statement);

          } catch (final Throwable t) {
            final Throwable throwable = RuntimeInterruptedException.wrapIfInterrupt(t);
            mFailure = throwable;
            clearEvaluations(throwable);
          }
        }
      });
    }

    @NotNull
    public ForkObserver<S, V> renew() {
      return new ForkObserver<S, V>(mStatement.evaluate(), mForker);
    }

    @Override
    void value(final V value, @NotNull final AsyncEvaluation<V> evaluation) {
      mExecutor.execute(new Runnable() {

        public void run() {
          final Throwable failure = mFailure;
          if (failure != null) {
            clearEvaluations(failure);
            return;
          }

          try {
            final Forker<S, V, AsyncEvaluation<V>, AsyncStatement<V>> forker = mForker;
            final DefaultAsyncStatement<V> statement = mStatement;
            final S stack = forker.value(mStack, value, statement);
            mStack = forker.done(stack, statement);

          } catch (final Throwable t) {
            final Throwable throwable = RuntimeInterruptedException.wrapIfInterrupt(t);
            mFailure = throwable;
            clearEvaluations(throwable);
          }
        }
      });
    }

    @NotNull
    Observer<AsyncEvaluation<V>> newObserver() {
      return new ChainForkObserver<S, V>(this);
    }

    private void clearEvaluations(@NotNull final Throwable failure) {
      final List<AsyncEvaluation<V>> evaluations = mEvaluations;
      for (final AsyncEvaluation<V> evaluation : evaluations) {
        try {
          evaluation.fail(failure);

        } catch (final Throwable ignored) {
          // cannot take any action
        }
      }

      evaluations.clear();
    }

    @NotNull
    private Object writeReplace() throws ObjectStreamException {
      return new ObserverProxy<S, V>(mStatement, mForker);
    }

    private static class ObserverProxy<S, V> extends SerializableProxy {

      private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

      private ObserverProxy(final DefaultAsyncStatement<V> statement,
          final Forker<S, ? super V, ? super AsyncEvaluation<V>, ? super AsyncStatement<V>>
              forker) {
        super(statement, proxy(forker));
      }

      @NotNull
      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ForkObserver<S, V>((DefaultAsyncStatement<V>) args[0],
              (Forker<S, ? super V, ? super AsyncEvaluation<V>, ? super AsyncStatement<V>>)
                  args[1]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void accept(final AsyncEvaluation<V> evaluation) {
      mExecutor.execute(new Runnable() {

        public void run() {
          try {
            final DefaultAsyncStatement<V> statement = mStatement;
            mStack = mForker.init(statement);
            statement.chain(ForkObserver.this);

          } catch (final Throwable t) {
            mFailure = RuntimeInterruptedException.wrapIfInterrupt(t);
          }
        }
      });
    }
  }

  private static abstract class StatementChain<V, R> implements AsyncEvaluation<V> {

    private volatile StateEvaluating mInnerState = new StateEvaluating();

    private Logger mLogger;

    private volatile StatementChain<R, ?> mNext;

    boolean cancel(@NotNull final Throwable exception) {
      if (mInnerState.failSafe(exception)) {
        fail(mNext, exception);
        return true;
      }

      return false;
    }

    @NotNull
    abstract StatementChain<V, R> copy();

    abstract void fail(StatementChain<R, ?> next, Throwable failure);

    final void failSafe(@NotNull final Throwable failure) {
      ConstantConditions.notNull("failure", failure);
      if (mInnerState.failSafe(failure)) {
        fail(mNext, failure);
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

    abstract void set(StatementChain<R, ?> next, V value);

    void setNext(@NotNull final StatementChain<R, ?> next) {
      mNext = next;
    }

    private class StateEvaluating {

      void fail(@NotNull final Throwable failure) {
        mInnerState = new StateFailed(failure);
      }

      boolean failSafe(@NotNull final Throwable failure) {
        mInnerState = new StateFailed(failure);
        return true;
      }

      void set() {
        mInnerState = new StateSet();
      }
    }

    private class StateFailed extends StateEvaluating {

      private final Throwable mFailure;

      private StateFailed(@NotNull final Throwable failure) {
        mFailure = failure;
      }

      @Override
      void fail(@NotNull final Throwable failure) {
        throwException();
      }

      void throwException() {
        final Throwable failure = mFailure;
        mLogger.wrn("Statement has already failed with reason: %s", failure);
        throw FailureException.wrap(failure);
      }

      @Override
      boolean failSafe(@NotNull final Throwable failure) {
        mLogger.wrn(failure, "Suppressed failure");
        return false;
      }

      @Override
      void set() {
        throwException();
      }
    }

    private class StateSet extends StateEvaluating {

      void throwException() {
        mLogger.wrn("Statement has already a value");
        throw FailureException.wrap(new IllegalStateException("statement has already a value"));
      }

      @Override
      boolean failSafe(@NotNull final Throwable failure) {
        mLogger.wrn(failure, "Suppressed failure");
        return false;
      }

      @Override
      void set() {
        throwException();
      }

      @Override
      void fail(@NotNull final Throwable failure) {
        throwException();
      }
    }

    public final void fail(@NotNull final Throwable failure) {
      ConstantConditions.notNull("failure", failure);
      mInnerState.fail(failure);
      fail(mNext, failure);
    }

    public final void set(final V value) {
      mInnerState.set();
      set(mNext, value);
    }
  }

  private static class StatementProxy extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private StatementProxy(final Observer<AsyncEvaluation<?>> observer, final boolean isEvaluated,
        final String loggerName, final List<StatementChain<?, ?>> chains) {
      super(proxy(observer), isEvaluated, loggerName, chains);
    }

    @NotNull
    @SuppressWarnings("unchecked")
    Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        final ChainHead<Object> head = new ChainHead<Object>();
        StatementChain<?, ?> tail = head;
        for (final StatementChain<?, ?> chain : (List<StatementChain<?, ?>>) args[3]) {
          ((StatementChain<?, Object>) tail).setNext((StatementChain<Object, ?>) chain);
          tail = chain;
        }

        return new DefaultAsyncStatement<Object>((Observer<AsyncEvaluation<?>>) args[0],
            (Boolean) args[1], (String) args[2], head, (StatementChain<?, Object>) tail);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }

  private class ChainTail extends StatementChain<V, Object> {

    private ChainTail() {
      setLogger(mLogger);
    }

    @NotNull
    @Override
    StatementChain<V, Object> copy() {
      return ConstantConditions.unsupported();
    }

    @Override
    boolean isTail() {
      return true;
    }

    @Override
    void fail(final StatementChain<Object, ?> next, final Throwable reason) {
      final StatementChain<V, ?> chain;
      synchronized (mMutex) {
        try {
          if ((chain = mChain) == null) {
            mState = StatementState.Failed;
            mHead.innerFail(reason);
            return;
          }

        } finally {
          mMutex.notifyAll();
        }
      }

      chain.fail(reason);
    }

    @Override
    void set(final StatementChain<Object, ?> next, final V value) {
      final StatementChain<V, ?> chain;
      synchronized (mMutex) {
        try {
          if ((chain = mChain) == null) {
            mState = StatementState.Set;
            mHead.innerSet(value);
            return;
          }

        } finally {
          mMutex.notifyAll();
        }
      }

      chain.set(value);
    }
  }
}

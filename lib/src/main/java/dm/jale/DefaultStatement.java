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
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import dm.jale.async.Action;
import dm.jale.async.Completer;
import dm.jale.async.Evaluation;
import dm.jale.async.FailureException;
import dm.jale.async.Mapper;
import dm.jale.async.Observer;
import dm.jale.async.RuntimeInterruptedException;
import dm.jale.async.RuntimeTimeoutException;
import dm.jale.async.Statement;
import dm.jale.async.Updater;
import dm.jale.config.BuildConfig;
import dm.jale.executor.ExecutorPool;
import dm.jale.log.Logger;
import dm.jale.util.ConstantConditions;
import dm.jale.util.SerializableProxy;
import dm.jale.util.TimeUnits;
import dm.jale.util.TimeUnits.Condition;

import static dm.jale.executor.ExecutorPool.loopExecutor;
import static dm.jale.executor.ExecutorPool.withThrottling;

/**
 * Created by davide-maestroni on 01/12/2018.
 */
class DefaultStatement<V> implements Statement<V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final PropagationHead<?> mHead;

  private final boolean mIsEvaluated;

  private final boolean mIsFork;

  private final Logger mLogger;

  private final Object mMutex;

  private final Observer<Evaluation<?>> mObserver;

  private final StatementPropagation<?, V> mTail;

  private ArrayList<WeakReference<Statement<?>>> mForked =
      new ArrayList<WeakReference<Statement<?>>>();

  private StatementPropagation<V, ?> mPropagation;

  private StatementState mState = StatementState.Evaluating;

  @SuppressWarnings("unchecked")
  DefaultStatement(@NotNull final Observer<? super Evaluation<V>> observer,
      final boolean isEvaluated, @Nullable final String loggerName) {
    mObserver = (Observer<Evaluation<?>>) ConstantConditions.notNull("observer", observer);
    mIsEvaluated = isEvaluated;
    mIsFork = false;
    mLogger = Logger.newLogger(this, loggerName, Locale.ENGLISH);
    final PropagationHead<V> head = new PropagationHead<V>();
    head.setLogger(mLogger);
    head.setNext(new PropagationTail());
    mMutex = head.getMutex();
    mHead = head;
    mTail = head;
    try {
      observer.accept(head);

    } catch (final Throwable t) {
      head.failSafe(t);
    }
  }

  @SuppressWarnings("unchecked")
  private DefaultStatement(@NotNull final Observer<? super Evaluation<V>> observer,
      final boolean isEvaluated, @NotNull final Logger logger) {
    // forking
    mObserver = (Observer<Evaluation<?>>) observer;
    mIsEvaluated = isEvaluated;
    mIsFork = (observer instanceof ForkObserver);
    mLogger = logger;
    final PropagationHead<V> head = new PropagationHead<V>();
    head.setLogger(mLogger);
    head.setNext(new PropagationTail());
    mMutex = head.getMutex();
    mHead = head;
    mTail = head;
    try {
      observer.accept(head);

    } catch (final Throwable t) {
      head.failSafe(t);
    }
  }

  @SuppressWarnings("unchecked")
  private DefaultStatement(@NotNull final Observer<Evaluation<?>> observer,
      final boolean isEvaluated, @Nullable final String loggerName,
      @NotNull final PropagationHead<?> head, @NotNull final StatementPropagation<?, V> tail) {
    // serialization
    mObserver = observer;
    mIsEvaluated = isEvaluated;
    mIsFork = (observer instanceof ForkObserver);
    mLogger = Logger.newLogger(this, loggerName, Locale.ENGLISH);
    mMutex = head.getMutex();
    mHead = head;
    mTail = tail;
    tail.setNext(new PropagationTail());
    StatementPropagation<?, ?> propagation = head;
    while (!propagation.isTail()) {
      propagation.setLogger(mLogger);
      propagation = propagation.mNext;
    }

    try {
      observer.accept(head);

    } catch (final Throwable t) {
      head.failSafe(t);
    }
  }

  @SuppressWarnings("unchecked")
  private DefaultStatement(@NotNull final Observer<Evaluation<?>> observer,
      final boolean isEvaluated, @NotNull final Logger logger,
      @NotNull final PropagationHead<?> head, @NotNull final StatementPropagation<?, ?> tail,
      @NotNull final StatementPropagation<?, V> propagation) {
    // propagation
    mObserver = observer;
    mIsEvaluated = isEvaluated;
    mIsFork = false;
    mLogger = logger;
    mMutex = head.getMutex();
    mHead = head;
    mTail = propagation;
    propagation.setNext(new PropagationTail());
    ((StatementPropagation<?, Object>) tail).setNext((StatementPropagation<Object, V>) propagation);
  }

  @SuppressWarnings("unchecked")
  private DefaultStatement(@NotNull final Observer<Evaluation<?>> observer,
      final boolean isEvaluated, @NotNull final Logger logger,
      @NotNull final PropagationHead<?> head, @NotNull final StatementPropagation<?, V> tail) {
    // copy
    mObserver = observer;
    mIsEvaluated = isEvaluated;
    mIsFork = (observer instanceof ForkObserver);
    mLogger = logger;
    mMutex = head.getMutex();
    mHead = head;
    mTail = tail;
    tail.setNext(new PropagationTail());
    try {
      observer.accept(head);

    } catch (final Throwable t) {
      head.failSafe(t);
    }
  }

  @SuppressWarnings("unchecked")
  public boolean cancel(final boolean mayInterruptIfRunning) {
    final Observer<? extends Evaluation<?>> observer = mObserver;
    if (mIsFork) {
      if (((ForkObserver<?, ?>) observer).cancel(mayInterruptIfRunning)) {
        return true;
      }

      boolean isCancelled = false;
      final Iterator<WeakReference<Statement<?>>> iterator = mForked.iterator();
      while (iterator.hasNext()) {
        final Statement<?> statement = iterator.next().get();
        if (statement == null) {
          iterator.remove();

        } else if (statement.cancel(mayInterruptIfRunning)) {
          isCancelled = true;
        }
      }

      return isCancelled;
    }

    StatementPropagation<?, ?> propagation = mHead;
    if (observer instanceof PropagationForkObserver) {
      ((PropagationForkObserver) observer).cancel(propagation);
    }

    final CancellationException exception = new CancellationException("statement is cancelled");
    if (mayInterruptIfRunning && (observer instanceof InterruptibleObserver)) {
      if (propagation.cancel(exception)) {
        ((InterruptibleObserver<?>) observer).interrupt();
        return true;
      }

      propagation = propagation.mNext;
    }

    while (!propagation.isTail()) {
      if (propagation.cancel(exception)) {
        return true;
      }

      propagation = propagation.mNext;
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

  public void consume() {
    propagate(new PropagationConsume<V>());
  }

  @NotNull
  public Statement<V> elseCatch(@NotNull final Mapper<? super Throwable, ? extends V> mapper,
      @Nullable final Class<?>[] exceptionTypes) {
    return propagate(
        new ElseCatchStatementHandler<V>(mapper, Asyncs.cloneExceptionTypes(exceptionTypes)));
  }

  @NotNull
  public Statement<V> elseDo(@NotNull final Observer<? super Throwable> observer,
      @Nullable final Class<?>[] exceptionTypes) {
    return propagate(
        new ElseDoStatementHandler<V>(observer, Asyncs.cloneExceptionTypes(exceptionTypes)));
  }

  @NotNull
  public Statement<V> elseIf(
      @NotNull final Mapper<? super Throwable, ? extends Statement<? extends V>> mapper,
      @Nullable final Class<?>[] exceptionTypes) {
    return propagate(
        new ElseIfStatementHandler<V>(mapper, Asyncs.cloneExceptionTypes(exceptionTypes)));
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public DefaultStatement<V> evaluate() {
    final Logger logger = mLogger;
    final PropagationHead<?> head = mHead;
    final PropagationHead<?> newHead = head.copy();
    newHead.setLogger(logger);
    StatementPropagation<?, ?> newTail = newHead;
    StatementPropagation<?, ?> next = head;
    while (next != mTail) {
      next = next.mNext;
      StatementPropagation<?, ?> propagation = next.copy();
      propagation.setLogger(logger);
      ((StatementPropagation<?, Object>) newTail).setNext(
          (StatementPropagation<Object, ?>) propagation);
      newTail = propagation;
    }

    return new DefaultStatement<V>(renewObserver(), true, logger, newHead,
        (StatementPropagation<?, V>) newTail);
  }

  @NotNull
  public Statement<V> evaluated() {
    return (mIsEvaluated) ? this : evaluate();
  }

  @NotNull
  public <S> Statement<V> fork(
      @NotNull final Forker<S, ? super V, ? super Evaluation<V>, ? super Statement<V>> forker) {
    return new DefaultStatement<V>(new ForkObserver<S, V>(this, forker), mIsEvaluated, mLogger);
  }

  @NotNull
  public <S> Statement<V> fork(@Nullable final Mapper<? super Statement<V>, S> init,
      @Nullable final Updater<S, ? super V, ? super Statement<V>> value,
      @Nullable final Updater<S, ? super Throwable, ? super Statement<V>> failure,
      @Nullable final Completer<S, ? super Statement<V>> done,
      @Nullable final Updater<S, ? super Evaluation<V>, ? super Statement<V>> evaluation) {
    return fork(new ComposedStatementForker<S, V>(init, value, failure, done, evaluation));
  }

  @NotNull
  public Statement<V> forkOn(@NotNull final Executor executor) {
    return fork(new ExecutorStatementForker<V>(executor));
  }

  public boolean getDone() {
    return getDone(-1, TimeUnit.MILLISECONDS);
  }

  public boolean getDone(final long timeout, @NotNull final TimeUnit timeUnit) {
    checkSupported();
    deadLockWarning(timeout);
    @SuppressWarnings("UnnecessaryLocalVariable") final PropagationHead<?> head = mHead;
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
    final PropagationHead<?> head = mHead;
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
    final PropagationHead<?> head = mHead;
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
      return (mPropagation == null);
    }
  }

  @NotNull
  public <R> Statement<R> then(@NotNull final Mapper<? super V, R> mapper) {
    return propagate(new ThenStatementHandler<V, R>(mapper));
  }

  @NotNull
  public Statement<V> thenDo(@NotNull final Observer<? super V> observer) {
    return propagate(new ThenDoStatementHandler<V, V>(observer));
  }

  @NotNull
  public <R> Statement<R> thenIf(@NotNull final Mapper<? super V, ? extends Statement<R>> mapper) {
    return propagate(new ThenIfStatementHandler<V, R>(mapper));
  }

  @NotNull
  public <R> Statement<R> thenTry(@NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final Mapper<? super V, R> mapper) {
    return propagate(
        new TryStatementHandler<V, R>(closeable, new ThenStatementHandler<V, R>(mapper),
            mLogger.getName()));
  }

  @NotNull
  public Statement<V> thenTryDo(@NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final Observer<? super V> observer) {
    return propagate(
        new TryStatementHandler<V, V>(closeable, new ThenDoStatementHandler<V, V>(observer),
            mLogger.getName()));
  }

  @NotNull
  public <R> Statement<R> thenTryIf(@NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final Mapper<? super V, ? extends Statement<R>> mapper) {
    return propagate(new TryIfStatementHandler<V, R>(closeable, mapper, mLogger.getName()));
  }

  @NotNull
  public Statement<V> whenDone(@NotNull final Action action) {
    return propagate(new DoneStatementHandler<V>(action));
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

  public void to(@NotNull final Evaluation<? super V> evaluation) {
    checkEvaluated();
    propagate(new ToEvaluationStatementHandler<V>(evaluation)).consume();
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

  private void checkEvaluated() {
    if (!mIsEvaluated) {
      ConstantConditions.unsupported("the statement has not been evaluated", "checkEvaluated");
    }
  }

  private void checkFinal() {
    if (mPropagation != null) {
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
  private <R> Statement<R> propagate(@NotNull final StatementHandler<V, R> handler) {
    return propagate(new PropagationHandler<V, R>(handler));
  }

  @NotNull
  @SuppressWarnings("unchecked")
  private <R> Statement<R> propagate(@NotNull final StatementPropagation<V, R> propagation) {
    final PropagationHead<?> head = mHead;
    final Logger logger = mLogger;
    final Runnable propagate;
    final Observer<? extends Evaluation<?>> observer = mObserver;
    if (mIsFork) {
      final Statement<R> forked =
          new DefaultStatement<V>(((ForkObserver<?, V>) observer).newObserver(), mIsEvaluated,
              logger).propagate(propagation);
      synchronized (mMutex) {
        final ArrayList<WeakReference<Statement<?>>> newForked =
            new ArrayList<WeakReference<Statement<?>>>();
        final Iterator<WeakReference<Statement<?>>> iterator = mForked.iterator();
        while (iterator.hasNext()) {
          final WeakReference<Statement<?>> next = iterator.next();
          if (next.get() == null) {
            iterator.remove();

          } else {
            newForked.add(next);
          }
        }

        newForked.add(new WeakReference<Statement<?>>(forked));
        mForked = newForked;
      }

      return forked;
    }

    synchronized (mMutex) {
      if (mPropagation != null) {
        throw new IllegalStateException("the statement evaluation is already propagated");

      } else {
        propagation.setLogger(logger);
        propagate = head.propagate(propagation);
        mState = StatementState.Propagated;
        mPropagation = propagation;
        mMutex.notifyAll();
      }
    }

    final DefaultStatement<R> statement =
        new DefaultStatement<R>((Observer<Evaluation<?>>) observer, mIsEvaluated, logger, head,
            mTail, propagation);
    if (propagate != null) {
      propagate.run();
    }

    return statement;
  }

  @NotNull
  private Observer<Evaluation<?>> renewObserver() {
    final Observer<Evaluation<?>> observer = mObserver;
    if (observer instanceof RenewableObserver) {
      return ((RenewableObserver<Evaluation<?>>) observer).renew();
    }

    return observer;
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    final ArrayList<StatementPropagation<?, ?>> propagations =
        new ArrayList<StatementPropagation<?, ?>>();
    final PropagationHead<?> head = mHead;
    StatementPropagation<?, ?> propagation = head;
    while (propagation != mTail) {
      if (propagation != head) {
        propagations.add(propagation);
      }

      propagation = propagation.mNext;
    }

    if (propagation != head) {
      propagations.add(propagation);
    }

    return new StatementProxy(mObserver, mIsEvaluated, mLogger.getName(), propagations);
  }

  private static class ForkObserver<S, V> extends StatementHandler<V, V>
      implements RenewableObserver<Evaluation<V>>, Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final ArrayList<Evaluation<V>> mEvaluations = new ArrayList<Evaluation<V>>();

    private final Executor mExecutor;

    private final Forker<S, V, Evaluation<V>, Statement<V>> mForker;

    private Throwable mFailure;

    private S mStack;

    private DefaultStatement<V> mStatement;

    @SuppressWarnings("unchecked")
    private ForkObserver(@NotNull final DefaultStatement<V> statement,
        @NotNull final Forker<S, ? super V, ? super Evaluation<V>, ? super Statement<V>> forker) {
      mForker =
          (Forker<S, V, Evaluation<V>, Statement<V>>) ConstantConditions.notNull("forker", forker);
      mExecutor = withThrottling(1, loopExecutor());
      mStatement = statement;
    }

    public void accept(final Evaluation<V> evaluation) {
      mExecutor.execute(new Runnable() {

        public void run() {
          try {
            final DefaultStatement<V> statement = mStatement;
            try {
              mStack = mForker.init(statement);

            } finally {
              statement.propagate(ForkObserver.this);
            }

          } catch (final Throwable t) {
            mFailure = t;
          }
        }
      });
    }

    boolean cancel(final boolean mayInterruptIfRunning) {
      return mStatement.cancel(mayInterruptIfRunning);
    }

    @Override
    void failure(@NotNull final Throwable failure, @NotNull final Evaluation<V> evaluation) {
      checkFailed();
      mExecutor.execute(new Runnable() {

        public void run() {
          final DefaultStatement<V> statement = mStatement;
          if (mFailure != null) {
            statement.mLogger.wrn("Ignoring failure: %s", failure);
            return;
          }

          try {
            final Forker<S, V, Evaluation<V>, Statement<V>> forker = mForker;
            final S stack = forker.failure(mStack, failure, statement);
            mStack = forker.done(stack, statement);

          } catch (final Throwable t) {
            mFailure = t;
            clearEvaluations(t);
          }
        }
      });
    }

    @NotNull
    public ForkObserver<S, V> renew() {
      return new ForkObserver<S, V>(mStatement.evaluate(), mForker);
    }

    @Override
    void value(final V value, @NotNull final Evaluation<V> evaluation) {
      checkFailed();
      mExecutor.execute(new Runnable() {

        public void run() {
          final DefaultStatement<V> statement = mStatement;
          if (mFailure != null) {
            statement.mLogger.wrn("Ignoring value: %s", value);
            return;
          }

          try {
            final Forker<S, V, Evaluation<V>, Statement<V>> forker = mForker;
            final S stack = forker.value(mStack, value, statement);
            mStack = forker.done(stack, statement);

          } catch (final Throwable t) {
            mFailure = t;
            clearEvaluations(t);
          }
        }
      });
    }

    @NotNull
    Observer<Evaluation<V>> newObserver() {
      return new PropagationForkObserver<S, V>(this);
    }

    void propagate(final Evaluation<V> evaluation) {
      mExecutor.execute(new Runnable() {

        public void run() {
          final Throwable failure = mFailure;
          if (failure != null) {
            Asyncs.failSafe(evaluation, failure);
            return;
          }

          mEvaluations.add(evaluation);
          try {
            mStack = mForker.evaluation(mStack, evaluation, mStatement);

          } catch (final Throwable t) {
            mFailure = t;
            clearEvaluations(t);
          }
        }
      });
    }

    void stopPropagation(final Evaluation<V> evaluation) {
      mExecutor.execute(new Runnable() {

        public void run() {
          mEvaluations.remove(evaluation);
        }
      });
    }

    private void checkFailed() {
      final Throwable failure = mFailure;
      if (failure != null) {
        throw FailureException.wrap(failure);
      }
    }

    private void clearEvaluations(@NotNull final Throwable failure) {
      final ArrayList<Evaluation<V>> evaluations = mEvaluations;
      for (final Evaluation<V> evaluation : evaluations) {
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

      private ObserverProxy(final DefaultStatement<V> statement,
          final Forker<S, ? super V, ? super Evaluation<V>, ? super Statement<V>> forker) {
        super(statement, proxy(forker));
      }

      @NotNull
      @SuppressWarnings("unchecked")
      private Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ForkObserver<S, V>((DefaultStatement<V>) args[0],
              (Forker<S, ? super V, ? super Evaluation<V>, ? super Statement<V>>) args[1]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }
  }

  private static class PropagationConsume<V> extends StatementPropagation<V, Object> {

    private volatile WeakReference<StatementPropagation<Object, ?>> mNext =
        new WeakReference<StatementPropagation<Object, ?>>(null);

    @NotNull
    StatementPropagation<V, Object> copy() {
      return new PropagationConsume<V>();
    }

    private void complete() {
      final StatementPropagation<Object, ?> next = mNext.get();
      if (next != null) {
        next.set(null);
      }
    }

    void fail(final StatementPropagation<Object, ?> next, final Throwable failure) {
      getLogger().dbg("Consuming failure: %s", failure);
      complete();
    }

    void set(final StatementPropagation<Object, ?> next, final V value) {
      getLogger().dbg("Consuming value: %s", value);
      complete();
    }

    @Override
    void setNext(@NotNull final StatementPropagation<Object, ?> next) {
      mNext = new WeakReference<StatementPropagation<Object, ?>>(next);
    }
  }

  private static class PropagationForkObserver<S, V>
      implements RenewableObserver<Evaluation<V>>, Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final ForkObserver<S, V> mObserver;

    private PropagationForkObserver(@NotNull final ForkObserver<S, V> observer) {
      mObserver = observer;
    }

    @NotNull
    public PropagationForkObserver<S, V> renew() {
      final ForkObserver<S, V> observer = mObserver.renew();
      observer.accept(null);
      return new PropagationForkObserver<S, V>(observer);
    }

    void cancel(final Evaluation<V> evaluation) {
      mObserver.stopPropagation(evaluation);
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
          return new PropagationForkObserver<S, V>(observer);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public void accept(final Evaluation<V> evaluation) {
      mObserver.propagate(evaluation);
    }

  }

  private static class PropagationHandler<V, R> extends StatementPropagation<V, R>
      implements Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final StatementHandler<V, R> mHandler;

    PropagationHandler(@NotNull final StatementHandler<V, R> handler) {
      mHandler = handler;
    }

    @NotNull
    private Object writeReplace() throws ObjectStreamException {
      return new PropagationProxy<V, R>(mHandler);
    }

    private static class PropagationProxy<V, R> implements Serializable {

      private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

      private final StatementHandler<V, R> mHandler;

      private PropagationProxy(@NotNull final StatementHandler<V, R> handler) {
        mHandler = handler;
      }

      @NotNull
      private Object readResolve() throws ObjectStreamException {
        try {
          return new PropagationHandler<V, R>(mHandler);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    @NotNull
    StatementPropagation<V, R> copy() {
      return new PropagationHandler<V, R>(mHandler.renew());
    }

    void fail(final StatementPropagation<R, ?> next, final Throwable failure) {
      try {
        getLogger().dbg("Processing failure with reason: %s", failure);
        mHandler.failure(failure, next);

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Statement has been cancelled");
        next.failSafe(e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing failure with reason: %s", failure);
        next.failSafe(t);
      }
    }

    void set(final StatementPropagation<R, ?> next, final V value) {
      try {
        getLogger().dbg("Processing value: %s", value);
        mHandler.value(value, next);

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Statement has been cancelled");
        next.failSafe(e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing value: %s", value);
        next.failSafe(t);
      }
    }
  }

  private static class PropagationHead<V> extends StatementPropagation<V, V> {

    private final Object mMutex = new Object();

    private Throwable mException;

    private StateEvaluating mInnerState = new StateEvaluating();

    private StatementState mState = StatementState.Evaluating;

    private Object mValue;

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

    @Nullable
    Runnable propagate(@NotNull final StatementPropagation<?, ?> propagation) {
      final Runnable propagate = mInnerState.propagate(propagation);
      mState = StatementState.Evaluating;
      return propagate;
    }

    private class StateEvaluating {

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

      @Nullable
      Runnable propagate(@NotNull final StatementPropagation<?, ?> propagation) {
        return null;
      }
    }

    private class StateFailed extends StateEvaluating {

      @Nullable
      @Override
      FailureException getFailure() {
        return FailureException.wrap(mException);
      }

      Object getValue() {
        throw FailureException.wrap(mException);
      }

      @Nullable
      @Override
      Runnable propagate(@NotNull final StatementPropagation<?, ?> propagation) {
        getLogger().dbg("Propagating statement [%s => %s]", StatementState.Failed,
            StatementState.Evaluating);
        final Throwable exception = mException;
        mException = null;
        mInnerState = new StateEvaluating();
        return new Runnable() {

          public void run() {
            propagation.failSafe(exception);
          }
        };
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
      Runnable propagate(@NotNull final StatementPropagation<?, ?> propagation) {
        getLogger().dbg("Propagating statement [%s => %s]", StatementState.Set,
            StatementState.Evaluating);
        final Object value = mValue;
        mValue = null;
        mInnerState = new StateEvaluating();
        return new Runnable() {

          @SuppressWarnings("unchecked")
          public void run() {
            ((StatementPropagation<Object, ?>) propagation).set(value);
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
    PropagationHead<V> copy() {
      return new PropagationHead<V>();
    }

    @Override
    public void fail(final StatementPropagation<V, ?> next, final Throwable failure) {
      next.fail(failure);
    }

    @Override
    public void set(final StatementPropagation<V, ?> next, final V value) {
      next.set(value);
    }
  }

  private static abstract class StatementPropagation<V, R> implements Evaluation<V> {

    private volatile StateEvaluating mInnerState = new StateEvaluating();

    private Logger mLogger;

    private volatile StatementPropagation<R, ?> mNext;

    public final void fail(@NotNull final Throwable failure) {
      ConstantConditions.notNull("failure", failure);
      mInnerState.fail(failure);
      fail(mNext, failure);
    }

    public final void set(final V value) {
      mInnerState.set();
      set(mNext, value);
    }

    boolean cancel(@NotNull final Throwable exception) {
      if (mInnerState.failSafe(exception)) {
        fail(mNext, exception);
        return true;
      }

      return false;
    }

    @NotNull
    abstract StatementPropagation<V, R> copy();

    abstract void fail(StatementPropagation<R, ?> next, Throwable failure);

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

    abstract void set(StatementPropagation<R, ?> next, V value);

    void setNext(@NotNull final StatementPropagation<R, ?> next) {
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
  }

  private static class StatementProxy extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private StatementProxy(final Observer<Evaluation<?>> observer, final boolean isEvaluated,
        final String loggerName, final List<StatementPropagation<?, ?>> propagations) {
      super(proxy(observer), isEvaluated, loggerName, propagations);
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        final PropagationHead<Object> head = new PropagationHead<Object>();
        StatementPropagation<?, ?> tail = head;
        for (final StatementPropagation<?, ?> propagation : (List<StatementPropagation<?, ?>>)
            args[3]) {
          ((StatementPropagation<?, Object>) tail).setNext(
              (StatementPropagation<Object, ?>) propagation);
          tail = propagation;
        }

        return new DefaultStatement<Object>((Observer<Evaluation<?>>) args[0], (Boolean) args[1],
            (String) args[2], head, (StatementPropagation<?, Object>) tail);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }

  private class PropagationTail extends StatementPropagation<V, Object> {

    private PropagationTail() {
      setLogger(mLogger);
    }

    @NotNull
    @Override
    StatementPropagation<V, Object> copy() {
      return ConstantConditions.unsupported();
    }

    @Override
    boolean isTail() {
      return true;
    }

    @Override
    void fail(final StatementPropagation<Object, ?> next, final Throwable reason) {
      final StatementPropagation<V, ?> propagation;
      synchronized (mMutex) {
        try {
          if ((propagation = mPropagation) == null) {
            mState = StatementState.Failed;
            mHead.innerFail(reason);
            return;
          }

        } finally {
          mMutex.notifyAll();
        }
      }

      propagation.fail(reason);
    }

    @Override
    void set(final StatementPropagation<Object, ?> next, final V value) {
      final StatementPropagation<V, ?> propagation;
      synchronized (mMutex) {
        try {
          if ((propagation = mPropagation) == null) {
            mState = StatementState.Set;
            mHead.innerSet(value);
            return;
          }

        } finally {
          mMutex.notifyAll();
        }
      }

      propagation.set(value);
    }
  }
}

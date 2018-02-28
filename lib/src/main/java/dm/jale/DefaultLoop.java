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
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import dm.jale.config.BuildConfig;
import dm.jale.eventual.Action;
import dm.jale.eventual.Completer;
import dm.jale.eventual.Evaluation;
import dm.jale.eventual.EvaluationCollection;
import dm.jale.eventual.EvaluationState;
import dm.jale.eventual.FailureException;
import dm.jale.eventual.Loop;
import dm.jale.eventual.Mapper;
import dm.jale.eventual.Observer;
import dm.jale.eventual.Provider;
import dm.jale.eventual.RuntimeInterruptedException;
import dm.jale.eventual.RuntimeTimeoutException;
import dm.jale.eventual.Settler;
import dm.jale.eventual.SimpleState;
import dm.jale.eventual.Statement;
import dm.jale.eventual.Updater;
import dm.jale.executor.ExecutorPool;
import dm.jale.log.Logger;
import dm.jale.util.ConstantConditions;
import dm.jale.util.DoubleQueue;
import dm.jale.util.Iterables;
import dm.jale.util.SerializableProxy;
import dm.jale.util.TimeUnits;
import dm.jale.util.TimeUnits.Condition;

import static dm.jale.executor.ExecutorPool.loopExecutor;
import static dm.jale.executor.ExecutorPool.withThrottling;

/**
 * Created by davide-maestroni on 02/01/2018.
 */
class DefaultLoop<V> implements Loop<V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final PropagationHead<?> mHead;

  private final boolean mIsEvaluated;

  private final boolean mIsFork;

  private final Logger mLogger;

  private final Object mMutex;

  private final Observer<EvaluationCollection<?>> mObserver;

  private final LoopPropagation<?, V> mTail;

  private ArrayList<WeakReference<Loop<?>>> mForked = new ArrayList<WeakReference<Loop<?>>>();

  private LoopPropagation<V, ?> mPropagation;

  private StatementState mState = StatementState.Evaluating;

  @SuppressWarnings("unchecked")
  DefaultLoop(@NotNull final Observer<? super EvaluationCollection<V>> observer,
      final boolean isEvaluated, @Nullable final String loggerName) {
    mObserver = (Observer<EvaluationCollection<?>>) observer;
    mIsEvaluated = isEvaluated;
    mIsFork = false;
    mLogger = Logger.newLogger(this, loggerName, Locale.ENGLISH);
    final PropagationHead<V> head = new PropagationHead<V>();
    head.setLogger(mLogger);
    head.setNext(new PropagationTail(head));
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
  private DefaultLoop(@NotNull final Observer<? super EvaluationCollection<V>> observer,
      final boolean isEvaluated, @NotNull final Logger logger) {
    // forking
    mObserver = (Observer<EvaluationCollection<?>>) observer;
    mIsEvaluated = isEvaluated;
    mIsFork = (observer instanceof ForkObserver);
    mLogger = logger;
    final PropagationHead<V> head = new PropagationHead<V>();
    head.setLogger(mLogger);
    head.setNext(new PropagationTail(head));
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
  private DefaultLoop(@NotNull final Observer<EvaluationCollection<?>> observer,
      final boolean isEvaluated, @Nullable final String loggerName,
      @NotNull final PropagationHead<?> head, @NotNull final LoopPropagation<?, V> tail) {
    // serialization
    mObserver = observer;
    mIsEvaluated = isEvaluated;
    mIsFork = (observer instanceof ForkObserver);
    mLogger = Logger.newLogger(this, loggerName, Locale.ENGLISH);
    mMutex = head.getMutex();
    mHead = head;
    mTail = tail;
    tail.setNext(new PropagationTail((PropagationHead<V>) head));
    LoopPropagation<?, ?> propagation = head;
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
  private DefaultLoop(@NotNull final Observer<EvaluationCollection<?>> observer,
      final boolean isEvaluated, @NotNull final Logger logger,
      @NotNull final PropagationHead<?> head, @NotNull final LoopPropagation<?, V> tail,
      final boolean observe) {
    // copy/propagation
    mObserver = observer;
    mIsEvaluated = isEvaluated;
    mIsFork = (observer instanceof ForkObserver);
    mLogger = logger;
    mMutex = head.getMutex();
    mHead = head;
    mTail = tail;
    tail.setNext(new PropagationTail((PropagationHead<V>) head));
    if (observe) {
      try {
        observer.accept(head);

      } catch (final Throwable t) {
        head.failSafe(t);
      }
    }
  }

  @SuppressWarnings(
      {"unchecked", "ConstantConditions", "SynchronizationOnLocalVariableOrMethodParameter"})
  private static <V> void addTo(@NotNull final Object mutex,
      @NotNull final NestedQueue<SimpleState<V>> queue,
      @NotNull final LoopPropagation<V, ?> propagation) {
    boolean isValue;
    SimpleState<V> state;
    ArrayList<?> outputs = null;
    synchronized (mutex) {
      if (queue.isEmpty()) {
        return;
      }

      state = queue.removeFirst();
      isValue = state.isSet();
      if (isValue) {
        outputs = new ArrayList<V>();
      }
    }

    while (state != null) {
      if (isValue) {
        if (state.isSet()) {
          ((ArrayList<V>) outputs).add(state.value());

        } else {
          propagation.addValues((Iterable<V>) outputs);
          final ArrayList<Throwable> failures = new ArrayList<Throwable>();
          failures.add(state.failure());
          outputs = failures;
        }

      } else {
        if (state.isFailed()) {
          ((ArrayList<Throwable>) outputs).add(state.failure());

        } else {
          propagation.addFailures((Iterable<Throwable>) outputs);
          final ArrayList<V> values = new ArrayList<V>();
          values.add(state.value());
          outputs = values;
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

  @SuppressWarnings("unchecked")
  public boolean cancel(final boolean mayInterruptIfRunning) {
    final Observer<? extends EvaluationCollection<?>> observer = mObserver;
    if (mIsFork) {
      if (((ForkObserver<?, ?>) observer).cancel(mayInterruptIfRunning)) {
        return true;
      }

      boolean isCancelled = false;
      final Iterator<WeakReference<Loop<?>>> iterator = mForked.iterator();
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

    LoopPropagation<?, ?> propagation = mHead;
    if (observer instanceof PropagationForkObserver) {
      ((PropagationForkObserver) observer).cancel(propagation);
    }

    final CancellationException exception = new CancellationException("loop is cancelled");
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

  public void consume() {
    checkEvaluated();
    propagate(new PropagationConsume<V>());
  }

  @NotNull
  public <R> Statement<R> eventually(@NotNull final Mapper<? super Iterable<V>, R> mapper) {
    return toStatement().eventually(mapper);
  }

  @NotNull
  public Statement<Iterable<V>> eventuallyDo(
      @NotNull final Observer<? super Iterable<V>> observer) {
    return toStatement().eventuallyDo(observer);
  }

  @NotNull
  public <R> Statement<R> eventuallyIf(
      @NotNull final Mapper<? super Iterable<V>, ? extends Statement<R>> mapper) {
    return toStatement().eventuallyIf(mapper);
  }

  @NotNull
  public <R> Statement<R> eventuallyTry(
      @NotNull final Mapper<? super Iterable<V>, ? extends Closeable> closeable,
      @NotNull final Mapper<? super Iterable<V>, R> mapper) {
    return toStatement().eventuallyTry(closeable, mapper);
  }

  @NotNull
  public Statement<Iterable<V>> eventuallyTryDo(
      @NotNull final Mapper<? super Iterable<V>, ? extends Closeable> closeable,
      @NotNull final Observer<? super Iterable<V>> observer) {
    return toStatement().eventuallyTryDo(closeable, observer);
  }

  @NotNull
  public <R> Statement<R> eventuallyTryIf(
      @NotNull final Mapper<? super Iterable<V>, ? extends Closeable> closeable,
      @NotNull final Mapper<? super Iterable<V>, ? extends Statement<R>> mapper) {
    return toStatement().eventuallyTryIf(closeable, mapper);
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

  public Iterable<V> getValue() {
    return getValue(-1, TimeUnit.MILLISECONDS);
  }

  @SuppressWarnings("unchecked")
  public Iterable<V> getValue(final long timeout, @NotNull final TimeUnit timeUnit) {
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
      return (mPropagation == null);
    }
  }

  @NotNull
  public Statement<Iterable<V>> whenDone(@NotNull final Action action) {
    return toStatement().whenDone(action);
  }

  @NotNull
  public Loop<V> elseCatch(@NotNull final Mapper<? super Throwable, ? extends Iterable<V>> mapper,
      @Nullable final Class<?>... exceptionTypes) {
    return yield(new ElseCatchYielder<V>(mapper, Eventuals.cloneExceptionTypes(exceptionTypes)));
  }

  @NotNull
  public Loop<V> elseDo(@NotNull final Observer<? super Throwable> observer,
      @Nullable final Class<?>... exceptionTypes) {
    return yield(new ElseDoYielder<V>(observer, Eventuals.cloneExceptionTypes(exceptionTypes)));
  }

  @NotNull
  public Loop<V> elseIf(
      @NotNull final Mapper<? super Throwable, ? extends Statement<? extends Iterable<V>>> mapper,
      @Nullable final Class<?>... exceptionTypes) {
    return yield(new ElseIfYielder<V>(mapper, Eventuals.cloneExceptionTypes(exceptionTypes)));
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public DefaultLoop<V> evaluate() {
    final Logger logger = mLogger;
    final PropagationHead<?> head = mHead;
    final PropagationHead<?> newHead = head.copy();
    newHead.setLogger(logger);
    LoopPropagation<?, ?> newTail = newHead;
    LoopPropagation<?, ?> next = head;
    while (next != mTail) {
      next = next.mNext;
      LoopPropagation<?, ?> propagation = next.copy();
      propagation.setLogger(logger);
      ((LoopPropagation<?, Object>) newTail).setNext((LoopPropagation<Object, ?>) propagation);
      newTail = propagation;
    }

    return new DefaultLoop<V>(renewObserver(), true, logger, newHead,
        (LoopPropagation<?, V>) newTail, true);
  }

  @NotNull
  public Loop<V> evaluated() {
    return (mIsEvaluated) ? this : evaluate();
  }

  @NotNull
  public <S> Loop<V> fork(
      @NotNull final Forker<S, ? super Iterable<V>, ? super Evaluation<Iterable<V>>, ? super
          Statement<Iterable<V>>> forker) {
    return forkLoop(new StatementLoopForker<S, V>(forker));
  }

  @NotNull
  public <S> Loop<V> fork(@Nullable final Mapper<? super Statement<Iterable<V>>, S> init,
      @Nullable final Updater<S, ? super Iterable<V>, ? super Statement<Iterable<V>>> value,
      @Nullable final Updater<S, ? super Throwable, ? super Statement<Iterable<V>>> failure,
      @Nullable final Completer<S, ? super Statement<Iterable<V>>> done,
      @Nullable final Updater<S, ? super Evaluation<Iterable<V>>, ? super Statement<Iterable<V>>>
          evaluation) {
    return fork(
        new ComposedStatementForker<S, Iterable<V>>(init, value, failure, done, evaluation));
  }

  @NotNull
  public Loop<V> forkOn(@NotNull final Executor executor) {
    return forkLoop(new ExecutorLoopForker<V>(withThrottling(1, executor), mLogger.getName()));
  }

  @NotNull
  public Loop<V> elseForEach(@NotNull final Mapper<? super Throwable, V> mapper,
      @Nullable Class<?>... exceptionTypes) {
    return propagate(
        new ElseCatchLoopExpression<V>(mapper, Eventuals.cloneExceptionTypes(exceptionTypes)));
  }

  @NotNull
  public Loop<V> elseForEachDo(@NotNull final Observer<? super Throwable> observer,
      @Nullable Class<?>... exceptionTypes) {
    return propagate(
        new ElseDoLoopExpression<V>(observer, Eventuals.cloneExceptionTypes(exceptionTypes)));
  }

  @NotNull
  public Loop<V> elseForEachIf(
      @NotNull final Mapper<? super Throwable, ? extends Statement<? extends V>> mapper,
      @Nullable Class<?>... exceptionTypes) {
    return propagate(
        new ElseIfStatementExpression<V>(mapper, Eventuals.cloneExceptionTypes(exceptionTypes)));
  }

  @NotNull
  public Loop<V> elseForEachLoop(
      @NotNull final Mapper<? super Throwable, ? extends Iterable<? extends V>> mapper,
      @Nullable Class<?>... exceptionTypes) {
    return propagate(
        new ElseLoopLoopExpression<V>(mapper, Eventuals.cloneExceptionTypes(exceptionTypes)));
  }

  @NotNull
  public Loop<V> elseForEachLoopIf(
      @NotNull final Mapper<? super Throwable, ? extends Loop<? extends V>> mapper,
      @Nullable Class<?>... exceptionTypes) {
    return propagate(new ElseLoopIfStatementExpression<V>(mapper,
        Eventuals.cloneExceptionTypes(exceptionTypes)));
  }

  @NotNull
  public Loop<V> elseForEachOrderedIf(
      @NotNull final Mapper<? super Throwable, ? extends Statement<? extends V>> mapper,
      @Nullable Class<?>... exceptionTypes) {
    return propagateOrdered(
        new ElseIfStatementExpression<V>(mapper, Eventuals.cloneExceptionTypes(exceptionTypes)));
  }

  @NotNull
  public Loop<V> elseForEachOrderedLoopIf(
      @NotNull final Mapper<? super Throwable, ? extends Loop<? extends V>> mapper,
      @Nullable Class<?>... exceptionTypes) {
    return propagateOrdered(new ElseLoopIfStatementExpression<V>(mapper,
        Eventuals.cloneExceptionTypes(exceptionTypes)));
  }

  @NotNull
  public <R> Loop<R> eventuallyLoop(
      @NotNull final Mapper<? super Iterable<V>, ? extends Iterable<R>> mapper) {
    return yield(ListYielder.<V>instance()).forEachLoop(mapper);
  }

  @NotNull
  public <R> Loop<R> eventuallyLoopIf(
      @NotNull final Mapper<? super Iterable<V>, ? extends Loop<R>> mapper) {
    return yield(ListYielder.<V>instance()).forEachLoopIf(mapper);
  }

  @NotNull
  public <R> Loop<R> forEach(@NotNull final Mapper<? super V, R> mapper) {
    return propagate(new ForEachLoopExpression<V, R>(mapper));
  }

  @NotNull
  public Loop<V> forEachDo(@NotNull final Observer<? super V> observer) {
    return propagate(new ForEachDoLoopExpression<V, V>(observer));
  }

  @NotNull
  public Loop<V> forEachDone(@NotNull final Action action) {
    return propagate(new DoneLoopExpression<V>(action));
  }

  @NotNull
  public <R> Loop<R> forEachIf(@NotNull final Mapper<? super V, ? extends Statement<R>> mapper) {
    return propagate(new EventuallyIfStatementExpression<V, R>(mapper));
  }

  @NotNull
  public <R> Loop<R> forEachLoop(@NotNull final Mapper<? super V, ? extends Iterable<R>> mapper) {
    return propagate(new ForEachLoopLoopExpression<V, R>(mapper));
  }

  @NotNull
  public <R> Loop<R> forEachLoopIf(@NotNull final Mapper<? super V, ? extends Loop<R>> mapper) {
    return propagate(new ForEachLoopIfStatementExpression<V, R>(mapper));
  }

  @NotNull
  public <R> Loop<R> forEachOrderedIf(
      @NotNull final Mapper<? super V, ? extends Statement<R>> mapper) {
    return propagateOrdered(new EventuallyIfStatementExpression<V, R>(mapper));
  }

  @NotNull
  public <R> Loop<R> forEachOrderedLoopIf(
      @NotNull final Mapper<? super V, ? extends Loop<R>> mapper) {
    return propagateOrdered(new ForEachLoopIfStatementExpression<V, R>(mapper));
  }

  @NotNull
  public <R> Loop<R> forEachOrderedTryIf(
      @NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final Mapper<? super V, ? extends Statement<R>> mapper) {
    return propagateOrdered(
        new TryIfStatementExpression<V, R>(closeable, mapper, mLogger.getName()));
  }

  @NotNull
  public <R> Loop<R> forEachOrderedTryLoopIf(
      @NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final Mapper<? super V, ? extends Loop<R>> mapper) {
    return propagateOrdered(new TryStatementLoopExpression<V, R>(closeable,
        new ForEachLoopIfStatementExpression<V, R>(mapper), mLogger.getName()));
  }

  @NotNull
  public <R> Loop<R> forEachTry(@NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final Mapper<? super V, R> mapper) {
    return propagate(
        new TryStatementExpression<V, R>(closeable, new EventuallyStatementExpression<V, R>(mapper),
            mLogger.getName()));
  }

  @NotNull
  public Loop<V> forEachTryDo(@NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final Observer<? super V> observer) {
    return propagate(new TryStatementExpression<V, V>(closeable,
        new EventuallyDoStatementExpression<V, V>(observer), mLogger.getName()));
  }

  @NotNull
  public <R> Loop<R> forEachTryIf(@NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final Mapper<? super V, ? extends Statement<R>> mapper) {
    return propagate(new TryIfStatementExpression<V, R>(closeable, mapper, mLogger.getName()));
  }

  @NotNull
  public <R> Loop<R> forEachTryLoop(@NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final Mapper<? super V, ? extends Iterable<R>> mapper) {
    return propagate(new TryStatementLoopExpression<V, R>(closeable,
        new ForEachLoopStatementExpression<V, R>(mapper), mLogger.getName()));
  }

  @NotNull
  public <R> Loop<R> forEachTryLoopIf(
      @NotNull final Mapper<? super V, ? extends Closeable> closeable,
      @NotNull final Mapper<? super V, ? extends Loop<R>> mapper) {
    return propagate(new TryStatementLoopExpression<V, R>(closeable,
        new ForEachLoopIfStatementExpression<V, R>(mapper), mLogger.getName()));
  }

  @NotNull
  public <S> Loop<V> forkLoop(
      @NotNull final Forker<S, ? super V, ? super EvaluationCollection<V>, ? super Loop<V>>
          forker) {
    return new DefaultLoop<V>(new ForkObserver<S, V>(this, forker), mIsEvaluated, mLogger);
  }

  @NotNull
  public <S> Loop<V> forkLoop(@Nullable final Mapper<? super Loop<V>, S> init,
      @Nullable final Updater<S, ? super V, ? super Loop<V>> value,
      @Nullable final Updater<S, ? super Throwable, ? super Loop<V>> failure,
      @Nullable final Completer<S, ? super Loop<V>> done,
      @Nullable final Updater<S, ? super EvaluationCollection<V>, ? super Loop<V>> evaluation) {
    return forkLoop(new ComposedLoopForker<S, V>(init, value, failure, done, evaluation));
  }

  @NotNull
  public Loop<V> forkOn(@NotNull final Executor executor, final int maxValues,
      final int maxFailures) {
    return forkLoop(
        new ExecutorLoopBatchForker<V>(withThrottling(1, executor), maxValues, maxFailures,
            mLogger.getName()));
  }

  @NotNull
  public Loop<V> forkOnParallel(@NotNull final Executor executor, final int maxValues,
      final int maxFailures) {
    return forkLoop(
        new ExecutorLoopBatchForker<V>(executor, maxValues, maxFailures, mLogger.getName()));
  }

  @NotNull
  public Loop<V> forkOnParallel(@NotNull final Executor executor) {
    return forkLoop(new ExecutorLoopForker<V>(executor, mLogger.getName()));
  }

  @NotNull
  public Generator<EvaluationState<V>> generateStates() {
    return generateStates(-1, TimeUnit.MILLISECONDS);
  }

  @NotNull
  public Generator<EvaluationState<V>> generateStates(final long timeout,
      @NotNull final TimeUnit timeUnit) {
    checkSupported();
    return new StateGenerator<V>(this, timeout, timeUnit);
  }

  @NotNull
  public Generator<V> generateValues() {
    return generateValues(-1, TimeUnit.MILLISECONDS);
  }

  @NotNull
  public Generator<V> generateValues(final long timeout, @NotNull final TimeUnit timeUnit) {
    checkSupported();
    return new ValueGenerator<V>(this, timeout, timeUnit);
  }

  @NotNull
  public List<EvaluationState<V>> getStates(final int maxCount) {
    return getStates(maxCount, -1, TimeUnit.MILLISECONDS);
  }

  @NotNull
  public List<EvaluationState<V>> getStates(final int maxCount, final long timeout,
      @NotNull final TimeUnit timeUnit) {
    checkSupported();
    deadLockWarning(timeout);
    final PropagationHead<?> head = mHead;
    final ArrayList<EvaluationState<V>> outputs = new ArrayList<EvaluationState<V>>();
    synchronized (mMutex) {
      try {
        if (TimeUnits.waitUntil(mMutex, new Condition() {

          public boolean isTrue() {
            checkFinal();
            return ((head.getStates().size() >= maxCount) || head.getState().isDone());
          }
        }, timeout, timeUnit)) {
          @SuppressWarnings("unchecked") final DoubleQueue<SimpleState<V>> states =
              ((PropagationHead<V>) head).getStates();
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
    final PropagationHead<?> head = mHead;
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
              ((PropagationHead<V>) head).getStates();
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

  @SuppressWarnings("unchecked")
  public void to(@NotNull final EvaluationCollection<? super V> evaluation) {
    checkEvaluated();
    if (mIsFork) {
      @SuppressWarnings(
          "UnnecessaryLocalVariable") final Observer<? extends EvaluationCollection<?>> observer =
          mObserver;
      ((ForkObserver<?, V>) observer).propagate((EvaluationCollection<V>) evaluation);

    } else {
      propagate(new ToEvaluationLoopExpression<V>(evaluation)).consume();
    }
  }

  @NotNull
  public <S, R> Loop<R> yield(@NotNull final Yielder<S, ? super V, R> yielder) {
    return propagate(new YieldLoopExpression<S, V, R>(yielder, mLogger.getName()));
  }

  @NotNull
  public <S, R> Loop<R> yield(@Nullable final Provider<S> init,
      @Nullable final Mapper<S, ? extends Boolean> loop,
      @Nullable final Updater<S, ? super V, ? super YieldOutputs<R>> value,
      @Nullable final Updater<S, ? super Throwable, ? super YieldOutputs<R>> failure,
      @Nullable final Settler<S, ? super YieldOutputs<R>> done) {
    return yield(new ComposedYielder<S, V, R>(init, loop, value, failure, done));
  }

  @NotNull
  public <S, R> Loop<R> yieldOrdered(@NotNull final Yielder<S, ? super V, R> yielder) {
    return propagateOrdered(new YieldLoopExpression<S, V, R>(yielder, mLogger.getName()));
  }

  @NotNull
  public <S, R> Loop<R> yieldOrdered(@Nullable final Provider<S> init,
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

  public void to(@NotNull final Evaluation<? super Iterable<V>> evaluation) {
    to(new CollectionToEvaluation<V>(evaluation, mLogger));
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

  private void checkEvaluated() {
    if (!mIsEvaluated) {
      ConstantConditions.unsupported("the loop has not been evaluated", "checkEvaluated");
    }
  }

  private void checkFinal() {
    if (mPropagation != null) {
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
  private <R> Loop<R> propagate(@NotNull final StatementExpression<V, R> expression) {
    return propagate(new PropagationStatementExpression<V, R>(expression));
  }

  @NotNull
  private <R> Loop<R> propagate(@NotNull final StatementLoopExpression<V, R> expression) {
    return propagate(new PropagationStatementLoopExpression<V, R>(expression));
  }

  @NotNull
  private <R> Loop<R> propagate(@NotNull final LoopExpression<V, R> expression) {
    return propagate(new PropagationLoopExpression<V, R>(expression));
  }

  @NotNull
  @SuppressWarnings("unchecked")
  private <R> Loop<R> propagate(@NotNull final LoopPropagation<V, R> propagation) {
    final Logger logger = mLogger;
    final PropagationHead<?> head = mHead;
    final Runnable propagate;
    final Observer<? extends EvaluationCollection<?>> observer = mObserver;
    if (mIsFork) {
      final Loop<R> forked =
          new DefaultLoop<V>(((ForkObserver<?, V>) observer).newObserver(), mIsEvaluated,
              mLogger).propagate(propagation);
      synchronized (mMutex) {
        final ArrayList<WeakReference<Loop<?>>> newForked = new ArrayList<WeakReference<Loop<?>>>();
        final Iterator<WeakReference<Loop<?>>> iterator = mForked.iterator();
        while (iterator.hasNext()) {
          final WeakReference<Loop<?>> next = iterator.next();
          final Statement<?> statement = next.get();
          if (statement == null) {
            iterator.remove();

          } else if (statement.isDone()) {
            final DefaultLoop<?> defaultLoop = (DefaultLoop<?>) statement;
            ((PropagationForkObserver) defaultLoop.mObserver).cancel(defaultLoop.mHead);

          } else {
            newForked.add(next);
          }
        }

        newForked.add(new WeakReference<Loop<?>>(forked));
        mForked = newForked;
      }

      return forked;
    }

    final DefaultLoop<R> loop;
    synchronized (mMutex) {
      if (mPropagation != null) {
        throw new IllegalStateException("the loop evaluation is already propagated");

      } else {
        propagation.setLogger(logger);
        propagate = ((PropagationHead<V>) head).propagate(propagation);
        mState = StatementState.Propagated;
        mPropagation = propagation;
        mMutex.notifyAll();
      }

      loop = new DefaultLoop<R>((Observer<EvaluationCollection<?>>) observer, mIsEvaluated, logger,
          head, propagation, false);
    }

    if (propagate != null) {
      propagate.run();
    }

    ((LoopPropagation<?, Object>) mTail).setNext((LoopPropagation<Object, V>) propagation);
    return loop;
  }

  @NotNull
  private <R> Loop<R> propagateOrdered(@NotNull final LoopExpression<V, R> expression) {
    return propagate(new PropagationLoopExpressionOrdered<V, R>(expression));
  }

  @NotNull
  private <R> Loop<R> propagateOrdered(@NotNull final StatementExpression<V, R> expression) {
    return propagate(new PropagationStatementExpressionOrdered<V, R>(expression));
  }

  @NotNull
  private <R> Loop<R> propagateOrdered(@NotNull final StatementLoopExpression<V, R> expression) {
    return propagate(new PropagationStatementLoopExpressionOrdered<V, R>(expression));
  }

  @NotNull
  private Observer<EvaluationCollection<?>> renewObserver() {
    final Observer<EvaluationCollection<?>> observer = mObserver;
    if (observer instanceof RenewableObserver) {
      return ((RenewableObserver<EvaluationCollection<?>>) observer).renew();
    }

    return observer;
  }

  @NotNull
  private Statement<Iterable<V>> toStatement() {
    return new DefaultStatement<Iterable<V>>(new ToStatementObserver<V>(this), mIsEvaluated,
        mLogger.getName());
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    final ArrayList<LoopPropagation<?, ?>> propagations = new ArrayList<LoopPropagation<?, ?>>();
    final PropagationHead<?> head = mHead;
    LoopPropagation<?, ?> propagation = head;
    while (propagation != mTail) {
      if (propagation != head) {
        propagations.add(propagation);
      }

      propagation = propagation.mNext;
    }

    if (propagation != head) {
      propagations.add(propagation);
    }

    return new LoopProxy(mObserver, mIsEvaluated, mLogger.getName(), propagations);
  }

  private static class ForkObserver<S, V> extends LoopExpression<V, V>
      implements RenewableObserver<EvaluationCollection<V>>, Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final ArrayList<EvaluationCollection<V>> mEvaluations =
        new ArrayList<EvaluationCollection<V>>();

    private final Executor mExecutor;

    private final Forker<S, V, EvaluationCollection<V>, Loop<V>> mForker;

    private volatile Throwable mFailure;

    private DefaultLoop<V> mLoop;

    private S mStack;

    @SuppressWarnings("unchecked")
    private ForkObserver(@NotNull final DefaultLoop<V> loop,
        @NotNull final Forker<S, ? super V, ? super EvaluationCollection<V>, ? super Loop<V>>
            forker) {
      mForker =
          (Forker<S, V, EvaluationCollection<V>, Loop<V>>) ConstantConditions.notNull("forker",
              forker);
      mExecutor = withThrottling(1, loopExecutor());
      mLoop = loop;
    }

    public void accept(final EvaluationCollection<V> evaluation) {
      mExecutor.execute(new Runnable() {

        public void run() {
          try {
            final DefaultLoop<V> loop = mLoop;
            try {
              mStack = mForker.init(loop);

            } finally {
              loop.propagate(ForkObserver.this);
            }

          } catch (final Throwable t) {
            mFailure = t;
          }
        }
      });
    }

    @Override
    void addFailure(@NotNull final Throwable failure,
        @NotNull final EvaluationCollection<V> evaluation) {
      checkFailed();
      mExecutor.execute(new Runnable() {

        public void run() {
          final DefaultLoop<V> loop = mLoop;
          if (mFailure != null) {
            loop.mLogger.wrn("Ignoring failure: %s", failure);
            return;
          }

          try {
            mStack = mForker.failure(mStack, failure, loop);

          } catch (final Throwable t) {
            mFailure = t;
            clearEvaluations(t);
          }
        }
      });
    }

    @Override
    void addFailures(@Nullable final Iterable<? extends Throwable> failures,
        @NotNull final EvaluationCollection<V> evaluation) {
      checkFailed();
      mExecutor.execute(new Runnable() {

        public void run() {
          final DefaultLoop<V> loop = mLoop;
          if (mFailure != null) {
            loop.mLogger.wrn("Ignoring failures: %s", Iterables.toString(failures));
            return;
          }

          if (failures != null) {
            try {
              @SuppressWarnings(
                  "UnnecessaryLocalVariable") final Forker<S, V, EvaluationCollection<V>, Loop<V>>
                  forker = mForker;
              for (final Throwable failure : failures) {
                mStack = forker.failure(mStack, failure, loop);
              }

            } catch (final Throwable t) {
              mFailure = t;
              clearEvaluations(t);
            }
          }
        }
      });
    }

    @Override
    void addValue(final V value, @NotNull final EvaluationCollection<V> evaluation) {
      checkFailed();
      mExecutor.execute(new Runnable() {

        public void run() {
          final DefaultLoop<V> loop = mLoop;
          if (mFailure != null) {
            loop.mLogger.wrn("Ignoring value: %s", value);
            return;
          }

          try {
            mStack = mForker.value(mStack, value, loop);

          } catch (final Throwable t) {
            mFailure = t;
            clearEvaluations(t);
          }
        }
      });
    }

    @Override
    void addValues(@Nullable final Iterable<? extends V> values,
        @NotNull final EvaluationCollection<V> evaluation) {
      checkFailed();
      mExecutor.execute(new Runnable() {

        public void run() {
          final DefaultLoop<V> loop = mLoop;
          if (mFailure != null) {
            loop.mLogger.wrn("Ignoring values: %s", Iterables.toString(values));
            return;
          }

          if (values != null) {
            try {
              @SuppressWarnings(
                  "UnnecessaryLocalVariable") final Forker<S, V, EvaluationCollection<V>, Loop<V>>
                  forker = mForker;
              for (final V value : values) {
                mStack = forker.value(mStack, value, loop);
              }

            } catch (final Throwable t) {
              mFailure = t;
              clearEvaluations(t);
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
    void set(@NotNull final EvaluationCollection<V> evaluation) {
      checkFailed();
      mExecutor.execute(new Runnable() {

        public void run() {
          final DefaultLoop<V> loop = mLoop;
          if (mFailure != null) {
            loop.mLogger.wrn("Ignoring completion");
            return;
          }

          try {
            mStack = mForker.done(mStack, loop);

          } catch (final Throwable t) {
            mFailure = t;
            clearEvaluations(t);
          }
        }
      });
    }

    boolean cancel(final boolean mayInterruptIfRunning) {
      return mLoop.cancel(mayInterruptIfRunning);
    }

    @NotNull
    Observer<EvaluationCollection<V>> newObserver() {
      return new PropagationForkObserver<S, V>(this);
    }

    void propagate(final EvaluationCollection<V> evaluation) {
      mExecutor.execute(new Runnable() {

        public void run() {
          final Throwable failure = mFailure;
          if (failure != null) {
            Eventuals.failSafe(evaluation, failure);
            return;
          }

          mEvaluations.add(evaluation);
          try {
            mStack = mForker.evaluation(mStack, evaluation, mLoop);

          } catch (final Throwable t) {
            mFailure = t;
            clearEvaluations(t);
          }
        }
      });
    }

    void stopPropagation(final EvaluationCollection<V> evaluation) {
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
      final ArrayList<EvaluationCollection<V>> evaluations = mEvaluations;
      for (final EvaluationCollection<V> evaluation : evaluations) {
        Eventuals.failSafe(evaluation, failure);
      }

      evaluations.clear();
    }

    @NotNull
    private Object writeReplace() throws ObjectStreamException {
      return new ObserverProxy<S, V>(mLoop.evaluate(), mForker);
    }

    private static class ObserverProxy<S, V> extends SerializableProxy {

      private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

      private ObserverProxy(final DefaultLoop<V> loop,
          final Forker<S, ? super V, ? super EvaluationCollection<V>, ? super Loop<V>> forker) {
        super(loop, proxy(forker));
      }

      @NotNull
      @SuppressWarnings("unchecked")
      private Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ForkObserver<S, V>((DefaultLoop<V>) args[0],
              (Forker<S, ? super V, ? super EvaluationCollection<V>, ? super Loop<V>>) args[1]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }
  }

  private static abstract class LoopPropagation<V, R> implements EvaluationCollection<V> {

    private static final Runnable NO_OP = new Runnable() {

      public void run() {
      }
    };

    private final Object mMutex = new Object();

    private volatile StateEvaluating mInnerState = new StateEvaluating();

    private volatile Logger mLogger;

    private volatile LoopPropagation<R, ?> mNext;

    @NotNull
    public final EvaluationCollection<V> addFailure(@NotNull final Throwable failure) {
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

    abstract void addFailure(LoopPropagation<R, ?> next, @NotNull Throwable failure);

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

    abstract void addFailures(LoopPropagation<R, ?> next,
        @NotNull Iterable<? extends Throwable> failures);

    abstract void addValue(LoopPropagation<R, ?> next, V value);

    abstract void addValues(LoopPropagation<R, ?> next, @NotNull Iterable<? extends V> values);

    boolean cancel(@NotNull final Throwable exception) {
      final Runnable command;
      synchronized (mMutex) {
        command = mInnerState.cancel(exception);
      }

      if (command != null) {
        command.run();
        final LoopPropagation<R, ?> next = mNext;
        addFailure(next, exception);
        set(next);
        return true;
      }

      return false;
    }

    @NotNull
    abstract LoopPropagation<V, R> copy();

    final void failSafe(@NotNull final Throwable failure) {
      final Runnable command;
      synchronized (mMutex) {
        command = mInnerState.failSafe(failure);
      }

      if (command != null) {
        command.run();
        final LoopPropagation<R, ?> next = mNext;
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
        final LoopPropagation<R, ?> next = mNext;
        addValue(next, value);
        set(next);
      }
    }

    abstract void set(LoopPropagation<R, ?> next);

    void setNext(@NotNull LoopPropagation<R, ?> next) {
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
        throw FailureException.wrap(new IllegalStateException("loop has already completed"));
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
        throw new IllegalStateException("loop has already completed");
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
            state.addTo(LoopPropagation.this);
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
        mLogger.wrn("Loop has already completed");
        throw new IllegalStateException("loop has already completed");
      }

      @Nullable
      @Override
      Runnable addFailureSafe(@NotNull final Throwable failure) {
        mLogger.wrn("Loop has already completed");
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
        mLogger.wrn("Loop has already completed");
        throw new IllegalStateException("loop has already completed");
      }
    }

    @NotNull
    public EvaluationCollection<V> addFailures(
        @Nullable final Iterable<? extends Throwable> failures) {
      final Runnable command;
      synchronized (mMutex) {
        command = mInnerState.add();
      }

      if (command != null) {
        command.run();
        if (failures != null) {
          addFailures(mNext, ConstantConditions.notNullElements("failures", failures));
        }
      }

      return this;
    }

    @NotNull
    public final EvaluationCollection<V> addValue(final V value) {
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
    public final EvaluationCollection<V> addValues(@Nullable final Iterable<? extends V> values) {
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

    private LoopProxy(final Observer<EvaluationCollection<?>> observer, final boolean isEvaluated,
        final String loggerName, final List<LoopPropagation<?, ?>> propagations) {
      super(proxy(observer), isEvaluated, loggerName, propagations);
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        final PropagationHead<Object> head = new PropagationHead<Object>();
        LoopPropagation<?, ?> tail = head;
        for (final LoopPropagation<?, ?> propagation : (List<LoopPropagation<?, ?>>) args[3]) {
          ((LoopPropagation<?, Object>) tail).setNext((LoopPropagation<Object, ?>) propagation);
          tail = propagation;
        }

        return new DefaultLoop<Object>((Observer<EvaluationCollection<?>>) args[0],
            (Boolean) args[1], (String) args[2], head, (LoopPropagation<?, Object>) tail);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }

  private static class PropagationConsume<V> extends LoopPropagation<V, Object> {

    private volatile WeakReference<LoopPropagation<Object, ?>> mNext =
        new WeakReference<LoopPropagation<Object, ?>>(null);

    @Override
    void addFailure(final LoopPropagation<Object, ?> next, @NotNull final Throwable failure) {
      getLogger().dbg("Consuming failure: %s", failure);
    }

    private void complete() {
      final LoopPropagation<Object, ?> next = mNext.get();
      if (next != null) {
        next.set();
      }
    }

    @Override
    void setNext(@NotNull final LoopPropagation<Object, ?> next) {
      mNext = new WeakReference<LoopPropagation<Object, ?>>(next);
    }

    @Override
    void addFailures(final LoopPropagation<Object, ?> next,
        @NotNull final Iterable<? extends Throwable> failures) {
      getLogger().dbg("Consuming failures: %s", Iterables.toString(failures));
    }

    @Override
    void addValue(final LoopPropagation<Object, ?> next, final V value) {
      getLogger().dbg("Consuming value: %s", value);
    }

    @Override
    void addValues(final LoopPropagation<Object, ?> next,
        @NotNull final Iterable<? extends V> values) {
      getLogger().dbg("Consuming values: %s", Iterables.toString(values));
    }

    @NotNull
    @Override
    LoopPropagation<V, Object> copy() {
      return new PropagationConsume<V>();
    }

    @Override
    void set(final LoopPropagation<Object, ?> next) {
      getLogger().dbg("Consuming completion");
      complete();
    }
  }

  private static class PropagationForkObserver<S, V>
      implements RenewableObserver<EvaluationCollection<V>>, Serializable {

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

    void cancel(final EvaluationCollection<V> evaluation) {
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

    public void accept(final EvaluationCollection<V> evaluation) {
      mObserver.propagate(evaluation);
    }
  }

  private static class PropagationHead<V> extends LoopPropagation<V, V> {

    private final Object mMutex = new Object();

    private StateEvaluating mInnerState = new StateEvaluating();

    private StatementState mState = StatementState.Evaluating;

    private DoubleQueue<SimpleState<V>> mStates = new DoubleQueue<SimpleState<V>>();

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

    @Nullable
    Runnable propagate(final LoopPropagation<V, ?> propagation) {
      final Runnable propagate = mInnerState.propagation(propagation);
      mState = StatementState.Evaluating;
      return propagate;
    }

    private class StateEvaluating {

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

      @Nullable
      Runnable propagation(final LoopPropagation<V, ?> propagation) {
        propagation.prepend(consumeStates());
        return null;
      }
    }

    private class StateSet extends StateEvaluating {

      @Override
      void innerSet() {
        throw exception(StatementState.Set);
      }

      @Override
      boolean isSet() {
        return true;
      }

      @Nullable
      @Override
      Runnable propagation(final LoopPropagation<V, ?> propagation) {
        getLogger().dbg("Propagating loop [%s => %s]", StatementState.Set,
            StatementState.Evaluating);
        mInnerState = new StateEvaluating();
        propagation.prepend(consumeStates());
        return new Runnable() {

          public void run() {
            propagation.set();
          }
        };
      }
    }

    @Override
    void addFailure(final LoopPropagation<V, ?> next, @NotNull final Throwable failure) {
      next.addFailure(failure);
    }

    @Override
    void addFailures(final LoopPropagation<V, ?> next,
        @NotNull  final Iterable<? extends Throwable> failures) {
      next.addFailures(failures);
    }

    @Override
    void addValue(final LoopPropagation<V, ?> next, final V value) {
      next.addValue(value);
    }

    @Override
    void addValues(final LoopPropagation<V, ?> next, @NotNull final Iterable<? extends V> values) {
      next.addValues(values);
    }

    @NotNull
    @Override
    PropagationHead<V> copy() {
      return new PropagationHead<V>();
    }

    @Override
    void set(final LoopPropagation<V, ?> next) {
      next.set();
    }
  }

  private static class PropagationLoopExpression<V, R> extends LoopPropagation<V, R>
      implements Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final PropagationEvaluationCollection mEvaluation =
        new PropagationEvaluationCollection();

    private final LoopExpression<V, R> mExpression;

    private final AtomicLong mPendingCount = new AtomicLong(1);

    private PropagationLoopExpression(@Nullable final LoopExpression<V, R> expression) {
      mExpression = ConstantConditions.notNull("expression", expression);
    }

    private void innerFailSafe(final LoopPropagation<R, ?> next, @NotNull final Throwable failure) {
      mPendingCount.set(0);
      next.failSafe(failure);
    }

    private void innerSet(final LoopPropagation<R, ?> next) {
      if (mPendingCount.decrementAndGet() > 0) {
        return;
      }

      next.set();
    }

    @NotNull
    private Object writeReplace() throws ObjectStreamException {
      return new PropagationProxy<V, R>(mExpression);
    }

    private static class PropagationProxy<V, R> implements Serializable {

      private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

      private final LoopExpression<V, R> mExpression;

      private PropagationProxy(final LoopExpression<V, R> expression) {
        mExpression = expression;
      }

      @NotNull
      private Object readResolve() throws ObjectStreamException {
        try {
          return new PropagationLoopExpression<V, R>(mExpression);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    private class PropagationEvaluationCollection implements EvaluationCollection<R> {

      private volatile LoopPropagation<R, ?> mNext;

      @NotNull
      PropagationEvaluationCollection withNext(final LoopPropagation<R, ?> next) {
        mNext = next;
        return this;
      }

      @NotNull
      public EvaluationCollection<R> addFailure(@NotNull final Throwable failure) {
        mNext.addFailure(failure);
        return this;
      }

      @NotNull
      public EvaluationCollection<R> addFailures(
          @Nullable final Iterable<? extends Throwable> failures) {
        mNext.addFailures(failures);
        return this;
      }

      @NotNull
      public EvaluationCollection<R> addValue(final R value) {
        mNext.addValue(value);
        return this;
      }

      @NotNull
      public EvaluationCollection<R> addValues(@Nullable final Iterable<? extends R> values) {
        mNext.addValues(values);
        return this;
      }

      public void set() {
        innerSet(mNext);
      }
    }

    @Override
    void addFailure(final LoopPropagation<R, ?> next, @NotNull final Throwable failure) {
      mPendingCount.incrementAndGet();
      try {
        mExpression.addFailure(failure, mEvaluation.withNext(next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing failure with reason: %s", failure);
        innerFailSafe(next, t);
      }
    }

    @Override
    void addValue(final LoopPropagation<R, ?> next, final V value) {
      mPendingCount.incrementAndGet();
      try {
        mExpression.addValue(value, mEvaluation.withNext(next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing value: %s", value);
        innerFailSafe(next, t);
      }
    }

    @Override
    void addValues(final LoopPropagation<R, ?> next, @NotNull final Iterable<? extends V> values) {
      mPendingCount.incrementAndGet();
      try {
        mExpression.addValues(values, mEvaluation.withNext(next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing values: %s", Iterables.toString(values));
        innerFailSafe(next, t);
      }
    }

    @NotNull
    @Override
    LoopPropagation<V, R> copy() {
      return new PropagationLoopExpression<V, R>(mExpression.renew());
    }

    @Override
    void addFailures(final LoopPropagation<R, ?> next,
        @NotNull final Iterable<? extends Throwable> failures) {
      mPendingCount.incrementAndGet();
      try {
        mExpression.addFailures(failures, mEvaluation.withNext(next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing value: %s", Iterables.toString(failures));
        innerFailSafe(next, t);
      }
    }

    @Override
    void set(final LoopPropagation<R, ?> next) {
      try {
        mExpression.set(mEvaluation.withNext(next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while completing loop");
        innerFailSafe(next, t);
      }
    }
  }

  private static class PropagationLoopExpressionOrdered<V, R> extends LoopPropagation<V, R>
      implements Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final LoopExpression<V, R> mExpression;

    private final Object mMutex = new Object();

    private final AtomicLong mPendingCount = new AtomicLong(1);

    private final NestedQueue<SimpleState<R>> mQueue = new NestedQueue<SimpleState<R>>();

    private PropagationLoopExpressionOrdered(@Nullable final LoopExpression<V, R> expression) {
      mExpression = ConstantConditions.notNull("expression", expression);
    }

    private void flushQueue(final LoopPropagation<R, ?> next) {
      addTo(mMutex, mQueue, next);
    }

    private void innerFailSafe(final LoopPropagation<R, ?> next, @NotNull final Throwable failure) {
      mPendingCount.set(0);
      synchronized (mMutex) {
        mQueue.clear();
      }

      next.failSafe(failure);
    }

    private void innerSet(final LoopPropagation<R, ?> next) {
      if (mPendingCount.decrementAndGet() > 0) {
        return;
      }

      next.set();
    }

    @NotNull
    private Object writeReplace() throws ObjectStreamException {
      return new PropagationProxy<V, R>(mExpression);
    }

    private static class PropagationProxy<V, R> implements Serializable {

      private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

      private final LoopExpression<V, R> mExpression;

      private PropagationProxy(final LoopExpression<V, R> expression) {
        mExpression = expression;
      }

      @NotNull
      private Object readResolve() throws ObjectStreamException {
        try {
          return new PropagationLoopExpressionOrdered<V, R>(mExpression);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    private class PropagationEvaluations implements EvaluationCollection<R> {

      private final LoopPropagation<R, ?> mNext;

      private final NestedQueue<SimpleState<R>> mQueue;

      private PropagationEvaluations(@NotNull final NestedQueue<SimpleState<R>> queue,
          final LoopPropagation<R, ?> next) {
        mQueue = queue;
        mNext = next;
      }

      @NotNull
      public EvaluationCollection<R> addFailure(@NotNull final Throwable failure) {
        synchronized (mMutex) {
          mQueue.add(SimpleState.<R>ofFailure(failure));
        }

        flushQueue(mNext);
        return this;
      }

      @NotNull
      public EvaluationCollection<R> addFailures(
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
      public EvaluationCollection<R> addValue(final R value) {
        synchronized (mMutex) {
          mQueue.add(SimpleState.ofValue(value));
        }

        flushQueue(mNext);
        return this;
      }

      @NotNull
      public EvaluationCollection<R> addValues(@Nullable final Iterable<? extends R> values) {
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

        final LoopPropagation<R, ?> next = mNext;
        flushQueue(next);
        innerSet(next);
      }
    }

    @Override
    void addFailure(final LoopPropagation<R, ?> next, @NotNull final Throwable failure) {
      mPendingCount.incrementAndGet();
      try {
        mExpression.addFailure(failure, new PropagationEvaluations(mQueue.addNested(), next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing failure with reason: %s", failure);
        innerFailSafe(next, t);
      }
    }

    @Override
    void addValue(final LoopPropagation<R, ?> next, final V value) {
      mPendingCount.incrementAndGet();
      try {
        mExpression.addValue(value, new PropagationEvaluations(mQueue.addNested(), next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing value: %s", value);
        innerFailSafe(next, t);
      }
    }

    @Override
    void addValues(final LoopPropagation<R, ?> next, @NotNull final Iterable<? extends V> values) {
      mPendingCount.incrementAndGet();
      try {
        mExpression.addValues(values, new PropagationEvaluations(mQueue.addNested(), next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing values: %s", Iterables.toString(values));
        innerFailSafe(next, t);
      }
    }

    @NotNull
    @Override
    LoopPropagation<V, R> copy() {
      return new PropagationLoopExpressionOrdered<V, R>(mExpression.renew());
    }

    @Override
    void addFailures(final LoopPropagation<R, ?> next,
        @NotNull final Iterable<? extends Throwable> failures) {
      mPendingCount.incrementAndGet();
      try {
        mExpression.addFailures(failures, new PropagationEvaluations(mQueue.addNested(), next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing failures: %s", Iterables.toString(failures));
        innerFailSafe(next, t);
      }
    }

    @Override
    void set(final LoopPropagation<R, ?> next) {
      try {
        mExpression.set(new PropagationEvaluations(mQueue.addNested(), next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while completing loop");
        innerFailSafe(next, t);
      }
    }
  }

  private static class PropagationStatementExpression<V, R> extends LoopPropagation<V, R>
      implements Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final PropagationEvaluation mEvaluation = new PropagationEvaluation();

    private final StatementExpression<V, R> mExpression;

    private final AtomicLong mPendingCount = new AtomicLong(1);

    private PropagationStatementExpression(@Nullable final StatementExpression<V, R> expression) {
      mExpression = ConstantConditions.notNull("expression", expression);
    }

    private void innerFailSafe(final LoopPropagation<R, ?> next, @NotNull final Throwable failure) {
      mPendingCount.set(0);
      next.failSafe(failure);
    }

    private void innerSet(final LoopPropagation<R, ?> next) {
      if (mPendingCount.decrementAndGet() > 0) {
        return;
      }

      next.set();
    }

    @NotNull
    private Object writeReplace() throws ObjectStreamException {
      return new PropagationProxy<V, R>(mExpression);
    }

    private static class PropagationProxy<V, R> implements Serializable {

      private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

      private final StatementExpression<V, R> mExpression;

      private PropagationProxy(final StatementExpression<V, R> expression) {
        mExpression = expression;
      }

      @NotNull
      private Object readResolve() throws ObjectStreamException {
        try {
          return new PropagationStatementExpression<V, R>(mExpression);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    private class PropagationEvaluation implements Evaluation<R> {

      private volatile LoopPropagation<R, ?> mNext;

      public void fail(@NotNull final Throwable failure) {
        final LoopPropagation<R, ?> next = mNext;
        next.addFailureSafe(failure);
        innerSet(next);
      }

      @NotNull
      PropagationEvaluation withNext(final LoopPropagation<R, ?> next) {
        mNext = next;
        return this;
      }

      public void set(final R value) {
        final LoopPropagation<R, ?> next = mNext;
        next.addValue(value);
        innerSet(next);
      }
    }

    @Override
    void addFailure(final LoopPropagation<R, ?> next, @NotNull final Throwable failure) {
      mPendingCount.incrementAndGet();
      try {
        mExpression.failure(failure, mEvaluation.withNext(next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing failure with reason: %s", failure);
        innerFailSafe(next, t);
      }
    }

    @Override
    void addValue(final LoopPropagation<R, ?> next, final V value) {
      mPendingCount.incrementAndGet();
      try {
        mExpression.value(value, mEvaluation.withNext(next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing value: %s", value);
        innerFailSafe(next, t);
      }
    }

    @Override
    void addValues(final LoopPropagation<R, ?> next, @NotNull final Iterable<? extends V> values) {
      mPendingCount.addAndGet(Iterables.size(values));
      final PropagationEvaluation evaluation = mEvaluation.withNext(next);
      for (final V value : values) {
        try {
          mExpression.value(value, evaluation);

        } catch (final CancellationException e) {
          getLogger().wrn(e, "Loop has been cancelled");
          innerFailSafe(next, e);
          break;

        } catch (final Throwable t) {
          getLogger().err(t, "Error while processing value: %s", value);
          innerFailSafe(next, t);
          break;
        }
      }
    }

    @NotNull
    @Override
    LoopPropagation<V, R> copy() {
      return new PropagationStatementExpression<V, R>(mExpression.renew());
    }

    @Override
    void addFailures(final LoopPropagation<R, ?> next,
        @NotNull final Iterable<? extends Throwable> failures) {
      mPendingCount.addAndGet(Iterables.size(failures));
      final PropagationEvaluation evaluation = mEvaluation.withNext(next);
      for (final Throwable failure : failures) {
        try {
          mExpression.failure(failure, evaluation);

        } catch (final CancellationException e) {
          getLogger().wrn(e, "Loop has been cancelled");
          innerFailSafe(next, e);
          break;

        } catch (final Throwable t) {
          getLogger().err(t, "Error while processing failure with reason: %s", failure);
          innerFailSafe(next, t);
          break;
        }
      }
    }

    @Override
    void set(final LoopPropagation<R, ?> next) {
      innerSet(next);
    }
  }

  private static class PropagationStatementExpressionOrdered<V, R> extends LoopPropagation<V, R>
      implements Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final StatementExpression<V, R> mExpression;

    private final Object mMutex = new Object();

    private final AtomicLong mPendingCount = new AtomicLong(1);

    private final NestedQueue<SimpleState<R>> mQueue = new NestedQueue<SimpleState<R>>();

    private PropagationStatementExpressionOrdered(
        @Nullable final StatementExpression<V, R> expression) {
      mExpression = ConstantConditions.notNull("expression", expression);
    }

    private void flushQueue(final LoopPropagation<R, ?> next) {
      addTo(mMutex, mQueue, next);
    }

    private void flushQueueSafe(final LoopPropagation<R, ?> next) {
      try {
        flushQueue(next);

      } catch (final Throwable t) {
        getLogger().wrn(t, "Suppressed failure");
      }
    }

    private void innerFailSafe(final LoopPropagation<R, ?> next, @NotNull final Throwable failure) {
      mPendingCount.set(0);
      synchronized (mMutex) {
        mQueue.clear();
      }

      next.failSafe(failure);
    }

    private void innerSet(final LoopPropagation<R, ?> next) {
      if (mPendingCount.decrementAndGet() > 0) {
        return;
      }

      next.set();
    }

    @NotNull
    private Object writeReplace() throws ObjectStreamException {
      return new PropagationProxy<V, R>(mExpression);
    }

    private static class PropagationProxy<V, R> implements Serializable {

      private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

      private final StatementExpression<V, R> mExpression;

      private PropagationProxy(final StatementExpression<V, R> expression) {
        mExpression = expression;
      }

      @NotNull
      private Object readResolve() throws ObjectStreamException {
        try {
          return new PropagationStatementExpressionOrdered<V, R>(mExpression);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    private class PropagationEvaluation implements Evaluation<R> {

      private final LoopPropagation<R, ?> mNext;

      private final NestedQueue<SimpleState<R>> mQueue;

      private PropagationEvaluation(@NotNull final NestedQueue<SimpleState<R>> queue,
          final LoopPropagation<R, ?> next) {
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
    void addFailure(final LoopPropagation<R, ?> next, @NotNull final Throwable failure) {
      mPendingCount.incrementAndGet();
      try {
        mExpression.failure(failure, new PropagationEvaluation(mQueue.addNested(), next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing failure with reason: %s", failure);
        innerFailSafe(next, t);
      }
    }

    @Override
    void addValue(final LoopPropagation<R, ?> next, final V value) {
      mPendingCount.incrementAndGet();
      try {
        mExpression.value(value, new PropagationEvaluation(mQueue.addNested(), next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing value: %s", value);
        innerFailSafe(next, t);
      }
    }

    @Override
    void addValues(final LoopPropagation<R, ?> next, @NotNull final Iterable<? extends V> values) {
      mPendingCount.addAndGet(Iterables.size(values));
      for (final V value : values) {
        try {
          mExpression.value(value, new PropagationEvaluation(mQueue.addNested(), next));

        } catch (final CancellationException e) {
          getLogger().wrn(e, "Loop has been cancelled");
          innerFailSafe(next, e);
          break;

        } catch (final Throwable t) {
          getLogger().err(t, "Error while processing value: %s", value);
          innerFailSafe(next, t);
          break;
        }
      }
    }

    @NotNull
    @Override
    LoopPropagation<V, R> copy() {
      return new PropagationStatementExpressionOrdered<V, R>(mExpression.renew());
    }

    @Override
    void addFailures(final LoopPropagation<R, ?> next,
        @NotNull final Iterable<? extends Throwable> failures) {
      mPendingCount.addAndGet(Iterables.size(failures));
      for (final Throwable failure : failures) {
        try {
          mExpression.failure(failure, new PropagationEvaluation(mQueue.addNested(), next));

        } catch (final CancellationException e) {
          getLogger().wrn(e, "Loop has been cancelled");
          innerFailSafe(next, e);
          break;

        } catch (final Throwable t) {
          getLogger().err(t, "Error while processing value: %s", failure);
          innerFailSafe(next, t);
          break;
        }
      }
    }

    @Override
    void set(final LoopPropagation<R, ?> next) {
      flushQueue(next);
      innerSet(next);
    }
  }

  private static class PropagationStatementLoopExpression<V, R> extends LoopPropagation<V, R>
      implements Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final PropagationEvaluationCollection mEvaluation =
        new PropagationEvaluationCollection();

    private final StatementLoopExpression<V, R> mExpression;

    private final AtomicLong mPendingCount = new AtomicLong(1);

    private PropagationStatementLoopExpression(
        @Nullable final StatementLoopExpression<V, R> expression) {
      mExpression = ConstantConditions.notNull("expression", expression);
    }

    private void innerFailSafe(final LoopPropagation<R, ?> next, @NotNull final Throwable failure) {
      mPendingCount.set(0);
      next.failSafe(failure);
    }

    private void innerSet(final LoopPropagation<R, ?> next) {
      if (mPendingCount.decrementAndGet() > 0) {
        return;
      }

      next.set();
    }

    @NotNull
    private Object writeReplace() throws ObjectStreamException {
      return new PropagationProxy<V, R>(mExpression);
    }

    private static class PropagationProxy<V, R> implements Serializable {

      private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

      private final StatementLoopExpression<V, R> mExpression;

      private PropagationProxy(final StatementLoopExpression<V, R> expression) {
        mExpression = expression;
      }

      @NotNull
      private Object readResolve() throws ObjectStreamException {
        try {
          return new PropagationStatementLoopExpression<V, R>(mExpression);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    private class PropagationEvaluationCollection implements EvaluationCollection<R> {

      private volatile LoopPropagation<R, ?> mNext;

      @NotNull
      PropagationEvaluationCollection withNext(final LoopPropagation<R, ?> next) {
        mNext = next;
        return this;
      }

      @NotNull
      public EvaluationCollection<R> addFailure(@NotNull final Throwable failure) {
        mNext.addFailure(failure);
        return this;
      }

      @NotNull
      public EvaluationCollection<R> addFailures(
          @Nullable final Iterable<? extends Throwable> failures) {
        mNext.addFailures(failures);
        return this;
      }

      @NotNull
      public EvaluationCollection<R> addValue(final R value) {
        mNext.addValue(value);
        return this;
      }

      @NotNull
      public EvaluationCollection<R> addValues(@Nullable final Iterable<? extends R> values) {
        mNext.addValues(values);
        return this;
      }

      public void set() {
        innerSet(mNext);
      }
    }

    @Override
    void addFailure(final LoopPropagation<R, ?> next, @NotNull final Throwable failure) {
      mPendingCount.incrementAndGet();
      try {
        mExpression.failure(failure, mEvaluation.withNext(next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing failure with reason: %s", failure);
        innerFailSafe(next, t);
      }
    }

    @Override
    void addValue(final LoopPropagation<R, ?> next, final V value) {
      mPendingCount.incrementAndGet();
      try {
        mExpression.value(value, mEvaluation.withNext(next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing value: %s", value);
        innerFailSafe(next, t);
      }
    }

    @Override
    void addValues(final LoopPropagation<R, ?> next, @NotNull final Iterable<? extends V> values) {
      mPendingCount.addAndGet(Iterables.size(values));
      final PropagationEvaluationCollection evaluation = mEvaluation.withNext(next);
      for (final V value : values) {
        try {
          mExpression.value(value, evaluation);

        } catch (final CancellationException e) {
          getLogger().wrn(e, "Loop has been cancelled");
          innerFailSafe(next, e);
          break;

        } catch (final Throwable t) {
          getLogger().err(t, "Error while processing value: %s", value);
          innerFailSafe(next, t);
          break;
        }
      }
    }

    @NotNull
    @Override
    LoopPropagation<V, R> copy() {
      return new PropagationStatementLoopExpression<V, R>(mExpression.renew());
    }

    @Override
    void addFailures(final LoopPropagation<R, ?> next,
        @NotNull final Iterable<? extends Throwable> failures) {
      mPendingCount.addAndGet(Iterables.size(failures));
      final PropagationEvaluationCollection evaluation = mEvaluation.withNext(next);
      for (final Throwable failure : failures) {
        try {
          mExpression.failure(failure, evaluation);

        } catch (final CancellationException e) {
          getLogger().wrn(e, "Loop has been cancelled");
          innerFailSafe(next, e);
          break;

        } catch (final Throwable t) {
          getLogger().err(t, "Error while processing failure with reason: %s", failure);
          innerFailSafe(next, t);
          break;
        }
      }
    }

    @Override
    void set(final LoopPropagation<R, ?> next) {
      innerSet(next);
    }
  }

  private static class PropagationStatementLoopExpressionOrdered<V, R> extends LoopPropagation<V, R>
      implements Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final StatementLoopExpression<V, R> mExpression;

    private final Object mMutex = new Object();

    private final AtomicLong mPendingCount = new AtomicLong(1);

    private final NestedQueue<SimpleState<R>> mQueue = new NestedQueue<SimpleState<R>>();

    private PropagationStatementLoopExpressionOrdered(
        @Nullable final StatementLoopExpression<V, R> expression) {
      mExpression = ConstantConditions.notNull("expression", expression);
    }

    private void flushQueue(final LoopPropagation<R, ?> next) {
      addTo(mMutex, mQueue, next);
    }

    private void innerFailSafe(final LoopPropagation<R, ?> next, @NotNull final Throwable failure) {
      mPendingCount.set(0);
      synchronized (mMutex) {
        mQueue.clear();
      }

      next.failSafe(failure);
    }

    private void innerSet(final LoopPropagation<R, ?> next) {
      if (mPendingCount.decrementAndGet() > 0) {
        return;
      }

      next.set();
    }

    @NotNull
    private Object writeReplace() throws ObjectStreamException {
      return new PropagationProxy<V, R>(mExpression);
    }

    private static class PropagationProxy<V, R> implements Serializable {

      private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

      private final StatementLoopExpression<V, R> mExpression;

      private PropagationProxy(final StatementLoopExpression<V, R> expression) {
        mExpression = expression;
      }

      @NotNull
      private Object readResolve() throws ObjectStreamException {
        try {
          return new PropagationStatementLoopExpressionOrdered<V, R>(mExpression);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    private class PropagationEvaluations implements EvaluationCollection<R> {

      private final LoopPropagation<R, ?> mNext;

      private final NestedQueue<SimpleState<R>> mQueue;

      private PropagationEvaluations(@NotNull final NestedQueue<SimpleState<R>> queue,
          final LoopPropagation<R, ?> next) {
        mQueue = queue;
        mNext = next;
      }

      @NotNull
      public EvaluationCollection<R> addFailure(@NotNull final Throwable failure) {
        synchronized (mMutex) {
          mQueue.add(SimpleState.<R>ofFailure(failure));
        }

        flushQueue(mNext);
        return this;
      }

      @NotNull
      public EvaluationCollection<R> addFailures(
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
      public EvaluationCollection<R> addValue(final R value) {
        synchronized (mMutex) {
          mQueue.add(SimpleState.ofValue(value));
        }

        flushQueue(mNext);
        return this;
      }

      @NotNull
      public EvaluationCollection<R> addValues(@Nullable final Iterable<? extends R> values) {
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

        final LoopPropagation<R, ?> next = mNext;
        flushQueue(next);
        innerSet(next);
      }
    }

    @Override
    void addFailure(final LoopPropagation<R, ?> next, @NotNull final Throwable failure) {
      mPendingCount.incrementAndGet();
      try {
        mExpression.failure(failure, new PropagationEvaluations(mQueue.addNested(), next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing failure with reason: %s", failure);
        innerFailSafe(next, t);
      }
    }

    @Override
    void addValue(final LoopPropagation<R, ?> next, final V value) {
      mPendingCount.incrementAndGet();
      try {
        mExpression.value(value, new PropagationEvaluations(mQueue.addNested(), next));

      } catch (final CancellationException e) {
        getLogger().wrn(e, "Loop has been cancelled");
        innerFailSafe(next, e);

      } catch (final Throwable t) {
        getLogger().err(t, "Error while processing value: %s", value);
        innerFailSafe(next, t);
      }
    }

    @Override
    void addValues(final LoopPropagation<R, ?> next, @NotNull final Iterable<? extends V> values) {
      mPendingCount.addAndGet(Iterables.size(values));
      for (final V value : values) {
        try {
          mExpression.value(value, new PropagationEvaluations(mQueue.addNested(), next));

        } catch (final CancellationException e) {
          getLogger().wrn(e, "Loop has been cancelled");
          innerFailSafe(next, e);
          break;

        } catch (final Throwable t) {
          getLogger().err(t, "Error while processing value: %s", value);
          innerFailSafe(next, t);
          break;
        }
      }
    }

    @NotNull
    @Override
    LoopPropagation<V, R> copy() {
      return new PropagationStatementLoopExpressionOrdered<V, R>(mExpression.renew());
    }

    @Override
    void addFailures(final LoopPropagation<R, ?> next,
        @NotNull final Iterable<? extends Throwable> failures) {
      mPendingCount.addAndGet(Iterables.size(failures));
      for (final Throwable failure : failures) {
        try {
          mExpression.failure(failure, new PropagationEvaluations(mQueue.addNested(), next));

        } catch (final CancellationException e) {
          getLogger().wrn(e, "Loop has been cancelled");
          innerFailSafe(next, e);
          break;

        } catch (final Throwable t) {
          getLogger().err(t, "Error while processing failure with reason: %s", failure);
          innerFailSafe(next, t);
          break;
        }
      }
    }

    @Override
    void set(final LoopPropagation<R, ?> next) {
      innerSet(next);
    }
  }

  private static class StateGenerator<V> implements Generator<EvaluationState<V>> {

    private final DefaultLoop<V> mLoop;

    private final TimeUnit mTimeUnit;

    private final long mTimeout;

    private StateGenerator(@NotNull final DefaultLoop<V> loop, final long timeout,
        @NotNull final TimeUnit timeUnit) {
      mTimeUnit = ConstantConditions.notNull("timeUnit", timeUnit);
      mTimeout = timeout;
      mLoop = loop;
    }

    @NotNull
    public GeneratorIterator<EvaluationState<V>> iterator() {
      return new StateIterator<V>(mLoop.evaluate(), mTimeout, mTimeUnit);
    }
  }

  private static class StateIterator<V> implements GeneratorIterator<EvaluationState<V>> {

    private final DefaultLoop<V> mLoop;

    private final TimeUnit mOriginalTimeUnit;

    private final long mOriginalTimeout;

    private final TimeUnit mTimeUnit;

    private final long mTimeout;

    private StateIterator(@NotNull final DefaultLoop<V> loop, final long timeout,
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
      final DefaultLoop<V> loop = mLoop;
      final PropagationHead<?> head = loop.mHead;
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
    public List<EvaluationState<V>> next(final int maxCount) {
      final ArrayList<EvaluationState<V>> outputs = new ArrayList<EvaluationState<V>>();
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

    public int readNext(@NotNull final Collection<? super EvaluationState<V>> collection,
        final int maxCount) {
      return readNext(collection, maxCount, -1, TimeUnit.MILLISECONDS);
    }

    @NotNull
    public List<EvaluationState<V>> next(final int maxCount, final long timeout,
        @NotNull final TimeUnit timeUnit) {
      final ArrayList<EvaluationState<V>> outputs = new ArrayList<EvaluationState<V>>();
      readNext(outputs, maxCount, timeout, timeUnit);
      return outputs;
    }

    public int readNext(@NotNull final Collection<? super EvaluationState<V>> collection,
        final int maxCount, final long timeout, @NotNull final TimeUnit timeUnit) {
      final DefaultLoop<V> loop = mLoop;
      final PropagationHead<?> head = loop.mHead;
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
                ((PropagationHead<V>) head).getStates();
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

    public EvaluationState<V> next() {
      final DefaultLoop<V> loop = mLoop;
      final PropagationHead<?> head = loop.mHead;
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
                ((PropagationHead<V>) head).getStates();
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

  private static class ValueGenerator<V> implements Generator<V> {

    private final DefaultLoop<V> mLoop;

    private final TimeUnit mTimeUnit;

    private final long mTimeout;

    private ValueGenerator(@NotNull final DefaultLoop<V> loop, final long timeout,
        @NotNull final TimeUnit timeUnit) {
      mTimeUnit = ConstantConditions.notNull("timeUnit", timeUnit);
      mTimeout = timeout;
      mLoop = loop;
    }

    @NotNull
    public GeneratorIterator<V> iterator() {
      return new ValueIterator<V>(mLoop.evaluate(), mTimeout, mTimeUnit);
    }
  }

  private static class ValueIterator<V> implements GeneratorIterator<V> {

    private final DefaultLoop<V> mLoop;

    private final TimeUnit mOriginalTimeUnit;

    private final long mOriginalTimeout;

    private final TimeUnit mTimeUnit;

    private final long mTimeout;

    private ValueIterator(@NotNull final DefaultLoop<V> loop, final long timeout,
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
      final DefaultLoop<V> loop = mLoop;
      final PropagationHead<?> head = loop.mHead;
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
      final DefaultLoop<V> loop = mLoop;
      final PropagationHead<?> head = loop.mHead;
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
                ((PropagationHead<V>) head).getStates();
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
      final DefaultLoop<V> loop = mLoop;
      final PropagationHead<?> head = loop.mHead;
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
                ((PropagationHead<V>) head).getStates();
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

  private class PropagationTail extends LoopPropagation<V, Object> {

    private final PropagationHead<V> mHead;

    private PropagationTail(@NotNull final PropagationHead<V> head) {
      mHead = head;
      setLogger(mLogger);
    }

    @Override
    void addFailure(final LoopPropagation<Object, ?> next, @NotNull final Throwable failure) {
      final LoopPropagation<V, ?> propagation;
      synchronized (mMutex) {
        try {
          mState = StatementState.Failed;
          if ((propagation = mPropagation) == null) {
            mHead.innerAddFailure(failure);
            return;
          }

        } finally {
          mMutex.notifyAll();
        }
      }

      propagation.addFailure(failure);
    }

    @Override
    void addFailures(final LoopPropagation<Object, ?> next,
        @NotNull final Iterable<? extends Throwable> failures) {
      final LoopPropagation<V, ?> propagation;
      synchronized (mMutex) {
        try {
          if ((propagation = mPropagation) == null) {
            @SuppressWarnings("UnnecessaryLocalVariable") final PropagationHead<V> head = mHead;
            for (final Throwable failure : failures) {
              head.innerAddFailure(failure);
            }

            return;
          }

        } finally {
          mMutex.notifyAll();
        }
      }

      propagation.addFailures(failures);
    }

    @Override
    void set(final LoopPropagation<Object, ?> next) {
      final LoopPropagation<V, ?> propagation;
      synchronized (mMutex) {
        try {
          if (mState == StatementState.Evaluating) {
            mState = StatementState.Set;
          }

          if ((propagation = mPropagation) == null) {
            mHead.innerSet();
            return;
          }

        } finally {
          mMutex.notifyAll();
        }
      }

      propagation.set();
    }

    @Override
    boolean isTail() {
      return true;
    }

    @Override
    void addValue(final LoopPropagation<Object, ?> next, final V value) {
      final LoopPropagation<V, ?> propagation;
      synchronized (mMutex) {
        try {
          if ((propagation = mPropagation) == null) {
            mHead.innerAddValue(value);
            return;
          }

        } finally {
          mMutex.notifyAll();
        }
      }

      propagation.addValue(value);
    }

    @Override
    void addValues(final LoopPropagation<Object, ?> next,
        @NotNull final Iterable<? extends V> values) {
      final LoopPropagation<V, ?> propagation;
      synchronized (mMutex) {
        try {
          if ((propagation = mPropagation) == null) {
            @SuppressWarnings("UnnecessaryLocalVariable") final PropagationHead<V> head = mHead;
            for (final V value : values) {
              head.innerAddValue(value);
            }

            return;
          }

        } finally {
          mMutex.notifyAll();
        }
      }

      propagation.addValues(values);
    }

    @NotNull
    @Override
    LoopPropagation<V, Object> copy() {
      return ConstantConditions.unsupported();
    }
  }
}

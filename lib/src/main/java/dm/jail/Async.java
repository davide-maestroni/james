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

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import dm.jail.async.AsyncLoop;
import dm.jail.async.AsyncResult;
import dm.jail.async.AsyncResultCollection;
import dm.jail.async.AsyncState;
import dm.jail.async.AsyncStatement;
import dm.jail.async.AsyncStatement.Forker;
import dm.jail.async.DeclaredStatement;
import dm.jail.async.InterruptibleObserver;
import dm.jail.async.Mapper;
import dm.jail.async.Observer;
import dm.jail.async.SimpleState;
import dm.jail.executor.ScheduledExecutor;
import dm.jail.log.LogPrinter;
import dm.jail.log.LogPrinter.Level;
import dm.jail.util.ConstantConditions;
import dm.jail.util.RuntimeInterruptedException;
import dm.jail.util.SerializableProxy;
import dm.jail.util.Threads;

/**
 * Created by davide-maestroni on 01/12/2018.
 */
public class Async {

  private final ScheduledExecutor mExecutor;

  private final Level mLogLevel;

  private final LogPrinter mLogPrinter;

  public Async() {
    this(null, null, null);
  }

  private Async(@Nullable final ScheduledExecutor executor, @Nullable final LogPrinter printer,
      @Nullable final Level level) {
    mExecutor = executor;
    mLogPrinter = printer;
    mLogLevel = level;
  }

  private static void test() {

    class ForkStack<V> {

      AsyncStatement<String> forked;

      ArrayList<AsyncResult<V>> results = new ArrayList<AsyncResult<V>>();

      AsyncState<V> state;

      long timestamp = -1;
    }

    new Async().value("hello")
               .fork(
                   new Forker<ForkStack<String>, AsyncStatement<String>, String,
                       AsyncResult<String>>() {

                     public ForkStack<String> done(@NotNull final AsyncStatement<String> statement,
                         final ForkStack<String> stack) {
                       return stack;
                     }

                     public ForkStack<String> failure(
                         @NotNull final AsyncStatement<String> statement,
                         final ForkStack<String> stack, @NotNull final Throwable failure) {
                       stack.state = SimpleState.ofFailure(failure);
                       for (final AsyncResult<String> result : stack.results) {
                         result.fail(failure);
                       }

                       stack.results.clear();
                       return stack;
                     }

                     public ForkStack<String> init(
                         @NotNull final AsyncStatement<String> statement) {
                       return new ForkStack<String>();
                     }

                     public ForkStack<String> statement(
                         @NotNull final AsyncStatement<String> statement,
                         final ForkStack<String> stack, @NotNull final AsyncResult<String> result) {
                       final AsyncState<String> state = stack.state;
                       if (state == null) {
                         stack.results.add(result);

                       } else if (state.isFailed()) {
                         result.fail(state.failure());

                       } else if (System.currentTimeMillis() - stack.timestamp <= 10000) {
                         result.set(state.value());

                       } else {
                         if (stack.forked == null) {
                           stack.forked = statement.evaluate().fork(this);
                         }

                         stack.forked.to(result);
                       }

                       return stack;
                     }

                     public ForkStack<String> value(@NotNull final AsyncStatement<String> statement,
                         final ForkStack<String> stack, final String value) {
                       stack.timestamp = System.currentTimeMillis();
                       stack.state = SimpleState.ofValue(value);
                       for (final AsyncResult<String> result : stack.results) {
                         result.set(value);
                       }

                       stack.results.clear();
                       return stack;
                     }
                   });
  }

  @NotNull
  public <V> AsyncStatement<V> failure(@NotNull final Throwable failure) {
    return statement(new FailureObserver<V>(failure));
  }

  @NotNull
  public <V> AsyncLoop<V> failures(@NotNull final Iterable<? extends Throwable> failures) {
    return null;
  }

  @NotNull
  public Async log(@Nullable final Level level) {
    return new Async(mExecutor, mLogPrinter, level);
  }

  @NotNull
  public Async log(@Nullable final LogPrinter printer) {
    return new Async(mExecutor, printer, mLogLevel);
  }

  @NotNull
  public <V> AsyncLoop<V> loop(@NotNull final Observer<AsyncResultCollection<V>> observer) {
    return null;
  }

  @NotNull
  public <S, V, R> AsyncLoop<R> loopOf(
      @NotNull final Combiner<S, ? super AsyncStatement<V>, ? super V, ? super
          AsyncResultCollection<? extends R>> combiner,
      @NotNull final Iterable<? extends AsyncStatement<? extends V>> statements) {
    return null;
  }

  @NotNull
  public <S, V, R> AsyncLoop<R> loopOf(@Nullable final Mapper<? super AsyncStatement<V>, S> init,
      @Nullable final CombinationUpdater<S, ? super AsyncStatement<V>, ? super V, ? super
          AsyncResultCollection<? extends R>> value,
      @Nullable final CombinationUpdater<S, ? super AsyncStatement<V>, ? super Throwable, ? super
          AsyncResultCollection<? extends R>> failure,
      @Nullable final CombinationCompleter<S, ? super AsyncStatement<V>, ? super
          AsyncResultCollection<? extends R>> done,
      @Nullable final CombinationSettler<S, ? super AsyncStatement<V>, ? super
          AsyncResultCollection<? extends R>> settle,
      @NotNull final Iterable<? extends AsyncStatement<? extends V>> statements) {
    return null;
  }

  @NotNull
  public Async on(@NotNull final ScheduledExecutor executor) {
    return new Async(ConstantConditions.notNull("executor", executor), mLogPrinter, mLogLevel);
  }

  @NotNull
  public <V> AsyncStatement<V> statement(@NotNull final Observer<AsyncResult<V>> observer) {
    final ScheduledExecutor executor = mExecutor;
    if (executor != null) {
      return new DefaultAsyncStatement<V>(new ExecutorObserver<V>(observer, executor), mLogPrinter,
          mLogLevel);
    }

    return new DefaultAsyncStatement<V>(observer, mLogPrinter, mLogLevel);
  }

  @NotNull
  public DeclaredStatement<Void> statementDeclaration() {
    return new DefaultDeclaredStatement<Void>(new StatementMapper(this));
  }

  @NotNull
  public <S, V, R> AsyncStatement<R> statementOf(
      @NotNull final Combiner<S, ? super AsyncStatement<V>, ? super V, ? super AsyncResult<?
          extends R>> combiner,
      @NotNull final Iterable<? extends AsyncStatement<? extends V>> statements) {
    return null;
  }

  @NotNull
  public <S, V, R> AsyncStatement<R> statementOf(
      @Nullable final Mapper<? super AsyncStatement<V>, S> init,
      @Nullable final CombinationUpdater<S, ? super AsyncStatement<V>, ? super V, ? super
          AsyncResult<? extends R>> value,
      @Nullable final CombinationUpdater<S, ? super AsyncStatement<V>, ? super Throwable, ? super
          AsyncResult<? extends R>> failure,
      @Nullable final CombinationCompleter<S, ? super AsyncStatement<V>, ? super AsyncResult<?
          extends R>> done,
      @Nullable final CombinationSettler<S, ? super AsyncStatement<V>, ? super AsyncResult<?
          extends R>> settle,
      @NotNull final Iterable<? extends AsyncStatement<? extends V>> statements) {
    return null;
  }

  @NotNull
  public <V> AsyncStatement<V> value(final V value) {
    return statement(new ValueObserver<V>(value));
  }

  @NotNull
  public <V> AsyncLoop<V> values(@NotNull final Iterable<V> values) {
    return null;
  }

  interface CombinationCompleter<S, A, R> {

    S complete(@NotNull List<A> statements, int index, S stack, @NotNull R result) throws Exception;
  }

  interface CombinationSettler<S, A, R> {

    void settle(@NotNull List<A> statements, S stack, @NotNull R result) throws Exception;
  }

  interface CombinationUpdater<S, A, V, R> {

    S update(@NotNull List<A> statements, int index, S stack, V value, @NotNull R result) throws
        Exception;
  }

  interface Combiner<S, A, V, R> {

    S done(@NotNull List<A> statements, int index, S stack, @NotNull R result) throws Exception;

    S failure(@NotNull List<A> statements, int index, S stack, Throwable failure,
        @NotNull R result) throws Exception;

    S init(@NotNull List<A> statements) throws Exception;

    void settle(@NotNull List<A> statements, S stack, @NotNull R result) throws Exception;

    S value(@NotNull List<A> statements, int index, S stack, V value, @NotNull R result) throws
        Exception;
  }

  private static class ExecutorObserver<V>
      implements InterruptibleObserver<AsyncResult<V>>, Serializable {

    private final ScheduledExecutor mExecutor;

    private final Observer<AsyncResult<V>> mObserver;

    private final AtomicReference<Thread> mThread = new AtomicReference<Thread>();

    private ExecutorObserver(@NotNull final Observer<AsyncResult<V>> observer,
        @NotNull final ScheduledExecutor executor) {
      mObserver = ConstantConditions.notNull("observer", observer);
      mExecutor = executor;
    }

    public void accept(final AsyncResult<V> result) {
      mExecutor.execute(new Runnable() {

        public void run() {
          final AtomicReference<Thread> thread = mThread;
          thread.set(Thread.currentThread());
          try {
            mObserver.accept(result);

          } catch (final Throwable t) {
            thread.set(null);
            // TODO: 16/01/2018 invert order of call?
            RuntimeInterruptedException.throwIfInterrupt(t);
            result.fail(t);
          }
        }
      });
    }

    public void interrupt() {
      final Thread thread = mThread.get();
      if (thread != null) {
        Threads.interruptIfWaiting(thread);
      }
    }

    @NotNull
    private Object writeReplace() throws ObjectStreamException {
      return new ObserverProxy<V>(mObserver, mExecutor);
    }

    private static class ObserverProxy<V> extends SerializableProxy {

      private ObserverProxy(final Observer<AsyncResult<V>> observer,
          final ScheduledExecutor executor) {
        super(proxy(observer), executor);
      }

      @NotNull
      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ExecutorObserver<V>((Observer<AsyncResult<V>>) args[0],
              (ScheduledExecutor) args[1]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }
  }

  private static class FailureObserver<V> implements Observer<AsyncResult<V>>, Serializable {

    private final Throwable mFailure;

    private FailureObserver(@NotNull final Throwable failure) {
      mFailure = ConstantConditions.notNull("failure", failure);
    }

    public void accept(final AsyncResult<V> result) {
      result.fail(mFailure);
    }
  }

  private static class StatementMapper
      implements Mapper<Observer<AsyncResult<Void>>, AsyncStatement<Void>>, Serializable {

    private final Async mAsync;

    private StatementMapper(@NotNull final Async async) {
      mAsync = async;
    }

    public AsyncStatement<Void> apply(final Observer<AsyncResult<Void>> observer) {
      return mAsync.statement(observer);
    }
  }

  private static class ValueObserver<V> implements Observer<AsyncResult<V>>, Serializable {

    private final V mValue;

    private ValueObserver(final V value) {
      mValue = value;
    }

    public void accept(final AsyncResult<V> result) {
      result.set(mValue);
    }
  }
}

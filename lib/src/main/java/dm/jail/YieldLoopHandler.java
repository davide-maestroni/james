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
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

import dm.jail.async.AsyncLoop;
import dm.jail.async.AsyncLoop.YieldResults;
import dm.jail.async.AsyncLoop.Yielder;
import dm.jail.async.AsyncResult;
import dm.jail.async.AsyncResultCollection;
import dm.jail.async.AsyncStatement;
import dm.jail.async.RuntimeInterruptedException;
import dm.jail.config.BuildConfig;
import dm.jail.executor.ScheduledExecutor;
import dm.jail.log.LogPrinter;
import dm.jail.log.LogPrinter.Level;
import dm.jail.log.Logger;
import dm.jail.util.ConstantConditions;
import dm.jail.util.SerializableProxy;

import static dm.jail.executor.ScheduledExecutors.immediateExecutor;
import static dm.jail.executor.ScheduledExecutors.withThrottling;

/**
 * Created by davide-maestroni on 02/05/2018.
 */
class YieldLoopHandler<S, V, R> extends AsyncLoopHandler<V, R> implements Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final ScheduledExecutor mExecutor;

  private final Logger mLogger;

  private final Yielder<S, ? super V, R> mYielder;

  private final YielderResults<R> mYielderResults = new YielderResults<R>();

  private boolean mIsFailed;

  private boolean mIsInitialized;

  private S mStack;

  YieldLoopHandler(@NotNull final Yielder<S, ? super V, R> yielder,
      @Nullable final LogPrinter printer, @Nullable final Level level) {
    mYielder = ConstantConditions.notNull("yielder", yielder);
    mExecutor = withThrottling(immediateExecutor(), 1);
    mLogger = Logger.newLogger(printer, level, this);
  }

  @Override
  void addFailure(@NotNull final Throwable failure,
      @NotNull final AsyncResultCollection<R> results) {
    mExecutor.execute(new YielderRunnable(results) {

      @Override
      protected void innerRun(@NotNull final YielderResults<R> results) throws Exception {
        mStack = mYielder.failure(mStack, failure, results);
      }
    });
  }

  @Override
  void addFailures(@Nullable final Iterable<Throwable> failures,
      @NotNull final AsyncResultCollection<R> results) {
    if (failures == null) {
      return;
    }

    mExecutor.execute(new YielderRunnable(results) {

      @Override
      protected void innerRun(@NotNull final YielderResults<R> results) throws Exception {
        final Yielder<S, ? super V, R> yielder = mYielder;
        for (final Throwable failure : failures) {
          mStack = yielder.failure(mStack, failure, results);
        }
      }
    });
  }

  @Override
  void addValue(final V value, @NotNull final AsyncResultCollection<R> results) {
    mExecutor.execute(new YielderRunnable(results) {

      @Override
      protected void innerRun(@NotNull final YielderResults<R> results) throws Exception {
        mStack = mYielder.value(mStack, value, results);
      }
    });
  }

  @Override
  void addValues(@Nullable final Iterable<V> values,
      @NotNull final AsyncResultCollection<R> results) {
    if (values == null) {
      return;
    }

    mExecutor.execute(new YielderRunnable(results) {

      @Override
      protected void innerRun(@NotNull final YielderResults<R> results) throws Exception {
        final Yielder<S, ? super V, R> yielder = mYielder;
        for (final V value : values) {
          mStack = yielder.value(mStack, value, results);
        }
      }
    });
  }

  @Override
  void set(@NotNull final AsyncResultCollection<R> results) {
    mExecutor.execute(new YielderRunnable(results) {

      @Override
      public void run() {
        super.run();
        results.set();
      }

      @Override
      protected void innerRun(@NotNull final YielderResults<R> results) throws Exception {
        mYielder.done(mStack, results);
      }
    });
  }

  private void failSafe(@NotNull final AsyncResultCollection<R> results,
      @NotNull final Throwable failure) {
    mYielderResults.withResults(results).set();
    try {
      results.addFailure(failure).set();

    } catch (final Throwable ignored) {
      // cannot take any action
    }
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    final Logger logger = mLogger;
    return new HandlerProxy<S, V, R>(mYielder, logger.getLogPrinter(), logger.getLogLevel());
  }

  private static class HandlerProxy<S, V, R> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private HandlerProxy(final Yielder<S, ? super V, R> yielder, final LogPrinter printer,
        final Level level) {
      super(proxy(yielder), printer, level);
    }

    @NotNull
    @SuppressWarnings("unchecked")
    Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new YieldLoopHandler<S, V, R>((Yielder<S, ? super V, R>) args[0],
            (LogPrinter) args[1], (Level) args[2]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }

  private static class YielderResults<V> implements YieldResults<V> {

    private final AtomicBoolean mIsSet = new AtomicBoolean();

    private AsyncResultCollection<V> mResults;

    @NotNull
    public YieldResults<V> yieldFailure(@NotNull final Throwable failure) {
      checkSet();
      mResults.addFailure(failure);
      return this;
    }

    @NotNull
    public YieldResults<V> yieldFailures(@Nullable final Iterable<Throwable> failures) {
      checkSet();
      mResults.addFailures(failures);
      return this;
    }

    @NotNull
    public YieldResults<V> yieldIf(@NotNull final AsyncStatement<? extends V> statement) {
      checkSet();
      statement.to(new AsyncResult<V>() {

        public void fail(@NotNull final Throwable failure) {
          mResults.addFailure(failure);
        }

        public void set(final V value) {
          mResults.addValue(value);
        }
      });
      return this;
    }

    @NotNull
    @SuppressWarnings("unchecked")
    public YieldResults<V> yieldLoop(@NotNull final AsyncStatement<? extends Iterable<V>> loop) {
      checkSet();
      if (loop instanceof AsyncLoop) {
        ((AsyncLoop<V>) loop).to(new AsyncResultCollection<V>() {

          @NotNull
          public AsyncResultCollection<V> addFailure(@NotNull final Throwable failure) {
            mResults.addFailure(failure);
            return this;
          }

          @NotNull
          public AsyncResultCollection<V> addFailures(
              @Nullable final Iterable<Throwable> failures) {
            mResults.addFailures(failures);
            return this;
          }

          @NotNull
          public AsyncResultCollection<V> addValue(final V value) {
            mResults.addValue(value);
            return this;
          }

          @NotNull
          public AsyncResultCollection<V> addValues(@Nullable final Iterable<V> values) {
            mResults.addValues(values);
            return this;
          }

          public void set() {
          }
        });

      } else {
        loop.to(new AsyncResult<Iterable<V>>() {

          public void fail(@NotNull final Throwable failure) {
            mResults.addFailure(failure);
          }

          public void set(final Iterable<V> value) {
            mResults.addValues(value);
          }
        });
      }

      return this;
    }

    @NotNull
    public YieldResults<V> yieldValue(final V value) {
      checkSet();
      mResults.addValue(value);
      return this;
    }

    @NotNull
    public YieldResults<V> yieldValues(@Nullable final Iterable<V> value) {
      checkSet();
      mResults.addValues(value);
      return this;
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
    private YielderResults<V> withResults(@NotNull final AsyncResultCollection<V> results) {
      mResults = results;
      return this;
    }
  }

  private abstract class YielderRunnable implements Runnable {

    private final AsyncResultCollection<R> mResults;

    private YielderRunnable(@NotNull final AsyncResultCollection<R> results) {
      mResults = results;
    }

    public void run() {
      final AsyncResultCollection<R> results = mResults;
      try {
        if (mIsFailed) {
          results.set();
          return;
        }

        if (!mIsInitialized) {
          mIsInitialized = true;
          mStack = mYielder.init();
        }

        innerRun(mYielderResults.withResults(results));
        results.set();

      } catch (final CancellationException e) {
        if (!mIsFailed) {
          mLogger.wrn(e, "Loop has been cancelled");
        }

        mIsFailed = true;
        failSafe(results, e);

      } catch (final Throwable t) {
        if (!mIsFailed) {
          mLogger.err(t, "Error while completing loop");
        }

        mIsFailed = true;
        failSafe(results, RuntimeInterruptedException.wrapIfInterrupt(t));
      }
    }

    protected abstract void innerRun(@NotNull YielderResults<R> results) throws Exception;
  }
}

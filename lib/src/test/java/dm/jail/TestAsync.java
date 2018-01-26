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
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import dm.jail.async.AsyncResult;
import dm.jail.async.AsyncStatement;
import dm.jail.async.DeferredStatement;
import dm.jail.async.Mapper;
import dm.jail.async.Observer;
import dm.jail.executor.ScheduledExecutors;
import dm.jail.log.LogPrinter;
import dm.jail.log.LogPrinter.Level;
import dm.jail.log.LogPrinters;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Created by davide-maestroni on 01/17/2018.
 */
public class TestAsync {

  @Test
  public void constructor() {
    new Async();
  }

  @Test
  public void deferred() {
    final DeferredStatement<Integer> deferredStatement =
        new Async().deferred().then(new Mapper<Void, Integer>() {

          public Integer apply(final Void ignored) {
            return 3;
          }
        });
    assertThat(deferredStatement.isSet()).isFalse();
    assertThat(deferredStatement.isDone()).isFalse();
    final AsyncStatement<Integer> statement = deferredStatement.evaluate();
    assertThat(deferredStatement.isSet()).isTrue();
    assertThat(statement.isDone()).isTrue();
    assertThat(statement.getValue()).isEqualTo(3);
  }

  @Test
  public void deferredReEvaluate() {
    final DeferredStatement<Integer> deferredStatement =
        new Async().deferred().then(new Mapper<Void, Integer>() {

          public Integer apply(final Void ignored) {
            return 3;
          }
        });
    assertThat(deferredStatement.isSet()).isFalse();
    assertThat(deferredStatement.isDone()).isFalse();
    final AsyncStatement<Integer> statement = deferredStatement.reEvaluate();
    assertThat(deferredStatement.isSet()).isTrue();
    assertThat(deferredStatement.getValue()).isEqualTo(3);
    assertThat(statement.isDone()).isTrue();
    assertThat(statement.getValue()).isEqualTo(3);
  }

  @Test
  @SuppressWarnings("ConstantConditions")
  public void failure() {
    assertThat(new Async().failure(new IllegalAccessException())
                          .getFailure()
                          .getCause()).isExactlyInstanceOf(IllegalAccessException.class);
  }

  @Test
  public void immutable() {
    final Async async = new Async();
    assertThat(async.logWith(Level.SILENT)).isNotSameAs(async);
    assertThat(async.logWith(LogPrinters.nullPrinter())).isNotSameAs(async);
    assertThat(async.on(ScheduledExecutors.immediateExecutor())).isNotSameAs(async);
  }

  @Test
  public void logLevelDebug() {
    final TestLogPrinter logPrinter = new TestLogPrinter();
    new Async().logWith(logPrinter)
               .logWith(Level.DEBUG)
               .value(null)
               .then(new Mapper<Object, Object>() {

                 public Object apply(final Object input) throws Exception {
                   throw new Exception();
                 }
               })
               .cancel(true);
    assertThat(logPrinter.dbgCalled.get()).isTrue();
    assertThat(logPrinter.wrnCalled.get()).isTrue();
    assertThat(logPrinter.errCalled.get()).isTrue();
  }

  @Test
  public void logLevelError() {
    final TestLogPrinter logPrinter = new TestLogPrinter();
    new Async().logWith(logPrinter)
               .logWith(Level.ERROR)
               .value(null)
               .then(new Mapper<Object, Object>() {

                 public Object apply(final Object input) throws Exception {
                   throw new Exception();
                 }
               })
               .cancel(true);
    assertThat(logPrinter.dbgCalled.get()).isFalse();
    assertThat(logPrinter.wrnCalled.get()).isFalse();
    assertThat(logPrinter.errCalled.get()).isTrue();
  }

  @Test
  public void logLevelSilent() {
    final TestLogPrinter logPrinter = new TestLogPrinter();
    new Async().logWith(logPrinter)
               .logWith(Level.SILENT)
               .value(null)
               .then(new Mapper<Object, Object>() {

                 public Object apply(final Object input) throws Exception {
                   throw new Exception();
                 }
               })
               .cancel(true);
    assertThat(logPrinter.dbgCalled.get()).isFalse();
    assertThat(logPrinter.wrnCalled.get()).isFalse();
    assertThat(logPrinter.errCalled.get()).isFalse();
  }

  @Test
  public void logLevelWarning() {
    final TestLogPrinter logPrinter = new TestLogPrinter();
    new Async().logWith(logPrinter)
               .logWith(Level.WARNING)
               .value(null)
               .then(new Mapper<Object, Object>() {

                 public Object apply(final Object input) throws Exception {
                   throw new Exception();
                 }
               })
               .cancel(true);
    assertThat(logPrinter.dbgCalled.get()).isFalse();
    assertThat(logPrinter.wrnCalled.get()).isTrue();
    assertThat(logPrinter.errCalled.get()).isTrue();
  }

  @Test
  @SuppressWarnings("ConstantConditions")
  public void statementAsyncFailure() {
    assertThat(new Async().on(ScheduledExecutors.backgroundExecutor())
                          .statement(new Observer<AsyncResult<Integer>>() {

                            public void accept(final AsyncResult<Integer> result) {
                              result.fail(new IllegalAccessException());
                            }
                          })
                          .getFailure()
                          .getCause()).isExactlyInstanceOf(IllegalAccessException.class);
  }

  @Test
  public void statementAsyncValue() {
    assertThat(new Async().on(ScheduledExecutors.backgroundExecutor())
                          .statement(new Observer<AsyncResult<Integer>>() {

                            public void accept(final AsyncResult<Integer> result) {
                              result.set(3);
                            }
                          })
                          .getValue()).isEqualTo(3);
  }

  @Test
  @SuppressWarnings("ConstantConditions")
  public void statementDelayedFailure() {
    final long startTime = System.currentTimeMillis();
    assertThat(new Async().on(
        ScheduledExecutors.withDelay(ScheduledExecutors.backgroundExecutor(), 100,
            TimeUnit.MILLISECONDS)).statement(new Observer<AsyncResult<Integer>>() {

      public void accept(final AsyncResult<Integer> result) {
        result.fail(new IllegalAccessException());
      }
    }).getFailure().getCause()).isExactlyInstanceOf(IllegalAccessException.class);
    assertThat(System.currentTimeMillis() - startTime).isGreaterThanOrEqualTo(100);
  }

  @Test
  public void statementDelayedValue() {
    final long startTime = System.currentTimeMillis();
    assertThat(new Async().on(
        ScheduledExecutors.withDelay(ScheduledExecutors.backgroundExecutor(), 100,
            TimeUnit.MILLISECONDS)).statement(new Observer<AsyncResult<Integer>>() {

      public void accept(final AsyncResult<Integer> result) {
        result.set(3);
      }
    }).getValue()).isEqualTo(3);
    assertThat(System.currentTimeMillis() - startTime).isGreaterThanOrEqualTo(100);
  }

  @Test
  @SuppressWarnings("ConstantConditions")
  public void statementFailure() {
    assertThat(new Async().statement(new Observer<AsyncResult<Integer>>() {

      public void accept(final AsyncResult<Integer> result) {
        result.fail(new IllegalAccessException());
      }
    }).getFailure().getCause()).isExactlyInstanceOf(IllegalAccessException.class);
  }

  @Test
  public void statementValue() {
    assertThat(new Async().statement(new Observer<AsyncResult<Integer>>() {

      public void accept(final AsyncResult<Integer> result) {
        result.set(3);
      }
    }).getValue()).isEqualTo(3);
  }

  @Test
  public void value() {
    assertThat(new Async().value(3).getValue()).isEqualTo(3);
  }

  private static class TestLogPrinter implements LogPrinter {

    private final AtomicBoolean dbgCalled = new AtomicBoolean();

    private final AtomicBoolean errCalled = new AtomicBoolean();

    private final AtomicBoolean wrnCalled = new AtomicBoolean();

    public void dbg(@NotNull final List<Object> contexts, @Nullable final String message,
        @Nullable final Throwable throwable) {
      dbgCalled.set(true);
    }

    public void err(@NotNull final List<Object> contexts, @Nullable final String message,
        @Nullable final Throwable throwable) {
      errCalled.set(true);
    }

    public void wrn(@NotNull final List<Object> contexts, @Nullable final String message,
        @Nullable final Throwable throwable) {
      wrnCalled.set(true);
    }
  }
}

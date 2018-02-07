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
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import dm.jail.async.AsyncResult;
import dm.jail.async.AsyncResults;
import dm.jail.async.AsyncState;
import dm.jail.async.AsyncStatement;
import dm.jail.async.AsyncStatement.Forker;
import dm.jail.async.Mapper;
import dm.jail.async.Observer;
import dm.jail.async.SimpleState;
import dm.jail.executor.ExecutorPool;
import dm.jail.log.LogLevel;
import dm.jail.log.LogPrinters;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Created by davide-maestroni on 01/17/2018.
 */
public class TestAsync {

  private static void aaa() {

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

  @Test
  public void bbb() {
    assertThat(new Async().loop(new Observer<AsyncResults<Object>>() {

      public void accept(final AsyncResults<Object> results) {
        results.addFailures(Collections.<Throwable>singletonList(null)).set();
      }
    }).isFailed()).isTrue();
  }

  @Test
  public void constructor() {
    new Async();
  }

  @Test
  @SuppressWarnings("ConstantConditions")
  public void failure() {
    assertThat(new Async().failure(new IllegalAccessException())
                          .getFailure()
                          .getCause()).isExactlyInstanceOf(IllegalAccessException.class);
  }

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void failureNPE() {
    new Async().failure(null);
  }

  @Test
  public void immutable() {
    final Async async = new Async();
    assertThat(async.log(LogLevel.SILENT)).isNotSameAs(async);
    assertThat(async.log(LogPrinters.nullPrinter())).isNotSameAs(async);
    assertThat(async.on(ExecutorPool.immediateExecutor())).isNotSameAs(async);
  }

  @Test
  public void logLevelDebug() {
    final TestLogPrinter logPrinter = new TestLogPrinter();
    new Async().log(logPrinter).log(LogLevel.DEBUG).value(null).then(new Mapper<Object, Object>() {

      public Object apply(final Object input) throws Exception {
        throw new Exception();
      }
    }).cancel(true);
    assertThat(logPrinter.isDbgCalled()).isTrue();
    assertThat(logPrinter.isWrnCalled()).isTrue();
    assertThat(logPrinter.isErrCalled()).isTrue();
  }

  @Test
  public void logLevelError() {
    final TestLogPrinter logPrinter = new TestLogPrinter();
    new Async().log(logPrinter).log(LogLevel.ERROR).value(null).then(new Mapper<Object, Object>() {

      public Object apply(final Object input) throws Exception {
        throw new Exception();
      }
    }).cancel(true);
    assertThat(logPrinter.isDbgCalled()).isFalse();
    assertThat(logPrinter.isWrnCalled()).isFalse();
    assertThat(logPrinter.isErrCalled()).isTrue();
  }

  @Test
  public void logLevelSilent() {
    final TestLogPrinter logPrinter = new TestLogPrinter();
    new Async().log(logPrinter).log(LogLevel.SILENT).value(null).then(new Mapper<Object, Object>() {

      public Object apply(final Object input) throws Exception {
        throw new Exception();
      }
    }).cancel(true);
    assertThat(logPrinter.isDbgCalled()).isFalse();
    assertThat(logPrinter.isWrnCalled()).isFalse();
    assertThat(logPrinter.isErrCalled()).isFalse();
  }

  @Test
  public void logLevelWarning() {
    final TestLogPrinter logPrinter = new TestLogPrinter();
    new Async().log(logPrinter)
               .log(LogLevel.WARNING)
               .value(null)
               .then(new Mapper<Object, Object>() {

                 public Object apply(final Object input) throws Exception {
                   throw new Exception();
                 }
               })
               .cancel(true);
    assertThat(logPrinter.isDbgCalled()).isFalse();
    assertThat(logPrinter.isWrnCalled()).isTrue();
    assertThat(logPrinter.isErrCalled()).isTrue();
  }

  @Test
  @SuppressWarnings("ConstantConditions")
  public void statementAsyncFailure() {
    assertThat(new Async().on(ExecutorPool.backgroundExecutor())
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
    assertThat(new Async().on(ExecutorPool.backgroundExecutor())
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
        ExecutorPool.withDelay(100, TimeUnit.MILLISECONDS, ExecutorPool.backgroundExecutor()))
                          .statement(new Observer<AsyncResult<Integer>>() {

                            public void accept(final AsyncResult<Integer> result) {
                              result.fail(new IllegalAccessException());
                            }
                          })
                          .getFailure()
                          .getCause()).isExactlyInstanceOf(IllegalAccessException.class);
    assertThat(System.currentTimeMillis() - startTime).isGreaterThanOrEqualTo(100);
  }

  @Test
  public void statementDelayedValue() {
    final long startTime = System.currentTimeMillis();
    assertThat(new Async().on(
        ExecutorPool.withDelay(100, TimeUnit.MILLISECONDS, ExecutorPool.backgroundExecutor()))
                          .statement(new Observer<AsyncResult<Integer>>() {

                            public void accept(final AsyncResult<Integer> result) {
                              result.set(3);
                            }
                          })
                          .getValue()).isEqualTo(3);
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

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void statementNPE() {
    new Async().statement(null);
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
  public void unevaluated() {
    final AsyncStatement<Integer> unevaluatedStatement = new Async().unevaluated().value(3);
    assertThat(unevaluatedStatement.isSet()).isFalse();
    assertThat(unevaluatedStatement.isDone()).isFalse();
    final AsyncStatement<Integer> statement = unevaluatedStatement.evaluate();
    assertThat(unevaluatedStatement.isSet()).isFalse();
    assertThat(unevaluatedStatement.isDone()).isFalse();
    assertThat(statement.isDone()).isTrue();
    assertThat(statement.getValue()).isEqualTo(3);
  }

  @Test
  public void unevaluatedEvaluate() {
    final AsyncStatement<Integer> unevaluatedStatement = new Async().unevaluated().value(3);
    assertThat(unevaluatedStatement.isSet()).isFalse();
    assertThat(unevaluatedStatement.isDone()).isFalse();
    final AsyncStatement<Integer> statement = unevaluatedStatement.evaluate();
    assertThat(unevaluatedStatement.isSet()).isFalse();
    assertThat(unevaluatedStatement.isDone()).isFalse();
    assertThat(statement.isDone()).isTrue();
    assertThat(statement.getValue()).isEqualTo(3);
  }

  @Test
  public void value() {
    assertThat(new Async().value(3).getValue()).isEqualTo(3);
  }
}

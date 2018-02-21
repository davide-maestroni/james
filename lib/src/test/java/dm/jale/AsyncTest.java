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
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import dm.jale.TestLogPrinter.Level;
import dm.jale.async.Evaluation;
import dm.jale.async.EvaluationCollection;
import dm.jale.async.EvaluationState;
import dm.jale.async.Mapper;
import dm.jale.async.Observer;
import dm.jale.async.SimpleState;
import dm.jale.async.Statement;
import dm.jale.async.Statement.Forker;
import dm.jale.executor.ExecutorPool;
import dm.jale.log.LogConnector;
import dm.jale.log.LogPrinter;
import dm.jale.log.Logger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Created by davide-maestroni on 01/17/2018.
 */
public class AsyncTest {

  private static void aaa() {

    class ForkStack<V> {

      ArrayList<Evaluation<V>> evaluation = new ArrayList<Evaluation<V>>();

      Statement<String> forked;

      EvaluationState<V> state;

      long timestamp = -1;
    }

    new Async().value("hello")
               .fork(
                   new Forker<ForkStack<String>, String, Evaluation<String>, Statement<String>>() {

                     public ForkStack<String> done(final ForkStack<String> stack,
                         @NotNull final Statement<String> context) {
                       return stack;
                     }

                     public ForkStack<String> evaluation(final ForkStack<String> stack,
                         @NotNull final Evaluation<String> evaluation,
                         @NotNull final Statement<String> context) {
                       final EvaluationState<String> state = stack.state;
                       if (state == null) {
                         stack.evaluation.add(evaluation);

                       } else if (state.isFailed()) {
                         evaluation.fail(state.failure());

                       } else if (System.currentTimeMillis() - stack.timestamp <= 10000) {
                         evaluation.set(state.value());

                       } else {
                         if (stack.forked == null) {
                           stack.forked = context.evaluate().fork(this);
                         }

                         stack.forked.to(evaluation);
                       }

                       return stack;
                     }

                     public ForkStack<String> failure(final ForkStack<String> stack,
                         @NotNull final Throwable failure,
                         @NotNull final Statement<String> context) {
                       stack.state = SimpleState.ofFailure(failure);
                       for (final Evaluation<String> evaluation : stack.evaluation) {
                         evaluation.fail(failure);
                       }

                       stack.evaluation.clear();
                       return stack;
                     }

                     public ForkStack<String> init(@NotNull final Statement<String> context) {
                       return new ForkStack<String>();
                     }

                     public ForkStack<String> value(final ForkStack<String> stack,
                         final String value, @NotNull final Statement<String> context) {
                       stack.timestamp = System.currentTimeMillis();
                       stack.state = SimpleState.ofValue(value);
                       for (final Evaluation<String> evaluation : stack.evaluation) {
                         evaluation.set(value);
                       }

                       stack.evaluation.clear();
                       return stack;
                     }
                   });
  }

  @Test
  public void bbb() {
    // TODO: 08/02/2018 move to loop tests
    assertThat(new Async().loop(new Observer<EvaluationCollection<Object>>() {

      public void accept(final EvaluationCollection<Object> evaluation) {
        evaluation.addFailures(Collections.<Throwable>singletonList(null)).set();
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
    assertThat(async.loggerName("test")).isNotSameAs(async);
    assertThat(async.loggerName(null)).isNotSameAs(async);
    assertThat(async.evaluateOn(ExecutorPool.immediateExecutor())).isNotSameAs(async);
  }

  @Test
  public void logLevelDebug() {
    final TestLogPrinter logPrinter = new TestLogPrinter(Level.DBG);
    Logger.clearConnectors();
    Logger.addConnector(new LogConnector() {

      public LogPrinter getPrinter(@NotNull final String loggerName,
          @NotNull final List<Object> contexts) {
        return logPrinter;
      }
    });
    new Async().value(null).then(new Mapper<Object, Object>() {

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
    final TestLogPrinter logPrinter = new TestLogPrinter(Level.ERR);
    Logger.clearConnectors();
    Logger.addConnector(new LogConnector() {

      public LogPrinter getPrinter(@NotNull final String loggerName,
          @NotNull final List<Object> contexts) {
        return logPrinter;
      }
    });
    new Async().value(null).then(new Mapper<Object, Object>() {

      public Object apply(final Object input) throws Exception {
        throw new Exception();
      }
    }).cancel(true);
    assertThat(logPrinter.isDbgCalled()).isFalse();
    assertThat(logPrinter.isWrnCalled()).isFalse();
    assertThat(logPrinter.isErrCalled()).isTrue();
  }

  @Test
  public void logLevelWarning() {
    final TestLogPrinter logPrinter = new TestLogPrinter(Level.WRN);
    Logger.clearConnectors();
    Logger.addConnector(new LogConnector() {

      public LogPrinter getPrinter(@NotNull final String loggerName,
          @NotNull final List<Object> contexts) {
        return logPrinter;
      }
    });
    new Async().value(null).then(new Mapper<Object, Object>() {

      public Object apply(final Object input) throws Exception {
        throw new Exception();
      }
    }).cancel(true);
    assertThat(logPrinter.isDbgCalled()).isFalse();
    assertThat(logPrinter.isWrnCalled()).isTrue();
    assertThat(logPrinter.isErrCalled()).isTrue();
  }

  @Test
  @SuppressWarnings("ConstantConditions")
  public void statementAsyncFailure() {
    assertThat(new Async().evaluateOn(ExecutorPool.backgroundExecutor())
                          .statement(new Observer<Evaluation<Integer>>() {

                            public void accept(final Evaluation<Integer> evaluation) {
                              evaluation.fail(new IllegalAccessException());
                            }
                          })
                          .getFailure()
                          .getCause()).isExactlyInstanceOf(IllegalAccessException.class);
  }

  @Test
  public void statementAsyncValue() {
    assertThat(new Async().evaluateOn(ExecutorPool.backgroundExecutor())
                          .statement(new Observer<Evaluation<Integer>>() {

                            public void accept(final Evaluation<Integer> evaluation) {
                              evaluation.set(3);
                            }
                          })
                          .getValue()).isEqualTo(3);
  }

  @Test
  @SuppressWarnings("ConstantConditions")
  public void statementDelayedFailure() {
    final long startTime = System.currentTimeMillis();
    assertThat(new Async().evaluateOn(
        ExecutorPool.withDelay(100, TimeUnit.MILLISECONDS, ExecutorPool.backgroundExecutor()))
                          .statement(new Observer<Evaluation<Integer>>() {

                            public void accept(final Evaluation<Integer> evaluation) {
                              evaluation.fail(new IllegalAccessException());
                            }
                          })
                          .getFailure()
                          .getCause()).isExactlyInstanceOf(IllegalAccessException.class);
    assertThat(System.currentTimeMillis() - startTime).isGreaterThanOrEqualTo(100);
  }

  @Test
  public void statementDelayedValue() {
    final long startTime = System.currentTimeMillis();
    assertThat(new Async().evaluateOn(
        ExecutorPool.withDelay(100, TimeUnit.MILLISECONDS, ExecutorPool.backgroundExecutor()))
                          .statement(new Observer<Evaluation<Integer>>() {

                            public void accept(final Evaluation<Integer> evaluation) {
                              evaluation.set(3);
                            }
                          })
                          .getValue()).isEqualTo(3);
    assertThat(System.currentTimeMillis() - startTime).isGreaterThanOrEqualTo(100);
  }

  @Test
  @SuppressWarnings("ConstantConditions")
  public void statementFailure() {
    assertThat(new Async().statement(new Observer<Evaluation<Integer>>() {

      public void accept(final Evaluation<Integer> evaluation) {
        evaluation.fail(new IllegalAccessException());
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
    assertThat(new Async().statement(new Observer<Evaluation<Integer>>() {

      public void accept(final Evaluation<Integer> evaluation) {
        evaluation.set(3);
      }
    }).getValue()).isEqualTo(3);
  }

  @Test
  public void unevaluated() {
    final Statement<Integer> unevaluatedStatement = new Async().evaluated(false).value(3);
    assertThat(unevaluatedStatement.isSet()).isFalse();
    assertThat(unevaluatedStatement.isDone()).isFalse();
    final Statement<Integer> statement = unevaluatedStatement.evaluate();
    assertThat(unevaluatedStatement.isSet()).isFalse();
    assertThat(unevaluatedStatement.isDone()).isFalse();
    assertThat(statement.isDone()).isTrue();
    assertThat(statement.getValue()).isEqualTo(3);
  }

  @Test
  public void unevaluatedEvaluate() {
    final Statement<Integer> unevaluatedStatement = new Async().evaluated(false).value(3);
    assertThat(unevaluatedStatement.isSet()).isFalse();
    assertThat(unevaluatedStatement.isDone()).isFalse();
    final Statement<Integer> statement = unevaluatedStatement.evaluate();
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

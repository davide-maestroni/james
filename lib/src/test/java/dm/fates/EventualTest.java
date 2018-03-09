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

package dm.fates;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import dm.fates.TestLogPrinter.Level;
import dm.fates.eventual.Evaluation;
import dm.fates.eventual.EvaluationCollection;
import dm.fates.eventual.EvaluationState;
import dm.fates.eventual.Mapper;
import dm.fates.eventual.Observer;
import dm.fates.eventual.SimpleState;
import dm.fates.eventual.Statement;
import dm.fates.eventual.Statement.Forker;
import dm.fates.executor.ExecutorPool;
import dm.fates.log.LogConnector;
import dm.fates.log.LogPrinter;
import dm.fates.log.Logger;

import static dm.fates.executor.ExecutorPool.immediateExecutor;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Created by davide-maestroni on 01/17/2018.
 */
public class EventualTest {

  private static void aaa() {

    class ForkStack<V> {

      ArrayList<Evaluation<V>> evaluation = new ArrayList<Evaluation<V>>();

      Statement<String> forked;

      EvaluationState<V> state;

      long timestamp = -1;
    }

    new Eventual().value("hello")
        .fork(new Forker<ForkStack<String>, String, Evaluation<String>, Statement<String>>() {

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
              @NotNull final Throwable failure, @NotNull final Statement<String> context) {
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

          public ForkStack<String> value(final ForkStack<String> stack, final String value,
              @NotNull final Statement<String> context) {
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
    assertThat(new Eventual().loop(new Observer<EvaluationCollection<Object>>() {

      public void accept(final EvaluationCollection<Object> evaluation) {
        evaluation.addFailures(Collections.<Throwable>singletonList(null)).set();
      }
    }).isFailed()).isTrue();
  }

  @Test
  public void constructor() {
    new Eventual();
  }

  @Test
  @SuppressWarnings("ConstantConditions")
  public void failure() {
    assertThat(new Eventual().failure(new IllegalAccessException())
        .getFailure()
        .getCause()).isExactlyInstanceOf(IllegalAccessException.class);
  }

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void failureNPE() {
    new Eventual().failure(null);
  }

  @Test
  public void getExecutor() {
    assertThat(new Eventual().evaluateOn(immediateExecutor()).getExecutor()).isSameAs(
        immediateExecutor());
  }

  @Test
  public void getExecutorDefault() {
    assertThat(new Eventual().getExecutor()).isNull();
  }

  @Test
  public void getIsEvaluated() {
    assertThat(new Eventual().evaluated(false).isEvaluated()).isFalse();
  }

  @Test
  public void getIsEvaluatedDefault() {
    assertThat(new Eventual().isEvaluated()).isTrue();
  }

  @Test
  public void getLoggerName() {
    assertThat(new Eventual().loggerName("test").getLoggerName()).isEqualTo("test");
  }

  @Test
  public void getLoggerNameDefault() {
    assertThat(new Eventual().getLoggerName()).isNull();
  }

  @Test
  public void immutable() {
    final Eventual eventual = new Eventual();
    assertThat(eventual.loggerName("test")).isNotSameAs(eventual);
    assertThat(eventual.loggerName(null)).isNotSameAs(eventual);
    assertThat(eventual.evaluateOn(immediateExecutor())).isNotSameAs(eventual);
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
    new Eventual().value(null).eventually(new Mapper<Object, Object>() {

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
    new Eventual().value(null).eventually(new Mapper<Object, Object>() {

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
    new Eventual().value(null).eventually(new Mapper<Object, Object>() {

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
    assertThat(new Eventual().evaluateOn(ExecutorPool.backgroundExecutor())
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
    assertThat(new Eventual().evaluateOn(ExecutorPool.backgroundExecutor())
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
    assertThat(new Eventual().evaluateOn(
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
    assertThat(new Eventual().evaluateOn(
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
    assertThat(new Eventual().statement(new Observer<Evaluation<Integer>>() {

      public void accept(final Evaluation<Integer> evaluation) {
        evaluation.fail(new IllegalAccessException());
      }
    }).getFailure().getCause()).isExactlyInstanceOf(IllegalAccessException.class);
  }

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void statementNPE() {
    new Eventual().statement(null);
  }

  @Test
  public void statementValue() {
    assertThat(new Eventual().statement(new Observer<Evaluation<Integer>>() {

      public void accept(final Evaluation<Integer> evaluation) {
        evaluation.set(3);
      }
    }).getValue()).isEqualTo(3);
  }

  @Test
  public void unevaluated() {
    final Statement<Integer> unevaluatedStatement = new Eventual().evaluated(false).value(3);
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
    final Statement<Integer> unevaluatedStatement = new Eventual().evaluated(false).value(3);
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
    assertThat(new Eventual().value(3).getValue()).isEqualTo(3);
  }
}

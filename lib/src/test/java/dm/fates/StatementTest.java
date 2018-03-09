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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import dm.fates.eventual.Action;
import dm.fates.eventual.Completer;
import dm.fates.eventual.Evaluation;
import dm.fates.eventual.EvaluationState;
import dm.fates.eventual.Mapper;
import dm.fates.eventual.Observer;
import dm.fates.eventual.RuntimeTimeoutException;
import dm.fates.eventual.SimpleState;
import dm.fates.eventual.Statement;
import dm.fates.eventual.StatementForker;
import dm.fates.eventual.Updater;
import dm.fates.log.Logger;
import dm.fates.util.ConstantConditions;

import static dm.fates.executor.ExecutorPool.backgroundExecutor;
import static dm.fates.executor.ExecutorPool.withDelay;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Created by davide-maestroni on 01/19/2018.
 */
public class StatementTest {

  @NotNull
  private static Statement<String> createStatement() {
    return new Eventual().value("test").eventually(new Mapper<String, String>() {

      public String apply(final String input) {
        return input.toUpperCase();
      }
    });
  }

  @NotNull
  private static Statement<String> createStatementAsync() {
    return new Eventual().value("test")
        .forkOn(backgroundExecutor())
        .eventually(new Mapper<String, String>() {

          public String apply(final String input) {
            return input.toUpperCase();
          }
        });
  }

  @NotNull
  private static Statement<String> createStatementFork(@NotNull final Statement<String> statement) {
    return fork(statement.eventually(new Mapper<String, String>() {

      public String apply(final String input) {
        return input.toUpperCase();
      }
    }));
  }

  @NotNull
  private static <V> Statement<V> fork(@NotNull final Statement<V> statement) {
    return statement.fork(Eventual.bufferedStatementForker(new StatementForker<Evaluation<V>, V>() {

      public Evaluation<V> done(final Evaluation<V> stack, @NotNull final Statement<V> context) {
        return stack;
      }

      public Evaluation<V> evaluation(final Evaluation<V> stack,
          @NotNull final Evaluation<V> evaluation, @NotNull final Statement<V> context) {
        if (stack != null) {
          evaluation.fail(new IllegalStateException());

        } else {
          return evaluation;
        }

        return stack;
      }

      public Evaluation<V> failure(final Evaluation<V> stack, @NotNull final Throwable failure,
          @NotNull final Statement<V> context) {
        stack.fail(failure);
        return stack;
      }

      public Evaluation<V> init(@NotNull final Statement<V> context) {
        return null;
      }

      public Evaluation<V> value(final Evaluation<V> stack, final V value,
          @NotNull final Statement<V> context) {
        stack.set(value);
        return stack;
      }
    }));
  }

  @Test
  public void cancelUnevaluated() {
    final Statement<String> statement = new Eventual().evaluated(false).value("test");
    assertThat(statement.isCancelled()).isFalse();
    assertThat(statement.isDone()).isFalse();
    assertThat(statement.cancel(false)).isTrue();
    assertThat(statement.isCancelled()).isTrue();
    assertThat(statement.isDone()).isTrue();
  }

  @Test
  public void cancelled() {
    final Statement<Void> statement = new Eventual().evaluated(false).value(null);
    statement.cancel(false);
    assertThat(statement.isCancelled()).isTrue();
  }

  @Test
  public void consumeFailure() {
    final Statement<String> statement = new Eventual().failure(new Exception("test"));
    statement.consume();
    assertThat(statement.isDone()).isTrue();
  }

  @Test
  public void consumeValue() {
    final Statement<String> statement = new Eventual().value("test");
    statement.consume();
    assertThat(statement.isDone()).isTrue();
  }

  @Test
  public void creation() {
    final Statement<String> statement =
        new Eventual().statement(new Observer<Evaluation<String>>() {

          public void accept(final Evaluation<String> evaluation) {
            evaluation.set("hello");
          }
        });
    assertThat(statement.value()).isEqualTo("hello");
  }

  @Test
  public void creationAsyncFailure() {
    final Statement<String> statement = new Eventual().evaluateOn(backgroundExecutor())
        .statement(new Observer<Evaluation<String>>() {

          public void accept(final Evaluation<String> evaluation) throws Exception {
            throw new Exception("test");
          }
        });
    assertThat(
        ConstantConditions.notNull(statement.getFailure()).getCause().getMessage()).isEqualTo(
        "test");
  }

  @Test
  public void creationFailure() {
    final Statement<String> statement =
        new Eventual().statement(new Observer<Evaluation<String>>() {

          public void accept(final Evaluation<String> evaluation) throws Exception {
            throw new Exception("test");
          }
        });
    assertThat(statement.failure().getMessage()).isEqualTo("test");
  }

  @Test
  public void doneOn() {
    final Statement<String> statement =
        new Eventual().evaluateOn(withDelay(100, TimeUnit.MILLISECONDS, backgroundExecutor()))
            .value("test");
    statement.getDone();
    assertThat(statement.isDone()).isTrue();
  }

  @Test
  public void elseCatch() {
    final Statement<String> statement =
        new Eventual().<String>failure(new IllegalStateException("test")).elseCatch(
            new Mapper<Throwable, String>() {

              public String apply(final Throwable error) {
                return error.getMessage();
              }
            });
    assertThat(statement.getValue()).isEqualTo("test");
  }

  @Test
  public void elseCatchFiltered() {
    final Statement<String> statement =
        new Eventual().<String>failure(new IllegalStateException("test")).elseCatch(
            new Mapper<Throwable, String>() {

              public String apply(final Throwable error) {
                return error.getMessage();
              }
            }, IllegalStateException.class);
    assertThat(statement.getValue()).isEqualTo("test");
  }

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void elseCatchNPE() {
    new Eventual().value(null).elseCatch(null);
  }

  @Test
  public void elseCatchNoType() {
    assertThat(new Eventual().failure(new Exception()).elseCatch(new Mapper<Throwable, Object>() {

      public Object apply(final Throwable input) {
        return null;
      }
    }, (Class<?>[]) null).failure()).isExactlyInstanceOf(Exception.class);
  }

  @Test
  public void elseCatchNotFiltered() {
    final Statement<String> statement =
        new Eventual().<String>failure(new IllegalStateException("test")).elseCatch(
            new Mapper<Throwable, String>() {

              public String apply(final Throwable error) {
                return error.getMessage();
              }
            }, IOException.class);
    assertThat(ConstantConditions.notNull(statement.getFailure()).getCause()).isExactlyInstanceOf(
        IllegalStateException.class);
  }

  @Test(expected = NullPointerException.class)
  public void elseCatchTypesNPE() {
    new Eventual().value(null).elseCatch(new Mapper<Throwable, Object>() {

      public Object apply(final Throwable input) {
        return null;
      }
    }, new Class<?>[]{null});
  }

  @Test
  public void elseDo() {
    final AtomicReference<String> ref = new AtomicReference<String>();
    final Statement<String> statement =
        new Eventual().<String>failure(new IllegalStateException("test")).elseDo(
            new Observer<Throwable>() {

              public void accept(final Throwable input) {
                ref.set(input.getMessage());
              }
            });
    assertThat(ConstantConditions.notNull(statement.getFailure()).getCause()).isExactlyInstanceOf(
        IllegalStateException.class);
    assertThat(ref.get()).isEqualTo("test");
  }

  @Test
  public void elseDoFail() {
    final Statement<String> statement =
        new Eventual().<String>failure(new IllegalStateException("test")).elseDo(
            new Observer<Throwable>() {

              public void accept(final Throwable input) {
                throw new IllegalArgumentException();
              }
            });
    assertThat(ConstantConditions.notNull(statement.getFailure()).getCause()).isExactlyInstanceOf(
        IllegalArgumentException.class);
  }

  @Test
  public void elseDoFiltered() {
    final AtomicReference<String> ref = new AtomicReference<String>();
    final Statement<String> statement =
        new Eventual().<String>failure(new IllegalStateException("test")).elseDo(
            new Observer<Throwable>() {

              public void accept(final Throwable input) {
                ref.set(input.getMessage());
              }
            }, IllegalStateException.class);
    assertThat(ConstantConditions.notNull(statement.getFailure()).getCause()).isExactlyInstanceOf(
        IllegalStateException.class);
    assertThat(ref.get()).isEqualTo("test");
  }

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void elseDoNPE() {
    new Eventual().value(null).elseDo(null);
  }

  @Test
  public void elseDoNoType() {
    final AtomicReference<String> ref = new AtomicReference<String>();
    assertThat(new Eventual().failure(new Exception()).elseDo(new Observer<Throwable>() {

      public void accept(final Throwable input) throws Exception {
        ref.set("test");
      }
    }, (Class<?>[]) null).failure()).isExactlyInstanceOf(Exception.class);
    assertThat(ref.get()).isNull();
  }

  @Test
  public void elseDoNotFiltered() {
    final AtomicReference<String> ref = new AtomicReference<String>();
    final Statement<String> statement =
        new Eventual().<String>failure(new IllegalStateException("test")).elseDo(
            new Observer<Throwable>() {

              public void accept(final Throwable input) {
                ref.set(input.getMessage());
              }
            }, IOException.class);
    assertThat(ConstantConditions.notNull(statement.getFailure()).getCause()).isExactlyInstanceOf(
        IllegalStateException.class);
    assertThat(ref.get()).isNull();
  }

  @Test(expected = NullPointerException.class)
  public void elseDoTypesNPE() {
    new Eventual().value(null).elseDo(new Observer<Throwable>() {

      public void accept(final Throwable input) {

      }
    }, new Class<?>[]{null});
  }

  @Test
  public void elseEval() {
    final Statement<Integer> statement =
        new Eventual().<Integer>failure(new IllegalStateException("test")).elseEval(
            new Mapper<Throwable, Statement<Integer>>() {

              public Statement<Integer> apply(final Throwable input) {
                return new Eventual().value(input.getMessage().length());
              }
            });
    assertThat(statement.getValue()).isEqualTo(4);
  }

  @Test
  public void elseEvalAsync() {
    final Statement<Integer> statement =
        new Eventual().<Integer>failure(new IllegalStateException("test")).elseEval(
            new Mapper<Throwable, Statement<Integer>>() {

              public Statement<Integer> apply(final Throwable input) {
                return new Eventual().value(input.getMessage().length())
                    .forkOn(withDelay(100, TimeUnit.MILLISECONDS, backgroundExecutor()));
              }
            });
    assertThat(statement.getValue()).isEqualTo(4);
  }

  @Test
  public void elseEvalAsyncFiltered() {
    final Statement<Integer> statement =
        new Eventual().<Integer>failure(new IllegalStateException("test")).elseEval(
            new Mapper<Throwable, Statement<Integer>>() {

              public Statement<Integer> apply(final Throwable input) {
                return new Eventual().value(input.getMessage().length())
                    .forkOn(withDelay(100, TimeUnit.MILLISECONDS, backgroundExecutor()));
              }
            }, IllegalStateException.class);
    assertThat(statement.getValue()).isEqualTo(4);
  }

  @Test
  public void elseEvalAsyncNotFiltered() {
    final Statement<Integer> statement =
        new Eventual().<Integer>failure(new IllegalStateException("test")).elseEval(
            new Mapper<Throwable, Statement<Integer>>() {

              public Statement<Integer> apply(final Throwable input) {
                return new Eventual().value(input.getMessage().length())
                    .forkOn(withDelay(100, TimeUnit.MILLISECONDS, backgroundExecutor()));
              }
            }, IOException.class);
    assertThat(ConstantConditions.notNull(statement.getFailure()).getCause()).isExactlyInstanceOf(
        IllegalStateException.class);
  }

  @Test
  public void elseEvalFail() {
    final Statement<Integer> statement =
        new Eventual().<Integer>failure(new IllegalStateException("test")).elseEval(
            new Mapper<Throwable, Statement<Integer>>() {

              public Statement<Integer> apply(final Throwable input) {
                return new Eventual().failure(new IllegalArgumentException());
              }
            });
    assertThat(ConstantConditions.notNull(statement.getFailure()).getCause()).isExactlyInstanceOf(
        IllegalArgumentException.class);
  }

  @Test
  public void elseEvalFiltered() {
    final Statement<Integer> statement =
        new Eventual().<Integer>failure(new IllegalStateException("test")).elseEval(
            new Mapper<Throwable, Statement<Integer>>() {

              public Statement<Integer> apply(final Throwable input) {
                return new Eventual().value(input.getMessage().length());
              }
            }, IllegalStateException.class);
    assertThat(statement.getValue()).isEqualTo(4);
  }

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void elseEvalNPE() {
    new Eventual().value(null).elseEval(null);
  }

  @Test
  public void elseEvalNoType() {
    assertThat(
        new Eventual().failure(new Exception()).elseEval(new Mapper<Throwable, Statement<?>>() {

          public Statement<?> apply(final Throwable input) {
            return new Eventual().value(null);
          }
        }, (Class<?>[]) null).failure()).isExactlyInstanceOf(Exception.class);
  }

  @Test
  public void elseEvalNotFiltered() {
    final Statement<Integer> statement =
        new Eventual().<Integer>failure(new IllegalStateException("test")).elseEval(
            new Mapper<Throwable, Statement<Integer>>() {

              public Statement<Integer> apply(final Throwable input) {
                return new Eventual().value(input.getMessage().length());
              }
            }, IOException.class);
    assertThat(ConstantConditions.notNull(statement.getFailure()).getCause()).isExactlyInstanceOf(
        IllegalStateException.class);
  }

  @Test
  public void elseEvalStatementNPE() {
    assertThat(
        new Eventual().failure(new Exception()).elseEval(new Mapper<Throwable, Statement<?>>() {

          public Statement<?> apply(final Throwable input) {
            return null;
          }
        }).failure()).isExactlyInstanceOf(NullPointerException.class);
  }

  @Test(expected = NullPointerException.class)
  public void elseEvalTypesNPE() {
    new Eventual().value(null).elseEval(new Mapper<Throwable, Statement<?>>() {

      public Statement<?> apply(final Throwable input) {
        return new Eventual().value(null);
      }
    }, new Class<?>[]{null});
  }

  @Test
  public void elseFail() {
    final Statement<String> statement =
        new Eventual().<String>failure(new IllegalStateException("test")).elseCatch(
            new Mapper<Throwable, String>() {

              public String apply(final Throwable input) {
                throw new IllegalArgumentException();
              }
            });
    assertThat(ConstantConditions.notNull(statement.getFailure()).getCause()).isExactlyInstanceOf(
        IllegalArgumentException.class);
  }

  @Test
  public void evaluate() {
    final Random random = new Random();
    final Statement<Float> statement =
        new Eventual().evaluateOn(withDelay(100, TimeUnit.MILLISECONDS, backgroundExecutor()))
            .statement(new Observer<Evaluation<Float>>() {

              public void accept(final Evaluation<Float> evaluation) {
                evaluation.set(random.nextFloat());
              }
            });
    long startTime = System.currentTimeMillis();
    final Float value = statement.getValue();
    assertThat(System.currentTimeMillis() - startTime).isGreaterThanOrEqualTo(100);
    startTime = System.currentTimeMillis();
    assertThat(statement.evaluate().getValue()).isNotEqualTo(value);
    assertThat(System.currentTimeMillis() - startTime).isGreaterThanOrEqualTo(100);
  }

  @Test
  public void evaluateFork() {
    final Random random = new Random();
    final Statement<Float> statement =
        fork(new Eventual().statement(new Observer<Evaluation<Float>>() {

          public void accept(final Evaluation<Float> evaluation) {
            evaluation.set(random.nextFloat());
          }
        })).eventually(new Mapper<Float, Float>() {

          public Float apply(final Float input) {
            return input;
          }
        });
    final Float value = statement.getValue();
    assertThat(statement.evaluate().getValue()).isNotEqualTo(value);
  }

  @Test
  public void evaluated() {
    final Random random = new Random();
    final Statement<Integer> statement =
        new Eventual().evaluated(false).statement(new Observer<Evaluation<Integer>>() {

          public void accept(final Evaluation<Integer> evaluation) {
            evaluation.set(random.nextInt());
          }
        }).evaluated();
    assertThat(statement.getValue()).isEqualTo(statement.evaluated().getValue());
    assertThat(statement.evaluated().getValue()).isEqualTo(statement.evaluated().getValue());
  }

  @Test
  public void evaluatedEvaluate() {
    final Random random = new Random();
    final Statement<Integer> statement =
        new Eventual().evaluated(false).statement(new Observer<Evaluation<Integer>>() {

          public void accept(final Evaluation<Integer> evaluation) {
            evaluation.set(random.nextInt());
          }
        });
    assertThat(statement.evaluated().getValue()).isNotEqualTo(statement.evaluate().getValue());
  }

  @Test
  public void evaluating() {
    final Statement<String> statement =
        new Eventual().value("test").forkOn(withDelay(1, TimeUnit.SECONDS, backgroundExecutor()));
    assertThat(statement.isEvaluating()).isTrue();
  }

  @Test
  public void evaluatingBackground() {
    final Statement<String> statement =
        new Eventual().value("test").forkOn(withDelay(1, TimeUnit.SECONDS, backgroundExecutor()));
    assertThat(statement.isEvaluating()).isTrue();
  }

  @Test
  public void evaluatingEvaluated() {
    final Statement<String> statement = //
        new Eventual().evaluated(false)
            .value("test")
            .forkOn(withDelay(1, SECONDS, backgroundExecutor()))
            .eventually(new Mapper<String, String>() {

              public String apply(final String input) {
                return input.toUpperCase();
              }
            })
            .evaluated();
    assertThat(statement.isEvaluating()).isTrue();
  }

  @Test(expected = IllegalStateException.class)
  public void evaluatingFailureException() {
    final Statement<String> statement = new Eventual().<String>failure(new Exception()).forkOn(
        withDelay(1, TimeUnit.SECONDS, backgroundExecutor()));
    ConstantConditions.notNull(statement.failure());
  }

  @Test
  public void evaluatingOn() {
    final Statement<String> statement =
        new Eventual().evaluateOn(withDelay(1, TimeUnit.SECONDS, backgroundExecutor()))
            .value("test");
    assertThat(statement.isEvaluating()).isTrue();
  }

  @Test
  public void evaluatingUnevaluated() {
    final Statement<String> statement = //
        new Eventual().evaluated(false)
            .value("test")
            .forkOn(withDelay(1, SECONDS, backgroundExecutor()))
            .eventually(new Mapper<String, String>() {

              public String apply(final String input) {
                return input.toUpperCase();
              }
            });
    assertThat(statement.isEvaluating()).isFalse();
  }

  @Test(expected = IllegalStateException.class)
  public void evaluatingValueException() {
    final Statement<String> statement =
        new Eventual().value("test").forkOn(withDelay(1, TimeUnit.SECONDS, backgroundExecutor()));
    statement.value();
  }

  @Test
  public void eventually() {
    final Statement<String> statement =
        new Eventual().value("test").eventually(new Mapper<String, String>() {

          public String apply(final String input) {
            return input.toUpperCase();
          }
        });
    assertThat(statement.getValue()).isEqualTo("TEST");
  }

  @Test
  public void eventuallyDo() {
    final AtomicReference<String> ref = new AtomicReference<String>();
    final Statement<String> statement =
        new Eventual().value("test").eventuallyDo(new Observer<String>() {

          public void accept(final String input) {
            ref.set(input);
          }
        });
    assertThat(statement.getValue()).isEqualTo("test");
    assertThat(ref.get()).isEqualTo("test");
  }

  @Test
  public void eventuallyDoFail() {
    final Statement<String> statement =
        new Eventual().value("test").eventuallyDo(new Observer<String>() {

          public void accept(final String input) {
            throw new IllegalArgumentException();
          }
        });
    assertThat(ConstantConditions.notNull(statement.getFailure()).getCause()).isExactlyInstanceOf(
        IllegalArgumentException.class);
  }

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void eventuallyDoNPE() {
    new Eventual().value(null).eventuallyDo(null);
  }

  @Test
  public void eventuallyEval() {
    final Statement<Integer> statement =
        new Eventual().value("test").eventuallyEval(new Mapper<String, Statement<Integer>>() {

          public Statement<Integer> apply(final String input) {
            return new Eventual().value(input.length());
          }
        });
    assertThat(statement.getValue()).isEqualTo(4);
  }

  @Test
  public void eventuallyEvalAsync() {
    final Statement<Integer> statement =
        new Eventual().value("test").eventuallyEval(new Mapper<String, Statement<Integer>>() {

          public Statement<Integer> apply(final String input) {
            return new Eventual().value(input.length())
                .forkOn(withDelay(100, TimeUnit.MILLISECONDS, backgroundExecutor()));
          }
        });
    assertThat(statement.getValue()).isEqualTo(4);
  }

  @Test
  public void eventuallyEvalFail() {
    final Statement<Integer> statement =
        new Eventual().value("test").eventuallyEval(new Mapper<String, Statement<Integer>>() {

          public Statement<Integer> apply(final String input) {
            return new Eventual().failure(new IllegalArgumentException());
          }
        });
    assertThat(ConstantConditions.notNull(statement.getFailure()).getCause()).isExactlyInstanceOf(
        IllegalArgumentException.class);
  }

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void eventuallyEvalNPE() {
    new Eventual().value(null).eventuallyEval(null);
  }

  @Test
  public void eventuallyEvalStatementNPE() {
    assertThat(new Eventual().value(null).eventuallyEval(new Mapper<Object, Statement<Object>>() {

      public Statement<Object> apply(final Object input) {
        return null;
      }
    }).failure()).isExactlyInstanceOf(NullPointerException.class);
  }

  @Test
  public void eventuallyFail() {
    final Statement<String> statement =
        new Eventual().value("test").eventually(new Mapper<String, String>() {

          public String apply(final String input) {
            throw new IllegalArgumentException();
          }
        });
    assertThat(ConstantConditions.notNull(statement.getFailure()).getCause()).isExactlyInstanceOf(
        IllegalArgumentException.class);
  }

  @Test(expected = IllegalStateException.class)
  public void eventuallyIllegal() {
    final Statement<String> statement = new Eventual().value("test");
    statement.eventually(new Mapper<String, String>() {

      public String apply(final String input) {
        return input.toUpperCase();
      }
    });
    statement.eventually(new ToUpper());
  }

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void eventuallyNPE() {
    new Eventual().value(null).eventually(null);
  }

  @Test
  public void eventuallyTry() {
    final AtomicCloseable closeable = new AtomicCloseable();
    final Statement<String> statement =
        new Eventual().value("test").eventuallyTry(new Mapper<String, Closeable>() {

          public Closeable apply(final String input) {
            return closeable;
          }
        }, new Mapper<String, String>() {

          public String apply(final String input) {
            return input.toUpperCase();
          }
        });
    assertThat(statement.getValue()).isEqualTo("TEST");
    assertThat(closeable.isCalled()).isTrue();
  }

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void eventuallyTryCloseableNPE() {
    new Eventual().value(null).eventuallyTry(null, new Mapper<Object, Object>() {

      public Object apply(final Object input) {
        return null;
      }
    });
  }

  @Test
  public void eventuallyTryDo() {
    final AtomicCloseable closeable = new AtomicCloseable();
    final AtomicReference<String> ref = new AtomicReference<String>();
    final Statement<String> statement =
        new Eventual().value("test").eventuallyTryDo(new Mapper<String, Closeable>() {

          public Closeable apply(final String input) {
            return closeable;
          }
        }, new Observer<String>() {

          public void accept(final String input) {
            ref.set(input);
          }
        });
    assertThat(statement.getValue()).isEqualTo("test");
    assertThat(ref.get()).isEqualTo("test");
    assertThat(closeable.isCalled()).isTrue();
  }

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void eventuallyTryDoCloseableNPE() {
    new Eventual().value(null).eventuallyTryDo(null, new Observer<Object>() {

      public void accept(final Object input) {

      }
    });
  }

  @Test
  public void eventuallyTryDoException() {
    final AtomicReference<String> ref = new AtomicReference<String>();
    final Statement<String> statement =
        new Eventual().value("test").eventuallyTryDo(new Mapper<String, Closeable>() {

          public Closeable apply(final String input) {
            throw new RuntimeException();
          }
        }, new Observer<String>() {

          public void accept(final String input) {
            ref.set(input);
          }
        });
    assertThat(statement.isFailed()).isTrue();
    assertThat(ref.get()).isNull();
  }

  @Test
  public void eventuallyTryDoFail() {
    final AtomicCloseable closeable = new AtomicCloseable();
    final Statement<String> statement =
        new Eventual().value("test").eventuallyTryDo(new Mapper<String, Closeable>() {

          public Closeable apply(final String input) {
            return closeable;
          }
        }, new Observer<String>() {

          public void accept(final String input) {
            throw new IllegalArgumentException();
          }
        });
    assertThat(ConstantConditions.notNull(statement.getFailure()).getCause()).isExactlyInstanceOf(
        IllegalArgumentException.class);
    assertThat(closeable.isCalled()).isTrue();
  }

  @Test
  public void eventuallyTryDoIOException() {
    final AtomicReference<String> ref = new AtomicReference<String>();
    final Statement<String> statement =
        new Eventual().value("test").eventuallyTryDo(new Mapper<String, Closeable>() {

          public Closeable apply(final String input) {
            return new Closeable() {

              public void close() throws IOException {
                throw new IOException();
              }
            };
          }
        }, new Observer<String>() {

          public void accept(final String input) {
            ref.set(input);
          }
        });
    assertThat(statement.getValue()).isEqualTo("test");
    assertThat(ref.get()).isEqualTo("test");
  }

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void eventuallyTryDoNPE() {
    new Eventual().value(null).eventuallyTryDo(new Mapper<Object, Closeable>() {

      public Closeable apply(final Object input) {
        return null;
      }
    }, null);
  }

  @Test
  public void eventuallyTryDoNull() {
    final AtomicReference<String> ref = new AtomicReference<String>();
    final Statement<String> statement =
        new Eventual().value("test").eventuallyTryDo(new Mapper<String, Closeable>() {

          public Closeable apply(final String input) {
            return null;
          }
        }, new Observer<String>() {

          public void accept(final String input) {
            ref.set(input);
          }
        });
    assertThat(statement.getValue()).isEqualTo("test");
    assertThat(ref.get()).isEqualTo("test");
  }

  @Test
  public void eventuallyTryEval() {
    final AtomicCloseable closeable = new AtomicCloseable();
    final Statement<Integer> statement =
        new Eventual().value("test").eventuallyTryEval(new Mapper<String, Closeable>() {

          public Closeable apply(final String input) {
            return closeable;
          }
        }, new Mapper<String, Statement<Integer>>() {

          public Statement<Integer> apply(final String input) {
            return new Eventual().value(input.length());
          }
        });
    assertThat(statement.getValue()).isEqualTo(4);
    assertThat(closeable.isCalled()).isTrue();
  }

  @Test
  public void eventuallyTryEvalAsync() {
    final AtomicCloseable closeable = new AtomicCloseable();
    final Statement<Integer> statement =
        new Eventual().value("test").eventuallyTryEval(new Mapper<String, Closeable>() {

          public Closeable apply(final String input) {
            return closeable;
          }
        }, new Mapper<String, Statement<Integer>>() {

          public Statement<Integer> apply(final String input) {
            return new Eventual().value(input.length())
                .forkOn(withDelay(100, TimeUnit.MILLISECONDS, backgroundExecutor()));
          }
        });
    assertThat(statement.getValue()).isEqualTo(4);
    assertThat(closeable.isCalled()).isTrue();
  }

  @Test
  public void eventuallyTryEvalAsyncException() {
    final Statement<Integer> statement =
        new Eventual().value("test").eventuallyTryEval(new Mapper<String, Closeable>() {

          public Closeable apply(final String input) {
            throw new RuntimeException();
          }
        }, new Mapper<String, Statement<Integer>>() {

          public Statement<Integer> apply(final String input) {
            return new Eventual().value(input.length())
                .forkOn(withDelay(100, TimeUnit.MILLISECONDS, backgroundExecutor()));
          }
        });
    assertThat(statement.getFailure()).isNotNull();
  }

  @Test
  public void eventuallyTryEvalAsyncIOException() {
    final Statement<Integer> statement =
        new Eventual().value("test").eventuallyTryEval(new Mapper<String, Closeable>() {

          public Closeable apply(final String input) {
            return new Closeable() {

              public void close() throws IOException {
                throw new IOException();
              }
            };
          }
        }, new Mapper<String, Statement<Integer>>() {

          public Statement<Integer> apply(final String input) {
            return new Eventual().value(input.length())
                .forkOn(withDelay(100, TimeUnit.MILLISECONDS, backgroundExecutor()));
          }
        });
    assertThat(statement.getFailure()).isNotNull();
  }

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void eventuallyTryEvalCloseableNPE() {
    new Eventual().value(null).eventuallyTryEval(null, new Mapper<Object, Statement<Object>>() {

      public Statement<Object> apply(final Object input) {
        return new Eventual().value(null);
      }
    });
  }

  @Test
  public void eventuallyTryEvalException() {
    final Statement<Integer> statement =
        new Eventual().value("test").eventuallyTryEval(new Mapper<String, Closeable>() {

          public Closeable apply(final String input) {
            throw new RuntimeException();
          }
        }, new Mapper<String, Statement<Integer>>() {

          public Statement<Integer> apply(final String input) {
            return new Eventual().value(input.length());
          }
        });
    assertThat(statement.isFailed()).isTrue();
  }

  @Test
  public void eventuallyTryEvalFail() {
    final AtomicCloseable closeable = new AtomicCloseable();
    final Statement<Integer> statement =
        new Eventual().value("test").eventuallyTryEval(new Mapper<String, Closeable>() {

          public Closeable apply(final String input) {
            return closeable;
          }
        }, new Mapper<String, Statement<Integer>>() {

          public Statement<Integer> apply(final String input) {
            return new Eventual().failure(new IllegalArgumentException());
          }
        });
    assertThat(ConstantConditions.notNull(statement.getFailure()).getCause()).isExactlyInstanceOf(
        IllegalArgumentException.class);
    assertThat(closeable.isCalled()).isTrue();
  }

  @Test
  public void eventuallyTryEvalIOException() {
    final Statement<Integer> statement =
        new Eventual().value("test").eventuallyTryEval(new Mapper<String, Closeable>() {

          public Closeable apply(final String input) {
            return new Closeable() {

              public void close() throws IOException {
                throw new IOException();
              }
            };
          }
        }, new Mapper<String, Statement<Integer>>() {

          public Statement<Integer> apply(final String input) {
            return new Eventual().value(input.length());
          }
        });
    assertThat(statement.isFailed()).isTrue();
  }

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void eventuallyTryEvalNPE() {
    new Eventual().value(null).eventuallyTryEval(new Mapper<Object, Closeable>() {

      public Closeable apply(final Object input) {
        return null;
      }
    }, null);
  }

  @Test
  public void eventuallyTryEvalNull() {
    final Statement<Integer> statement =
        new Eventual().value("test").eventuallyTryEval(new Mapper<String, Closeable>() {

          public Closeable apply(final String input) {
            return null;
          }
        }, new Mapper<String, Statement<Integer>>() {

          public Statement<Integer> apply(final String input) {
            return new Eventual().value(input.length());
          }
        });
    assertThat(statement.getValue()).isEqualTo(4);
  }

  @Test
  public void eventuallyTryEvalStatementNPE() {
    assertThat(new Eventual().value(null).eventuallyTryEval(new Mapper<Object, Closeable>() {

      public Closeable apply(final Object input) {
        return null;
      }
    }, new Mapper<Object, Statement<Object>>() {

      public Statement<Object> apply(final Object input) {
        return null;
      }
    }).failure()).isExactlyInstanceOf(NullPointerException.class);
  }

  @Test
  public void eventuallyTryException() {
    final Statement<String> statement =
        new Eventual().value("test").eventuallyTry(new Mapper<String, Closeable>() {

          public Closeable apply(final String input) {
            throw new RuntimeException();
          }
        }, new Mapper<String, String>() {

          public String apply(final String input) {
            return input.toUpperCase();
          }
        });
    assertThat(statement.isFailed()).isTrue();
  }

  @Test
  public void eventuallyTryFail() {
    final AtomicCloseable closeable = new AtomicCloseable();
    final Statement<String> statement =
        new Eventual().value("test").eventuallyTry(new Mapper<String, Closeable>() {

          public Closeable apply(final String input) {
            return closeable;
          }
        }, new Mapper<String, String>() {

          public String apply(final String input) {
            throw new IllegalArgumentException();
          }
        });
    assertThat(ConstantConditions.notNull(statement.getFailure()).getCause()).isExactlyInstanceOf(
        IllegalArgumentException.class);
    assertThat(closeable.isCalled()).isTrue();
  }

  @Test
  public void eventuallyTryIOException() {
    final Statement<String> statement =
        new Eventual().value("test").eventuallyTry(new Mapper<String, Closeable>() {

          public Closeable apply(final String input) {
            return new Closeable() {

              public void close() throws IOException {
                throw new IOException();
              }
            };
          }
        }, new Mapper<String, String>() {

          public String apply(final String input) {
            return input.toUpperCase();
          }
        });
    assertThat(statement.getValue()).isEqualTo("TEST");
  }

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void eventuallyTryNPE() {
    new Eventual().value(null).eventuallyTry(new Mapper<Object, Closeable>() {

      public Closeable apply(final Object input) {
        return null;
      }
    }, null);
  }

  @Test
  public void eventuallyTryNull() {
    final Statement<String> statement =
        new Eventual().value("test").eventuallyTry(new Mapper<String, Closeable>() {

          public Closeable apply(final String input) {
            return null;
          }
        }, new Mapper<String, String>() {

          public String apply(final String input) {
            return input.toUpperCase();
          }
        });
    assertThat(statement.getValue()).isEqualTo("TEST");
  }

  @Test
  public void failEventually() {
    final Statement<String> statement =
        new Eventual().<String>failure(new NullPointerException("test")).eventually(
            new Mapper<String, String>() {

              public String apply(final String input) {
                return input.toUpperCase();
              }
            });
    assertThat(
        ConstantConditions.notNull(statement.getFailure()).getCause().getMessage()).isEqualTo(
        "test");
  }

  @Test
  public void failure() {
    final Statement<String> statement = new Eventual().failure(new IllegalArgumentException());
    assertThat(statement.failure()).isExactlyInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void failureCancelled() {
    final Statement<String> statement = new Eventual().failure(new IllegalArgumentException());
    assertThat(statement.isCancelled()).isFalse();
  }

  @Test
  public void failureEvaluating() {
    final Statement<String> statement = new Eventual().failure(new IllegalArgumentException());
    assertThat(statement.isEvaluating()).isFalse();
  }

  @Test
  public void failureFailed() {
    final Statement<String> statement = new Eventual().failure(new IllegalArgumentException());
    assertThat(statement.isFailed()).isTrue();
  }

  @Test(expected = IllegalStateException.class)
  public void failureIllegal() {
    final Statement<Object> statement = new Eventual().failure(new IllegalArgumentException());
    statement.eventuallyDo(new Observer<Object>() {

      public void accept(final Object input) {
      }
    });
    ConstantConditions.notNull(statement.failure());
  }

  @Test(expected = IllegalStateException.class)
  public void failureInvalidStateEvaluating() {
    final Statement<Void> statement = new Eventual().evaluated(false).failure(new Exception());
    assertThat(statement.failure());
  }

  @Test(expected = IllegalStateException.class)
  public void failureInvalidStateValue() {
    final Statement<String> statement = new Eventual().value(null);
    assertThat(statement.failure());
  }

  @Test
  public void failureSet() {
    final Statement<String> statement = new Eventual().failure(new IllegalArgumentException());
    assertThat(statement.isSet()).isFalse();
  }

  @Test
  public void fork() {
    final Statement<String> statement = createStatementFork(new Eventual().value("test"));
    assertThat(statement.isFinal()).isTrue();
    assertThat(statement.isDone()).isFalse();
    assertThat(statement.eventually(new Mapper<String, String>() {

      public String apply(final String input) {
        return input;
      }
    }).value()).isEqualTo("TEST");
  }

  @Test
  public void forkCancel() {
    final Statement<String> statement = createStatementFork(
        new Eventual().evaluateOn(withDelay(1, TimeUnit.SECONDS, backgroundExecutor()))
            .value("test"));
    assertThat(statement.isFinal()).isTrue();
    assertThat(statement.isDone()).isFalse();
    assertThat(statement.cancel(true)).isTrue();
    assertThat(statement.isCancelled()).isFalse();
    assertThat(statement.isFailed()).isFalse();
    assertThat(statement.eventually(new Mapper<String, String>() {

      public String apply(final String input) {
        return input;
      }
    }).isFailed()).isTrue();
  }

  @Test
  public void forkCancelFailure() {
    final Statement<String> statement = createStatementFork(new Eventual().value("test"));
    assertThat(statement.isFinal()).isTrue();
    assertThat(statement.isDone()).isFalse();
    assertThat(statement.cancel(false)).isFalse();
    assertThat(statement.isCancelled()).isFalse();
    assertThat(statement.eventually(new Mapper<String, String>() {

      public String apply(final String input) {
        return input;
      }
    }).value()).isEqualTo("TEST");
  }

  @Test
  public void forkCancelForked() {
    final Statement<String> statement = createStatementFork(
        new Eventual().evaluateOn(withDelay(1, TimeUnit.SECONDS, backgroundExecutor()))
            .value("test"));
    final Statement<String> forked = statement.eventually(new Mapper<String, String>() {

      public String apply(final String input) {
        return input;
      }
    });
    assertThat(statement.isFinal()).isTrue();
    assertThat(statement.isDone()).isFalse();
    assertThat(forked.cancel(true)).isTrue();
    assertThat(statement.isCancelled()).isFalse();
    assertThat(statement.isFailed()).isFalse();
    assertThat(forked.isCancelled()).isTrue();
    assertThat(forked.isFailed()).isTrue();
  }

  @Test
  public void forkDefaultFunctions() {
    final Statement<String> statement = //
        new Eventual().value("test")
            .fork(null, null, null, null,
                new Updater<Object, Evaluation<String>, Statement<String>>() {

                  public Object update(final Object stack, final Evaluation<String> evaluation,
                      @NotNull final Statement<String> statement) {
                    evaluation.set("TEST");
                    return null;
                  }
                });
    assertThat(statement.isFinal()).isTrue();
    assertThat(statement.isDone()).isFalse();
    assertThat(statement.eventually(new Mapper<String, String>() {

      public String apply(final String input) {
        return input;
      }
    }).value()).isEqualTo("TEST");
  }

  @Test
  public void forkDefaultStatement() {
    final Statement<String> statement = //
        new Eventual().value("test").fork(null, null, null, null, null);
    assertThat(statement.isFinal()).isTrue();
    assertThat(statement.isDone()).isFalse();
    assertThat(statement.eventually(new Mapper<String, String>() {

      public String apply(final String input) {
        return input;
      }
    }).failure()).isExactlyInstanceOf(IllegalStateException.class);
  }

  @Test
  public void forkFailure() {
    final Statement<String> statement =
        createStatementFork(new Eventual().<String>failure(new IllegalArgumentException()));
    assertThat(statement.isFinal()).isTrue();
    assertThat(statement.isDone()).isFalse();
    assertThat(statement.isCancelled()).isFalse();
    assertThat(statement.isFailed()).isFalse();
    assertThat(statement.eventually(new Mapper<String, String>() {

      public String apply(final String input) {
        return input;
      }
    }).isFailed()).isTrue();
  }

  @Test
  public void forkFailureDoneFail() {
    final Statement<String> statement = //
        new Eventual().<String>failure(new Exception()).fork(null, null, null,
            new Completer<String, Statement<String>>() {

              public String complete(final String stack,
                  @NotNull final Statement<String> statement) {
                throw new RuntimeException("test");
              }
            }, null);
    assertThat(statement.isFinal()).isTrue();
    assertThat(statement.isDone()).isFalse();
    assertThat(statement.eventually(new Mapper<String, String>() {

      public String apply(final String input) {
        return input;
      }
    }).failure().getMessage()).isEqualTo("test");
  }

  @Test(expected = IllegalStateException.class)
  public void forkFailureException() {
    final Statement<String> statement =
        createStatementFork(new Eventual().<String>failure(new Exception()));
    ConstantConditions.notNull(statement.failure());
  }

  @Test
  public void forkFailureFail() {
    final Statement<String> statement = //
        new Eventual().<String>failure(new Exception()).fork(null, null,
            new Updater<String, Throwable, Statement<String>>() {

              public String update(final String stack, final Throwable input,
                  @NotNull final Statement<String> statement) {
                throw new RuntimeException("test");
              }
            }, null, null);
    assertThat(statement.isFinal()).isTrue();
    assertThat(statement.isDone()).isFalse();
    assertThat(statement.eventually(new Mapper<String, String>() {

      public String apply(final String input) {
        return input;
      }
    }).failure().getMessage()).isEqualTo("test");
  }

  @Test
  public void forkFunctions() {
    final Statement<String> statement = //
        new Eventual().value("test").fork(new Mapper<Statement<String>, EvaluationState<String>>() {

          public EvaluationState<String> apply(final Statement<String> input) {
            return null;
          }
        }, new Updater<EvaluationState<String>, String, Statement<String>>() {

          public EvaluationState<String> update(final EvaluationState<String> stack,
              final String value, @NotNull final Statement<String> statement) {
            return SimpleState.ofValue(value);
          }
        }, new Updater<EvaluationState<String>, Throwable, Statement<String>>() {

          public EvaluationState<String> update(final EvaluationState<String> stack,
              final Throwable failure, @NotNull final Statement<String> statement) {
            return SimpleState.ofFailure(failure);
          }
        }, new Completer<EvaluationState<String>, Statement<String>>() {

          public EvaluationState<String> complete(final EvaluationState<String> stack,
              @NotNull final Statement<String> statement) {
            return stack;
          }
        }, new Updater<EvaluationState<String>, Evaluation<String>, Statement<String>>() {

          public EvaluationState<String> update(final EvaluationState<String> stack,
              final Evaluation<String> evaluation, @NotNull final Statement<String> statement) {
            if (stack != null) {
              stack.to(evaluation);
            }

            return null;
          }
        });
    assertThat(statement.isFinal()).isTrue();
    assertThat(statement.isDone()).isFalse();
    assertThat(statement.eventually(new Mapper<String, String>() {

      public String apply(final String input) {
        return input.toUpperCase();
      }
    }).value()).isEqualTo("TEST");
  }

  @Test(expected = UnsupportedOperationException.class)
  public void forkGetFailureException() {
    final Statement<String> statement =
        createStatementFork(new Eventual().<String>failure(new Exception()));
    ConstantConditions.notNull(statement.getFailure());
  }

  @Test(expected = UnsupportedOperationException.class)
  public void forkGetValueException() {
    final Statement<String> statement = createStatementFork(new Eventual().value("test"));
    statement.getValue();
  }

  @Test
  public void forkInit() {
    final Statement<String> statement = //
        new Eventual().value("test").fork(new Mapper<Statement<String>, String>() {

          public String apply(final Statement<String> input) {
            return "TEST";
          }
        }, null, null, null, new Updater<String, Evaluation<String>, Statement<String>>() {

          public String update(final String stack, final Evaluation<String> evaluation,
              @NotNull final Statement<String> statement) {
            evaluation.set(stack);
            return stack;
          }
        });
    assertThat(statement.isFinal()).isTrue();
    assertThat(statement.isDone()).isFalse();
    assertThat(statement.eventually(new Mapper<String, String>() {

      public String apply(final String input) {
        return input;
      }
    }).value()).isEqualTo("TEST");
  }

  @Test
  public void forkInitFail() {
    final Statement<String> statement = //
        new Eventual().value("test").fork(new Mapper<Statement<String>, String>() {

          public String apply(final Statement<String> input) {
            throw new RuntimeException("test");
          }
        }, null, null, null, null);
    assertThat(statement.isFinal()).isTrue();
    assertThat(statement.isDone()).isFalse();
    assertThat(statement.eventually(new Mapper<String, String>() {

      public String apply(final String input) {
        return input;
      }
    }).failure().getMessage()).isEqualTo("test");
  }

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void forkNPE() {
    new Eventual().value(null).fork(null);
  }

  @Test
  public void forkStatementFail() {
    final Statement<String> statement = //
        new Eventual().value("test")
            .fork(null, null, null, null,
                new Updater<String, Evaluation<String>, Statement<String>>() {

                  public String update(final String stack, final Evaluation<String> input,
                      @NotNull final Statement<String> statement) {
                    throw new RuntimeException("test");
                  }
                });
    assertThat(statement.isFinal()).isTrue();
    assertThat(statement.isDone()).isFalse();
    assertThat(statement.eventually(new Mapper<String, String>() {

      public String apply(final String input) {
        return input;
      }
    }).failure().getMessage()).isEqualTo("test");
  }

  @Test
  public void forkValueDoneFail() {
    final Statement<String> statement = //
        new Eventual().value("test")
            .fork(null, null, null, new Completer<String, Statement<String>>() {

              public String complete(final String stack,
                  @NotNull final Statement<String> statement) {
                throw new RuntimeException("test");
              }
            }, null);
    assertThat(statement.isFinal()).isTrue();
    assertThat(statement.isDone()).isFalse();
    assertThat(statement.eventually(new Mapper<String, String>() {

      public String apply(final String input) {
        return input;
      }
    }).failure().getMessage()).isEqualTo("test");
  }

  @Test(expected = IllegalStateException.class)
  public void forkValueException() {
    final Statement<String> statement = createStatementFork(new Eventual().value("test"));
    statement.value();
  }

  @Test
  public void forkValueFail() {
    final Statement<String> statement = //
        new Eventual().value("test").fork(null, new Updater<String, String, Statement<String>>() {

          public String update(final String stack, final String input,
              @NotNull final Statement<String> statement) {
            throw new RuntimeException("test");
          }
        }, null, null, null);
    assertThat(statement.isFinal()).isTrue();
    assertThat(statement.isDone()).isFalse();
    assertThat(statement.eventually(new Mapper<String, String>() {

      public String apply(final String input) {
        return input;
      }
    }).failure().getMessage()).isEqualTo("test");
  }

  @Test
  public void get() throws ExecutionException, InterruptedException {
    final Statement<String> statement =
        new Eventual().evaluateOn(withDelay(100, TimeUnit.MILLISECONDS, backgroundExecutor()))
            .value("test");
    assertThat(statement.get()).isEqualTo("test");
  }

  @Test(expected = CancellationException.class)
  public void getCancelled() throws ExecutionException, InterruptedException {
    final Statement<String> statement =
        new Eventual().evaluateOn(withDelay(100, TimeUnit.MILLISECONDS, backgroundExecutor()))
            .value("test");
    assertThat(statement.cancel(true)).isTrue();
    statement.get();
  }

  @Test(expected = CancellationException.class)
  public void getCancelledWithTimeout() throws ExecutionException, InterruptedException,
      TimeoutException {
    final Statement<String> statement =
        new Eventual().evaluateOn(withDelay(100, TimeUnit.MILLISECONDS, backgroundExecutor()))
            .value("test");
    assertThat(statement.cancel(true)).isTrue();
    statement.get(10, TimeUnit.MILLISECONDS);
  }

  @Test(expected = ExecutionException.class)
  public void getFailure() throws ExecutionException, InterruptedException {
    final Statement<String> statement =
        new Eventual().evaluateOn(withDelay(100, TimeUnit.MILLISECONDS, backgroundExecutor()))
            .failure(new IllegalArgumentException());
    statement.get();
  }

  @Test(expected = IllegalStateException.class)
  public void getFailureIllegal() {
    final Statement<Object> statement = new Eventual().failure(new IllegalArgumentException());
    statement.eventuallyDo(new Observer<Object>() {

      public void accept(final Object input) {
      }
    });
    ConstantConditions.notNull(statement.getFailure());
  }

  @Test(expected = NullPointerException.class)
  @SuppressWarnings({"ConstantConditions", "ThrowableNotThrown"})
  public void getFailureNPE() {
    new Eventual().value(null).getFailure(0, null);
  }

  @Test(expected = RuntimeTimeoutException.class)
  public void getFailureTimeout() throws ExecutionException, InterruptedException,
      TimeoutException {
    final Statement<String> statement =
        new Eventual().evaluateOn(withDelay(1, TimeUnit.SECONDS, backgroundExecutor()))
            .value("test");
    assertThat(statement.getFailure(10, TimeUnit.MILLISECONDS));
  }

  @Test(expected = ExecutionException.class)
  public void getFailureWithTimeout() throws ExecutionException, InterruptedException,
      TimeoutException {
    final Statement<String> statement =
        new Eventual().evaluateOn(withDelay(100, TimeUnit.MILLISECONDS, backgroundExecutor()))
            .failure(new IllegalArgumentException());
    statement.get(1, TimeUnit.SECONDS);
  }

  @Test(expected = TimeoutException.class)
  public void getTimeout() throws ExecutionException, InterruptedException, TimeoutException {
    final Statement<String> statement =
        new Eventual().evaluateOn(withDelay(1, TimeUnit.SECONDS, backgroundExecutor()))
            .value("test");
    statement.get(10, TimeUnit.MILLISECONDS);
  }

  @Test(expected = IllegalStateException.class)
  public void getValueIllegal() {
    final Statement<Object> statement = new Eventual().value(null);
    statement.eventuallyDo(new Observer<Object>() {

      public void accept(final Object input) {
      }
    });
    statement.getValue();
  }

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void getValueNPE() {
    new Eventual().value(null).getValue(0, null);
  }

  @Test(expected = RuntimeTimeoutException.class)
  public void getValueTimeout() {
    final Statement<String> statement = //
        new Eventual().value("test")
            .forkOn(withDelay(1, TimeUnit.SECONDS, backgroundExecutor()))
            .eventually(new ToUpper());
    statement.getValue(0, TimeUnit.MILLISECONDS);
  }

  @Test
  public void getWithTimeout() throws ExecutionException, InterruptedException, TimeoutException {
    final Statement<String> statement =
        new Eventual().evaluateOn(withDelay(100, TimeUnit.MILLISECONDS, backgroundExecutor()))
            .value("test");
    assertThat(statement.get(1, TimeUnit.SECONDS)).isEqualTo("test");
  }

  @Test
  public void isFinal() {
    final Statement<String> statement = new Eventual().failure(new IllegalArgumentException());
    assertThat(statement.isFinal()).isTrue();
  }

  @Test
  public void isNotFinal() {
    final Statement<String> statement = new Eventual().failure(new IllegalArgumentException());
    final Statement<String> newStatement = statement.eventuallyDo(new Observer<String>() {

      public void accept(final String input) {

      }
    });
    assertThat(statement.isFinal()).isFalse();
    assertThat(newStatement.isFinal()).isTrue();
  }

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void onNPE() {
    new Eventual().value(null).forkOn(null);
  }

  @Test
  public void serialize() throws IOException, ClassNotFoundException {
    final Statement<String> statement = createStatement();
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(statement);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final Statement<String> deserialized =
        (Statement<String>) objectInputStream.readObject();
    assertThat(deserialized.getValue()).isEqualTo("TEST");
  }

  @Test
  public void serializeAsync() throws IOException, ClassNotFoundException {
    final Statement<String> statement = createStatementAsync();
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(statement);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final Statement<String> deserialized =
        (Statement<String>) objectInputStream.readObject();
    assertThat(deserialized.getValue()).isEqualTo("TEST");
  }

  @Test
  public void serializeElse() throws IOException, ClassNotFoundException {
    final Statement<String> statement =
        new Eventual().<String>failure(new IllegalArgumentException("test")).elseCatch(
            new ToMessage());
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(statement);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final Statement<String> deserialized =
        (Statement<String>) objectInputStream.readObject();
    assertThat(deserialized.getValue()).isEqualTo("test");
  }

  @Test
  public void serializeElseDo() throws IOException, ClassNotFoundException {
    final Statement<String> statement =
        new Eventual().<String>failure(new IllegalArgumentException("test")).elseDo(
            new PrintMessage("test.logger"));
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(statement);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final Statement<String> deserialized =
        (Statement<String>) objectInputStream.readObject();
    assertThat(
        ConstantConditions.notNull(deserialized.getFailure()).getCause().getMessage()).isEqualTo(
        "test");
  }

  @Test
  public void serializeElseEval() throws IOException, ClassNotFoundException {
    final Statement<String> statement =
        new Eventual().<String>failure(new IllegalArgumentException("test")).elseEval(
            new ToMessageStatement());
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(statement);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final Statement<String> deserialized =
        (Statement<String>) objectInputStream.readObject();
    assertThat(deserialized.getValue()).isEqualTo("test");
  }

  @Test(expected = IOException.class)
  public void serializeError() throws IOException, ClassNotFoundException {
    final Statement<String> promise = new Eventual().statement(new Observer<Evaluation<String>>() {

      public void accept(final Evaluation<String> evaluation) {
        evaluation.set("test");
      }
    });
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(promise);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    objectInputStream.readObject();
  }

  @Test(expected = IOException.class)
  public void serializeErrorPropagation() throws IOException, ClassNotFoundException {
    final Statement<String> promise = createStatement().elseCatch(new Mapper<Throwable, String>() {

      public String apply(final Throwable input) {
        return null;
      }
    });
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(promise);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    objectInputStream.readObject();
  }

  @Test
  public void serializeEvaluated() throws IOException, ClassNotFoundException {
    final Statement<String> statement = createStatement().evaluate();
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(statement);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final Statement<String> deserialized =
        (Statement<String>) objectInputStream.readObject();
    assertThat(deserialized.getValue()).isEqualTo("TEST");
  }

  @Test
  public void serializeEventually() throws IOException, ClassNotFoundException {
    final Statement<String> statement = new Eventual().value("test").eventually(new ToUpper());
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(statement);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final Statement<String> deserialized =
        (Statement<String>) objectInputStream.readObject();
    assertThat(deserialized.getValue()).isEqualTo("TEST");
  }

  @Test
  public void serializeEventuallyDo() throws IOException, ClassNotFoundException {
    final Statement<String> statement =
        new Eventual().value("test").eventuallyDo(new PrintString("test.logger"));
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(statement);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final Statement<String> deserialized =
        (Statement<String>) objectInputStream.readObject();
    assertThat(deserialized.getValue()).isEqualTo("test");
  }

  @Test
  public void serializeEventuallyEval() throws IOException, ClassNotFoundException {
    final Statement<String> statement =
        new Eventual().value("test").eventuallyEval(new ToUpperStatement());
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(statement);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final Statement<String> deserialized =
        (Statement<String>) objectInputStream.readObject();
    assertThat(deserialized.getValue()).isEqualTo("TEST");
  }

  @Test
  public void serializeEventuallyTry() throws IOException, ClassNotFoundException {
    final Statement<AtomicBoolean> statement = new Eventual().value(new AtomicBoolean())
        .eventuallyTry(new ToCloseable(), new Identity<AtomicBoolean>());
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(statement);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final Statement<AtomicBoolean> deserialized =
        (Statement<AtomicBoolean>) objectInputStream.readObject();
    assertThat(deserialized.getValue().get()).isTrue();
  }

  @Test
  public void serializeEventuallyTryDo() throws IOException, ClassNotFoundException {
    final Statement<AtomicBoolean> statement = new Eventual().value(new AtomicBoolean())
        .eventuallyTryDo(new ToCloseable(), new Sink<AtomicBoolean>());
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(statement);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final Statement<AtomicBoolean> deserialized =
        (Statement<AtomicBoolean>) objectInputStream.readObject();
    assertThat(deserialized.getValue().get()).isTrue();
  }

  @Test
  public void serializeEventuallyTryEval() throws IOException, ClassNotFoundException {
    final Statement<AtomicBoolean> statement = new Eventual().value(new AtomicBoolean())
        .eventuallyTryEval(new ToCloseable(), new ToStatement<AtomicBoolean>());
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(statement);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final Statement<AtomicBoolean> deserialized =
        (Statement<AtomicBoolean>) objectInputStream.readObject();
    assertThat(deserialized.getValue().get()).isTrue();
  }

  @Test
  public void serializeFork() throws IOException, ClassNotFoundException {
    final Statement<String> statement = createStatementFork(new Eventual().value("test"));
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(statement);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final Statement<String> deserialized =
        (Statement<String>) objectInputStream.readObject();
    assertThat(deserialized.eventually(new Mapper<String, String>() {

      public String apply(final String input) {
        return input;
      }
    }).getValue()).isEqualTo("TEST");
  }

  @Test
  public void serializeForkDefault() throws IOException, ClassNotFoundException {
    final Statement<String> statement =
        new Eventual().value("test").fork(null, null, null, null, null);
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(statement);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final Statement<String> deserialized =
        (Statement<String>) objectInputStream.readObject();
    assertThat(deserialized.eventually(new Mapper<String, String>() {

      public String apply(final String input) {
        return input;
      }
    }).failure()).isExactlyInstanceOf(IllegalStateException.class);
  }

  @Test
  public void serializeForked() throws IOException, ClassNotFoundException {
    final Statement<String> statement =
        createStatementFork(new Eventual().value("test")).eventually(new ToUpper());
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(statement);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final Statement<String> deserialized =
        (Statement<String>) objectInputStream.readObject();
    assertThat(deserialized.getValue()).isEqualTo("TEST");
  }

  @Test
  public void serializePartial() throws IOException, ClassNotFoundException {
    final Statement<String> statement = new Eventual().value("test");
    statement.eventually(new ToUpper());
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(statement);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final Statement<String> deserialized =
        (Statement<String>) objectInputStream.readObject();
    assertThat(deserialized.getValue()).isEqualTo("test");
  }

  @Test
  public void serializeUnevaluated() throws IOException, ClassNotFoundException {
    final Statement<String> statement = new Eventual().evaluated(false).value("test");
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(statement);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final Statement<String> deserialized =
        (Statement<String>) objectInputStream.readObject();
    assertThat(deserialized.evaluated().getValue()).isEqualTo("test");
  }

  @Test
  public void serializeUnevaluatedFork() throws IOException, ClassNotFoundException {
    final Statement<String> statement =
        createStatementFork(new Eventual().evaluated(false).value("test"));
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(statement);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final Statement<String> deserialized =
        (Statement<String>) objectInputStream.readObject();
    assertThat(deserialized.eventually(new ToUpper()).evaluate().getValue()).isEqualTo("TEST");
  }

  @Test
  public void serializeUnevaluatedForked() throws IOException, ClassNotFoundException {
    final Statement<String> statement =
        createStatementFork(new Eventual().evaluated(false).value("test")).eventually(
            new ToUpper());
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(statement);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final Statement<String> deserialized =
        (Statement<String>) objectInputStream.readObject();
    assertThat(deserialized.evaluated().getValue()).isEqualTo("TEST");
  }

  @Test
  public void serializeWhenDone() throws IOException, ClassNotFoundException {
    final Statement<String> statement = new Eventual().value("test").whenDone(new NoOp());
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(statement);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final Statement<String> deserialized =
        (Statement<String>) objectInputStream.readObject();
    assertThat(deserialized.getValue()).isEqualTo("test");
  }

  @Test
  public void stateCancelled() {
    final Statement<String> statement =
        new Eventual().evaluateOn(withDelay(1, TimeUnit.SECONDS, backgroundExecutor()))
            .value("test");
    statement.cancel(true);
    statement.getDone();
    assertThat(statement.isCancelled()).isTrue();
    assertThat(statement.isDone()).isTrue();
    assertThat(statement.isEvaluating()).isFalse();
    assertThat(statement.isFailed()).isTrue();
    assertThat(statement.isSet()).isFalse();
  }

  @Test
  public void stateEvaluating() {
    final Statement<String> statement =
        new Eventual().evaluateOn(withDelay(1, TimeUnit.SECONDS, backgroundExecutor()))
            .value("test");
    assertThat(statement.isCancelled()).isFalse();
    assertThat(statement.isDone()).isFalse();
    assertThat(statement.isEvaluating()).isTrue();
    assertThat(statement.isFailed()).isFalse();
    assertThat(statement.isSet()).isFalse();
  }

  @Test
  public void stateExtended() {
    final Statement<String> statement = new Eventual().value("test");
    statement.eventually(new ToUpper());
    assertThat(statement.isCancelled()).isFalse();
    assertThat(statement.isDone()).isTrue();
    assertThat(statement.isEvaluating()).isFalse();
    assertThat(statement.isFailed()).isFalse();
    assertThat(statement.isSet()).isFalse();
  }

  @Test
  public void stateFailed() {
    final Statement<String> statement = new Eventual().failure(new Exception());
    assertThat(statement.isCancelled()).isFalse();
    assertThat(statement.isDone()).isTrue();
    assertThat(statement.isEvaluating()).isFalse();
    assertThat(statement.isFailed()).isTrue();
    assertThat(statement.isSet()).isFalse();
  }

  @Test
  public void stateSet() {
    final Statement<String> statement = new Eventual().value("test");
    assertThat(statement.isCancelled()).isFalse();
    assertThat(statement.isDone()).isTrue();
    assertThat(statement.isEvaluating()).isFalse();
    assertThat(statement.isFailed()).isFalse();
    assertThat(statement.isSet()).isTrue();
  }

  @Test(expected = UnsupportedOperationException.class)
  public void toException() {
    final TestEvaluation<String> evaluation = new TestEvaluation<String>();
    final Statement<String> statement = new Eventual().evaluated(false).value("test");
    statement.to(evaluation);
  }

  @Test
  public void value() {
    final Statement<String> statement = new Eventual().value("hello");
    assertThat(statement.value()).isEqualTo("hello");
  }

  @Test
  public void valueCancelled() {
    final Statement<String> statement = new Eventual().value("hello");
    assertThat(statement.isCancelled()).isFalse();
  }

  @Test
  public void valueEvaluating() {
    final Statement<String> statement = new Eventual().value("hello");
    assertThat(statement.isEvaluating()).isFalse();
  }

  @Test
  public void valueFailed() {
    final Statement<String> statement = new Eventual().value("hello");
    assertThat(statement.isFailed()).isFalse();
  }

  @Test(expected = IllegalStateException.class)
  public void valueIllegal() {
    final Statement<Object> statement = new Eventual().value(null);
    statement.eventuallyDo(new Observer<Object>() {

      public void accept(final Object input) {
      }
    });
    ConstantConditions.notNull(statement.value());
  }

  @Test(expected = IllegalStateException.class)
  public void valueInvalidStateEvaluating() {
    final Statement<Void> statement = new Eventual().evaluated(false).value(null);
    statement.value();
  }

  @Test(expected = IllegalStateException.class)
  public void valueInvalidStateFailure() {
    final Statement<String> statement = new Eventual().failure(new Exception());
    statement.value();
  }

  @Test
  public void valueSet() {
    final Statement<String> statement = new Eventual().value("hello");
    assertThat(statement.isSet()).isTrue();
  }

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void waitDoneNPE() {
    new Eventual().value(null).getDone(0, null);
  }

  @Test
  public void waitDoneOn() {
    final Statement<String> statement =
        new Eventual().evaluateOn(withDelay(100, TimeUnit.MILLISECONDS, backgroundExecutor()))
            .value("test");
    assertThat(statement.getDone(1, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  public void waitDoneTimeout() {
    final Statement<String> statement = //
        new Eventual().value("test")
            .forkOn(withDelay(1, TimeUnit.SECONDS, backgroundExecutor()))
            .eventually(new ToUpper());
    assertThat(statement.getDone(1, TimeUnit.MILLISECONDS)).isFalse();
  }

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void whenDoneNPE() {
    new Eventual().value(null).whenDone(null);
  }

  private static class Identity<V> implements Mapper<V, V> {

    public V apply(final V input) {
      return input;
    }
  }

  private static class MyObjectInputStream extends ObjectInputStream {

    // TODO: 16/02/2018 Service executor stop

    public MyObjectInputStream(final InputStream inputStream) throws IOException {
      super(inputStream);
      enableResolveObject(true);
    }

    @Override
    protected Object resolveObject(final Object o) throws IOException {
      System.out.println("Deserialized instance of: " + o.getClass().getName());
      return super.resolveObject(o);
    }
  }

  private static class NoOp implements Action {

    public void perform() {
    }
  }

  private static class PrintMessage implements Observer<Throwable>, Serializable {

    private final String mLoggerName;

    private PrintMessage(@NotNull final String loggerName) {
      mLoggerName = loggerName;
    }

    public void accept(final Throwable input) {
      Logger.newLogger(this, mLoggerName).err(input);
    }
  }

  private static class PrintString implements Observer<String>, Serializable {

    private final String mLoggerName;

    private PrintString(@NotNull final String loggerName) {
      mLoggerName = loggerName;
    }

    public void accept(final String input) {
      Logger.newLogger(this, mLoggerName).dbg(input);
    }
  }

  private static class Sink<V> implements Observer<V> {

    public void accept(final V input) {
    }
  }

  private static class ToCloseable implements Mapper<AtomicBoolean, Closeable> {

    public Closeable apply(final AtomicBoolean input) {
      return new Closeable() {

        public void close() {
          input.set(true);
        }
      };
    }
  }

  private static class ToMessage implements Mapper<Throwable, String> {

    public String apply(final Throwable input) {
      return input.getMessage();
    }
  }

  private static class ToMessageStatement implements Mapper<Throwable, Statement<String>> {

    public Statement<String> apply(final Throwable input) {
      return new Eventual().value(input.getMessage());
    }
  }

  private static class ToStatement<V> implements Mapper<V, Statement<V>> {

    public Statement<V> apply(final V input) {
      return new Eventual().value(input);
    }
  }

  private static class ToUpper implements Mapper<String, String> {

    public String apply(final String input) {
      return input.toUpperCase();
    }
  }

  private static class ToUpperStatement implements Mapper<String, Statement<String>> {

    public Statement<String> apply(final String input) {
      return new Eventual().value(input.toUpperCase());
    }
  }
}

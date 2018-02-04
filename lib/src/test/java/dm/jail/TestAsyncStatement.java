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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
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

import dm.jail.async.Action;
import dm.jail.async.AsyncResult;
import dm.jail.async.AsyncState;
import dm.jail.async.AsyncStatement;
import dm.jail.async.AsyncStatement.ForkCompleter;
import dm.jail.async.AsyncStatement.ForkUpdater;
import dm.jail.async.AsyncStatement.Forker;
import dm.jail.async.Mapper;
import dm.jail.async.Observer;
import dm.jail.async.RuntimeTimeoutException;
import dm.jail.async.SimpleState;
import dm.jail.log.LogPrinter;
import dm.jail.log.LogPrinters;
import dm.jail.log.Logger;
import dm.jail.util.ConstantConditions;

import static dm.jail.executor.ScheduledExecutors.backgroundExecutor;
import static dm.jail.executor.ScheduledExecutors.withDelay;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Created by davide-maestroni on 01/19/2018.
 */
public class TestAsyncStatement {

  @NotNull
  private static AsyncStatement<String> createStatement() {
    return new Async().value("test").then(new Mapper<String, String>() {

      public String apply(final String input) {
        return input.toUpperCase();
      }
    });
  }

  @NotNull
  private static AsyncStatement<String> createStatementAsync() {
    return new Async().value("test").on(backgroundExecutor()).then(new Mapper<String, String>() {

      public String apply(final String input) {
        return input.toUpperCase();
      }
    });
  }

  @NotNull
  private static AsyncStatement<String> createStatementFork(
      @NotNull final AsyncStatement<String> statement) {
    return fork(statement.then(new Mapper<String, String>() {

      public String apply(final String input) {
        return input.toUpperCase();
      }
    }));
  }

  @NotNull
  private static <V> AsyncStatement<V> fork(@NotNull final AsyncStatement<V> statement) {
    // TODO: 04/02/2018 ForkStack default implementation
    return statement.fork(new Forker<AsyncState<V>, AsyncStatement<V>, V, AsyncResult<V>>() {

      public AsyncState<V> done(@NotNull final AsyncStatement<V> statement,
          final AsyncState<V> stack) {
        return stack;
      }

      public AsyncState<V> failure(@NotNull final AsyncStatement<V> statement,
          final AsyncState<V> stack, @NotNull final Throwable failure) {
        return SimpleState.ofFailure(failure);
      }

      public AsyncState<V> init(@NotNull final AsyncStatement<V> statement) {
        return null;
      }

      public AsyncState<V> statement(@NotNull final AsyncStatement<V> statement,
          final AsyncState<V> stack, @NotNull final AsyncResult<V> result) {
        if (stack != null) {
          stack.to(result);
        }

        return null;
      }

      public AsyncState<V> value(@NotNull final AsyncStatement<V> statement,
          final AsyncState<V> stack, final V value) {
        return SimpleState.ofValue(value);
      }
    });
  }

  @Test
  public void cancelUnevaluated() {
    final AsyncStatement<String> statement = new Async().unevaluated().value("test");
    assertThat(statement.isCancelled()).isFalse();
    assertThat(statement.isDone()).isFalse();
    assertThat(statement.cancel(false)).isTrue();
    assertThat(statement.isCancelled()).isTrue();
    assertThat(statement.isDone()).isTrue();
  }

  @Test
  public void cancelled() {
    final AsyncStatement<Void> statement = new Async().unevaluated().value(null);
    statement.cancel(false);
    assertThat(statement.isCancelled()).isTrue();
  }

  @Test
  public void creation() {
    final AsyncStatement<String> statement =
        new Async().statement(new Observer<AsyncResult<String>>() {

          public void accept(final AsyncResult<String> result) {
            result.set("hello");
          }
        });
    assertThat(statement.value()).isEqualTo("hello");
  }

  @Test
  public void creationAsyncFailure() {
    final AsyncStatement<String> statement =
        new Async().on(backgroundExecutor()).statement(new Observer<AsyncResult<String>>() {

          public void accept(final AsyncResult<String> result) throws Exception {
            throw new Exception("test");
          }
        });
    assertThat(
        ConstantConditions.notNull(statement.getFailure()).getCause().getMessage()).isEqualTo(
        "test");
  }

  @Test
  public void creationFailure() {
    final AsyncStatement<String> statement =
        new Async().statement(new Observer<AsyncResult<String>>() {

          public void accept(final AsyncResult<String> result) throws Exception {
            throw new Exception("test");
          }
        });
    assertThat(statement.failure().getMessage()).isEqualTo("test");
  }

  @Test
  public void doneOn() {
    final AsyncStatement<String> statement =
        new Async().on(withDelay(backgroundExecutor(), 100, TimeUnit.MILLISECONDS)).value("test");
    statement.waitDone();
    assertThat(statement.isDone()).isTrue();
  }

  @Test
  public void elseCatch() {
    final AsyncStatement<String> statement =
        new Async().<String>failure(new IllegalStateException("test")).elseCatch(
            new Mapper<Throwable, String>() {

              public String apply(final Throwable error) {
                return error.getMessage();
              }
            });
    assertThat(statement.getValue()).isEqualTo("test");
  }

  @Test
  public void elseCatchFiltered() {
    final AsyncStatement<String> statement =
        new Async().<String>failure(new IllegalStateException("test")).elseCatch(
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
    new Async().value(null).elseCatch(null);
  }

  @Test
  public void elseCatchNoType() {
    assertThat(new Async().failure(new Exception()).elseCatch(new Mapper<Throwable, Object>() {

      public Object apply(final Throwable input) {
        return null;
      }
    }, (Class<?>[]) null).failure()).isExactlyInstanceOf(Exception.class);
  }

  @Test
  public void elseCatchNotFiltered() {
    final AsyncStatement<String> statement =
        new Async().<String>failure(new IllegalStateException("test")).elseCatch(
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
    new Async().value(null).elseCatch(new Mapper<Throwable, Object>() {

      public Object apply(final Throwable input) {
        return null;
      }
    }, new Class<?>[]{null});
  }

  @Test
  public void elseDo() {
    final AtomicReference<String> ref = new AtomicReference<String>();
    final AsyncStatement<String> statement =
        new Async().<String>failure(new IllegalStateException("test")).elseDo(
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
    final AsyncStatement<String> statement =
        new Async().<String>failure(new IllegalStateException("test")).elseDo(
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
    final AsyncStatement<String> statement =
        new Async().<String>failure(new IllegalStateException("test")).elseDo(
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
    new Async().value(null).elseDo(null);
  }

  @Test
  public void elseDoNoType() {
    final AtomicReference<String> ref = new AtomicReference<String>();
    assertThat(new Async().failure(new Exception()).elseDo(new Observer<Throwable>() {

      public void accept(final Throwable input) throws Exception {
        ref.set("test");
      }
    }, (Class<?>[]) null).failure()).isExactlyInstanceOf(Exception.class);
    assertThat(ref.get()).isNull();
  }

  @Test
  public void elseDoNotFiltered() {
    final AtomicReference<String> ref = new AtomicReference<String>();
    final AsyncStatement<String> statement =
        new Async().<String>failure(new IllegalStateException("test")).elseDo(
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
    new Async().value(null).elseDo(new Observer<Throwable>() {

      public void accept(final Throwable input) {

      }
    }, new Class<?>[]{null});
  }

  @Test
  public void elseFail() {
    final AsyncStatement<String> statement =
        new Async().<String>failure(new IllegalStateException("test")).elseCatch(
            new Mapper<Throwable, String>() {

              public String apply(final Throwable input) {
                throw new IllegalArgumentException();
              }
            });
    assertThat(ConstantConditions.notNull(statement.getFailure()).getCause()).isExactlyInstanceOf(
        IllegalArgumentException.class);
  }

  @Test
  public void elseIf() {
    final AsyncStatement<Integer> statement =
        new Async().<Integer>failure(new IllegalStateException("test")).elseIf(
            new Mapper<Throwable, AsyncStatement<Integer>>() {

              public AsyncStatement<Integer> apply(final Throwable input) {
                return new Async().value(input.getMessage().length());
              }
            });
    assertThat(statement.getValue()).isEqualTo(4);
  }

  @Test
  public void elseIfAsync() {
    final AsyncStatement<Integer> statement =
        new Async().<Integer>failure(new IllegalStateException("test")).elseIf(
            new Mapper<Throwable, AsyncStatement<Integer>>() {

              public AsyncStatement<Integer> apply(final Throwable input) {
                return new Async().value(input.getMessage().length())
                                  .on(withDelay(backgroundExecutor(), 100, TimeUnit.MILLISECONDS));
              }
            });
    assertThat(statement.getValue()).isEqualTo(4);
  }

  @Test
  public void elseIfAsyncFiltered() {
    final AsyncStatement<Integer> statement =
        new Async().<Integer>failure(new IllegalStateException("test")).elseIf(
            new Mapper<Throwable, AsyncStatement<Integer>>() {

              public AsyncStatement<Integer> apply(final Throwable input) {
                return new Async().value(input.getMessage().length())
                                  .on(withDelay(backgroundExecutor(), 100, TimeUnit.MILLISECONDS));
              }
            }, IllegalStateException.class);
    assertThat(statement.getValue()).isEqualTo(4);
  }

  @Test
  public void elseIfAsyncNotFiltered() {
    final AsyncStatement<Integer> statement =
        new Async().<Integer>failure(new IllegalStateException("test")).elseIf(
            new Mapper<Throwable, AsyncStatement<Integer>>() {

              public AsyncStatement<Integer> apply(final Throwable input) {
                return new Async().value(input.getMessage().length())
                                  .on(withDelay(backgroundExecutor(), 100, TimeUnit.MILLISECONDS));
              }
            }, IOException.class);
    assertThat(ConstantConditions.notNull(statement.getFailure()).getCause()).isExactlyInstanceOf(
        IllegalStateException.class);
  }

  @Test
  public void elseIfFail() {
    final AsyncStatement<Integer> statement =
        new Async().<Integer>failure(new IllegalStateException("test")).elseIf(
            new Mapper<Throwable, AsyncStatement<Integer>>() {

              public AsyncStatement<Integer> apply(final Throwable input) {
                return new Async().failure(new IllegalArgumentException());
              }
            });
    assertThat(ConstantConditions.notNull(statement.getFailure()).getCause()).isExactlyInstanceOf(
        IllegalArgumentException.class);
  }

  @Test
  public void elseIfFiltered() {
    final AsyncStatement<Integer> statement =
        new Async().<Integer>failure(new IllegalStateException("test")).elseIf(
            new Mapper<Throwable, AsyncStatement<Integer>>() {

              public AsyncStatement<Integer> apply(final Throwable input) {
                return new Async().value(input.getMessage().length());
              }
            }, IllegalStateException.class);
    assertThat(statement.getValue()).isEqualTo(4);
  }

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void elseIfNPE() {
    new Async().value(null).elseIf(null);
  }

  @Test
  public void elseIfNoType() {
    assertThat(
        new Async().failure(new Exception()).elseIf(new Mapper<Throwable, AsyncStatement<?>>() {

          public AsyncStatement<?> apply(final Throwable input) {
            return new Async().value(null);
          }
        }, (Class<?>[]) null).failure()).isExactlyInstanceOf(Exception.class);
  }

  @Test
  public void elseIfNotFiltered() {
    final AsyncStatement<Integer> statement =
        new Async().<Integer>failure(new IllegalStateException("test")).elseIf(
            new Mapper<Throwable, AsyncStatement<Integer>>() {

              public AsyncStatement<Integer> apply(final Throwable input) {
                return new Async().value(input.getMessage().length());
              }
            }, IOException.class);
    assertThat(ConstantConditions.notNull(statement.getFailure()).getCause()).isExactlyInstanceOf(
        IllegalStateException.class);
  }

  @Test
  public void elseIfStatementNPE() {
    assertThat(
        new Async().failure(new Exception()).elseIf(new Mapper<Throwable, AsyncStatement<?>>() {

          public AsyncStatement<?> apply(final Throwable input) {
            return null;
          }
        }).failure()).isExactlyInstanceOf(NullPointerException.class);
  }

  @Test(expected = NullPointerException.class)
  public void elseIfTypesNPE() {
    new Async().value(null).elseIf(new Mapper<Throwable, AsyncStatement<?>>() {

      public AsyncStatement<?> apply(final Throwable input) {
        return new Async().value(null);
      }
    }, new Class<?>[]{null});
  }

  @Test
  public void evaluate() {
    final Random random = new Random();
    final AsyncStatement<Float> statement =
        new Async().on(withDelay(backgroundExecutor(), 100, TimeUnit.MILLISECONDS))
                   .statement(new Observer<AsyncResult<Float>>() {

                     public void accept(final AsyncResult<Float> result) {
                       result.set(random.nextFloat());
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
    final AsyncStatement<Float> statement =
        fork(new Async().statement(new Observer<AsyncResult<Float>>() {

          public void accept(final AsyncResult<Float> result) {
            result.set(random.nextFloat());
          }
        })).then(new Mapper<Float, Float>() {

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
    final AsyncStatement<Integer> statement =
        new Async().unevaluated().statement(new Observer<AsyncResult<Integer>>() {

          public void accept(final AsyncResult<Integer> result) {
            result.set(random.nextInt());
          }
        }).evaluated();
    assertThat(statement.getValue()).isEqualTo(statement.evaluated().getValue());
    assertThat(statement.evaluated().getValue()).isEqualTo(statement.evaluated().getValue());
  }

  @Test
  public void evaluatedEvaluate() {
    final Random random = new Random();
    final AsyncStatement<Integer> statement =
        new Async().unevaluated().statement(new Observer<AsyncResult<Integer>>() {

          public void accept(final AsyncResult<Integer> result) {
            result.set(random.nextInt());
          }
        });
    assertThat(statement.evaluated().getValue()).isNotEqualTo(statement.evaluate().getValue());
  }

  @Test
  public void evaluating() {
    final AsyncStatement<String> statement =
        new Async().value("test").on(withDelay(backgroundExecutor(), 1, TimeUnit.SECONDS));
    assertThat(statement.isEvaluating()).isTrue();
  }

  @Test
  public void evaluatingBackground() {
    final AsyncStatement<String> statement =
        new Async().value("test").on(withDelay(backgroundExecutor(), 1, TimeUnit.SECONDS));
    assertThat(statement.isEvaluating()).isTrue();
  }

  @Test
  public void evaluatingEvaluated() {
    final AsyncStatement<String> statement = //
        new Async().unevaluated()
                   .value("test")
                   .on(withDelay(backgroundExecutor(), 1, SECONDS))
                   .then(new Mapper<String, String>() {

                     public String apply(final String input) {
                       return input.toUpperCase();
                     }
                   })
                   .evaluated();
    assertThat(statement.isEvaluating()).isTrue();
  }

  @Test(expected = IllegalStateException.class)
  public void evaluatingFailureException() {
    final AsyncStatement<String> statement = new Async().<String>failure(new Exception()).on(
        withDelay(backgroundExecutor(), 1, TimeUnit.SECONDS));
    ConstantConditions.notNull(statement.failure());
  }

  @Test
  public void evaluatingOn() {
    final AsyncStatement<String> statement =
        new Async().on(withDelay(backgroundExecutor(), 1, TimeUnit.SECONDS)).value("test");
    assertThat(statement.isEvaluating()).isTrue();
  }

  @Test
  public void evaluatingUnevaluated() {
    final AsyncStatement<String> statement = //
        new Async().unevaluated()
                   .value("test")
                   .on(withDelay(backgroundExecutor(), 1, SECONDS))
                   .then(new Mapper<String, String>() {

                     public String apply(final String input) {
                       return input.toUpperCase();
                     }
                   });
    assertThat(statement.isEvaluating()).isFalse();
  }

  @Test(expected = IllegalStateException.class)
  public void evaluatingValueException() {
    final AsyncStatement<String> statement =
        new Async().value("test").on(withDelay(backgroundExecutor(), 1, TimeUnit.SECONDS));
    statement.value();
  }

  @Test
  public void failThen() {
    final AsyncStatement<String> statement =
        new Async().<String>failure(new NullPointerException("test")).then(
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
    final AsyncStatement<String> statement = new Async().failure(new IllegalArgumentException());
    assertThat(statement.failure()).isExactlyInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void failureCancelled() {
    final AsyncStatement<String> statement = new Async().failure(new IllegalArgumentException());
    assertThat(statement.isCancelled()).isFalse();
  }

  @Test
  public void failureEvaluating() {
    final AsyncStatement<String> statement = new Async().failure(new IllegalArgumentException());
    assertThat(statement.isEvaluating()).isFalse();
  }

  @Test
  public void failureFailed() {
    final AsyncStatement<String> statement = new Async().failure(new IllegalArgumentException());
    assertThat(statement.isFailed()).isTrue();
  }

  @Test(expected = IllegalStateException.class)
  public void failureIllegal() {
    final AsyncStatement<Object> statement = new Async().failure(new IllegalArgumentException());
    statement.thenDo(new Observer<Object>() {

      public void accept(final Object input) {
      }
    });
    ConstantConditions.notNull(statement.failure());
  }

  @Test(expected = IllegalStateException.class)
  public void failureInvalidStateEvaluating() {
    final AsyncStatement<Void> statement = new Async().unevaluated().failure(new Exception());
    assertThat(statement.failure());
  }

  @Test(expected = IllegalStateException.class)
  public void failureInvalidStateValue() {
    final AsyncStatement<String> statement = new Async().value(null);
    assertThat(statement.failure());
  }

  @Test
  public void failureSet() {
    final AsyncStatement<String> statement = new Async().failure(new IllegalArgumentException());
    assertThat(statement.isSet()).isFalse();
  }

  @Test
  public void fork() {
    final AsyncStatement<String> statement = createStatementFork(new Async().value("test"));
    assertThat(statement.isFinal()).isTrue();
    assertThat(statement.isDone()).isFalse();
    assertThat(statement.then(new Mapper<String, String>() {

      public String apply(final String input) {
        return input;
      }
    }).value()).isEqualTo("TEST");
  }

  @Test
  public void forkCancel() {
    final AsyncStatement<String> statement = createStatementFork(
        new Async().on(withDelay(backgroundExecutor(), 1, TimeUnit.SECONDS)).value("test"));
    assertThat(statement.isFinal()).isTrue();
    assertThat(statement.isDone()).isFalse();
    assertThat(statement.cancel(true)).isTrue();
    assertThat(statement.isCancelled()).isFalse();
    assertThat(statement.isFailed()).isFalse();
    assertThat(statement.then(new Mapper<String, String>() {

      public String apply(final String input) {
        return input;
      }
    }).isFailed()).isTrue();
  }

  @Test
  public void forkCancelFailure() {
    final AsyncStatement<String> statement = createStatementFork(new Async().value("test"));
    assertThat(statement.isFinal()).isTrue();
    assertThat(statement.isDone()).isFalse();
    assertThat(statement.cancel(false)).isFalse();
    assertThat(statement.isCancelled()).isFalse();
    assertThat(statement.then(new Mapper<String, String>() {

      public String apply(final String input) {
        return input;
      }
    }).value()).isEqualTo("TEST");
  }

  @Test
  public void forkCancelForked() {
    final AsyncStatement<String> statement = createStatementFork(
        new Async().on(withDelay(backgroundExecutor(), 1, TimeUnit.SECONDS)).value("test"));
    final AsyncStatement<String> forked = statement.then(new Mapper<String, String>() {

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
    final AsyncStatement<String> statement = //
        new Async().value("test")
                   .fork(null, null, null, null,
                       new ForkUpdater<Object, AsyncStatement<String>, AsyncResult<String>>() {

                         public Object update(@NotNull final AsyncStatement<String> statement,
                             final Object stack, final AsyncResult<String> result) {
                           result.set("TEST");
                           return null;
                         }
                       });
    assertThat(statement.isFinal()).isTrue();
    assertThat(statement.isDone()).isFalse();
    assertThat(statement.then(new Mapper<String, String>() {

      public String apply(final String input) {
        return input;
      }
    }).value()).isEqualTo("TEST");
  }

  @Test
  public void forkDefaultStatement() {
    final AsyncStatement<String> statement = //
        new Async().value("test").fork(null, null, null, null, null);
    assertThat(statement.isFinal()).isTrue();
    assertThat(statement.isDone()).isFalse();
    assertThat(statement.then(new Mapper<String, String>() {

      public String apply(final String input) {
        return input;
      }
    }).failure()).isExactlyInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  public void forkFailure() {
    final AsyncStatement<String> statement =
        createStatementFork(new Async().<String>failure(new IllegalArgumentException()));
    assertThat(statement.isFinal()).isTrue();
    assertThat(statement.isDone()).isFalse();
    assertThat(statement.isCancelled()).isFalse();
    assertThat(statement.isFailed()).isFalse();
    assertThat(statement.then(new Mapper<String, String>() {

      public String apply(final String input) {
        return input;
      }
    }).isFailed()).isTrue();
  }

  @Test
  public void forkFailureDoneFail() {
    final AsyncStatement<String> statement = //
        new Async().<String>failure(new Exception()).fork(null, null, null,
            new ForkCompleter<String, AsyncStatement<String>>() {

              public String complete(@NotNull final AsyncStatement<String> statement,
                  final String stack) {
                throw new RuntimeException("test");
              }
            }, null);
    assertThat(statement.isFinal()).isTrue();
    assertThat(statement.isDone()).isFalse();
    assertThat(statement.then(new Mapper<String, String>() {

      public String apply(final String input) {
        return input;
      }
    }).failure().getMessage()).isEqualTo("test");
  }

  @Test(expected = IllegalStateException.class)
  public void forkFailureException() {
    final AsyncStatement<String> statement =
        createStatementFork(new Async().<String>failure(new Exception()));
    ConstantConditions.notNull(statement.failure());
  }

  @Test
  public void forkFailureFail() {
    final AsyncStatement<String> statement = //
        new Async().<String>failure(new Exception()).fork(null, null,
            new ForkUpdater<String, AsyncStatement<String>, Throwable>() {

              public String update(@NotNull final AsyncStatement<String> statement,
                  final String stack, final Throwable input) {
                throw new RuntimeException("test");
              }
            }, null, null);
    assertThat(statement.isFinal()).isTrue();
    assertThat(statement.isDone()).isFalse();
    assertThat(statement.then(new Mapper<String, String>() {

      public String apply(final String input) {
        return input;
      }
    }).failure().getMessage()).isEqualTo("test");
  }

  @Test
  public void forkFunctions() {
    final AsyncStatement<String> statement = //
        new Async().value("test").fork(new Mapper<AsyncStatement<String>, AsyncState<String>>() {

          public AsyncState<String> apply(final AsyncStatement<String> input) {
            return null;
          }
        }, new ForkUpdater<AsyncState<String>, AsyncStatement<String>, String>() {

          public AsyncState<String> update(@NotNull final AsyncStatement<String> statement,
              final AsyncState<String> stack, final String value) {
            return SimpleState.ofValue(value);
          }
        }, new ForkUpdater<AsyncState<String>, AsyncStatement<String>, Throwable>() {

          public AsyncState<String> update(@NotNull final AsyncStatement<String> statement,
              final AsyncState<String> stack, final Throwable failure) {
            return SimpleState.ofFailure(failure);
          }
        }, new ForkCompleter<AsyncState<String>, AsyncStatement<String>>() {

          public AsyncState<String> complete(@NotNull final AsyncStatement<String> statement,
              final AsyncState<String> stack) {
            return stack;
          }
        }, new ForkUpdater<AsyncState<String>, AsyncStatement<String>, AsyncResult<String>>() {

          public AsyncState<String> update(@NotNull final AsyncStatement<String> statement,
              final AsyncState<String> stack, final AsyncResult<String> result) {
            if (stack != null) {
              stack.to(result);
            }

            return null;
          }
        });
    assertThat(statement.isFinal()).isTrue();
    assertThat(statement.isDone()).isFalse();
    assertThat(statement.then(new Mapper<String, String>() {

      public String apply(final String input) {
        return input.toUpperCase();
      }
    }).value()).isEqualTo("TEST");
  }

  @Test(expected = UnsupportedOperationException.class)
  public void forkGetFailureException() {
    final AsyncStatement<String> statement =
        createStatementFork(new Async().<String>failure(new Exception()));
    ConstantConditions.notNull(statement.getFailure());
  }

  @Test(expected = UnsupportedOperationException.class)
  public void forkGetValueException() {
    final AsyncStatement<String> statement = createStatementFork(new Async().value("test"));
    statement.getValue();
  }

  @Test
  public void forkInit() {
    final AsyncStatement<String> statement = //
        new Async().value("test").fork(new Mapper<AsyncStatement<String>, String>() {

                                         public String apply(final AsyncStatement<String> input) {
                                           return "TEST";
                                         }
                                       }, null, null, null,
            new ForkUpdater<String, AsyncStatement<String>, AsyncResult<String>>() {

              public String update(@NotNull final AsyncStatement<String> statement,
                  final String stack, final AsyncResult<String> result) {
                result.set(stack);
                return stack;
              }
            });
    assertThat(statement.isFinal()).isTrue();
    assertThat(statement.isDone()).isFalse();
    assertThat(statement.then(new Mapper<String, String>() {

      public String apply(final String input) {
        return input;
      }
    }).value()).isEqualTo("TEST");
  }

  @Test
  public void forkInitFail() {
    final AsyncStatement<String> statement = //
        new Async().value("test").fork(new Mapper<AsyncStatement<String>, String>() {

          public String apply(final AsyncStatement<String> input) {
            throw new RuntimeException("test");
          }
        }, null, null, null, null);
    assertThat(statement.isFinal()).isTrue();
    assertThat(statement.isDone()).isFalse();
    assertThat(statement.then(new Mapper<String, String>() {

      public String apply(final String input) {
        return input;
      }
    }).failure().getMessage()).isEqualTo("test");
  }

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void forkNPE() {
    new Async().value(null).fork(null);
  }

  @Test
  public void forkStatementFail() {
    final AsyncStatement<String> statement = //
        new Async().value("test")
                   .fork(null, null, null, null,
                       new ForkUpdater<String, AsyncStatement<String>, AsyncResult<String>>() {

                         public String update(@NotNull final AsyncStatement<String> statement,
                             final String stack, final AsyncResult<String> input) {
                           throw new RuntimeException("test");
                         }
                       });
    assertThat(statement.isFinal()).isTrue();
    assertThat(statement.isDone()).isFalse();
    assertThat(statement.then(new Mapper<String, String>() {

      public String apply(final String input) {
        return input;
      }
    }).failure().getMessage()).isEqualTo("test");
  }

  @Test
  public void forkValueDoneFail() {
    final AsyncStatement<String> statement = //
        new Async().value("test")
                   .fork(null, null, null, new ForkCompleter<String, AsyncStatement<String>>() {

                     public String complete(@NotNull final AsyncStatement<String> statement,
                         final String stack) {
                       throw new RuntimeException("test");
                     }
                   }, null);
    assertThat(statement.isFinal()).isTrue();
    assertThat(statement.isDone()).isFalse();
    assertThat(statement.then(new Mapper<String, String>() {

      public String apply(final String input) {
        return input;
      }
    }).failure().getMessage()).isEqualTo("test");
  }

  @Test(expected = IllegalStateException.class)
  public void forkValueException() {
    final AsyncStatement<String> statement = createStatementFork(new Async().value("test"));
    statement.value();
  }

  @Test
  public void forkValueFail() {
    final AsyncStatement<String> statement = //
        new Async().value("test")
                   .fork(null, new ForkUpdater<String, AsyncStatement<String>, String>() {

                     public String update(@NotNull final AsyncStatement<String> statement,
                         final String stack, final String input) {
                       throw new RuntimeException("test");
                     }
                   }, null, null, null);
    assertThat(statement.isFinal()).isTrue();
    assertThat(statement.isDone()).isFalse();
    assertThat(statement.then(new Mapper<String, String>() {

      public String apply(final String input) {
        return input;
      }
    }).failure().getMessage()).isEqualTo("test");
  }

  @Test
  public void get() throws ExecutionException, InterruptedException {
    final AsyncStatement<String> statement =
        new Async().on(withDelay(backgroundExecutor(), 100, TimeUnit.MILLISECONDS)).value("test");
    assertThat(statement.get()).isEqualTo("test");
  }

  @Test(expected = CancellationException.class)
  public void getCancelled() throws ExecutionException, InterruptedException {
    final AsyncStatement<String> statement =
        new Async().on(withDelay(backgroundExecutor(), 100, TimeUnit.MILLISECONDS)).value("test");
    assertThat(statement.cancel(true)).isTrue();
    statement.get();
  }

  @Test(expected = CancellationException.class)
  public void getCancelledWithTimeout() throws ExecutionException, InterruptedException,
      TimeoutException {
    final AsyncStatement<String> statement =
        new Async().on(withDelay(backgroundExecutor(), 100, TimeUnit.MILLISECONDS)).value("test");
    assertThat(statement.cancel(true)).isTrue();
    statement.get(10, TimeUnit.MILLISECONDS);
  }

  @Test(expected = ExecutionException.class)
  public void getFailure() throws ExecutionException, InterruptedException {
    final AsyncStatement<String> statement =
        new Async().on(withDelay(backgroundExecutor(), 100, TimeUnit.MILLISECONDS))
                   .failure(new IllegalArgumentException());
    statement.get();
  }

  @Test(expected = IllegalStateException.class)
  public void getFailureIllegal() {
    final AsyncStatement<Object> statement = new Async().failure(new IllegalArgumentException());
    statement.thenDo(new Observer<Object>() {

      public void accept(final Object input) {
      }
    });
    ConstantConditions.notNull(statement.getFailure());
  }

  @Test(expected = NullPointerException.class)
  @SuppressWarnings({"ConstantConditions", "ThrowableNotThrown"})
  public void getFailureNPE() {
    new Async().value(null).getFailure(0, null);
  }

  @Test(expected = RuntimeTimeoutException.class)
  public void getFailureTimeout() throws ExecutionException, InterruptedException,
      TimeoutException {
    final AsyncStatement<String> statement =
        new Async().on(withDelay(backgroundExecutor(), 1, TimeUnit.SECONDS)).value("test");
    assertThat(statement.getFailure(10, TimeUnit.MILLISECONDS));
  }

  @Test(expected = ExecutionException.class)
  public void getFailureWithTimeout() throws ExecutionException, InterruptedException,
      TimeoutException {
    final AsyncStatement<String> statement =
        new Async().on(withDelay(backgroundExecutor(), 100, TimeUnit.MILLISECONDS))
                   .failure(new IllegalArgumentException());
    statement.get(1, TimeUnit.SECONDS);
  }

  @Test(expected = TimeoutException.class)
  public void getTimeout() throws ExecutionException, InterruptedException, TimeoutException {
    final AsyncStatement<String> statement =
        new Async().on(withDelay(backgroundExecutor(), 1, TimeUnit.SECONDS)).value("test");
    statement.get(10, TimeUnit.MILLISECONDS);
  }

  @Test(expected = IllegalStateException.class)
  public void getValueIllegal() {
    final AsyncStatement<Object> statement = new Async().value(null);
    statement.thenDo(new Observer<Object>() {

      public void accept(final Object input) {
      }
    });
    statement.getValue();
  }

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void getValueNPE() {
    new Async().value(null).getValue(0, null);
  }

  @Test(expected = RuntimeTimeoutException.class)
  public void getValueTimeout() {
    final AsyncStatement<Object> statement =
        new Async().value(null).on(withDelay(backgroundExecutor(), 1, TimeUnit.SECONDS));
    statement.getValue(0, TimeUnit.MILLISECONDS);
  }

  @Test
  public void getWithTimeout() throws ExecutionException, InterruptedException, TimeoutException {
    final AsyncStatement<String> statement =
        new Async().on(withDelay(backgroundExecutor(), 100, TimeUnit.MILLISECONDS)).value("test");
    assertThat(statement.get(1, TimeUnit.SECONDS)).isEqualTo("test");
  }

  @Test
  public void isFinal() {
    final AsyncStatement<String> statement = new Async().failure(new IllegalArgumentException());
    assertThat(statement.isFinal()).isTrue();
  }

  @Test
  public void isNotFinal() {
    final AsyncStatement<String> statement = new Async().failure(new IllegalArgumentException());
    final AsyncStatement<String> newStatement = statement.thenDo(new Observer<String>() {

      public void accept(final String input) {

      }
    });
    assertThat(statement.isFinal()).isFalse();
    assertThat(newStatement.isFinal()).isTrue();
  }

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void onNPE() {
    new Async().value(null).on(null);
  }

  @Test
  public void serialize() throws IOException, ClassNotFoundException {
    final AsyncStatement<String> statement = createStatement();
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(statement);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final AsyncStatement<String> deserialized =
        (AsyncStatement<String>) objectInputStream.readObject();
    assertThat(deserialized.getValue()).isEqualTo("TEST");
  }

  @Test
  public void serializeAsync() throws IOException, ClassNotFoundException {
    final AsyncStatement<String> statement = createStatementAsync();
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(statement);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final AsyncStatement<String> deserialized =
        (AsyncStatement<String>) objectInputStream.readObject();
    assertThat(deserialized.getValue()).isEqualTo("TEST");
  }

  @Test
  public void serializeElse() throws IOException, ClassNotFoundException {
    final AsyncStatement<String> statement =
        new Async().<String>failure(new IllegalArgumentException("test")).elseCatch(
            new ToMessage());
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(statement);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final AsyncStatement<String> deserialized =
        (AsyncStatement<String>) objectInputStream.readObject();
    assertThat(deserialized.getValue()).isEqualTo("test");
  }

  @Test
  public void serializeElseDo() throws IOException, ClassNotFoundException {
    final AsyncStatement<String> statement =
        new Async().<String>failure(new IllegalArgumentException("test")).elseDo(
            new PrintMessage(LogPrinters.systemPrinter()));
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(statement);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final AsyncStatement<String> deserialized =
        (AsyncStatement<String>) objectInputStream.readObject();
    assertThat(
        ConstantConditions.notNull(deserialized.getFailure()).getCause().getMessage()).isEqualTo(
        "test");
  }

  @Test
  public void serializeElseIf() throws IOException, ClassNotFoundException {
    final AsyncStatement<String> statement =
        new Async().<String>failure(new IllegalArgumentException("test")).elseIf(
            new ToMessageStatement());
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(statement);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final AsyncStatement<String> deserialized =
        (AsyncStatement<String>) objectInputStream.readObject();
    assertThat(deserialized.getValue()).isEqualTo("test");
  }

  @Test(expected = IOException.class)
  public void serializeError() throws IOException, ClassNotFoundException {
    final AsyncStatement<String> promise =
        new Async().statement(new Observer<AsyncResult<String>>() {

          public void accept(final AsyncResult<String> result) {
            result.set("test");
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
  public void serializeErrorChain() throws IOException, ClassNotFoundException {
    final AsyncStatement<String> promise =
        createStatement().elseCatch(new Mapper<Throwable, String>() {

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
    final AsyncStatement<String> statement = createStatement().evaluate();
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(statement);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final AsyncStatement<String> deserialized =
        (AsyncStatement<String>) objectInputStream.readObject();
    assertThat(deserialized.getValue()).isEqualTo("TEST");
  }

  @Test
  public void serializeFork() throws IOException, ClassNotFoundException {
    final AsyncStatement<String> statement = createStatementFork(new Async().value("test"));
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(statement);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final AsyncStatement<String> deserialized =
        (AsyncStatement<String>) objectInputStream.readObject();
    assertThat(deserialized.then(new Mapper<String, String>() {

      public String apply(final String input) {
        return input;
      }
    }).getValue()).isEqualTo("TEST");
  }

  @Test
  public void serializeForkDefault() throws IOException, ClassNotFoundException {
    final AsyncStatement<String> statement =
        new Async().value("test").fork(null, null, null, null, null);
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(statement);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final AsyncStatement<String> deserialized =
        (AsyncStatement<String>) objectInputStream.readObject();
    assertThat(deserialized.then(new Mapper<String, String>() {

      public String apply(final String input) {
        return input;
      }
    }).failure()).isExactlyInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  public void serializeForked() throws IOException, ClassNotFoundException {
    final AsyncStatement<String> statement =
        createStatementFork(new Async().value("test")).then(new ToUpper());
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(statement);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final AsyncStatement<String> deserialized =
        (AsyncStatement<String>) objectInputStream.readObject();
    assertThat(deserialized.getValue()).isEqualTo("TEST");
  }

  @Test
  public void serializeThen() throws IOException, ClassNotFoundException {
    final AsyncStatement<String> statement = new Async().value("test").then(new ToUpper());
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(statement);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final AsyncStatement<String> deserialized =
        (AsyncStatement<String>) objectInputStream.readObject();
    assertThat(deserialized.getValue()).isEqualTo("TEST");
  }

  @Test
  public void serializeThenDo() throws IOException, ClassNotFoundException {
    final AsyncStatement<String> statement =
        new Async().value("test").thenDo(new PrintString(LogPrinters.systemPrinter()));
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(statement);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final AsyncStatement<String> deserialized =
        (AsyncStatement<String>) objectInputStream.readObject();
    assertThat(deserialized.getValue()).isEqualTo("test");
  }

  @Test
  public void serializeThenIf() throws IOException, ClassNotFoundException {
    final AsyncStatement<String> statement =
        new Async().value("test").thenIf(new ToUpperStatement());
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(statement);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final AsyncStatement<String> deserialized =
        (AsyncStatement<String>) objectInputStream.readObject();
    assertThat(deserialized.getValue()).isEqualTo("TEST");
  }

  @Test
  public void serializeThenTry() throws IOException, ClassNotFoundException {
    final AsyncStatement<AtomicBoolean> statement = new Async().value(new AtomicBoolean())
                                                               .thenTry(new ToCloseable(),
                                                                   new Identity<AtomicBoolean>());
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(statement);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final AsyncStatement<AtomicBoolean> deserialized =
        (AsyncStatement<AtomicBoolean>) objectInputStream.readObject();
    assertThat(deserialized.getValue().get()).isTrue();
  }

  @Test
  public void serializeThenTryDo() throws IOException, ClassNotFoundException {
    final AsyncStatement<AtomicBoolean> statement = new Async().value(new AtomicBoolean())
                                                               .thenTryDo(new ToCloseable(),
                                                                   new Sink<AtomicBoolean>());
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(statement);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final AsyncStatement<AtomicBoolean> deserialized =
        (AsyncStatement<AtomicBoolean>) objectInputStream.readObject();
    assertThat(deserialized.getValue().get()).isTrue();
  }

  @Test
  public void serializeThenTryIf() throws IOException, ClassNotFoundException {
    final AsyncStatement<AtomicBoolean> statement = new Async().value(new AtomicBoolean())
                                                               .thenTryIf(new ToCloseable(),
                                                                   new ToStatement<AtomicBoolean>
                                                                       ());
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(statement);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final AsyncStatement<AtomicBoolean> deserialized =
        (AsyncStatement<AtomicBoolean>) objectInputStream.readObject();
    assertThat(deserialized.getValue().get()).isTrue();
  }

  @Test
  public void serializeUnevaluated() throws IOException, ClassNotFoundException {
    final AsyncStatement<String> statement = new Async().unevaluated().value("test");
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(statement);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final AsyncStatement<String> deserialized =
        (AsyncStatement<String>) objectInputStream.readObject();
    assertThat(deserialized.evaluated().getValue()).isEqualTo("test");
  }

  @Test
  public void serializeUnevaluatedFork() throws IOException, ClassNotFoundException {
    final AsyncStatement<String> statement =
        createStatementFork(new Async().unevaluated().value("test"));
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(statement);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final AsyncStatement<String> deserialized =
        (AsyncStatement<String>) objectInputStream.readObject();
    assertThat(deserialized.then(new ToUpper()).evaluate().getValue()).isEqualTo("TEST");
  }

  @Test
  public void serializeUnevaluatedForked() throws IOException, ClassNotFoundException {
    final AsyncStatement<String> statement =
        createStatementFork(new Async().unevaluated().value("test")).then(new ToUpper());
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(statement);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final AsyncStatement<String> deserialized =
        (AsyncStatement<String>) objectInputStream.readObject();
    assertThat(deserialized.evaluated().getValue()).isEqualTo("TEST");
  }

  @Test
  public void serializeWhenDone() throws IOException, ClassNotFoundException {
    final AsyncStatement<String> statement = new Async().value("test").whenDone(new NoOp());
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(statement);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final AsyncStatement<String> deserialized =
        (AsyncStatement<String>) objectInputStream.readObject();
    assertThat(deserialized.getValue()).isEqualTo("test");
  }

  @Test
  public void stateCancelled() {
    final AsyncStatement<String> statement =
        new Async().on(withDelay(backgroundExecutor(), 1, TimeUnit.SECONDS)).value("test");
    statement.cancel(true);
    statement.waitDone();
    assertThat(statement.isCancelled()).isTrue();
    assertThat(statement.isDone()).isTrue();
    assertThat(statement.isEvaluating()).isFalse();
    assertThat(statement.isFailed()).isTrue();
    assertThat(statement.isSet()).isFalse();
  }

  @Test
  public void stateEvaluating() {
    final AsyncStatement<String> statement =
        new Async().on(withDelay(backgroundExecutor(), 1, TimeUnit.SECONDS)).value("test");
    assertThat(statement.isCancelled()).isFalse();
    assertThat(statement.isDone()).isFalse();
    assertThat(statement.isEvaluating()).isTrue();
    assertThat(statement.isFailed()).isFalse();
    assertThat(statement.isSet()).isFalse();
  }

  @Test
  public void stateExtended() {
    final AsyncStatement<String> statement = new Async().value("test");
    statement.then(new ToUpper());
    assertThat(statement.isCancelled()).isFalse();
    assertThat(statement.isDone()).isTrue();
    assertThat(statement.isEvaluating()).isFalse();
    assertThat(statement.isFailed()).isFalse();
    assertThat(statement.isSet()).isFalse();
  }

  @Test
  public void stateFailed() {
    final AsyncStatement<String> statement = new Async().failure(new Exception());
    assertThat(statement.isCancelled()).isFalse();
    assertThat(statement.isDone()).isTrue();
    assertThat(statement.isEvaluating()).isFalse();
    assertThat(statement.isFailed()).isTrue();
    assertThat(statement.isSet()).isFalse();
  }

  @Test
  public void stateSet() {
    final AsyncStatement<String> statement = new Async().value("test");
    assertThat(statement.isCancelled()).isFalse();
    assertThat(statement.isDone()).isTrue();
    assertThat(statement.isEvaluating()).isFalse();
    assertThat(statement.isFailed()).isFalse();
    assertThat(statement.isSet()).isTrue();
  }

  @Test
  public void then() {
    final AsyncStatement<String> statement =
        new Async().value("test").then(new Mapper<String, String>() {

          public String apply(final String input) {
            return input.toUpperCase();
          }
        });
    assertThat(statement.getValue()).isEqualTo("TEST");
  }

  @Test
  public void thenDo() {
    final AtomicReference<String> ref = new AtomicReference<String>();
    final AsyncStatement<String> statement =
        new Async().value("test").thenDo(new Observer<String>() {

          public void accept(final String input) {
            ref.set(input);
          }
        });
    assertThat(statement.getValue()).isEqualTo("test");
    assertThat(ref.get()).isEqualTo("test");
  }

  @Test
  public void thenDoFail() {
    final AsyncStatement<String> statement =
        new Async().value("test").thenDo(new Observer<String>() {

          public void accept(final String input) {
            throw new IllegalArgumentException();
          }
        });
    assertThat(ConstantConditions.notNull(statement.getFailure()).getCause()).isExactlyInstanceOf(
        IllegalArgumentException.class);
  }

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void thenDoNPE() {
    new Async().value(null).thenDo(null);
  }

  @Test
  public void thenFail() {
    final AsyncStatement<String> statement =
        new Async().value("test").then(new Mapper<String, String>() {

          public String apply(final String input) {
            throw new IllegalArgumentException();
          }
        });
    assertThat(ConstantConditions.notNull(statement.getFailure()).getCause()).isExactlyInstanceOf(
        IllegalArgumentException.class);
  }

  @Test
  public void thenIf() {
    final AsyncStatement<Integer> statement =
        new Async().value("test").thenIf(new Mapper<String, AsyncStatement<Integer>>() {

          public AsyncStatement<Integer> apply(final String input) {
            return new Async().value(input.length());
          }
        });
    assertThat(statement.getValue()).isEqualTo(4);
  }

  @Test
  public void thenIfAsync() {
    final AsyncStatement<Integer> statement =
        new Async().value("test").thenIf(new Mapper<String, AsyncStatement<Integer>>() {

          public AsyncStatement<Integer> apply(final String input) {
            return new Async().value(input.length())
                              .on(withDelay(backgroundExecutor(), 100, TimeUnit.MILLISECONDS));
          }
        });
    assertThat(statement.getValue()).isEqualTo(4);
  }

  @Test
  public void thenIfFail() {
    final AsyncStatement<Integer> statement =
        new Async().value("test").thenIf(new Mapper<String, AsyncStatement<Integer>>() {

          public AsyncStatement<Integer> apply(final String input) {
            return new Async().failure(new IllegalArgumentException());
          }
        });
    assertThat(ConstantConditions.notNull(statement.getFailure()).getCause()).isExactlyInstanceOf(
        IllegalArgumentException.class);
  }

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void thenIfNPE() {
    new Async().value(null).thenIf(null);
  }

  @Test
  public void thenIfStatementNPE() {
    assertThat(new Async().value(null).thenIf(new Mapper<Object, AsyncStatement<Object>>() {

      public AsyncStatement<Object> apply(final Object input) {
        return null;
      }
    }).failure()).isExactlyInstanceOf(NullPointerException.class);
  }

  @Test(expected = IllegalStateException.class)
  public void thenIllegal() {
    final AsyncStatement<String> statement = new Async().value("test");
    statement.then(new Mapper<String, String>() {

      public String apply(final String input) {
        return input.toUpperCase();
      }
    });
    statement.then(new ToUpper());
  }

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void thenNPE() {
    new Async().value(null).then(null);
  }

  @Test
  public void thenTry() {
    final AtomicCloseable closeable = new AtomicCloseable();
    final AsyncStatement<String> statement =
        new Async().value("test").thenTry(new Mapper<String, Closeable>() {

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
  public void thenTryCloseableNPE() {
    new Async().value(null).thenTry(null, new Mapper<Object, Object>() {

      public Object apply(final Object input) {
        return null;
      }
    });
  }

  @Test
  public void thenTryDo() {
    final AtomicCloseable closeable = new AtomicCloseable();
    final AtomicReference<String> ref = new AtomicReference<String>();
    final AsyncStatement<String> statement =
        new Async().value("test").thenTryDo(new Mapper<String, Closeable>() {

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
  public void thenTryDoCloseableNPE() {
    new Async().value(null).thenTryDo(null, new Observer<Object>() {

      public void accept(final Object input) {

      }
    });
  }

  @Test
  public void thenTryDoException() {
    final AtomicReference<String> ref = new AtomicReference<String>();
    final AsyncStatement<String> statement =
        new Async().value("test").thenTryDo(new Mapper<String, Closeable>() {

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
  public void thenTryDoFail() {
    final AtomicCloseable closeable = new AtomicCloseable();
    final AsyncStatement<String> statement =
        new Async().value("test").thenTryDo(new Mapper<String, Closeable>() {

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
  public void thenTryDoIOException() {
    final AtomicReference<String> ref = new AtomicReference<String>();
    final AsyncStatement<String> statement =
        new Async().value("test").thenTryDo(new Mapper<String, Closeable>() {

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
  public void thenTryDoNPE() {
    new Async().value(null).thenTryDo(new Mapper<Object, Closeable>() {

      public Closeable apply(final Object input) {
        return null;
      }
    }, null);
  }

  @Test
  public void thenTryDoNull() {
    final AtomicReference<String> ref = new AtomicReference<String>();
    final AsyncStatement<String> statement =
        new Async().value("test").thenTryDo(new Mapper<String, Closeable>() {

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
  public void thenTryException() {
    final AsyncStatement<String> statement =
        new Async().value("test").thenTry(new Mapper<String, Closeable>() {

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
  public void thenTryFail() {
    final AtomicCloseable closeable = new AtomicCloseable();
    final AsyncStatement<String> statement =
        new Async().value("test").thenTry(new Mapper<String, Closeable>() {

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
  public void thenTryIOException() {
    final AsyncStatement<String> statement =
        new Async().value("test").thenTry(new Mapper<String, Closeable>() {

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

  @Test
  public void thenTryIf() {
    final AtomicCloseable closeable = new AtomicCloseable();
    final AsyncStatement<Integer> statement =
        new Async().value("test").thenTryIf(new Mapper<String, Closeable>() {

          public Closeable apply(final String input) {
            return closeable;
          }
        }, new Mapper<String, AsyncStatement<Integer>>() {

          public AsyncStatement<Integer> apply(final String input) {
            return new Async().value(input.length());
          }
        });
    assertThat(statement.getValue()).isEqualTo(4);
    assertThat(closeable.isCalled()).isTrue();
  }

  @Test
  public void thenTryIfAsync() {
    final AtomicCloseable closeable = new AtomicCloseable();
    final AsyncStatement<Integer> statement =
        new Async().value("test").thenTryIf(new Mapper<String, Closeable>() {

          public Closeable apply(final String input) {
            return closeable;
          }
        }, new Mapper<String, AsyncStatement<Integer>>() {

          public AsyncStatement<Integer> apply(final String input) {
            return new Async().value(input.length())
                              .on(withDelay(backgroundExecutor(), 100, TimeUnit.MILLISECONDS));
          }
        });
    assertThat(statement.getValue()).isEqualTo(4);
    assertThat(closeable.isCalled()).isTrue();
  }

  @Test
  public void thenTryIfAsyncException() {
    final AsyncStatement<Integer> statement =
        new Async().value("test").thenTryIf(new Mapper<String, Closeable>() {

          public Closeable apply(final String input) {
            throw new RuntimeException();
          }
        }, new Mapper<String, AsyncStatement<Integer>>() {

          public AsyncStatement<Integer> apply(final String input) {
            return new Async().value(input.length())
                              .on(withDelay(backgroundExecutor(), 100, TimeUnit.MILLISECONDS));
          }
        });
    assertThat(statement.getFailure()).isNotNull();
  }

  @Test
  public void thenTryIfAsyncIOException() {
    final AsyncStatement<Integer> statement =
        new Async().value("test").thenTryIf(new Mapper<String, Closeable>() {

          public Closeable apply(final String input) {
            return new Closeable() {

              public void close() throws IOException {
                throw new IOException();
              }
            };
          }
        }, new Mapper<String, AsyncStatement<Integer>>() {

          public AsyncStatement<Integer> apply(final String input) {
            return new Async().value(input.length())
                              .on(withDelay(backgroundExecutor(), 100, TimeUnit.MILLISECONDS));
          }
        });
    assertThat(statement.getFailure()).isNotNull();
  }

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void thenTryIfCloseableNPE() {
    new Async().value(null).thenTryIf(null, new Mapper<Object, AsyncStatement<Object>>() {

      public AsyncStatement<Object> apply(final Object input) {
        return new Async().value(null);
      }
    });
  }

  @Test
  public void thenTryIfException() {
    final AsyncStatement<Integer> statement =
        new Async().value("test").thenTryIf(new Mapper<String, Closeable>() {

          public Closeable apply(final String input) {
            throw new RuntimeException();
          }
        }, new Mapper<String, AsyncStatement<Integer>>() {

          public AsyncStatement<Integer> apply(final String input) {
            return new Async().value(input.length());
          }
        });
    assertThat(statement.isFailed()).isTrue();
  }

  @Test
  public void thenTryIfFail() {
    final AtomicCloseable closeable = new AtomicCloseable();
    final AsyncStatement<Integer> statement =
        new Async().value("test").thenTryIf(new Mapper<String, Closeable>() {

          public Closeable apply(final String input) {
            return closeable;
          }
        }, new Mapper<String, AsyncStatement<Integer>>() {

          public AsyncStatement<Integer> apply(final String input) {
            return new Async().failure(new IllegalArgumentException());
          }
        });
    assertThat(ConstantConditions.notNull(statement.getFailure()).getCause()).isExactlyInstanceOf(
        IllegalArgumentException.class);
    assertThat(closeable.isCalled()).isTrue();
  }

  @Test
  public void thenTryIfIOException() {
    final AsyncStatement<Integer> statement =
        new Async().value("test").thenTryIf(new Mapper<String, Closeable>() {

          public Closeable apply(final String input) {
            return new Closeable() {

              public void close() throws IOException {
                throw new IOException();
              }
            };
          }
        }, new Mapper<String, AsyncStatement<Integer>>() {

          public AsyncStatement<Integer> apply(final String input) {
            return new Async().value(input.length());
          }
        });
    assertThat(statement.isFailed()).isTrue();
  }

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void thenTryIfNPE() {
    new Async().value(null).thenTryIf(new Mapper<Object, Closeable>() {

      public Closeable apply(final Object input) {
        return null;
      }
    }, null);
  }

  @Test
  public void thenTryIfNull() {
    final AsyncStatement<Integer> statement =
        new Async().value("test").thenTryIf(new Mapper<String, Closeable>() {

          public Closeable apply(final String input) {
            return null;
          }
        }, new Mapper<String, AsyncStatement<Integer>>() {

          public AsyncStatement<Integer> apply(final String input) {
            return new Async().value(input.length());
          }
        });
    assertThat(statement.getValue()).isEqualTo(4);
  }

  @Test
  public void thenTryIfStatementNPE() {
    assertThat(new Async().value(null).thenTryIf(new Mapper<Object, Closeable>() {

      public Closeable apply(final Object input) {
        return null;
      }
    }, new Mapper<Object, AsyncStatement<Object>>() {

      public AsyncStatement<Object> apply(final Object input) {
        return null;
      }
    }).failure()).isExactlyInstanceOf(NullPointerException.class);
  }

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void thenTryNPE() {
    new Async().value(null).thenTry(new Mapper<Object, Closeable>() {

      public Closeable apply(final Object input) {
        return null;
      }
    }, null);
  }

  @Test
  public void thenTryNull() {
    final AsyncStatement<String> statement =
        new Async().value("test").thenTry(new Mapper<String, Closeable>() {

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

  @Test(expected = UnsupportedOperationException.class)
  public void toException() {
    final TestResult<String> result = new TestResult<String>();
    final AsyncStatement<String> statement = new Async().unevaluated().value("test");
    statement.to(result);
  }

  @Test
  public void value() {
    final AsyncStatement<String> statement = new Async().value("hello");
    assertThat(statement.value()).isEqualTo("hello");
  }

  @Test
  public void valueCancelled() {
    final AsyncStatement<String> statement = new Async().value("hello");
    assertThat(statement.isCancelled()).isFalse();
  }

  @Test
  public void valueEvaluating() {
    final AsyncStatement<String> statement = new Async().value("hello");
    assertThat(statement.isEvaluating()).isFalse();
  }

  @Test
  public void valueFailed() {
    final AsyncStatement<String> statement = new Async().value("hello");
    assertThat(statement.isFailed()).isFalse();
  }

  @Test(expected = IllegalStateException.class)
  public void valueIllegal() {
    final AsyncStatement<Object> statement = new Async().value(null);
    statement.thenDo(new Observer<Object>() {

      public void accept(final Object input) {
      }
    });
    ConstantConditions.notNull(statement.value());
  }

  @Test(expected = IllegalStateException.class)
  public void valueInvalidStateEvaluating() {
    final AsyncStatement<Void> statement = new Async().unevaluated().value(null);
    statement.value();
  }

  @Test(expected = IllegalStateException.class)
  public void valueInvalidStateFailure() {
    final AsyncStatement<String> statement = new Async().failure(new Exception());
    statement.value();
  }

  @Test
  public void valueSet() {
    final AsyncStatement<String> statement = new Async().value("hello");
    assertThat(statement.isSet()).isTrue();
  }

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void waitDoneNPE() {
    new Async().value(null).waitDone(0, null);
  }

  @Test
  public void waitDoneOn() {
    final AsyncStatement<String> statement =
        new Async().on(withDelay(backgroundExecutor(), 100, TimeUnit.MILLISECONDS)).value("test");
    assertThat(statement.waitDone(1, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  public void waitDoneTimeout() {
    final AsyncStatement<Object> statement =
        new Async().value(null).on(withDelay(backgroundExecutor(), 1, TimeUnit.SECONDS));
    assertThat(statement.waitDone(1, TimeUnit.MILLISECONDS)).isFalse();
  }

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void whenDoneNPE() {
    new Async().value(null).whenDone(null);
  }

  private static class Identity<V> implements Mapper<V, V> {

    public V apply(final V input) {
      return input;
    }
  }

  private static class NoOp implements Action {

    public void perform() {
    }
  }

  private static class PrintMessage implements Observer<Throwable>, Serializable {

    private final LogPrinter mLogPrinter;

    private PrintMessage(@NotNull final LogPrinter logPrinter) {
      mLogPrinter = logPrinter;
    }

    public void accept(final Throwable input) {
      Logger.newLogger(mLogPrinter, null, this).err(input);
    }
  }

  private static class PrintString implements Observer<String>, Serializable {

    private final LogPrinter mLogPrinter;

    private PrintString(@NotNull final LogPrinter logPrinter) {
      mLogPrinter = logPrinter;
    }

    public void accept(final String input) {
      Logger.newLogger(mLogPrinter, null, this).dbg(input);
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

  private static class ToMessageStatement implements Mapper<Throwable, AsyncStatement<String>> {

    public AsyncStatement<String> apply(final Throwable input) {
      return new Async().value(input.getMessage());
    }
  }

  private static class ToStatement<V> implements Mapper<V, AsyncStatement<V>> {

    public AsyncStatement<V> apply(final V input) {
      return new Async().value(input);
    }
  }

  private static class ToUpper implements Mapper<String, String> {

    public String apply(final String input) {
      return input.toUpperCase();
    }
  }

  private static class ToUpperStatement implements Mapper<String, AsyncStatement<String>> {

    public AsyncStatement<String> apply(final String input) {
      return new Async().value(input.toUpperCase());
    }
  }
}

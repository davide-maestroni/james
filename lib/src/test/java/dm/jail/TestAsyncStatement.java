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
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import dm.jail.async.AsyncResult;
import dm.jail.async.AsyncState;
import dm.jail.async.AsyncStatement;
import dm.jail.async.AsyncStatement.Forker;
import dm.jail.async.Mapper;
import dm.jail.async.Observer;
import dm.jail.async.SimpleState;
import dm.jail.util.ConstantConditions;
import dm.jail.util.RuntimeTimeoutException;

import static dm.jail.executor.ScheduledExecutors.backgroundExecutor;
import static dm.jail.executor.ScheduledExecutors.withDelay;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Created by davide-maestroni on 01/19/2018.
 */
public class TestAsyncStatement {

  // TODO: 27/01/2018 observer throws 
  // TODO: 27/01/2018 closeable null + throws 
  // TODO: 27/01/2018 cancel forked branch 
  // TODO: 27/01/2018 fork functions 
  // TODO: 27/01/2018 chain illegalstate 
  // TODO: 27/01/2018 chain get + fork get 
  // TODO: 27/01/2018 evaluating getValue 
  // TODO: 27/01/2018 catch filtered exception type 
  // TODO: 27/01/2018 forker throws
  // TODO: 26/01/2018 evaluate/then serialization
  // TODO: 26/01/2018 NPE then and below + if => null statement

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
          final AsyncState<V> stack, @NotNull final AsyncResult<V> result) throws Exception {
        if (stack != null) {
          if (stack.isSet()) {
            result.set(stack.value());

          } else {
            result.fail(stack.failure());
          }
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
  public void cancelled() {
    final AsyncStatement<Void> statement = new Async().statementDeclaration();
    statement.cancel(true);
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

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void elseCatchNPE() {
    new Async().value(null).elseCatch(null);
  }

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void elseCatchTypesNPE() {
    new Async().value(null).elseCatch(new Mapper<Throwable, Object>() {

      public Object apply(final Throwable input) {
        return null;
      }
    }, (Class<?>) null);
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

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void elseDoNPE() {
    new Async().value(null).elseDo(null);
  }

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void elseDoTypesNPE() {
    new Async().value(null).elseDo(new Observer<Throwable>() {

      public void accept(final Throwable input) {

      }
    }, (Class<?>) null);
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

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void elseIfNPE() {
    new Async().value(null).elseIf(null);
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
  @SuppressWarnings("ConstantConditions")
  public void elseIfTypesNPE() {
    new Async().value(null).elseIf(new Mapper<Throwable, AsyncStatement<?>>() {

      public AsyncStatement<?> apply(final Throwable input) {
        return new Async().value(null);
      }
    }, (Class<?>) null);
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
  public void evaluating() {
    final AsyncStatement<Void> statement = new Async().statementDeclaration();
    assertThat(statement.isEvaluating()).isTrue();
  }

  @Test
  public void evaluatingBackground() {
    final AsyncStatement<String> statement =
        new Async().value("test").on(withDelay(backgroundExecutor(), 1, TimeUnit.SECONDS));
    assertThat(statement.isEvaluating()).isTrue();
  }

  @Test
  public void evaluatingOn() {
    final AsyncStatement<String> statement =
        new Async().on(withDelay(backgroundExecutor(), 1, TimeUnit.SECONDS)).value("test");
    assertThat(statement.isEvaluating()).isTrue();
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
  public void failureInvalidStateEvaluating() {
    final AsyncStatement<Void> statement = new Async().statementDeclaration();
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
    assertThat(statement.cancel(true)).isFalse();
    assertThat(statement.isCancelled()).isFalse();
    assertThat(statement.then(new Mapper<String, String>() {

      public String apply(final String input) {
        return input;
      }
    }).value()).isEqualTo("TEST");
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

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void forkNPE() {
    new Async().value(null).fork(null);
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

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void getValueNPE() {
    new Async().value(null).getValue(0, null);
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
  public void testSerialize() throws IOException, ClassNotFoundException {
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
  public void testSerializeAsync() throws IOException, ClassNotFoundException {
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

  @Test(expected = IOException.class)
  public void testSerializeError() throws IOException, ClassNotFoundException {
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
  public void testSerializeErrorChain() throws IOException, ClassNotFoundException {
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
  public void testSerializeFork() throws IOException, ClassNotFoundException {
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
  public void valueInvalidStateEvaluating() {
    final AsyncStatement<Void> statement = new Async().statementDeclaration();
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

  @Test
  public void waitDoneOn() {
    final AsyncStatement<String> statement =
        new Async().on(withDelay(backgroundExecutor(), 100, TimeUnit.MILLISECONDS)).value("test");
    assertThat(statement.waitDone(1, TimeUnit.SECONDS)).isTrue();
  }

  private static class AtomicCloseable implements Closeable {

    private final AtomicBoolean mIsCalled = new AtomicBoolean();

    public void close() throws IOException {
      mIsCalled.set(true);
    }

    boolean isCalled() {
      return mIsCalled.get();
    }
  }
}

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

import org.junit.Test;

import java.io.Closeable;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import dm.jail.async.AsyncResult;
import dm.jail.async.AsyncStatement;
import dm.jail.async.Mapper;
import dm.jail.async.Observer;
import dm.jail.util.ConstantConditions;
import dm.jail.util.RuntimeTimeoutException;

import static dm.jail.executor.ScheduledExecutors.backgroundExecutor;
import static dm.jail.executor.ScheduledExecutors.withDelay;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Created by davide-maestroni on 01/19/2018.
 */
public class TestAsyncStatement {

  @Test
  public void cancelled() {
    final AsyncStatement<Void> statement = new Async().deferred();
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
  public void evaluating() {
    final AsyncStatement<Void> statement = new Async().deferred();
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
        new Async().<String>failure(null).then(new Mapper<String, String>() {

          public String apply(final String input) {
            return input.toUpperCase();
          }
        });
    assertThat(ConstantConditions.notNull(statement.getFailure()).getCause()).isNull();
  }

  @Test
  public void failure() {
    final AsyncStatement<String> statement = new Async().failure(new IllegalArgumentException());
    assertThat(statement.failure().getCause()).isExactlyInstanceOf(IllegalArgumentException.class);
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
    final AsyncStatement<Void> statement = new Async().deferred();
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

  @Test
  public void renew() {
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
    assertThat(statement.renew().getValue()).isNotEqualTo(value);
    assertThat(System.currentTimeMillis() - startTime).isGreaterThanOrEqualTo(100);
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
    final AsyncStatement<Void> statement = new Async().deferred();
    statement.value();
  }

  @Test(expected = IllegalStateException.class)
  public void valueInvalidStateFailure() {
    final AsyncStatement<String> statement = new Async().failure(null);
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

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
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import dm.jail.async.Action;
import dm.jail.async.AsyncStatement;
import dm.jail.async.DeclaredStatement;
import dm.jail.async.Mapper;
import dm.jail.async.Observer;
import dm.jail.util.ConstantConditions;
import dm.jail.util.RuntimeTimeoutException;

import static dm.jail.executor.ScheduledExecutors.backgroundExecutor;
import static dm.jail.executor.ScheduledExecutors.withDelay;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Created by davide-maestroni on 01/30/2018.
 */
public class TestDeclaredStatement {

  // TODO: 30/01/2018 else*
  // TODO: 30/01/2018 fork
  // TODO: 30/01/2018 serialization

  @Test
  public void addTo() {
    final TestResultCollection<String> resultCollection = new TestResultCollection<String>();
    final AsyncStatement<String> statement =
        new Async().statementDeclaration().then(new Mapper<Void, String>() {

          public String apply(final Void input) {
            return "test";
          }
        }).evaluated();
    statement.addTo(resultCollection);
    assertThat(resultCollection.getStates()).hasSize(1);
    assertThat(resultCollection.getStates().get(0).value()).isEqualTo("test");
  }

  @Test(expected = UnsupportedOperationException.class)
  public void addToException() {
    final TestResultCollection<String> resultCollection = new TestResultCollection<String>();
    final AsyncStatement<String> statement =
        new Async().statementDeclaration().then(new Mapper<Void, String>() {

          public String apply(final Void input) {
            return "test";
          }
        });
    statement.addTo(resultCollection);
  }

  @Test
  public void addToFailure() {
    final TestResultCollection<String> resultCollection = new TestResultCollection<String>();
    final AsyncStatement<String> statement =
        new Async().statementDeclaration().then(new Mapper<Void, String>() {

          public String apply(final Void input) throws Exception {
            throw new Exception("test");
          }
        }).evaluated();
    statement.addTo(resultCollection);
    assertThat(resultCollection.getStates()).hasSize(1);
    assertThat(resultCollection.getStates().get(0).failure().getMessage()).isEqualTo("test");
  }

  @Test
  public void autoEvaluate() {
    AsyncStatement<String> statement =
        new Async().statementDeclaration().then(new Mapper<Void, String>() {

          public String apply(final Void input) {
            return "test";
          }
        }).autoEvaluate();
    assertThat(statement.isEvaluating()).isFalse();
    assertThat(statement.isFailed()).isFalse();
    assertThat(statement.isSet()).isFalse();
    statement = statement.then(new Mapper<String, String>() {

      public String apply(final String input) {
        return input.toUpperCase();
      }
    });
    assertThat(statement.isEvaluating()).isFalse();
    assertThat(statement.isFailed()).isFalse();
    assertThat(statement.isSet()).isTrue();
    assertThat(statement.value()).isEqualTo("TEST");
  }

  @Test
  public void autoEvaluateEvaluated() {
    AsyncStatement<String> statement =
        new Async().statementDeclaration().then(new Mapper<Void, String>() {

          public String apply(final Void input) {
            return "test";
          }
        }).autoEvaluate().evaluated();
    assertThat(statement.isEvaluating()).isFalse();
    assertThat(statement.isFailed()).isFalse();
    assertThat(statement.isSet()).isTrue();
    assertThat(statement.value()).isEqualTo("test");
  }

  @Test(expected = UnsupportedOperationException.class)
  public void autoEvaluateTo() {
    final TestResult<String> result = new TestResult<String>();
    AsyncStatement<String> statement =
        new Async().statementDeclaration().then(new Mapper<Void, String>() {

          public String apply(final Void input) {
            return "test";
          }
        }).autoEvaluate();
    statement.to(result);
  }

  @Test
  public void cancel() {
    final AsyncStatement<String> statement =
        new Async().statementDeclaration().then(new Mapper<Void, String>() {

          public String apply(final Void input) throws Exception {
            return "test";
          }
        });
    assertThat(statement.isCancelled()).isFalse();
    assertThat(statement.isDone()).isFalse();
    assertThat(statement.cancel(false)).isTrue();
    assertThat(statement.isCancelled()).isTrue();
    assertThat(statement.isDone()).isTrue();
  }

  @Test
  public void cancelEvaluated() {
    final AsyncStatement<String> statement = //
        new Async().statementDeclaration()
                   .on(withDelay(backgroundExecutor(), 1, SECONDS))
                   .then(new Mapper<Void, String>() {

                     public String apply(final Void input) throws Exception {
                       return "test";
                     }
                   })
                   .evaluated();
    assertThat(statement.isCancelled()).isFalse();
    assertThat(statement.cancel(false)).isTrue();
    assertThat(statement.isCancelled()).isTrue();
  }

  @Test
  public void cancelSet() {
    final AsyncStatement<String> statement =
        new Async().statementDeclaration().then(new Mapper<Void, String>() {

          public String apply(final Void input) throws Exception {
            return "test";
          }
        }).evaluated();
    assertThat(statement.isCancelled()).isFalse();
    assertThat(statement.isDone()).isTrue();
    assertThat(statement.cancel(false)).isFalse();
    assertThat(statement.isCancelled()).isFalse();
    assertThat(statement.isDone()).isTrue();
  }

  @Test
  public void creation() {
    final AsyncStatement<String> statement =
        new Async().statementDeclaration().then(new Mapper<Void, String>() {

          public String apply(final Void input) {
            return "hello";
          }
        }).evaluated();
    assertThat(statement.value()).isEqualTo("hello");
  }

  @Test
  public void evaluated() {
    final Random random = new Random();
    final DeclaredStatement<Integer> statement =
        new Async().statementDeclaration().autoEvaluate().then(new Mapper<Void, Integer>() {

          public Integer apply(final Void input) {
            return random.nextInt();
          }
        });
    assertThat(statement.getValue()).isEqualTo(statement.evaluated().getValue());
    assertThat(statement.evaluated().getValue()).isEqualTo(statement.evaluated().getValue());
  }

  @Test
  public void evaluatedEvaluate() {
    final Random random = new Random();
    final DeclaredStatement<Integer> statement =
        new Async().statementDeclaration().then(new Mapper<Void, Integer>() {

          public Integer apply(final Void input) {
            return random.nextInt();
          }
        });
    assertThat(statement.evaluated().getValue()).isNotEqualTo(statement.evaluate().getValue());
  }

  @Test
  public void evaluating() {
    final AsyncStatement<String> statement = //
        new Async().statementDeclaration()
                   .on(withDelay(backgroundExecutor(), 1, SECONDS))
                   .then(new Mapper<Void, String>() {

                     public String apply(final Void input) throws Exception {
                       return "test";
                     }
                   });
    assertThat(statement.isEvaluating()).isFalse();
  }

  @Test
  public void evaluatingEvaluated() {
    final AsyncStatement<String> statement = //
        new Async().statementDeclaration()
                   .on(withDelay(backgroundExecutor(), 1, SECONDS))
                   .then(new Mapper<Void, String>() {

                     public String apply(final Void input) throws Exception {
                       return "test";
                     }
                   })
                   .evaluated();
    assertThat(statement.isEvaluating()).isTrue();
  }

  @Test
  public void failed() {
    final AsyncStatement<String> statement =
        new Async().statementDeclaration().then(new Mapper<Void, String>() {

          public String apply(final Void input) throws Exception {
            throw new Exception("test");
          }
        });
    assertThat(statement.isFailed()).isFalse();
  }

  @Test
  public void failedEvaluated() {
    final AsyncStatement<String> statement =
        new Async().statementDeclaration().then(new Mapper<Void, String>() {

          public String apply(final Void input) throws Exception {
            throw new Exception("test");
          }
        }).evaluated();
    assertThat(statement.isFailed()).isTrue();
  }

  @Test
  public void failure() {
    final AsyncStatement<String> statement =
        new Async().statementDeclaration().then(new Mapper<Void, String>() {

          public String apply(final Void input) throws Exception {
            throw new Exception("test");
          }
        });
    assertThat(statement.evaluate().failure().getMessage()).isEqualTo("test");
  }

  @Test(expected = IllegalStateException.class)
  public void failureException() {
    final AsyncStatement<String> statement =
        new Async().statementDeclaration().then(new Mapper<Void, String>() {

          public String apply(final Void input) throws Exception {
            throw new Exception("test");
          }
        });
    assertThat(statement.failure());
  }

  @Test
  public void get() throws ExecutionException, InterruptedException {
    final AsyncStatement<String> statement = //
        new Async().statementDeclaration()
                   .on(withDelay(backgroundExecutor(), 100, TimeUnit.MILLISECONDS))
                   .then(new Mapper<Void, String>() {

                     public String apply(final Void input) throws Exception {
                       return "test";
                     }
                   })
                   .evaluated();
    assertThat(statement.get()).isEqualTo("test");
  }

  @Test(expected = UnsupportedOperationException.class)
  public void getException() throws ExecutionException, InterruptedException {
    final AsyncStatement<String> statement = //
        new Async().statementDeclaration()
                   .on(withDelay(backgroundExecutor(), 100, TimeUnit.MILLISECONDS))
                   .then(new Mapper<Void, String>() {

                     public String apply(final Void input) throws Exception {
                       return "test";
                     }
                   });
    statement.get();
  }

  @Test
  public void getFailure() {
    final AsyncStatement<String> statement = //
        new Async().statementDeclaration()
                   .on(withDelay(backgroundExecutor(), 100, TimeUnit.MILLISECONDS))
                   .then(new Mapper<Void, String>() {

                     public String apply(final Void input) throws Exception {
                       throw new Exception("test");
                     }
                   })
                   .evaluated();
    assertThat(
        ConstantConditions.notNull(statement.getFailure()).getCause().getMessage()).isEqualTo(
        "test");
  }

  @Test(expected = UnsupportedOperationException.class)
  public void getFailureException() {
    final AsyncStatement<String> statement = //
        new Async().statementDeclaration()
                   .on(withDelay(backgroundExecutor(), 100, TimeUnit.MILLISECONDS))
                   .then(new Mapper<Void, String>() {

                     public String apply(final Void input) throws Exception {
                       throw new Exception("test");
                     }
                   });
    assertThat(statement.getFailure());
  }

  @Test(expected = RuntimeTimeoutException.class)
  public void getFailureTimeout() {
    final AsyncStatement<String> statement =//
        new Async().statementDeclaration()
                   .on(withDelay(backgroundExecutor(), 1, TimeUnit.SECONDS))
                   .then(new Mapper<Void, String>() {

                     public String apply(final Void input) throws Exception {
                       throw new Exception("test");
                     }
                   })
                   .evaluated();
    assertThat(statement.getFailure(10, TimeUnit.MILLISECONDS));
  }

  @Test(expected = UnsupportedOperationException.class)
  public void getFailureTimeoutException() {
    final AsyncStatement<String> statement =//
        new Async().statementDeclaration()
                   .on(withDelay(backgroundExecutor(), 1, TimeUnit.SECONDS))
                   .then(new Mapper<Void, String>() {

                     public String apply(final Void input) throws Exception {
                       throw new Exception("test");
                     }
                   });
    assertThat(statement.getFailure(10, TimeUnit.MILLISECONDS));
  }

  @Test
  public void getFailureWithTimeout() {
    final AsyncStatement<String> statement =
        new Async().statementDeclaration().then(new Mapper<Void, String>() {

          public String apply(final Void input) throws Exception {
            throw new Exception("test");
          }
        }).evaluated();
    assertThat(ConstantConditions.notNull(statement.getFailure(10, TimeUnit.MILLISECONDS))
                                 .getCause()
                                 .getMessage()).isEqualTo("test");
  }

  @Test(expected = TimeoutException.class)
  public void getTimeout() throws ExecutionException, InterruptedException, TimeoutException {
    final AsyncStatement<String> statement =//
        new Async().statementDeclaration()
                   .on(withDelay(backgroundExecutor(), 1, TimeUnit.SECONDS))
                   .then(new Mapper<Void, String>() {

                     public String apply(final Void input) throws Exception {
                       return "test";
                     }
                   })
                   .evaluated();
    statement.get(10, TimeUnit.MILLISECONDS);
  }

  @Test(expected = UnsupportedOperationException.class)
  public void getTimeoutException() throws ExecutionException, InterruptedException,
      TimeoutException {
    final AsyncStatement<String> statement =//
        new Async().statementDeclaration()
                   .on(withDelay(backgroundExecutor(), 1, TimeUnit.SECONDS))
                   .then(new Mapper<Void, String>() {

                     public String apply(final Void input) throws Exception {
                       return "test";
                     }
                   });
    statement.get(10, TimeUnit.MILLISECONDS);
  }

  @Test
  public void getValue() {
    final AsyncStatement<String> statement = //
        new Async().statementDeclaration()
                   .on(withDelay(backgroundExecutor(), 100, TimeUnit.MILLISECONDS))
                   .then(new Mapper<Void, String>() {

                     public String apply(final Void input) throws Exception {
                       return "test";
                     }
                   })
                   .evaluated();
    assertThat(statement.getValue()).isEqualTo("test");
  }

  @Test(expected = UnsupportedOperationException.class)
  public void getValueException() {
    final AsyncStatement<String> statement = //
        new Async().statementDeclaration()
                   .on(withDelay(backgroundExecutor(), 100, TimeUnit.MILLISECONDS))
                   .then(new Mapper<Void, String>() {

                     public String apply(final Void input) throws Exception {
                       return "test";
                     }
                   });
    statement.getValue();
  }

  @Test(expected = RuntimeTimeoutException.class)
  public void getValueTimeout() {
    final AsyncStatement<String> statement =//
        new Async().statementDeclaration()
                   .on(withDelay(backgroundExecutor(), 1, TimeUnit.SECONDS))
                   .then(new Mapper<Void, String>() {

                     public String apply(final Void input) throws Exception {
                       return "test";
                     }
                   })
                   .evaluated();
    statement.getValue(10, TimeUnit.MILLISECONDS);
  }

  @Test(expected = UnsupportedOperationException.class)
  public void getValueTimeoutException() {
    final AsyncStatement<String> statement =//
        new Async().statementDeclaration()
                   .on(withDelay(backgroundExecutor(), 1, TimeUnit.SECONDS))
                   .then(new Mapper<Void, String>() {

                     public String apply(final Void input) throws Exception {
                       return "test";
                     }
                   });
    statement.getValue(10, TimeUnit.MILLISECONDS);
  }

  @Test
  public void getValueWithTimeout() {
    final AsyncStatement<String> statement =
        new Async().statementDeclaration().then(new Mapper<Void, String>() {

          public String apply(final Void input) throws Exception {
            return "test";
          }
        }).evaluated();
    assertThat(statement.getValue(10, TimeUnit.MILLISECONDS)).isEqualTo("test");
  }

  @Test
  public void getWithTimeout() throws ExecutionException, InterruptedException, TimeoutException {
    final AsyncStatement<String> statement =
        new Async().statementDeclaration().then(new Mapper<Void, String>() {

          public String apply(final Void input) throws Exception {
            return "test";
          }
        }).evaluated();
    assertThat(statement.get(10, TimeUnit.MILLISECONDS)).isEqualTo("test");
  }

  @Test
  public void isFinal() {
    final AsyncStatement<String> statement =
        new Async().statementDeclaration().then(new Mapper<Void, String>() {

          public String apply(final Void input) throws Exception {
            return "test";
          }
        });
    assertThat(statement.isFinal()).isTrue();
  }

  @Test
  public void isFinalEvaluated() {
    final AsyncStatement<String> statement =
        new Async().statementDeclaration().then(new Mapper<Void, String>() {

          public String apply(final Void input) throws Exception {
            return "test";
          }
        }).evaluated();
    assertThat(statement.isFinal()).isTrue();
  }

  @Test
  public void set() {
    final AsyncStatement<String> statement =
        new Async().statementDeclaration().then(new Mapper<Void, String>() {

          public String apply(final Void input) throws Exception {
            return "test";
          }
        });
    assertThat(statement.isSet()).isFalse();
  }

  @Test
  public void setEvaluated() {
    final AsyncStatement<String> statement =
        new Async().statementDeclaration().then(new Mapper<Void, String>() {

          public String apply(final Void input) throws Exception {
            return "test";
          }
        }).evaluated();
    assertThat(statement.isSet()).isTrue();
  }

  @Test
  public void then() {
    final AsyncStatement<String> statement =
        new Async().statementDeclaration().then(new Mapper<Void, String>() {

          public String apply(final Void input) {
            return "test";
          }
        }).evaluated();
    assertThat(statement.getValue()).isEqualTo("test");
  }

  @Test
  public void thenDo() {
    final AtomicReference<String> ref = new AtomicReference<String>();
    final AsyncStatement<Void> statement =
        new Async().statementDeclaration().thenDo(new Observer<Void>() {

          public void accept(final Void input) {
            ref.set("test");
          }
        }).evaluated();
    assertThat(statement.getValue()).isNull();
    assertThat(ref.get()).isEqualTo("test");
  }

  @Test
  public void thenIf() {
    final AsyncStatement<String> statement =
        new Async().statementDeclaration().thenIf(new Mapper<Void, AsyncStatement<String>>() {

          public AsyncStatement<String> apply(final Void input) {
            return new Async().value("test");
          }
        }).evaluated();
    assertThat(statement.getValue()).isEqualTo("test");
  }

  @Test
  public void thenTry() {
    final AtomicCloseable closeable = new AtomicCloseable();
    final AsyncStatement<String> statement =
        new Async().statementDeclaration().thenTry(new Mapper<Void, Closeable>() {

          public Closeable apply(final Void input) {
            return closeable;
          }
        }, new Mapper<Void, String>() {

          public String apply(final Void input) {
            return "test";
          }
        }).evaluated();
    assertThat(statement.getValue()).isEqualTo("test");
    assertThat(closeable.isCalled()).isTrue();
  }

  @Test
  public void thenTryDo() {
    final AtomicCloseable closeable = new AtomicCloseable();
    final AtomicReference<String> ref = new AtomicReference<String>();
    final AsyncStatement<Void> statement =
        new Async().statementDeclaration().thenTryDo(new Mapper<Void, Closeable>() {

          public Closeable apply(final Void input) {
            return closeable;
          }
        }, new Observer<Void>() {

          public void accept(final Void input) {
            ref.set("test");
          }
        }).evaluated();
    assertThat(statement.getValue()).isNull();
    assertThat(ref.get()).isEqualTo("test");
    assertThat(closeable.isCalled()).isTrue();
  }

  @Test
  public void thenTryIf() {
    final AtomicCloseable closeable = new AtomicCloseable();
    final AsyncStatement<String> statement =
        new Async().statementDeclaration().thenTryIf(new Mapper<Void, Closeable>() {

          public Closeable apply(final Void input) {
            return closeable;
          }
        }, new Mapper<Void, AsyncStatement<String>>() {

          public AsyncStatement<String> apply(final Void input) {
            return new Async().value("test");
          }
        }).evaluated();
    assertThat(statement.getValue()).isEqualTo("test");
    assertThat(closeable.isCalled()).isTrue();
  }

  @Test
  public void to() {
    final TestResult<String> result = new TestResult<String>();
    final AsyncStatement<String> statement =
        new Async().statementDeclaration().then(new Mapper<Void, String>() {

          public String apply(final Void input) {
            return "test";
          }
        }).evaluated();
    statement.to(result);
    assertThat(result.getState().value()).isEqualTo("test");
  }

  @Test(expected = UnsupportedOperationException.class)
  public void toException() {
    final TestResult<String> result = new TestResult<String>();
    final AsyncStatement<String> statement =
        new Async().statementDeclaration().then(new Mapper<Void, String>() {

          public String apply(final Void input) {
            return "test";
          }
        });
    statement.to(result);
  }

  @Test
  public void toFailure() {
    final TestResult<String> result = new TestResult<String>();
    final AsyncStatement<String> statement =
        new Async().statementDeclaration().then(new Mapper<Void, String>() {

          public String apply(final Void input) throws Exception {
            throw new Exception("test");
          }
        }).evaluated();
    statement.to(result);
    assertThat(result.getState().failure().getMessage()).isEqualTo("test");
  }

  @Test
  public void value() {
    final AsyncStatement<String> statement =
        new Async().statementDeclaration().then(new Mapper<Void, String>() {

          public String apply(final Void input) throws Exception {
            return "test";
          }
        });
    assertThat(statement.evaluate().value()).isEqualTo("test");
  }

  @Test(expected = IllegalStateException.class)
  public void valueException() {
    final AsyncStatement<String> statement =
        new Async().statementDeclaration().then(new Mapper<Void, String>() {

          public String apply(final Void input) throws Exception {
            return "test";
          }
        });
    statement.value();
  }

  @Test
  public void waitDone() {
    final AsyncStatement<String> statement = //
        new Async().statementDeclaration()
                   .on(withDelay(backgroundExecutor(), 100, TimeUnit.MILLISECONDS))
                   .then(new Mapper<Void, String>() {

                     public String apply(final Void input) throws Exception {
                       return "test";
                     }
                   })
                   .evaluated();
    statement.waitDone();
    assertThat(statement.isDone()).isTrue();
  }

  @Test(expected = UnsupportedOperationException.class)
  public void waitDoneException() {
    final AsyncStatement<String> statement = //
        new Async().statementDeclaration()
                   .on(withDelay(backgroundExecutor(), 100, TimeUnit.MILLISECONDS))
                   .then(new Mapper<Void, String>() {

                     public String apply(final Void input) throws Exception {
                       return "test";
                     }
                   });
    statement.waitDone();
  }

  @Test
  public void waitDoneTimeout() {
    final AsyncStatement<String> statement = //
        new Async().statementDeclaration()
                   .on(withDelay(backgroundExecutor(), 100, TimeUnit.MILLISECONDS))
                   .then(new Mapper<Void, String>() {

                     public String apply(final Void input) throws Exception {
                       return "test";
                     }
                   })
                   .evaluated();
    assertThat(statement.waitDone(1, TimeUnit.SECONDS)).isTrue();
  }

  @Test(expected = UnsupportedOperationException.class)
  public void waitDoneTimeoutException() {
    final AsyncStatement<String> statement = //
        new Async().statementDeclaration()
                   .on(withDelay(backgroundExecutor(), 100, TimeUnit.MILLISECONDS))
                   .then(new Mapper<Void, String>() {

                     public String apply(final Void input) throws Exception {
                       return "test";
                     }
                   });
    statement.waitDone(1, TimeUnit.SECONDS);
  }

  @Test
  public void whenDone() {
    final AtomicReference<String> ref = new AtomicReference<String>();
    final AsyncStatement<Void> statement =
        new Async().statementDeclaration().whenDone(new Action() {

          public void perform() {
            ref.set("test");
          }
        }).evaluated();
    assertThat(statement.getValue()).isNull();
    assertThat(ref.get()).isEqualTo("test");
  }
}

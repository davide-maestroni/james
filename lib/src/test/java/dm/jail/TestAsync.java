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

import dm.jail.async.AsyncResult;
import dm.jail.async.Observer;
import dm.jail.executor.ScheduledExecutors;
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
}

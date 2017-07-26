/*
 * Copyright 2017 Davide Maestroni
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

package dm.james;

import org.junit.Test;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import dm.james.processor.Processors;
import dm.james.promise.Mapper;
import dm.james.promise.Observer;
import dm.james.promise.Promise;
import dm.james.promise.Promise.Callback;

import static dm.james.executor.ScheduledExecutors.defaultExecutor;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Created by davide-maestroni on 07/21/2017.
 */
public class TestPromise {

  @Test
  public void testAPlus() {
    final Random random = new Random();
    final Bond bond = new Bond();
    final Promise<Integer> promise = bond.aPlus(bond.promise(new Observer<Callback<Integer>>() {

      public void accept(final Callback<Integer> callback) {
        callback.resolve(random.nextInt());
      }
    }));
    assertThat(promise.then(new Mapper<Integer, Integer>() {

      public Integer apply(final Integer input) {
        return input;
      }
    }).get()).isEqualTo(promise.then(new Mapper<Integer, Integer>() {

      public Integer apply(final Integer input) {
        return input;
      }
    }).get());
  }

  @Test
  public void testConstructor() {
    assertThat(new Bond().promise(new Observer<Callback<Object>>() {

      public void accept(final Callback<Object> callback) {

      }
    })).isNotNull();
  }

  @Test(expected = RuntimeException.class)
  @SuppressWarnings("ConstantConditions")
  public void testConstructorFail() {
    new Bond().promise(null);
  }

  @Test
  public void testGet() {
    assertThat(new Bond().promise(new Observer<Callback<String>>() {

      public void accept(final Callback<String> callback) {
        callback.resolve("test");
      }
    }).get()).isEqualTo("test");
  }

  @Test
  public void testGetDelayed() {
    assertThat(new Bond().promise(new Observer<Callback<String>>() {

      public void accept(final Callback<String> callback) {
        callback.resolve("test");
      }
    })
                         .then(Processors.<String>scheduleOn(defaultExecutor()).delayed(100,
                             TimeUnit.MILLISECONDS))
                         .get()).isEqualTo("test");
  }

  @Test
  public void testGetRepeat() {
    final Random random = new Random();
    final Promise<Integer> promise = new Bond().promise(new Observer<Callback<Integer>>() {

      public void accept(final Callback<Integer> callback) {
        callback.resolve(random.nextInt());
      }
    });
    assertThat(promise.get()).isEqualTo(promise.get());
  }

  @Test
  public void testThenRepeat() {
    final Random random = new Random();
    final Promise<Integer> promise = new Bond().promise(new Observer<Callback<Integer>>() {

      public void accept(final Callback<Integer> callback) {
        callback.resolve(random.nextInt());
      }
    });
    assertThat(promise.then(new Mapper<Integer, Integer>() {

      public Integer apply(final Integer input) {
        return input;
      }
    }).get()).isNotEqualTo(promise.then(new Mapper<Integer, Integer>() {

      public Integer apply(final Integer input) {
        return input;
      }
    }).get());
  }
}

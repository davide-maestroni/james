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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import dm.james.executor.ScheduledExecutors;
import dm.james.promise.Mapper;
import dm.james.promise.Observer;
import dm.james.promise.Promise;
import dm.james.promise.Promise.Callback;
import dm.james.promise.Promise.StatelessProcessor;
import dm.james.promise.Provider;
import dm.james.promise.ResolvablePromise;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Created by davide-maestroni on 06/30/2017.
 */
public class Testo {

  private static Promise<String> createPromise() {
    return new Bond().promise(new Observer<Callback<String>>() {

      public void accept(final Callback<String> callback) {
        callback.resolve("test");
      }
    }).thenMap(new Mapper<String, String>() {

      public String apply(final String input) {
        return input.toUpperCase();
      }
    });
  }

  private static ResolvablePromise<String, String> createResolvablePromise() {
    return new Bond().<String>resolvable().thenMap(new Mapper<String, String>() {

      public String apply(final String input) {
        return input.toUpperCase();
      }
    });
  }

  @org.junit.Test
  public void test0() {
    final String test = new Bond().promise(new Observer<Callback<String>>() {

      public void accept(final Callback<String> callback) {
        callback.resolve("test");
      }
    }).thenMap(new Mapper<String, String>() {

      public String apply(final String input) {
        return input.toUpperCase();
      }
    }).then(new StatelessProcessor<String, String>() {

      public void reject(@Nullable final Throwable reason,
          @NotNull final Callback<String> callback) {
        ScheduledExecutors.defaultExecutor().execute(new Runnable() {

          public void run() {
            callback.reject(reason);
          }
        });
      }

      public void resolve(final String input, @NotNull final Callback<String> callback) {
        ScheduledExecutors.defaultExecutor().execute(new Runnable() {

          public void run() {
            callback.resolve(input);
          }
        }, 100, TimeUnit.MILLISECONDS);
      }

      public void resolve(@NotNull final Callback<String> callback) {
        ScheduledExecutors.defaultExecutor().execute(new Runnable() {

          public void run() {
            callback.resolve();
          }
        });
      }
    }).get(1, TimeUnit.SECONDS);
    assertThat(test).isEqualTo("TEST");
  }

  @org.junit.Test
  public void test1() {
    final String test = new Bond().promise(new Observer<Callback<String>>() {

      public void accept(final Callback<String> callback) {
        callback.resolve("test");
      }
    }).then(new StatelessProcessor<String, String>() {

      public void resolve(final String input, @NotNull final Callback<String> callback) {
        ScheduledExecutors.defaultExecutor().execute(new Runnable() {

          public void run() {
            callback.resolve(input);
          }
        });
      }

      public void reject(@Nullable final Throwable reason,
          @NotNull final Callback<String> callback) {
        ScheduledExecutors.defaultExecutor().execute(new Runnable() {

          public void run() {
            callback.reject(reason);
          }
        });
      }

      public void resolve(@NotNull final Callback<String> callback) {
        ScheduledExecutors.defaultExecutor().execute(new Runnable() {

          public void run() {
            callback.resolve();
          }
        });
      }
    }).thenMap(new Mapper<String, String>() {

      public String apply(final String input) {
        return input.toUpperCase();
      }
    }).apply(new Mapper<Promise<String>, Promise<String>>() {

      public Promise<String> apply(final Promise<String> promise) {
        return new Bond().promise(new Observer<Callback<String>>() {

          public void accept(final Callback<String> callback) throws Exception {
            callback.defer(promise.thenMap(new Mapper<String, String>() {

              public String apply(final String input) {
                return input + "_suffix";
              }
            }));
          }
        });
      }
    }).get(1, TimeUnit.SECONDS);
    assertThat(test).isEqualTo("TEST_suffix");
  }

  @org.junit.Test
  public void testCached() {
    final Bond bond = new Bond();
    final Promise<Integer> promise = bond.promise(new Observer<Callback<Integer>>() {

      public void accept(final Callback<Integer> callback) {
        callback.resolve(new Random().nextInt());
      }
    }).apply(bond.<Integer>cache());
    final Integer integer = promise.get();
    assertThat(promise.thenFill(new Provider<Integer>() {

      public Integer get() {
        return null;
      }
    }).get()).isEqualTo(integer);
    assertThat(promise.thenFill(new Provider<Integer>() {

      public Integer get() {
        return null;
      }
    }).get()).isEqualTo(integer);
  }

  @org.junit.Test(expected = IOException.class)
  public void testCannotSerialize() throws IOException, ClassNotFoundException {
    final Promise<String> promise = new Bond().promise(new Observer<Callback<String>>() {

      public void accept(final Callback<String> callback) {
        callback.resolve("test");
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

  @org.junit.Test(expected = IOException.class)
  public void testCannotSerializeThen() throws IOException, ClassNotFoundException {
    final Promise<String> promise = createPromise().thenCatch(new Mapper<Throwable, String>() {

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

  @org.junit.Test
  public void testNotCached() {
    final Bond bond = new Bond();
    final Promise<Integer> promise = bond.promise(new Observer<Callback<Integer>>() {

      public void accept(final Callback<Integer> callback) {
        callback.resolve(new Random().nextInt());
      }
    });
    final Integer integer = promise.get();
    assertThat(promise.thenFill(new Provider<Integer>() {

      public Integer get() {
        return null;
      }
    }).get()).isEqualTo(integer);
    assertThat(promise.thenFill(new Provider<Integer>() {

      public Integer get() {
        return null;
      }
    }).get()).isNotEqualTo(integer);
  }

  @org.junit.Test
  public void testResolvableInitialState() {
    final ResolvablePromise<String, String> promise =
        new Bond().<String>resolvable().thenMap(new Mapper<String, String>() {

          public String apply(final String input) {
            return input.toUpperCase();
          }
        });
    assertThat(promise.isPending()).isTrue();
  }

  @org.junit.Test
  public void testResolvableRejectedState() {
    final ResolvablePromise<String, String> promise =
        new Bond().<String>resolvable().thenMap(new Mapper<String, String>() {

          public String apply(final String input) {
            return input.toUpperCase();
          }
        });
    promise.reject(null);
    assertThat(promise.isRejected()).isTrue();
    assertThat(promise.isResolved()).isTrue();
  }

  @org.junit.Test
  public void testResolvableResolvedEmptyState() {
    final ResolvablePromise<String, String> promise =
        new Bond().<String>resolvable().thenMap(new Mapper<String, String>() {

          public String apply(final String input) {
            return input.toUpperCase();
          }
        });
    promise.resolve();
    assertThat(promise.isFulfilled()).isTrue();
    assertThat(promise.isResolved()).isTrue();
  }

  @org.junit.Test
  public void testResolvableResolvedState() {
    final ResolvablePromise<String, String> promise =
        new Bond().<String>resolvable().thenMap(new Mapper<String, String>() {

          public String apply(final String input) {
            return input.toUpperCase();
          }
        });
    promise.resolve("test");
    assertThat(promise.isFulfilled()).isTrue();
    assertThat(promise.isResolved()).isTrue();
  }

  @org.junit.Test
  public void testResolvableSerialize() throws IOException, ClassNotFoundException {
    final ResolvablePromise<String, String> promise = createResolvablePromise();
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(promise);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final ResolvablePromise<String, String> deserialized =
        (ResolvablePromise<String, String>) objectInputStream.readObject();
    assertThat(deserialized.resolved("test").get()).isEqualTo("TEST");
  }

  @org.junit.Test
  public void testSerialize() throws IOException, ClassNotFoundException {
    final Promise<String> promise = createPromise();
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(promise);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final Promise<String> deserialized =
        (Promise<String>) objectInputStream.readObject();
    assertThat(deserialized.get()).isEqualTo("TEST");
  }
}

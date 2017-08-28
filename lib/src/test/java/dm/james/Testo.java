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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Random;

import dm.james.executor.ScheduledExecutor;
import dm.james.executor.ScheduledExecutors;
import dm.james.promise.DeferredPromise;
import dm.james.promise.Mapper;
import dm.james.promise.Observer;
import dm.james.promise.Promise;
import dm.james.promise.Chainable.Callback;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Created by davide-maestroni on 06/30/2017.
 */
public class Testo {

  private static DeferredPromise<String, String> createDeferredPromise() {
    return new Bond().<String>deferred().then(new Mapper<String, String>() {

      public String apply(final String input) {
        return input.toUpperCase();
      }
    });
  }

  private static Promise<String> createPromise() {
    return new Bond().promise(new Observer<Callback<String>>() {

      public void accept(final Callback<String> callback) {
        callback.resolve("test");
      }
    }).then(new Mapper<String, String>() {

      public String apply(final String input) {
        return input.toUpperCase();
      }
    });
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
    assertThat(promise.whenFulfilled(new Observer<Integer>() {

      public void accept(final Integer input) {

      }
    }).get()).isEqualTo(integer);
    assertThat(promise.whenFulfilled(new Observer<Integer>() {

      public void accept(final Integer input) {

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
    final Promise<String> promise = createPromise().catchAll(new Mapper<Throwable, String>() {

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
  public void testDeferredInitialState() {
    final DeferredPromise<String, String> promise =
        new Bond().<String>deferred().then(new Mapper<String, String>() {

          public String apply(final String input) {
            return input.toUpperCase();
          }
        });
    assertThat(promise.isPending()).isTrue();
  }

  @org.junit.Test
  public void testDeferredRejectedState() {
    final DeferredPromise<String, String> promise =
        new Bond().<String>deferred().then(new Mapper<String, String>() {

          public String apply(final String input) {
            return input.toUpperCase();
          }
        });
    promise.reject(null);
    assertThat(promise.isRejected()).isTrue();
    assertThat(promise.isResolved()).isTrue();
  }

  @org.junit.Test
  public void testDeferredResolvedNull() {
    final DeferredPromise<String, String> promise =
        new Bond().<String>deferred().then(new Mapper<String, String>() {

          public String apply(final String input) {
            return input;
          }
        });
    promise.resolve(null);
    assertThat(promise.isFulfilled()).isTrue();
    assertThat(promise.isResolved()).isTrue();
  }

  @org.junit.Test
  public void testDeferredResolvedState() {
    final DeferredPromise<String, String> promise =
        new Bond().<String>deferred().then(new Mapper<String, String>() {

          public String apply(final String input) {
            return input.toUpperCase();
          }
        });
    promise.resolve("test");
    assertThat(promise.isFulfilled()).isTrue();
    assertThat(promise.isResolved()).isTrue();
  }

  @org.junit.Test
  public void testDeferredSerialize() throws IOException, ClassNotFoundException {
    final DeferredPromise<String, String> promise = createDeferredPromise();
    final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
    objectOutputStream.writeObject(promise);
    final ByteArrayInputStream byteInputStream =
        new ByteArrayInputStream(byteOutputStream.toByteArray());
    final ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
    @SuppressWarnings("unchecked") final DeferredPromise<String, String> deserialized =
        (DeferredPromise<String, String>) objectInputStream.readObject();
    deserialized.resolve("test");
    assertThat(deserialized.get()).isEqualTo("TEST");
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
    assertThat(promise.whenFulfilled(new Observer<Integer>() {

      public void accept(final Integer input) {

      }
    }).get()).isEqualTo(integer);
    assertThat(promise.whenFulfilled(new Observer<Integer>() {

      public void accept(final Integer input) {

      }
    }).get()).isNotEqualTo(integer);
  }

  @org.junit.Test
  public void testS() throws IOException, ClassNotFoundException {
    final ScheduledExecutor executor = ScheduledExecutors.newPoolExecutor(2);
    final Promise<String> promise = createPromise().scheduleAll(executor);
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

  private static class MyObjectInputStream extends ObjectInputStream {

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
}

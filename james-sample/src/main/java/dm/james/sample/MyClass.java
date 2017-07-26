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

package dm.james.sample;

import dm.james.Bond;
import dm.james.promise.Promise;

public class MyClass {

  public static Promise<Integer> convert(Promise<CharSequence> promise) {
    return promise.then(MyClass::count);
  }

  public static int count(CharSequence sequence) {
    return sequence.length();
  }

  public static int getInt() {
    return 13;
  }

  public static void test() {
    final Promise<String> promise = new Bond().resolved("test");
    promise.then(String::toLowerCase);
    promise.then(CharSequence::length);
    promise.then(MyClass::count);
    promise.then(CharSequence.class::cast).apply(MyClass::convert);
  }
}

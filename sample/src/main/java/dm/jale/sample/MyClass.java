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

package dm.jale.sample;

import dm.jale.Async;
import dm.jale.async.AsyncStatement;

public class MyClass {

  public static AsyncStatement<Integer> convert(CharSequence sequence) {
    return new Async().value(sequence).then(MyClass::count);
  }

  public static int count(CharSequence sequence) {
    return sequence.length();
  }

  public static int getInt() {
    return 13;
  }

  public static void test() {
    final AsyncStatement<String> statement = new Async().value("test");
    statement.then(String::toLowerCase);
    statement.then(CharSequence::length);
    statement.then(MyClass::count);
    statement.then(CharSequence.class::cast).thenIf(MyClass::convert);
  }
}

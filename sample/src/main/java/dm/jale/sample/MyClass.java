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

import dm.jale.Eventual;
import dm.jale.eventual.Statement;

public class MyClass {

  public static Statement<Integer> convert(CharSequence sequence) {
    return new Eventual().value(sequence).eventually(MyClass::count);
  }

  public static int count(CharSequence sequence) {
    return sequence.length();
  }

  public static int getInt() {
    return 13;
  }

  public static void test() {
    final Statement<String> statement = new Eventual().value("test");
    statement.eventually(String::toLowerCase);
    statement.eventually(CharSequence::length);
    statement.eventually(MyClass::count);
    statement.eventually(CharSequence.class::cast).eventuallyIf(MyClass::convert);
  }
}

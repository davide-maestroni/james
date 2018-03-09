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

package dm.fates.ext;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;

import dm.fates.eventual.Statement;
import dm.fates.ext.proxy.ProxyMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Created by davide-maestroni on 02/22/2018.
 */
public class EventualExtTest {

  @Test
  public void proxy() {
    final ToString proxy = new EventualExt().proxy("test", ToString.class);
    assertThat(proxy.toString(ProxyMapper.<String>toStatement()).getValue()).isEqualTo("test");
    assertThat(proxy.toString()).isEqualTo("test");
    assertThat(proxy.toString(new ToStringMapper())).isEqualTo("test");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void proxyList() {
    final MyList<String> proxy =
        new EventualExt().proxy(Arrays.asList("test1", "test2"), MyList.class);
    assertThat(proxy.toString(ProxyMapper.<String>toStatement()).getValue()).isEqualTo(
        Arrays.asList("test1", "test2").toString());
    assertThat(proxy.subList(new ToListMapper<String>(), 1, 2).getValue()).containsExactly("test2");
  }

  @Test
  public void proxyPrinter() {
    final EventualPrinter proxy = new EventualExt().proxy(new Printer(), EventualPrinter.class);
    proxy.print(ProxyMapper.noWaitDone(), "test");
  }

  private interface EventualPrinter {

    void print(ProxyMapper<Void> mapper, String str);
  }

  private interface MyList<E> extends List<E> {

    @NotNull
    Statement<List<E>> subList(ToListMapper<E> mapper, int start, int end);

    Statement<String> toString(ProxyMapper<Statement<String>> mapper);
  }

  private interface ToString {

    void error();

    String toString(ToStringMapper mapper);

    Statement<String> toString(ProxyMapper<Statement<String>> mapper);
  }

  private static class Printer {

    public void print(String str) {
      System.out.println(str);
    }
  }

  private static class ToListMapper<E> extends ProxyMapper<Statement<List<E>>> {

    public Statement<List<E>> apply(final Statement<?> statement, final Type input2,
        final Type input3) {
      return (Statement<List<E>>) statement;
    }
  }

  private static class ToStringMapper extends ProxyMapper<String> {

    public String apply(final Statement<?> statement, final Type input2, final Type input3) {
      return (String) statement.getValue();
    }
  }
}

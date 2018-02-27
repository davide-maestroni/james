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

package dm.jale.ext;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import dm.jale.eventual.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Created by davide-maestroni on 02/22/2018.
 */
public class EventualExtTest {

  @Test
  public void proxy() {
    final ToString proxy = new EventualExt().proxy("test", ToString.class);
    assertThat(proxy.toStringStatement().getValue()).isEqualTo("test");
    assertThat(proxy.toString()).isEqualTo("test");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void proxyList() {
    final MyList<String> proxy =
        new EventualExt().proxy(Arrays.asList("test1", "test2"), MyList.class);
    assertThat(proxy.toStringStatement().getValue()).isEqualTo(
        Arrays.asList("test1", "test2").toString());
    assertThat(proxy.subListStatement(1, 2).getValue()).containsExactly("test2");
  }

  private interface MyList<E> extends List<E> {

    @NotNull
    Statement<List<E>> subListStatement(int start, int end);

    Statement<String> toStringStatement();
  }

  private interface ToString {

    void error();

    Statement<String> toStringStatement();
  }
}

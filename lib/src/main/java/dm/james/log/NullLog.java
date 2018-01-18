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

package dm.james.log;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.List;

/**
 * LogPrinter implementation simply discarding all messages.
 * <p>
 * Created by davide-maestroni on 10/04/2014.
 */
class NullLog extends TemplateLog implements Serializable {

  private static final NullLog sInstance = new NullLog();

  /**
   * Avoid explicit instantiation.
   */
  private NullLog() {
  }

  @NotNull
  static NullLog instance() {
    return sInstance;
  }

  @Override
  protected void log(@NotNull final Level level, @NotNull final List<Object> contexts,
      @Nullable final String message, @Nullable final Throwable throwable) {
  }

  Object readResolve() throws ObjectStreamException {
    return sInstance;
  }
}

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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import dm.jail.log.LogPrinter;

/**
 * Created by davide-maestroni on 01/31/2018.
 */
class TestLogPrinter implements LogPrinter {

  private final AtomicBoolean mDbgCalled = new AtomicBoolean();

  private final AtomicBoolean mErrCalled = new AtomicBoolean();

  private final AtomicBoolean mWrnCalled = new AtomicBoolean();

  public void dbg(@NotNull final List<Object> contexts, @Nullable final String message,
      @Nullable final Throwable throwable) {
    mDbgCalled.set(true);
  }

  public void err(@NotNull final List<Object> contexts, @Nullable final String message,
      @Nullable final Throwable throwable) {
    mErrCalled.set(true);
  }

  public void wrn(@NotNull final List<Object> contexts, @Nullable final String message,
      @Nullable final Throwable throwable) {
    mWrnCalled.set(true);
  }

  public boolean isDbgCalled() {
    return mDbgCalled.get();
  }

  public boolean isErrCalled() {
    return mErrCalled.get();
  }

  public boolean isWrnCalled() {
    return mWrnCalled.get();
  }
}

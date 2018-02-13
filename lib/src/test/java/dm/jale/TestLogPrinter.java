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

package dm.jale;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

import dm.jale.log.LogPrinter;

/**
 * Created by davide-maestroni on 01/31/2018.
 */
class TestLogPrinter implements LogPrinter {

  private final AtomicBoolean mDbgCalled = new AtomicBoolean();

  private final AtomicBoolean mErrCalled = new AtomicBoolean();

  private final Level mLevel;

  private final AtomicBoolean mWrnCalled = new AtomicBoolean();

  TestLogPrinter(@NotNull final Level level) {
    mLevel = level;
  }

  public boolean canLogDbg() {
    return mLevel.compareTo(Level.DBG) <= 0;
  }

  public boolean canLogErr() {
    return mLevel.compareTo(Level.ERR) <= 0;
  }

  public boolean canLogWrn() {
    return mLevel.compareTo(Level.WRN) <= 0;
  }

  public void dbg(@Nullable final String message, @Nullable final Throwable throwable) {
    mDbgCalled.set(true);
  }

  public void err(@Nullable final String message, @Nullable final Throwable throwable) {
    mErrCalled.set(true);
  }

  public void wrn(@Nullable final String message, @Nullable final Throwable throwable) {
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

  enum Level {
    DBG, WRN, ERR
  }
}

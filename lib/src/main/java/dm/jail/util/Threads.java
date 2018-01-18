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

package dm.jail.util;

import org.jetbrains.annotations.NotNull;

/**
 * Created by davide-maestroni on 08/05/2017.
 */
public class Threads {

  private static final Object mMutex = new Object();

  private static WeakIdentityHashMap<ThreadOwner, Void> mOwners =
      new WeakIdentityHashMap<ThreadOwner, Void>();

  private Threads() {
  }

  public static boolean isOwnedThread() {
    for (final ThreadOwner owner : mOwners.keySet()) {
      if ((owner != null) && owner.isOwnedThread()) {
        return true;
      }
    }

    return false;
  }

  public static void register(@NotNull final ThreadOwner owner) {
    ConstantConditions.notNull("owner", owner);
    synchronized (mMutex) {
      final WeakIdentityHashMap<ThreadOwner, Void> owners =
          new WeakIdentityHashMap<ThreadOwner, Void>(mOwners);
      owners.put(owner, null);
      mOwners = owners;
    }
  }

  public interface ThreadOwner {

    boolean isOwnedThread();
  }
}

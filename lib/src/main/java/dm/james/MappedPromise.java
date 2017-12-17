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

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

import dm.james.promise.Mapper;
import dm.james.promise.Promise;
import dm.james.promise.RejectionException;
import dm.james.util.ConstantConditions;

/**
 * Created by davide-maestroni on 07/21/2017.
 */
class MappedPromise<O> extends PromiseWrapper<O> implements Serializable {

  private final Mapper<Promise<?>, Promise<?>> mMapper;

  MappedPromise(@NotNull final Mapper<Promise<?>, Promise<?>> mapper,
      @NotNull final Promise<O> promise) {
    this(ConstantConditions.notNull("mapper", mapper), promise, false);
  }

  private MappedPromise(@NotNull final Mapper<Promise<?>, Promise<?>> mapper,
      @NotNull final Promise<O> promise, final boolean renew) {
    super(renew ? promise : mapPromise(mapper, promise));
    mMapper = mapper;
  }

  @NotNull
  @SuppressWarnings("unchecked")
  private static <O> Promise<O> mapPromise(@NotNull final Mapper<Promise<?>, Promise<?>> mapper,
      @NotNull final Promise<O> promise) {
    try {
      return (Promise<O>) mapper.apply(promise);

    } catch (final Exception e) {
      throw RejectionException.wrapIfNotRejectionException(e);
    }
  }

  @NotNull
  @Override
  public Promise<O> renew() {
    return new MappedPromise<O>(mMapper, wrapped(), true);
  }

  @NotNull
  protected <R> Promise<R> newInstance(@NotNull final Promise<R> promise) {
    return new MappedPromise<R>(mMapper, promise);
  }

  @NotNull
  Mapper<Promise<?>, Promise<?>> mapper() {
    return mMapper;
  }
}

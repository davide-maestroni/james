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

package dm.james.promise2;

import org.jetbrains.annotations.NotNull;

import dm.james.promise.Mapper;
import dm.james.promise.RejectionException;
import dm.james.util.ConstantConditions;

/**
 * Created by davide-maestroni on 12/11/2017.
 */
class MappedPromise<V> extends PromiseWrapper<V> {

  private final Mapper<? super Promise<?>, ? extends Promise<?>> mMapper;

  MappedPromise(@NotNull final Mapper<? super Promise<?>, ? extends Promise<?>> mapper,
      @NotNull final Promise<V> promise) {
    this(ConstantConditions.notNull("mapper", mapper), promise, true);
  }

  private MappedPromise(@NotNull final Mapper<? super Promise<?>, ? extends Promise<?>> mapper,
      @NotNull final Promise<V> promise, final boolean wrap) {
    super(wrap ? promise : mapPromise(mapper, promise));
    mMapper = mapper;
  }

  @NotNull
  @SuppressWarnings("unchecked")
  private static <R> Promise<R> mapPromise(
      @NotNull final Mapper<? super Promise<?>, ? extends Promise<?>> mapper,
      @NotNull final Promise<R> promise) {
    try {
      return (Promise<R>) mapper.apply(promise);

    } catch (final Exception e) {
      throw RejectionException.wrapIfNotRejectionException(e);
    }
  }

  @NotNull
  @Override
  public Promise<V> ordered() {
    return new MappedPromise<V>(mMapper, wrapped().ordered(), false);
  }

  @NotNull
  @Override
  public Promise<V> renew() {
    return new MappedPromise<V>(mMapper, wrapped(), false);
  }

  @NotNull
  @Override
  public Promise<V> trying() {
    return new MappedPromise<V>(mMapper, wrapped().trying(), false);
  }

  @NotNull
  protected <R> Promise<R> newInstance(@NotNull final Promise<R> promise) {
    return new MappedPromise<R>(mMapper, promise);
  }

  @NotNull
  Mapper<? super Promise<?>, ? extends Promise<?>> mapper() {
    return mMapper;
  }
}

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

package dm.fates.ext.proxy;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import dm.fates.eventual.Statement;
import dm.fates.ext.config.BuildConfig;
import dm.fates.ext.eventual.TriMapper;
import dm.fates.util.ConstantConditions;
import dm.fates.util.SerializableProxy;

/**
 * Created by davide-maestroni on 03/05/2018.
 */
public abstract class ProxyMapper<V> implements TriMapper<Statement<?>, Type, Type, V> {

  @NotNull
  public static <V> ProxyMapper<V> mapper(
      @NotNull final TriMapper<Statement<?>, Type, Type, V> mapper) {
    return new LambdaMapper<V>(mapper);
  }

  @NotNull
  public static ProxyMapper<Void> noWaitDone() {
    return NoWaitMapper.sInstance;
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public static <V> ProxyMapper<Statement<V>> toStatement() {
    return (ProxyMapper<Statement<V>>) ToStatementMapper.sInstance;
  }

  @Nullable
  private static Class<?> getRawType(@NotNull final Type type) {
    if (type instanceof Class) {
      return (Class<?>) type;
    }

    if (type instanceof ParameterizedType) {
      return (Class<?>) ((ParameterizedType) type).getRawType();
    }

    if (type instanceof GenericArrayType) {
      return Object[].class;
    }

    return null;
  }

  private static boolean isVoid(@NotNull final Type type) {
    return void.class.equals(type) || Void.class.equals(type);
  }

  private static class LambdaMapper<V> extends ProxyMapper<V> implements Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final TriMapper<Statement<?>, Type, Type, V> mMapper;

    private LambdaMapper(@NotNull final TriMapper<Statement<?>, Type, Type, V> mapper) {
      mMapper = ConstantConditions.notNull("mapper", mapper);
    }

    public V apply(final Statement<?> statement, final Type methodType,
        final Type targetType) throws Exception {
      return mMapper.apply(statement, methodType, targetType);
    }

    @NotNull
    private Object readResolve() throws ObjectStreamException {
      return new MapperProxy<V>(mMapper);
    }

    private static class MapperProxy<V> extends SerializableProxy {

      private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

      private MapperProxy(final TriMapper<Statement<?>, Type, Type, V> mapper) {
        super(proxy(mapper));
      }

      @NotNull
      @SuppressWarnings("unchecked")
      private Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new LambdaMapper<V>((TriMapper<Statement<?>, Type, Type, V>) args[0]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }
  }

  private static class NoWaitMapper extends ProxyMapper<Void> implements Serializable {

    private static final NoWaitMapper sInstance = new NoWaitMapper();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    @NotNull
    private Object readResolve() throws ObjectStreamException {
      return sInstance;
    }

    public Void apply(final Statement<?> statement, final Type methodType, final Type targetType) {
      if (!ProxyMapper.isVoid(methodType) || !ProxyMapper.isVoid(targetType)) {
        throw new IllegalArgumentException(
            "both called and target method must have no return type");
      }

      return null;
    }
  }

  private static class ToStatementMapper<V> extends ProxyMapper<Statement<V>>
      implements Serializable {

    private static final ToStatementMapper sInstance = new ToStatementMapper<Object>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    @NotNull
    private Object readResolve() throws ObjectStreamException {
      return sInstance;
    }

    @SuppressWarnings("unchecked")
    public Statement<V> apply(final Statement<?> statement, final Type methodType,
        final Type targetType) {
      if (!Statement.class.equals(ProxyMapper.getRawType(methodType))) {
        throw new IllegalArgumentException(
            "called method must return a " + Statement.class.getSimpleName() + " instance");
      }

      return (Statement<V>) statement;
    }
  }
}

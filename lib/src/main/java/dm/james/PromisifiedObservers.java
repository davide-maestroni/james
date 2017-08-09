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
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;

import dm.james.promise.Observer;
import dm.james.promise.Promise.Callback;
import dm.james.promise.PromiseIterable.CallbackIterable;
import dm.james.reflect.CallbackMapper;
import dm.james.util.ReflectionUtils;

/**
 * Created by davide-maestroni on 08/09/2017.
 */
class PromisifiedObservers {

  private static final Object CALLBACK_PLACEHOLDER = new Object();

  private PromisifiedObservers() {
  }

  @NotNull
  static <O> Observer<CallbackIterable<O>> iterableObserver(final Object target,
      @NotNull final Method method, @Nullable final Object[] params,
      @NotNull final CallbackMapper mapper) {
    final Object[] args = ReflectionUtils.asArgs(params);
    validateMethod(method, args, mapper);
    final Object[] inputs = buildInputs(method, args, mapper);
    for (final Object input : inputs) {
      if (input == CALLBACK_PLACEHOLDER) {
        return new CallbackIterableObserver<O>(target, method, inputs, mapper);
      }
    }

    return new MethodIterableObserver<O>(target, method, inputs);
  }

  @NotNull
  static <O> Observer<CallbackIterable<O>> iterableObserver(final Object target,
      @NotNull final Class<?> targetClass, @NotNull final String name,
      @Nullable final Object[] params, @NotNull final CallbackMapper mapper) {
    final Object[] args = ReflectionUtils.asArgs(params);
    final Method method = findBestMatchingMethod(targetClass, name, args, mapper);
    final Object[] inputs = buildInputs(method, args, mapper);
    for (final Object input : inputs) {
      if (input == CALLBACK_PLACEHOLDER) {
        return new CallbackIterableObserver<O>(target, method, inputs, mapper);
      }
    }

    return new MethodIterableObserver<O>(target, method, inputs);
  }

  @NotNull
  static <O> Observer<Callback<O>> observer(final Object target, @NotNull final Method method,
      @Nullable final Object[] params, @NotNull final CallbackMapper mapper) {
    final Object[] args = ReflectionUtils.asArgs(params);
    validateMethod(method, args, mapper);
    final Object[] inputs = buildInputs(method, args, mapper);
    for (final Object input : inputs) {
      if (input == CALLBACK_PLACEHOLDER) {
        return new CallbackObserver<O>(target, method, inputs, mapper);
      }
    }

    return new MethodObserver<O>(target, method, inputs);
  }

  @NotNull
  static <O> Observer<Callback<O>> observer(final Object target,
      @NotNull final Class<?> targetClass, @NotNull final String name,
      @Nullable final Object[] params, @NotNull final CallbackMapper mapper) {
    final Object[] args = ReflectionUtils.asArgs(params);
    final Method method = findBestMatchingMethod(targetClass, name, args, mapper);
    final Object[] inputs = buildInputs(method, args, mapper);
    for (final Object input : inputs) {
      if (input == CALLBACK_PLACEHOLDER) {
        return new CallbackObserver<O>(target, method, inputs, mapper);
      }
    }

    return new MethodObserver<O>(target, method, inputs);
  }

  @NotNull
  private static Object[] buildInputs(@NotNull final Method method, @NotNull final Object[] args,
      @NotNull final CallbackMapper mapper) {
    final Class<?>[] parameterTypes = method.getParameterTypes();
    final Object[] inputs = new Object[method.getParameterTypes().length];
    int index = 0;
    for (int i = 0; i < parameterTypes.length; ++i) {
      final Class<?> parameterType = parameterTypes[i];
      if (mapper.canMapCallbackIterable(parameterType)) {
        inputs[i] = CALLBACK_PLACEHOLDER;

      } else {
        inputs[i] = args[index++];
      }
    }

    return inputs;
  }

  private static int computeConfidence(@NotNull final Class<?>[] params,
      @NotNull final Object[] args) {
    final int length = params.length;
    final int argsLength = args.length;
    if (length != argsLength) {
      return -1;
    }

    int confidence = 0;
    for (int i = 0; i < argsLength; ++i) {
      final Object arg = args[i];
      final Class<?> param = params[i];
      if (arg != null) {
        if (arg == CALLBACK_PLACEHOLDER) {
          ++confidence;
        }

        final Class<?> boxingClass = ReflectionUtils.boxingClass(param);
        if (!boxingClass.isInstance(arg)) {
          confidence = -1;
          break;
        }

        if (arg.getClass().equals(boxingClass)) {
          ++confidence;
        }

      } else if (param.isPrimitive()) {
        confidence = -1;
        break;
      }
    }

    return confidence;
  }

  @Nullable
  private static Method findBestMatchingMethod(@NotNull final ArrayList<Method> methods,
      @NotNull final Object[] args, @NotNull final CallbackMapper mapper) {
    final ArrayList<Object> inputs = new ArrayList<Object>();
    Method bestMatch = null;
    boolean isClash = false;
    int maxConfidence = -1;
    for (final Method method : methods) {
      int index = 0;
      inputs.clear();
      final Class<?>[] parameterTypes = method.getParameterTypes();
      for (final Class<?> parameterType : parameterTypes) {
        if (mapper.canMapCallbackIterable(parameterType)) {
          inputs.add(CALLBACK_PLACEHOLDER);

        } else if (index < args.length - 1) {
          inputs.add(args[index++]);

        } else {
          break;
        }
      }

      final int confidence = computeConfidence(parameterTypes, inputs.toArray());
      if (confidence < 0) {
        continue;
      }

      if ((bestMatch == null) || (confidence > maxConfidence)) {
        isClash = false;
        bestMatch = method;
        maxConfidence = confidence;

      } else if (confidence == maxConfidence) {
        isClash = true;
      }
    }

    if (isClash) {
      throw new IllegalArgumentException(
          "more than one method found for arguments: " + Arrays.toString(args));
    }

    return bestMatch;
  }

  @NotNull
  private static Method findBestMatchingMethod(@NotNull final Class<?> targetClass,
      @NotNull final String name, @NotNull final Object[] args,
      @NotNull final CallbackMapper mapper) {
    final ArrayList<Method> methods = new ArrayList<Method>();
    for (final Method method : targetClass.getMethods()) {
      if (method.getName().equals(name)) {
        methods.add(method);
      }
    }

    Method method = findBestMatchingMethod(methods, args, mapper);
    if (method == null) {
      methods.clear();
      for (final Method declaredMethod : targetClass.getDeclaredMethods()) {
        if (!declaredMethod.getName().equals(name)) {
          continue;
        }

        final int modifiers = declaredMethod.getModifiers();
        if (Modifier.isProtected(modifiers)) {
          methods.add(declaredMethod);
        }
      }

      method = findBestMatchingMethod(methods, args, mapper);
      if (method == null) {
        methods.clear();
        for (final Method declaredMethod : targetClass.getDeclaredMethods()) {
          if (!declaredMethod.getName().equals(name)) {
            continue;
          }

          final int modifiers = declaredMethod.getModifiers();
          if (!Modifier.isPublic(modifiers) && !Modifier.isProtected(modifiers)
              && !Modifier.isPrivate(modifiers)) {
            methods.add(declaredMethod);
          }
        }

        method = findBestMatchingMethod(methods, args, mapper);
        if (method == null) {
          methods.clear();
          for (final Method declaredMethod : targetClass.getDeclaredMethods()) {
            if (!declaredMethod.getName().equals(name)) {
              continue;
            }

            final int modifiers = declaredMethod.getModifiers();
            if (Modifier.isPrivate(modifiers)) {
              methods.add(declaredMethod);
            }
          }

          method = findBestMatchingMethod(methods, args, mapper);
          if (method == null) {
            throw new IllegalArgumentException(
                "no suitable method found for type: " + targetClass.getName());
          }
        }
      }
    }

    return ReflectionUtils.makeAccessible(method);
  }

  private static void validateMethod(@NotNull final Method method, @NotNull final Object[] args,
      @NotNull final CallbackMapper mapper) {
    int index = 0;
    final Class<?>[] parameterTypes = method.getParameterTypes();
    for (final Class<?> parameterType : parameterTypes) {
      if (!mapper.canMapCallbackIterable(parameterType)) {
        if (index >= (args.length - 1)) {
          throw new IllegalArgumentException("invalid number of parameters for method: " + method);
        }

        final Object arg = args[index++];
        if ((arg != null) && !parameterType.isInstance(arg)) {
          throw new IllegalArgumentException("invalid parameters for method: " + method);
        }
      }
    }
  }

  // TODO: 07/08/2017 serializable?? proxy

  private static class CallbackIterableObserver<O> implements Observer<CallbackIterable<O>> {

    private final CallbackMapper mMapper;

    private final Method mMethod;

    private final Object[] mParams;

    private final Object mTarget;

    private CallbackIterableObserver(final Object target, @NotNull final Method method,
        @NotNull final Object[] params, @NotNull final CallbackMapper mapper) {
      mTarget = target;
      mMethod = method;
      mParams = params;
      mMapper = mapper;
    }

    @SuppressWarnings("unchecked")
    public void accept(final CallbackIterable<O> callback) throws Exception {
      @SuppressWarnings("UnnecessaryLocalVariable") final CallbackMapper mapper = mMapper;
      final Method method = mMethod;
      final Object[] params = mParams.clone();
      for (int i = 0; i < params.length; ++i) {
        if (params[i] == CALLBACK_PLACEHOLDER) {
          params[i] = mapper.mapCallbackIterable(method.getParameterTypes()[i],
              (CallbackIterable<Object>) callback);
        }
      }

      method.invoke(mTarget, params);
    }
  }

  private static class CallbackObserver<O> implements Observer<Callback<O>> {

    private final CallbackMapper mMapper;

    private final Method mMethod;

    private final Object[] mParams;

    private final Object mTarget;

    private CallbackObserver(final Object target, @NotNull final Method method,
        @NotNull final Object[] params, @NotNull final CallbackMapper mapper) {
      mTarget = target;
      mMethod = method;
      mParams = params;
      mMapper = mapper;
    }

    @SuppressWarnings("unchecked")
    public void accept(final Callback<O> callback) throws Exception {
      @SuppressWarnings("UnnecessaryLocalVariable") final CallbackMapper mapper = mMapper;
      final Method method = mMethod;
      final Object[] params = mParams.clone();
      for (int i = 0; i < params.length; ++i) {
        if (params[i] == CALLBACK_PLACEHOLDER) {
          params[i] =
              mapper.mapCallback(method.getParameterTypes()[i], (Callback<Object>) callback);
        }
      }

      method.invoke(mTarget, params);
    }
  }

  private static class MethodIterableObserver<O> implements Observer<CallbackIterable<O>> {

    private final Method mMethod;

    private final Object[] mParams;

    private final Object mTarget;

    private MethodIterableObserver(final Object target, @NotNull final Method method,
        @NotNull final Object[] params) {
      mTarget = target;
      mMethod = method;
      mParams = params;
    }

    @SuppressWarnings("unchecked")
    public void accept(final CallbackIterable<O> callback) throws Exception {
      callback.resolve((O) mMethod.invoke(mTarget, mParams));
    }
  }

  private static class MethodObserver<O> implements Observer<Callback<O>> {

    private final Method mMethod;

    private final Object[] mParams;

    private final Object mTarget;

    private MethodObserver(final Object target, @NotNull final Method method,
        @NotNull final Object[] params) {
      mTarget = target;
      mMethod = method;
      mParams = params;
    }

    @SuppressWarnings("unchecked")
    public void accept(final Callback<O> callback) throws Exception {
      callback.resolve((O) mMethod.invoke(mTarget, mParams));
    }
  }
}

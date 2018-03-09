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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;

import dm.fates.Eventual;
import dm.fates.eventual.Evaluation;
import dm.fates.eventual.Observer;
import dm.fates.ext.proxy.ProxyMapper;
import dm.fates.util.ConstantConditions;

import static dm.fates.util.Reflections.cloneArgs;

/**
 * Created by davide-maestroni on 02/22/2018.
 */
class EventualInvocationHandler implements InvocationHandler {

  private final Eventual mEventual;

  private final Object mTarget;

  EventualInvocationHandler(@NotNull final Eventual eventual, @NotNull final Object target) {
    mEventual = ConstantConditions.notNull("eventual", eventual);
    mTarget = ConstantConditions.notNull("target", target);
  }

  public Object invoke(final Object object, final Method method, final Object[] args) throws
      Throwable {
    final String methodName = method.getName();
    final Class<?> returnType = method.getReturnType();
    final Class<?>[] parameterTypes = method.getParameterTypes();
    final Method[] targetMethods = mTarget.getClass().getMethods();
    for (final Method targetMethod : targetMethods) {
      if (methodName.equals(targetMethod.getName()) && Arrays.equals(parameterTypes,
          targetMethod.getParameterTypes()) && returnType.isAssignableFrom(
          targetMethod.getReturnType())) {
        return mEventual.statement(new InvocationHandlerObserver(mTarget, targetMethod, args))
            .getValue();
      }
    }

    if (parameterTypes.length > 0) {
      final Class<?> parameterType = parameterTypes[0];
      if (ProxyMapper.class.isAssignableFrom(parameterType)) {
        final Class<?>[] normalizedParameterTypes = new Class[parameterTypes.length - 1];
        System.arraycopy(parameterTypes, 1, normalizedParameterTypes, 0,
            normalizedParameterTypes.length);
        final ProxyMapper<?> mapper = (ProxyMapper<?>) args[0];
        final Type expectedReturnType;
        if (ProxyMapper.class.equals(parameterType)) {
          expectedReturnType = null;

        } else {
          final Type genericSuperclass = parameterType.getGenericSuperclass();
          if (genericSuperclass instanceof ParameterizedType) {
            expectedReturnType =
                ((ParameterizedType) genericSuperclass).getActualTypeArguments()[0];

          } else {
            expectedReturnType = null;
          }
        }

        Method invokeMethod = null;
        for (final Method targetMethod : targetMethods) {
          if (methodName.equals(targetMethod.getName()) && Arrays.equals(normalizedParameterTypes,
              targetMethod.getParameterTypes()) && ((expectedReturnType == null)
              || expectedReturnType.toString().equals(method.getGenericReturnType().toString()))) {
            invokeMethod = targetMethod;
          }
        }

        if (invokeMethod != null) {
          final Object[] normalizedArgs = new Object[args.length - 1];
          System.arraycopy(args, 1, normalizedArgs, 0, normalizedArgs.length);
          return mapper.apply(mEventual.statement(
              new InvocationHandlerObserver(mTarget, invokeMethod, normalizedArgs)),
              method.getGenericReturnType(), invokeMethod.getGenericReturnType());
        }
      }
    }

    return ConstantConditions.unsupported(null, "invoke");
  }

  private static class InvocationHandlerObserver implements Observer<Evaluation<Object>> {

    private final Object[] mArgs;

    private final Method mMethod;

    private final Object mObject;

    private InvocationHandlerObserver(@NotNull final Object object, @NotNull final Method method,
        final Object[] args) {
      mObject = object;
      mMethod = method;
      mArgs = cloneArgs(args);
    }

    public void accept(final Evaluation<Object> evaluation) throws Exception {
      evaluation.set(mMethod.invoke(mObject, mArgs));
    }
  }
}

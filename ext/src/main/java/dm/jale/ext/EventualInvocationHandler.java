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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;

import dm.jale.Eventual;
import dm.jale.eventual.Evaluation;
import dm.jale.eventual.Observer;
import dm.jale.eventual.Statement;
import dm.jale.util.ConstantConditions;

import static dm.jale.util.Reflections.cloneArgs;

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

  private static boolean canReturnStatement(@NotNull final Method method,
      @NotNull final Method targetMethod) {
    final Class<?> targetReturnType = targetMethod.getReturnType();
    if (Statement.class.equals(method.getReturnType()) && !Statement.class.equals(
        targetReturnType)) {
      final Type genericReturnType = method.getGenericReturnType();
      if (genericReturnType instanceof ParameterizedType) {
        final Type[] typeArguments =
            ((ParameterizedType) genericReturnType).getActualTypeArguments();
        // TODO: 28/02/2018 rawType only?
        if (typeArguments[0].toString().equals(targetMethod.getGenericReturnType().toString())) {
          return true;
        }
      }
    }

    return false;
  }

  public Object invoke(final Object object, final Method method, final Object[] args) throws
      Throwable {
    final String methodName = method.getName();
    final Class<?> returnType = method.getReturnType();
    final Method[] targetMethods = mTarget.getClass().getMethods();
    for (final Method targetMethod : targetMethods) {
      if (methodName.equals(targetMethod.getName()) && Arrays.equals(method.getParameterTypes(),
          targetMethod.getParameterTypes()) && returnType.isAssignableFrom(
          targetMethod.getReturnType())) {
        return mEventual.statement(new InvocationHandlerObserver(mTarget, targetMethod, args))
            .getValue();
      }
    }

    for (final Method targetMethod : targetMethods) {
      if (methodName.equals(targetMethod.getName() + "Statement") && Arrays.equals(
          method.getParameterTypes(), targetMethod.getParameterTypes()) && canReturnStatement(
          method, targetMethod)) {
        return mEventual.statement(new InvocationHandlerObserver(mTarget, targetMethod, args));
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

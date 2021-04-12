/*
 * Copyright 2021 vg2902.org
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
package org.vg2902.synchrotask.spring.aop;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInvocation;
import org.vg2902.synchrotask.spring.SynchroTask;
import org.vg2902.synchrotask.spring.TaskId;
import org.vg2902.synchrotask.spring.TaskName;
import org.vg2902.synchrotask.spring.exception.IncorrectAnnotationException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
class SynchroTaskAopUtils {

    static boolean isEligibleMethod(Method method) {
        return getSynchroTaskAnnotation(method) != null;
    }

    static SynchroTask getSynchroTaskAnnotation(Method method) {
        return method.getAnnotation(SynchroTask.class);
    }

    static void validateArgList(Method method, Class<?> targetClass) throws NoSuchMethodException {
        String methodName = method.toGenericString();
        log.debug("Checking {}", methodName);

        int taskIdPosition = getAnnotatedArgPosition(method, targetClass, TaskId.class);
        log.debug("taskId parameter position is found: {}", taskIdPosition);

        int taskNamePosition = getAnnotatedArgPosition(method, targetClass, TaskName.class);
        log.debug("taskName parameter position is found: {}", taskNamePosition);
    }

    static <T extends Annotation> Object getAnnotatedArgValue(MethodInvocation methodInvocation, Class<T> argAnnotation) throws NoSuchMethodException {
        Method method = methodInvocation.getMethod();

        Class<?> targetClass = methodInvocation.getThis().getClass();
        Object[] arguments = methodInvocation.getArguments();

        int position = getAnnotatedArgPosition(method, targetClass, argAnnotation);
        log.debug("Parameter annotated with @{} is found at position {}", argAnnotation.getSimpleName(), position);

        return arguments[position];
    }

    static <T extends Annotation> int getAnnotatedArgPosition(Method method,
                                                              Class<?> targetClass,
                                                              Class<T> argAnnotation) throws NoSuchMethodException {
        Annotation[][] parameterAnnotations = getArgAnnotations(method, targetClass);
        List<Integer> positions = getAnnotatedArgPositions(parameterAnnotations, argAnnotation);

        int count = positions.size();

        if (count != 1) {
            throw new IncorrectAnnotationException(argAnnotation.getSimpleName(), method.toString(), count);
        }

        return positions.get(0);
    }

    static Annotation[][] getArgAnnotations(Method method, Class<?> targetClass) throws NoSuchMethodException {
        String methodName = method.getName();
        Class<?>[] parameterTypes = method.getParameterTypes();

        return targetClass
                .getMethod(methodName, parameterTypes)
                .getParameterAnnotations();
    }

    static List<Integer> getAnnotatedArgPositions(Annotation[][] parameterAnnotations, Class<? extends Annotation> annotationType) {
        List<Integer> result = new ArrayList<>();
        int i = 0;

        for (Annotation[] pa : parameterAnnotations) {
            for (Annotation a : pa) {
                if (a.annotationType() == annotationType) {
                    result.add(i);
                }
            }
            i++;
        }

        return result;
    }
}

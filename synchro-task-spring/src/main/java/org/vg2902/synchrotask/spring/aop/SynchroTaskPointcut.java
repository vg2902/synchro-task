/*
 * Copyright 2021-2024 vg2902.org
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

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.vg2902.synchrotask.core.exception.SynchroTaskException;
import org.vg2902.synchrotask.spring.SynchroTask;
import org.vg2902.synchrotask.spring.exception.IncorrectAnnotationException;

import java.lang.reflect.Method;

/**
 * {@link org.springframework.aop.Pointcut} implementation for {@link SynchroTask}-methods.
 */
@Slf4j
public class SynchroTaskPointcut extends StaticMethodMatcherPointcut {

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        if (!SynchroTaskAopUtils.isEligibleMethod(method))
            return false;

        try {
            SynchroTaskAopUtils.validateArgList(method, targetClass);
        } catch (IncorrectAnnotationException e) {
            throw e;
        } catch (Exception e) {
            throw new SynchroTaskException(e);
        }

        return true;
    }
}

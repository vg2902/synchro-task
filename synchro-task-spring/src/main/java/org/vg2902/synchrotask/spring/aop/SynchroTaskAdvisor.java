/*
 * Copyright 2021-2023 vg2902.org
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

import lombok.RequiredArgsConstructor;
import org.aopalliance.aop.Advice;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractPointcutAdvisor;
import org.vg2902.synchrotask.spring.SynchroTask;

/**
 * {@link org.springframework.aop.Advisor} implementation for {@link SynchroTask}-methods.
 */
@RequiredArgsConstructor
public class SynchroTaskAdvisor extends AbstractPointcutAdvisor {

    private final SynchroTaskAdvice synchroTaskAdvice;
    private final SynchroTaskPointcut synchroTaskPointcut;

    @Override
    public Pointcut getPointcut() {
        return synchroTaskPointcut;
    }

    @Override
    public Advice getAdvice() {
        return synchroTaskAdvice;
    }
}

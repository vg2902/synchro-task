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

import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.util.StringUtils;
import org.vg2902.synchrotask.core.api.LockTimeout;
import org.vg2902.synchrotask.core.api.SynchroTask;
import org.vg2902.synchrotask.core.api.SynchroTask.SynchroTaskBuilder;
import org.vg2902.synchrotask.core.api.SynchroTaskService;
import org.vg2902.synchrotask.core.utils.ThrowableTaskResult;
import org.vg2902.synchrotask.core.utils.ThrowableTaskUtils;
import org.vg2902.synchrotask.spring.TaskId;
import org.vg2902.synchrotask.spring.TaskName;

import java.lang.reflect.Method;

import static org.vg2902.synchrotask.core.api.LockTimeout.SYSTEM_DEFAULT;

/**
 * Implements the interceptor for {@link org.vg2902.synchrotask.spring.SynchroTask}-methods.
 */
@Slf4j
class SynchroTaskAdvice implements MethodInterceptor {

    @Autowired
    private ApplicationContext context;

    /**
     * ApplicationContext.getBean(Class aClass) method doesn't work properly in Spring versions before 3.1,
     * if there are more than one bean of the same type, even if one of them is annotated with @Primary.
     *
     * This is a workaround to identify the primary bean.
     */
    @Autowired(required = false)
    private SynchroTaskService defaultSynchroTaskService;

    @Override
    public Object invoke(MethodInvocation methodInvocation) throws Throwable {
        Method method = methodInvocation.getMethod();

        org.vg2902.synchrotask.spring.SynchroTask annotation = SynchroTaskAopUtils.getSynchroTaskAnnotation(method);
        log.debug("SynchroTask strategy: {}", annotation.onLock());

        LockTimeout lockTimeout = LockTimeout.of(annotation.lockTimeout());
        log.debug("SynchroTask lock timeout: {}", lockTimeout);

        boolean throwExceptionAfterTimeout = annotation.throwExceptionAfterTimeout();
        log.debug("SynchroTask throwExceptionAfterTimeout: {}", throwExceptionAfterTimeout);

        String serviceName = annotation.serviceName();
        log.debug("SynchroTask service name: {}", serviceName);

        SynchroTaskService service = getServiceBean(serviceName);
        log.debug("SynchroTaskService bean: {}", service);

        Object taskName = SynchroTaskAopUtils.getAnnotatedArgValue(methodInvocation, TaskName.class);
        log.debug("taskName value in SynchroTaskAdvice: {}", taskName);

        Object taskId = SynchroTaskAopUtils.getAnnotatedArgValue(methodInvocation, TaskId.class);
        log.debug("taskId value in SynchroTaskAdvice: {}", taskId);

        SynchroTaskBuilder<ThrowableTaskResult<Object>> builder = SynchroTask
                .from(ThrowableTaskUtils.getSupplier(methodInvocation::proceed))
                .withName(taskName)
                .withId(taskId);

        if (lockTimeout != SYSTEM_DEFAULT || !throwExceptionAfterTimeout) {
            log.debug("Applying lock timeout settings");

            builder
                    .withLockTimeout(lockTimeout)
                    .throwExceptionAfterTimeout(throwExceptionAfterTimeout);
        } else {
            log.debug("Applying collision strategy settings");

            builder.onLock(annotation.onLock());
        }

        SynchroTask<ThrowableTaskResult<Object>> synchroTask = builder.build();

        ThrowableTaskResult<Object> result = service.run(synchroTask);

        if (result.isFailed())
            throw result.getException();

        return result.getResult();
    }

    private SynchroTaskService getServiceBean(String serviceName) {
        log.debug("Default SynchroTaskService bean: {}", defaultSynchroTaskService);

        if (StringUtils.hasText(serviceName))
            return context.getBean(serviceName, SynchroTaskService.class);
        else if (defaultSynchroTaskService != null)
            return defaultSynchroTaskService;
        else
            return context.getBean(SynchroTaskService.class);
    }
}


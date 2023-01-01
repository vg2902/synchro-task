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
package org.vg2902.synchrotask.spring;

import org.assertj.core.api.SoftAssertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.vg2902.synchrotask.core.api.LockTimeout;
import org.vg2902.synchrotask.spring.IncorrectlyAnnotatedSynchroTasks.SynchroTaskWithMultipleTaskId;
import org.vg2902.synchrotask.spring.IncorrectlyAnnotatedSynchroTasks.SynchroTaskWithoutTaskId;
import org.vg2902.synchrotask.spring.exception.IncorrectAnnotationException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_SINGLETON;
import static org.vg2902.synchrotask.core.api.LockTimeout.MAX_SUPPORTED;
import static org.vg2902.synchrotask.core.api.LockTimeout.SYSTEM_DEFAULT;
import static org.vg2902.synchrotask.spring.aop.SynchroTaskConfiguration.ADVISOR_AUTO_PROXY_CREATOR_NAME;

/**
 * Test suite for Spring extensions.
 */
@RunWith(SpringJUnit4ClassRunner.class)
/*
 * Since {@link ContextConfiguration#classes()} is supported only from Spring 3.1, XML-based configuration is used.
 */
@ContextConfiguration(locations = {"classpath:test-context.xml"})
public class SynchroTaskSpringTest {

    @Autowired
    private ApplicationContext ctx;

    @Autowired
    @Qualifier(ADVISOR_AUTO_PROXY_CREATOR_NAME)
    private DefaultAdvisorAutoProxyCreator synchroTaskAdvisorProxyCreator;

    @Autowired
    @Qualifier("service1")
    private TestService service1;

    @Autowired
    @Qualifier("service2")
    private TestService service2;

    @Autowired
    private TestRunner testRunner;

    @Test
    public void waitingTask() {
        String result = testRunner.waitingTask("wait");
        org.vg2902.synchrotask.core.api.SynchroTask<?> lastTask = service1.getLastTask();

        SoftAssertions assertions = new SoftAssertions();
        assertions.assertThat(result).isEqualTo("waitingTask:wait");
        assertTask(assertions, lastTask, "wait", LockTimeout.SYSTEM_DEFAULT, true);
        assertions.assertAll();
    }

    @Test
    public void throwingTask() {
        String result = testRunner.throwingTask("throw");
        org.vg2902.synchrotask.core.api.SynchroTask<?> lastTask = service1.getLastTask();

        SoftAssertions assertions = new SoftAssertions();
        assertions.assertThat(result).isEqualTo("throwingTask:throw");
        assertTask(assertions, lastTask, "throw", LockTimeout.of(0), true);
        assertions.assertAll();
    }

    @Test
    public void returningTask() {
        String result = testRunner.returningTask("return");
        org.vg2902.synchrotask.core.api.SynchroTask<?> lastTask = service1.getLastTask();

        SoftAssertions assertions = new SoftAssertions();
        assertions.assertThat(result).isEqualTo("returningTask:return");
        assertTask(assertions, lastTask, "return", LockTimeout.of(0), false);
        assertions.assertAll();
    }

    @Test
    public void defaultService() {
        String result = testRunner.defaultTask("default");
        org.vg2902.synchrotask.core.api.SynchroTask<?> lastTask = service1.getLastTask();

        SoftAssertions assertions = new SoftAssertions();
        assertions.assertThat(result).isEqualTo("defaultTask:default");
        assertTask(assertions, lastTask, "default", LockTimeout.SYSTEM_DEFAULT, true);
        assertions.assertAll();
    }

    @Test
    public void failingTask() {
        SoftAssertions assertions = new SoftAssertions();
        assertions.assertThatThrownBy(() -> testRunner.failingTask("fail")).isInstanceOf(TestException.class);
        org.vg2902.synchrotask.core.api.SynchroTask<?> lastTask = service1.getLastTask();
        assertTask(assertions, lastTask, "fail", LockTimeout.SYSTEM_DEFAULT, true);
        assertions.assertAll();
    }

    @Test
    public void taskWithService1() {
        String result = testRunner.taskWithService1("default1");
        org.vg2902.synchrotask.core.api.SynchroTask<?> lastTask = service1.getLastTask();

        SoftAssertions assertions = new SoftAssertions();
        assertions.assertThat(result).isEqualTo("defaultTaskWithService1:default1");
        assertTask(assertions, lastTask, "default1", LockTimeout.SYSTEM_DEFAULT, true);
        assertions.assertAll();
    }

    @Test
    public void taskWithService2() {
        String result = testRunner.taskWithService2("default2");
        org.vg2902.synchrotask.core.api.SynchroTask<?> lastTask = service2.getLastTask();

        SoftAssertions assertions = new SoftAssertions();
        assertions.assertThat(result).isEqualTo("defaultTaskWithService2:default2");
        assertTask(assertions, lastTask, "default2", LockTimeout.SYSTEM_DEFAULT, true);
        assertions.assertAll();
    }

    @Test
    public void noLockTimeoutTask() {
        String result = testRunner.noLockTimeoutTask("noLock");
        org.vg2902.synchrotask.core.api.SynchroTask<?> lastTask = service1.getLastTask();

        SoftAssertions assertions = new SoftAssertions();
        assertions.assertThat(result).isEqualTo("noLockTimeoutTask:noLock");
        assertTask(assertions, lastTask, "noLock", LockTimeout.of(0), true);
        assertions.assertAll();
    }

    @Test
    public void defaultLockTimeoutTask() {
        String result = testRunner.defaultLockTimeoutTask("defaultLock");
        org.vg2902.synchrotask.core.api.SynchroTask<?> lastTask = service1.getLastTask();

        SoftAssertions assertions = new SoftAssertions();
        assertions.assertThat(result).isEqualTo("defaultLockTimeoutTask:defaultLock");
        assertTask(assertions, lastTask, "defaultLock", SYSTEM_DEFAULT, true);
        assertions.assertAll();
    }

    @Test
    public void maxSupportedLockTimeoutTask() {
        String result = testRunner.maxSupportedLockTimeoutTask("maxSupportedLock");
        org.vg2902.synchrotask.core.api.SynchroTask<?> lastTask = service1.getLastTask();

        SoftAssertions assertions = new SoftAssertions();
        assertions.assertThat(result).isEqualTo("maxSupportedLockTimeoutTask:maxSupportedLock");
        assertTask(assertions, lastTask, "maxSupportedLock", MAX_SUPPORTED, true);
        assertions.assertAll();
    }

    @Test
    public void customLockTimeoutTask() {
        String result = testRunner.customLockTimeoutTask("customLock");
        org.vg2902.synchrotask.core.api.SynchroTask<?> lastTask = service1.getLastTask();

        SoftAssertions assertions = new SoftAssertions();
        assertions.assertThat(result).isEqualTo("customLockTimeoutTask:customLock");
        assertTask(assertions, lastTask, "customLock", LockTimeout.of(20000), true);
        assertions.assertAll();
    }

    @Test
    public void returningTimeoutTask() {
        String result = testRunner.returningTimeoutTask("returningWithTimeout");
        org.vg2902.synchrotask.core.api.SynchroTask<?> lastTask = service1.getLastTask();

        SoftAssertions assertions = new SoftAssertions();
        assertions.assertThat(result).isEqualTo("returningTimeoutTask:returningWithTimeout");
        assertTask(assertions, lastTask, "returningWithTimeout", LockTimeout.MAX_SUPPORTED, false);
        assertions.assertAll();
    }

    private void assertTask(SoftAssertions assertions,
                            org.vg2902.synchrotask.core.api.SynchroTask<?> task,
                            Object taskId,
                            LockTimeout lockTimeout,
                            boolean throwExceptionAfterTimeout) {
        assertions.assertThat(task.getTaskId()).isEqualTo(taskId);
        assertions.assertThat(task.getLockTimeout()).isEqualTo(lockTimeout);
        assertions.assertThat(task.isThrowExceptionAfterTimeout()).isEqualTo(throwExceptionAfterTimeout);
    }

    @Test
    public void doesNotAcceptSynchroTaskWithoutTaskId() {
        Class<SynchroTaskWithoutTaskId> beanClass = SynchroTaskWithoutTaskId.class;
        loadSingletonBean("SynchroTaskWithoutTaskId", beanClass);

        assertThatThrownBy(() -> ctx.getBean(beanClass))
                .hasCause(new IncorrectAnnotationException(
                        "TaskId",
                        "public void org.vg2902.synchrotask.spring.IncorrectlyAnnotatedSynchroTasks$SynchroTaskWithoutTaskId.task(int)",
                        0));
    }

    @Test
    public void doesNotAcceptSynchroTaskWithMultipleTaskId() {
        Class<SynchroTaskWithMultipleTaskId> beanClass = SynchroTaskWithMultipleTaskId.class;
        loadSingletonBean("SynchroTaskWithMultipleTaskId", beanClass);

        assertThatThrownBy(() -> ctx.getBean(beanClass))
                .hasCause(new IncorrectAnnotationException(
                        "TaskId",
                        "public void org.vg2902.synchrotask.spring.IncorrectlyAnnotatedSynchroTasks$SynchroTaskWithMultipleTaskId.task(java.lang.String,int)",
                        2));
    }

    private void loadSingletonBean(String beanName, Class<?> beanClass) {
        ConfigurableListableBeanFactory beanFactory = ((ConfigurableApplicationContext) ctx).getBeanFactory();
        BeanDefinitionRegistry beanDefinitionRegistry = (BeanDefinitionRegistry) beanFactory;

        BeanDefinition beanDefinition = BeanDefinitionBuilder.rootBeanDefinition(beanClass)
                .setScope(SCOPE_SINGLETON)
                .getBeanDefinition();

        beanDefinitionRegistry.registerBeanDefinition(beanName, beanDefinition);
    }
}

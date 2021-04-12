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
import org.vg2902.synchrotask.core.api.CollisionStrategy;
import org.vg2902.synchrotask.spring.IncorrectlyAnnotatedSynchroTasks.SynchroTaskWithMultipleTaskId;
import org.vg2902.synchrotask.spring.IncorrectlyAnnotatedSynchroTasks.SynchroTaskWithMultipleTaskName;
import org.vg2902.synchrotask.spring.IncorrectlyAnnotatedSynchroTasks.SynchroTaskWithoutTaskId;
import org.vg2902.synchrotask.spring.IncorrectlyAnnotatedSynchroTasks.SynchroTaskWithoutTaskName;
import org.vg2902.synchrotask.spring.exception.IncorrectAnnotationException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_SINGLETON;
import static org.vg2902.synchrotask.core.api.CollisionStrategy.RETURN;
import static org.vg2902.synchrotask.core.api.CollisionStrategy.THROW;
import static org.vg2902.synchrotask.core.api.CollisionStrategy.WAIT;
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
        String result = testRunner.waitingTask("wait", 1);
        org.vg2902.synchrotask.core.api.SynchroTask<?> lastTask = service1.getLastTask();

        SoftAssertions assertions = new SoftAssertions();
        assertions.assertThat(result).isEqualTo("waitingTask:wait:1");
        assertTask(assertions, lastTask, "wait", 1, WAIT);
        assertions.assertAll();
    }

    @Test
    public void throwingTask() {
        String result = testRunner.throwingTask("throw", 2);
        org.vg2902.synchrotask.core.api.SynchroTask<?> lastTask = service1.getLastTask();

        SoftAssertions assertions = new SoftAssertions();
        assertions.assertThat(result).isEqualTo("throwingTask:throw:2");
        assertTask(assertions, lastTask, "throw", 2, THROW);
        assertions.assertAll();
    }

    @Test
    public void returningTask() {
        String result = testRunner.returningTask("return", 3);
        org.vg2902.synchrotask.core.api.SynchroTask<?> lastTask = service1.getLastTask();

        SoftAssertions assertions = new SoftAssertions();
        assertions.assertThat(result).isEqualTo("returningTask:return:3");
        assertTask(assertions, lastTask, "return", 3, RETURN);
        assertions.assertAll();
    }

    @Test
    public void defaultService() {
        String result = testRunner.defaultTask("default", 4);
        org.vg2902.synchrotask.core.api.SynchroTask<?> lastTask = service1.getLastTask();

        SoftAssertions assertions = new SoftAssertions();
        assertions.assertThat(result).isEqualTo("defaultTask:default:4");
        assertTask(assertions, lastTask, "default", 4, WAIT);
        assertions.assertAll();
    }

    @Test
    public void failingTask() {
        SoftAssertions assertions = new SoftAssertions();
        assertions.assertThatThrownBy(() -> testRunner.failingTask("fail", 5)).isInstanceOf(TestException.class);
        org.vg2902.synchrotask.core.api.SynchroTask<?> lastTask = service1.getLastTask();
        assertTask(assertions, lastTask, "fail", 5, WAIT);
        assertions.assertAll();
    }

    @Test
    public void taskWithService1() {
        String result = testRunner.taskWithService1("default1", 6);
        org.vg2902.synchrotask.core.api.SynchroTask<?> lastTask = service1.getLastTask();

        SoftAssertions assertions = new SoftAssertions();
        assertions.assertThat(result).isEqualTo("defaultTaskWithService1:default1:6");
        assertTask(assertions, lastTask, "default1", 6, WAIT);
        assertions.assertAll();
    }

    @Test
    public void taskWithService2() {
        String result = testRunner.taskWithService2("default2", 7);
        org.vg2902.synchrotask.core.api.SynchroTask<?> lastTask = service2.getLastTask();

        SoftAssertions assertions = new SoftAssertions();
        assertions.assertThat(result).isEqualTo("defaultTaskWithService2:default2:7");
        assertTask(assertions, lastTask, "default2", 7, WAIT);
        assertions.assertAll();
    }

    private void assertTask(SoftAssertions assertions,
                            org.vg2902.synchrotask.core.api.SynchroTask<?> task,
                            Object taskName,
                            Object taskId,
                            CollisionStrategy strategy) {
        assertions.assertThat(task.getTaskName()).isEqualTo(taskName);
        assertions.assertThat(task.getTaskId()).isEqualTo(taskId);
        assertions.assertThat(task.getCollisionStrategy()).isEqualTo(strategy);
    }

    @Test
    public void doesNotAcceptSynchroTaskWithoutTaskName() {
        Class<SynchroTaskWithoutTaskName> beanClass = SynchroTaskWithoutTaskName.class;
        loadSingletonBean("SynchroTaskWithoutTaskName", beanClass);

        assertThatThrownBy(() -> ctx.getBean(beanClass))
                .hasCause(new IncorrectAnnotationException(
                        "TaskName",
                        "public void org.vg2902.synchrotask.spring.IncorrectlyAnnotatedSynchroTasks$SynchroTaskWithoutTaskName.task(java.lang.String,int)",
                        0));
    }

    @Test
    public void doesNotAcceptSynchroTaskWithoutTaskId() {
        Class<SynchroTaskWithoutTaskId> beanClass = SynchroTaskWithoutTaskId.class;
        loadSingletonBean("SynchroTaskWithoutTaskId", beanClass);

        assertThatThrownBy(() -> ctx.getBean(beanClass))
                .hasCause(new IncorrectAnnotationException(
                        "TaskId",
                        "public void org.vg2902.synchrotask.spring.IncorrectlyAnnotatedSynchroTasks$SynchroTaskWithoutTaskId.task(java.lang.String,int)",
                        0));
    }

    @Test
    public void doesNotAcceptSynchroTaskWithMultipleTaskName() {
        Class<SynchroTaskWithMultipleTaskName> beanClass = SynchroTaskWithMultipleTaskName.class;
        loadSingletonBean("SynchroTaskWithMultipleTaskName", beanClass);

        assertThatThrownBy(() -> ctx.getBean(beanClass))
                .hasCause(new IncorrectAnnotationException(
                        "TaskName",
                        "public void org.vg2902.synchrotask.spring.IncorrectlyAnnotatedSynchroTasks$SynchroTaskWithMultipleTaskName.task(java.lang.String,java.lang.String,int)",
                        2));
    }

    @Test
    public void doesNotAcceptSynchroTaskWithMultipleTaskId() {
        Class<SynchroTaskWithMultipleTaskId> beanClass = SynchroTaskWithMultipleTaskId.class;
        loadSingletonBean("SynchroTaskWithMultipleTaskId", beanClass);

        assertThatThrownBy(() -> ctx.getBean(beanClass))
                .hasCause(new IncorrectAnnotationException(
                        "TaskId",
                        "public void org.vg2902.synchrotask.spring.IncorrectlyAnnotatedSynchroTasks$SynchroTaskWithMultipleTaskId.task(java.lang.String,java.lang.String,int)",
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

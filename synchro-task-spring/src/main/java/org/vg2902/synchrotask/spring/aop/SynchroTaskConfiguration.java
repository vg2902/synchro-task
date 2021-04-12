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

import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Defines essential beans for <b>SynchroTask</b> support.
 */
@Configuration
public class SynchroTaskConfiguration {

    public static final String ADVISOR_NAME = "synchroTaskAdvisor";
    public static final String ADVISOR_AUTO_PROXY_CREATOR_NAME = "synchroTaskAdvisorAutoProxyCreator";

    @Bean
    public SynchroTaskAdvice synchroTaskAdvice() {
        return new SynchroTaskAdvice();
    }

    @Bean
    public SynchroTaskPointcut synchroTaskPointcut() {
        return new SynchroTaskPointcut();
    }

    @Bean(ADVISOR_NAME)
    public SynchroTaskAdvisor synchroTaskAdvisor(SynchroTaskAdvice advice, SynchroTaskPointcut pointcut) {
        return new SynchroTaskAdvisor(advice, pointcut);
    }

    @Bean(ADVISOR_AUTO_PROXY_CREATOR_NAME)
    public DefaultAdvisorAutoProxyCreator synchroTaskAdvisorAutoProxyCreator() {
        DefaultAdvisorAutoProxyCreator autoProxyCreator = new DefaultAdvisorAutoProxyCreator();
        autoProxyCreator.setUsePrefix(true);
        autoProxyCreator.setAdvisorBeanNamePrefix(ADVISOR_NAME);
        return autoProxyCreator;
    }
}

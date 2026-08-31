package org.uengine.five.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Spring 컨텍스트 refresh 이전에 IAM 구현체를 등록한다.
 * {@code RuleRoleResolutionService} 의 @PostConstruct 와 같은 Registry 패턴이며,
 * {@code fill()} 이 빈 생성 중 호출되어도 provider 를 찾을 수 있게 한다.
 */
public class IAMServiceInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        IAMServiceRegister.registerAll();
    }
}

package org.uengine.five.messaging.kafka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * {@code uengine.messaging.mode=kafka} (또는 미지정) 일 때 {@code messaging-kafka} Spring
 * Profile 을 자동 활성화한다. 활성화된 프로파일에서 {@code application.yml} 의 Spring Cloud
 * Stream + Kafka 바인더 설정 블록이 로드되어 Kafka 인프라가 구성된다.
 *
 * <p>폴링 모드({@code mode=polling})에서는 프로파일이 활성화되지 않아 SCS Kafka 자체가
 * 로드되지 않는다 → {@code StreamBridge} bean 미생성, Kafka 연결 시도 0건.
 *
 * <p>등록은 {@code META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports}
 * 파일을 통해 이루어진다 (Spring Boot 2.7+ 권장 방식).
 *
 * <p>이 클래스는 Strategy 패턴을 유지하기 위해 messaging/kafka 패키지 안에 위치한다.
 * 도메인 코드와 Application 메인 클래스는 messaging 전략 활성화 매커니즘을 모르며,
 * 각 전략 패키지가 자기 활성화를 책임진다.
 */
public class KafkaProfileActivator implements EnvironmentPostProcessor {

    private static final String MODE_PROPERTY = "uengine.messaging.mode";
    private static final String DEFAULT_MODE = "kafka";
    private static final String KAFKA_PROFILE = "messaging-kafka";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String mode = environment.getProperty(MODE_PROPERTY, DEFAULT_MODE);
        if (DEFAULT_MODE.equalsIgnoreCase(mode)) {
            environment.addActiveProfile(KAFKA_PROFILE);
        }
    }
}

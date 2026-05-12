package org.uengine.five;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
// import org.springframework.boot.builder.SpringApplicationBuilder; // WAR
// import org.springframework.boot.web.servlet.support.SpringBootServletInitializer; // WAR
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.EnableAsync;
import org.uengine.five.overriding.ActivityQueue;
import org.uengine.five.overriding.EventMappingDeployFilter;
// import org.uengine.five.service.HfcIAMService;
import org.uengine.five.service.IAMCompanyRoleMapping;
import org.uengine.five.service.IAMServiceFactory;
import org.uengine.five.service.KeycloakIAMService;
import org.uengine.five.overriding.InstanceNameFilter;
import org.uengine.five.overriding.PayloadFilter;
import org.uengine.five.overriding.ServiceRegisterDeployFilter;
import org.uengine.five.overriding.SpringComponentFactory;
import org.uengine.five.service.DefinitionServiceUtil;
import org.uengine.five.service.LocalFileDefinitionServiceUtil;
import org.uengine.kernel.DeployFilter;
import org.uengine.kernel.GlobalContext;

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootApplication
@EnableAsync
@EnableFeignClients
@ComponentScan(basePackages = { "org.uengine.kernel.bpmn", "org.uengine.five" })
// WAR 배포 시: 아래처럼 SpringBootServletInitializer 상속 후 configure() 오버라이드 (import: SpringApplicationBuilder, SpringBootServletInitializer)
// public class ProcessServiceApplication extends SpringBootServletInitializer {
//     @Override
//     protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
//         return builder.sources(ProcessServiceApplication.class);
//     }
public class ProcessServiceApplication {

    public static ApplicationContext applicationContext;

    public static ObjectMapper objectMapper = createTypedJsonObjectMapper();

    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public static void main(String[] args) {
        // IAMService 구현체는 Spring 부팅 전에 등록
        // Spring 빈 초기화 중 IAMServiceFactory.getDefault()가 호출될 수 있으므로
        // SpringApplication.run() 보다 먼저 실행해야 합니다.
        IAMServiceFactory.register("keycloak", KeycloakIAMService.getDefault());
        // IAMServiceFactory.register("hfc", HfcIAMService.getDefault());

        applicationContext = SpringApplication.run(ProcessServiceApplication.class, args);
        GlobalContext.setComponentFactory(new SpringComponentFactory());
        // rolemapping.class, iam.provider는 uengine.properties에서 관리합니다.
        // (process-service/src/main/resources/org/uengine/uengine.properties)
    }

    /**
     * RoleMapping 구현체를 Application에서 prototype으로 등록.
     * RoleMapping.create() -> GlobalContext.componentFactory 경유 시 매번 새 인스턴스를 받습니다.
     */
    @Bean
    @Scope("prototype")
    public IAMCompanyRoleMapping roleMapping() {
        return new IAMCompanyRoleMapping();
    }

    // @EventListener(ApplicationReadyEvent.class)
    // public void onApplicationEvent() {
    // // 트랜잭션 동기화 로직을 여기서 처리
    // if (TransactionSynchronizationManager.isSynchronizationActive()) {
    // TransactionSynchronizationManager.registerSynchronization(
    // new TransactionSynchronizationAdapter() {
    // @Override
    // public void afterCompletion(int status) {
    // System.out.println("afterCompletion");
    // }
    // });
    // } else {
    // System.out.println("트랜잭션 동기화가 활성화되지 않았습니다.");
    // }
    // }

    // @Bean
    // public ServiceRegisterDeployFilter serviceRegisterDeployFilter() {
    // return new ServiceRegisterDeployFilter();
    // }

    @Bean
    public DeployFilter serviceRegisterDeployFilter() {
        return new ServiceRegisterDeployFilter();
    }

    @Bean
    public DeployFilter eventMappingDeployFilter() {
        return new EventMappingDeployFilter();
    }

    @Bean
    public InstanceNameFilter instanceNameFilter() {
        return new InstanceNameFilter();
    }

    @Value("${filter.payload.enabled:true}")
    private boolean isPayloadFilterEnabled;

    @Bean
    @ConditionalOnProperty(name = "filter.payload.enabled", havingValue = "true", matchIfMissing = true)
    public PayloadFilter payloadFilter() {
        return new PayloadFilter();
    }

    @Bean
    public ActivityQueue activityQueue(org.uengine.five.messaging.EventPublisher eventPublisher) {
        return new ActivityQueue(eventPublisher);
    }

    @Bean
    @Primary
    public DefinitionServiceUtil definitionServiceUtil() {
        // return new SupabaseDefinitionServiceUtil();
        return new LocalFileDefinitionServiceUtil();
    }

    public static ObjectMapper createTypedJsonObjectMapper() {
        return org.uengine.five.messaging.TypedJsonObjectMapperFactory.create();
    }

}
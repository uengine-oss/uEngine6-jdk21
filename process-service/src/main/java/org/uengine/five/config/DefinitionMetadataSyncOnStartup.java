package org.uengine.five.config;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.uengine.five.service.InstanceServiceImpl;

/**
 * 애플리케이션 기동 및 컨텍스트 로딩이 끝난 뒤, 정의 메타데이터(이벤트 매핑·서비스 엔드포인트 등) 전체 동기화를 한 번 백그라운드로 실행합니다.
 */
@Component
@ConditionalOnProperty(name = "uengine.definition.sync-on-startup.enabled", havingValue = "true")
public class DefinitionMetadataSyncOnStartup {

    private static final Logger log = LoggerFactory.getLogger(DefinitionMetadataSyncOnStartup.class);

    private final InstanceServiceImpl instanceService;
    private final boolean clearAllEventMappings;

    public DefinitionMetadataSyncOnStartup(InstanceServiceImpl instanceService,
            @Value("${uengine.definition.sync-on-startup.clear-all-event-mappings:false}") boolean clearAllEventMappings) {
        this.instanceService = instanceService;
        this.clearAllEventMappings = clearAllEventMappings;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void syncDefinitionsAfterReady() {
        try {
            Map<String, Object> result = instanceService.syncAllDefinitionChanges(clearAllEventMappings);
            log.info(
                    "Startup definition metadata sync finished: clearAllEventMappings={}, total={}, success={}, failed={}, failedPaths={}",
                    result.get("clearAllEventMappings"),
                    result.get("totalDefinitionCount"),
                    result.get("successCount"),
                    result.get("failedCount"),
                    result.get("failedPaths"));
        } catch (Exception e) {
            log.error("Startup definition metadata sync failed", e);
        }
    }
}

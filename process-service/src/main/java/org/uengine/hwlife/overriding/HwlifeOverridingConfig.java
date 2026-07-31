package org.uengine.hwlife.overriding;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.uengine.kernel.ActivityFilter;

@Configuration
public class HwlifeOverridingConfig {

    /**
     * ESB header → init_ep / init_group_cd.
     * {@code GlobalContext.getComponents(ActivityFilter.class)} 에 포함되려면 빈 등록 필요.
     */
    @Bean
    public ActivityFilter esbInitiatorActivityFilter() {
        return new EsbInitiatorActivityFilter();
    }
}

package org.uengine.five;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import org.uengine.five.overriding.SpringComponentFactory;
import org.uengine.kernel.GlobalContext;

@Component
public class GlobalContextSpringBridge implements ApplicationContextAware {

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        ProcessServiceApplication.applicationContext = applicationContext;
        GlobalContext.setComponentFactory(new SpringComponentFactory());
    }
}

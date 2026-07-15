package org.uengine.five.messaging.polling;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.uengine.kernel.DefaultComponentFactory;
import org.uengine.kernel.GlobalContext;
import org.uengine.kernel.IComponentFactory;

class InboxQuartzJobTest {

    @AfterEach
    void resetGlobalContext() {
        GlobalContext.setComponentFactory(new DefaultComponentFactory());
    }

    @Test
    void inboxPollJobDelegatesToSpringManagedService() throws Exception {
        InboxPollService service = mock(InboxPollService.class);
        GlobalContext.setComponentFactory(singletonFactory(InboxPollService.class, service));

        new InboxPollJob().execute(null);

        verify(service).runBatch();
    }

    @Test
    void directRunBatchAlsoDelegatesToSpringManagedService() {
        InboxPollService service = mock(InboxPollService.class);
        GlobalContext.setComponentFactory(singletonFactory(InboxPollService.class, service));

        new InboxPollJob().runBatch();

        verify(service).runBatch();
    }

    @Test
    void ttlCleanupJobDelegatesToSpringManagedService() throws Exception {
        InboxTtlCleanupService service = mock(InboxTtlCleanupService.class);
        GlobalContext.setComponentFactory(singletonFactory(InboxTtlCleanupService.class, service));

        new InboxTtlCleanupQuartzJob().execute(null);

        verify(service).cleanup();
    }

    @Test
    void directCleanupAlsoDelegatesToSpringManagedService() {
        InboxTtlCleanupService service = mock(InboxTtlCleanupService.class);
        GlobalContext.setComponentFactory(singletonFactory(InboxTtlCleanupService.class, service));

        new InboxTtlCleanupQuartzJob().cleanup();

        verify(service).cleanup();
    }

    private static <T> IComponentFactory singletonFactory(Class<T> type, T instance) {
        return new IComponentFactory() {
            @Override
            public <C> C getComponent(Class<C> clazz, Object[] constructorParameters) {
                if (clazz.equals(type)) {
                    return clazz.cast(instance);
                }
                throw new IllegalArgumentException("Unexpected component: " + clazz.getName());
            }

            @Override
            public <C> Map<String, C> getComponents(Class<C> clazz, Object[] constructorParameters) {
                return Map.of();
            }
        };
    }
}

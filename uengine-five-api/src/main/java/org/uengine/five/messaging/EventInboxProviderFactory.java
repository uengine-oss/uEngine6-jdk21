package org.uengine.five.messaging;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.uengine.kernel.GlobalContext;
import org.uengine.util.UEngineUtil;

/**
 * EventInboxProvider 정적 접근 팩토리 (Registry 패턴).
 *
 * <p>애플리케이션 기동 시 {@link #register(String, EventInboxProvider)} 로 구현체를 등록한 뒤
 * {@link #getDefault()} / {@link #get(String)} 으로 선택한다.</p>
 *
 * <p>선택 기준 (getDefault 호출 시):</p>
 * <ul>
 *   <li>환경변수: EVENT_INBOX_PROVIDER</li>
 *   <li>GlobalContext property: event-inbox.provider</li>
 *   <li>기본값: default</li>
 * </ul>
 */
public final class EventInboxProviderFactory {

    private static final String DEFAULT_PROVIDER_ID = "default";

    private static final Map<String, EventInboxProvider> REGISTRY = new ConcurrentHashMap<>();

    private EventInboxProviderFactory() {
    }

    public static void register(String providerId, EventInboxProvider provider) {
        if (providerId == null || provider == null) {
            return;
        }
        REGISTRY.put(providerId.trim().toLowerCase(), provider);
    }

    public static EventInboxProvider getDefault() {
        String providerId = System.getenv("EVENT_INBOX_PROVIDER");
        if (!UEngineUtil.isNotEmpty(providerId)) {
            providerId = GlobalContext.getPropertyString("event-inbox.provider", DEFAULT_PROVIDER_ID);
        }
        return get(providerId);
    }

    public static EventInboxProvider get(String providerId) {
        final String normalized = UEngineUtil.isNotEmpty(providerId)
                ? providerId.trim().toLowerCase()
                : DEFAULT_PROVIDER_ID;
        EventInboxProvider provider = REGISTRY.get(normalized);
        if (provider == null) {
            throw new IllegalStateException(
                    "No EventInboxProvider registered for provider '" + normalized + "'. " +
                    "Call EventInboxProviderFactory.register(providerId, provider) at application startup.");
        }
        return provider;
    }
}

package org.uengine.hwlife.rule;

/**
 * {@link RuleResolutionService} 정적 접근 지점.
 *
 * <p>{@code RuleBasedResolutionContext} 는 프로세스 정의에 직렬화되는 POJO 라 Spring DI 를
 * 받을 수 없다. 그래서 {@code IAMServiceFactory} 와 동일한 Registry 패턴으로, 기동 시점에
 * 등록된 서비스 빈을 런타임에 정적으로 끌어다 쓴다.</p>
 *
 * <p>등록은 {@link RuleResolutionService#register()}(@PostConstruct)에서 자동 수행된다.</p>
 */
public final class RuleResolutionSupport {

    private static volatile RuleResolutionService instance;

    private RuleResolutionSupport() {
    }

    public static void register(RuleResolutionService service) {
        instance = service;
    }

    public static RuleResolutionService get() {
        RuleResolutionService s = instance;
        if (s == null) {
            throw new IllegalStateException(
                    "RuleResolutionService 가 아직 등록되지 않았습니다. " +
                    "(RuleResolutionService 빈 생성/@PostConstruct register 여부 및 컴포넌트 스캔 확인)");
        }
        return s;
    }
}

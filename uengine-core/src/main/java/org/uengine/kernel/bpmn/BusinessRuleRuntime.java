package org.uengine.kernel.bpmn;

import java.util.Map;

/**
 * SPI for evaluating a business rule (DMN/JSON) at runtime.
 *
 * <p>
 * Implementation lives in the process-service layer (Spring-managed) and is
 * looked up via {@code GlobalContext.getComponent(BusinessRuleRuntime.class)}.
 * This indirection keeps {@link BusinessRuleTask} free of process-service
 * dependencies.
 * </p>
 */
public interface BusinessRuleRuntime {

    Map<String, Object> evaluate(String ruleId, Map<String, Object> inputs) throws Exception;
}

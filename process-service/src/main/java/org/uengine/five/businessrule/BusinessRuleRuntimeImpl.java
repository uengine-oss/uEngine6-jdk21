package org.uengine.five.businessrule;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.uengine.kernel.bpmn.BusinessRuleRuntime;

/**
 * {@link BusinessRuleRuntime} 구현체. BusinessRuleTask가 커널 레이어에서
 * {@link org.uengine.kernel.GlobalContext#getComponent(Class)}로 조회하여 사용한다.
 */
@Component
public class BusinessRuleRuntimeImpl implements BusinessRuleRuntime {

    @Autowired
    BusinessRuleStore businessRuleStore;

    @Autowired
    BusinessRuleEvaluator businessRuleEvaluator;

    @Override
    public Map<String, Object> evaluate(String ruleId, Map<String, Object> inputs) throws Exception {
        BusinessRuleStore.BusinessRuleFile file = businessRuleStore.loadOrThrow(ruleId);
        return businessRuleEvaluator.evaluate(file.getRuleJson(), inputs);
    }
}

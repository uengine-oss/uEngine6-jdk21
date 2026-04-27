package org.uengine.five.messaging;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * BpmMessageDispatcher 와 동일한 ObjectMapper 설정을 공유하기 위한 팩토리.
 * 폴링 모드 publisher/poller 가 round-trip 역직렬화를 보장하려면 반드시 이 팩토리가 만든
 * ObjectMapper 로 직렬화해야 한다.
 */
public final class TypedJsonObjectMapperFactory {

    private TypedJsonObjectMapperFactory() {}

    public static ObjectMapper create() {
        ObjectMapper om = new ObjectMapper();
        om.setVisibilityChecker(om.getSerializationConfig()
                .getDefaultVisibilityChecker()
                .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withCreatorVisibility(JsonAutoDetect.Visibility.NONE));
        om.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        om.setSerializationInclusion(JsonInclude.Include.NON_DEFAULT);
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        om.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        om.enableDefaultTypingAsProperty(ObjectMapper.DefaultTyping.NON_CONCRETE_AND_ARRAYS, "_type");
        om.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        return om;
    }
}

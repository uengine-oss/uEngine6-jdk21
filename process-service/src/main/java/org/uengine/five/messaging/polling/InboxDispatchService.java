package org.uengine.five.messaging.polling;

import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.uengine.five.ProcessServiceApplication;
import org.uengine.five.messaging.EventInbox;
import org.uengine.five.stream.BpmMessageDispatcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class InboxDispatchService {

    private final BpmMessageDispatcher dispatcher;
    private final ObjectMapper objectMapper = ProcessServiceApplication.createTypedJsonObjectMapper();

    public InboxDispatchService(BpmMessageDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatchInNewTx(EventInbox ev) {
        dispatcher.dispatch(rebuildMessage(ev));
    }

    private Message<String> rebuildMessage(EventInbox ev) {
        MessageBuilder<String> builder = MessageBuilder.withPayload(dispatchPayload(ev.getPayload()));
        if (ev.getEventName() != null) {
            builder.setHeader("type", ev.getEventName());
        }
        if (ev.getCorrKey() != null) {
            builder.setHeader("corrKey", ev.getCorrKey());
        }
        return builder.build();
    }

    private String dispatchPayload(String storedPayload) {
        if (storedPayload == null || storedPayload.isBlank()) {
            return "{}";
        }
        try {
            JsonNode root = objectMapper.readTree(storedPayload);
            if (isEventRequestWrapper(root)) {
                JsonNode payload = root.get("payload");
                return payload == null || payload.isNull() ? storedPayload : objectMapper.writeValueAsString(payload);
            }
        } catch (Exception ignored) {
            return storedPayload;
        }
        return storedPayload;
    }

    private boolean isEventRequestWrapper(JsonNode root) {
        if (root == null || !root.isObject()) {
            return false;
        }
        return root.has("eventName") || root.has("eventname") || root.has("eventNm") || root.has("evntNm")
                || root.has("corrKey") || root.has("corrkey") || root.has("loanPcesMgmtNo")
                || root.has("prcrRsltCodeNm") || root.has("prcsRsltCodeNm") || root.has("prcsRsltCntn")
                || root.size() == 1;
    }
}

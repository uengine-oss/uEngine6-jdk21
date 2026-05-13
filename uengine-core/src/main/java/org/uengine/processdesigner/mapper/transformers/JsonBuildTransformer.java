package org.uengine.processdesigner.mapper.transformers;

import java.util.LinkedHashMap;
import java.util.Map;

import org.uengine.kernel.ProcessInstance;
import org.uengine.processdesigner.mapper.Transformer;

import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonBuildTransformer extends Transformer {

	private static final long serialVersionUID = org.uengine.kernel.GlobalContext.SERIALIZATION_UID;

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private static final int PAIR_COUNT = 5;

	public JsonBuildTransformer() {
		setName("JsonBuild");
	}

	@Override
	public String[] getInputArguments() {
		String[] args = new String[PAIR_COUNT * 2];
		for (int i = 0; i < PAIR_COUNT; i++) {
			args[i * 2] = "key" + (i + 1);
			args[i * 2 + 1] = "val" + (i + 1);
		}
		return args;
	}

	@Override
	public Object transform(ProcessInstance instance, Map parameterMap, Map options) throws Exception {
		Map<String, Object> result = new LinkedHashMap<>();
		for (int i = 1; i <= PAIR_COUNT; i++) {
			Object keyVal = parameterMap.get("key" + i);
			if (keyVal == null) continue;
			String key = keyVal.toString();
			if (key.isEmpty()) continue;
			result.put(key, parameterMap.get("val" + i));
		}
		return OBJECT_MAPPER.writeValueAsString(result);
	}
}

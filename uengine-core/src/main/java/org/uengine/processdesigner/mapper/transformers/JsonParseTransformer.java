package org.uengine.processdesigner.mapper.transformers;

import java.util.List;
import java.util.Map;

import org.uengine.kernel.ProcessInstance;
import org.uengine.processdesigner.mapper.Transformer;

import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonParseTransformer extends Transformer {

	private static final long serialVersionUID = org.uengine.kernel.GlobalContext.SERIALIZATION_UID;

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private String path;

	public JsonParseTransformer() {
		setName("JsonParse");
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	@Override
	public String[] getInputArguments() {
		return new String[] { "json" };
	}

	@Override
	public Object transform(ProcessInstance instance, Map parameterMap, Map options) throws Exception {
		Object input = parameterMap.get("json");
		if (input == null) return null;

		Object root;
		if (input instanceof String) {
			String text = ((String) input).trim();
			if (text.isEmpty()) return null;
			root = OBJECT_MAPPER.readValue(text, Object.class);
		} else {
			root = input;
		}

		if (path == null || path.trim().isEmpty()) return root;
		return walk(root, path.trim());
	}

	private Object walk(Object node, String expr) {
		if (expr.startsWith("$.")) expr = expr.substring(2);
		else if (expr.startsWith("$")) expr = expr.substring(1);

		for (String segment : expr.split("\\.")) {
			if (segment.isEmpty() || node == null) continue;

			int bracket = segment.indexOf('[');
			String key = bracket >= 0 ? segment.substring(0, bracket) : segment;

			if (!key.isEmpty()) {
				if (!(node instanceof Map)) return null;
				node = ((Map<?, ?>) node).get(key);
			}

			while (bracket >= 0 && node != null) {
				int end = segment.indexOf(']', bracket);
				if (end < 0) break;
				int index = Integer.parseInt(segment.substring(bracket + 1, end));
				if (!(node instanceof List)) return null;
				List<?> list = (List<?>) node;
				node = index >= 0 && index < list.size() ? list.get(index) : null;
				bracket = segment.indexOf('[', end);
			}
		}
		return node;
	}
}

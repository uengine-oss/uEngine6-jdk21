package org.uengine.five.businessrule;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.uengine.contexts.HtmlFormContext;
import org.uengine.kernel.bpmn.BusinessRuleRuntime;
import org.uengine.processmanager.ProcessTransactionContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** A private, transaction-lived snapshot of the supported DMN table subset. */
public final class SimulationBusinessRuleContext {
    private static final String DMN = "https://www.omg.org/spec/DMN/20191111/MODEL/";
    private static final Set<String> ELEMENTS = Set.of("definitions", "decision", "decisionTable", "input",
            "inputExpression", "output", "rule", "inputEntry", "outputEntry", "text", "annotation", "annotationEntry");
    private static final Set<String> FEEL = Set.of("feel", "http://www.omg.org/spec/FEEL/20140401",
            "https://www.omg.org/spec/DMN/20180521/FEEL/", "https://www.omg.org/spec/DMN/20191111/FEEL/");
    private static final Pattern PATH = Pattern.compile("[\\p{L}_][\\p{L}\\p{N}_ ]*(?:\\.[\\p{L}_][\\p{L}\\p{N}_ ]*)*");
    // FEEL names may contain spaces. Tokens which introduce expressions are not names
    // in this subset, even though each character could occur in an ordinary label.
    private static final Set<String> EXPRESSION_TOKENS = Set.of("if", "then", "else", "for", "return", "some",
            "every", "satisfies", "in", "and", "or", "not", "between", "instance", "of", "function", "external",
            "true", "false", "null");
    private static final Pattern RESERVED = Pattern.compile("(?i)(^|\\.)(class|classLoader|processDefinition|processInstanceEntity)(\\.|$)");
    private static final String SCALAR = "(?:null|true|false|-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?|\"(?:[^\"\\\\]|\\\\[\"\\\\nrt])*\")";
    private static final Pattern LITERAL = Pattern.compile(SCALAR);
    private static final Pattern UNARY = Pattern.compile("(?:-|(?:<=|>=|<|>)\\s*" + SCALAR + "|" + SCALAR + "|not\\(\\s*" + SCALAR + "\\s*\\))");

    private record Rule(JsonNode json, Set<String> roots) { }
    private final Map<String, Rule> rules;

    private SimulationBusinessRuleContext(Map<String, Rule> rules) {
        this.rules = Collections.unmodifiableMap(new LinkedHashMap<>(rules));
    }

    public static SimulationBusinessRuleContext empty() {
        return new SimulationBusinessRuleContext(Map.of());
    }

    public static SimulationBusinessRuleContext current(ProcessTransactionContext transaction) {
        if (transaction == null) return null;
        Object value = transaction.getSharedContext(BusinessRuleRuntime.SIMULATION_CONTEXT_KEY);
        if (value == null) return null;
        if (value.getClass() != SimulationBusinessRuleContext.class) throw failure("잘못된 서버 DMN 시험 문맥");
        return (SimulationBusinessRuleContext) value;
    }

    public void pin(ProcessTransactionContext transaction) {
        if (transaction == null) throw failure("DMN 시험 트랜잭션이 없습니다");
        transaction.setSharedContext(BusinessRuleRuntime.SIMULATION_CONTEXT_KEY, this);
    }

    public boolean contains(String ruleId) { return rules.containsKey(ruleId); }
    public boolean hasRules() { return !rules.isEmpty(); }

    public SimulationBusinessRuleContext withRule(String ruleId, JsonNode raw, BusinessRuleEvaluator evaluator) {
        if (contains(ruleId)) return this;
        if (raw == null || !raw.isObject() || raw.has("rules") || !raw.path("dmnXml").isTextual()) {
            throw failure("DMN 시험은 dmnXml만 있는 룰을 지원합니다: " + ruleId);
        }
        Set<String> roots = new LinkedHashSet<>();
        String xml = validateXml(raw.deepCopy().path("dmnXml").asText(), roots);
        JsonNode snapshot = JsonNodeFactory.instance.objectNode().put("dmnXml", xml);
        try {
            evaluator.validateDmnForSimulation(snapshot);
        } catch (Exception error) {
            throw failure("DMN 모델을 해석할 수 없습니다: " + ruleId);
        }
        Map<String, Rule> next = new LinkedHashMap<>(rules);
        next.put(ruleId, new Rule(snapshot, Set.copyOf(roots)));
        return new SimulationBusinessRuleContext(next);
    }

    public Map<String, Object> evaluate(String ruleId, Map<String, Object> inputs, BusinessRuleEvaluator evaluator) {
        Rule rule = rules.get(ruleId);
        if (rule == null) throw failure("사전검사한 DMN 룰이 없습니다: " + ruleId);
        Map<String, Object> projected = new LinkedHashMap<>();
        for (String root : rule.roots) {
            Object value = inputs == null ? null : inputs.get(root);
            if (value != null && value.getClass() == HtmlFormContext.class) value = ((HtmlFormContext) value).getValueMap();
            projected.put(root, plain(value, Collections.newSetFromMap(new IdentityHashMap<>()), 0));
        }
        try {
            return evaluator.evaluate(rule.json.deepCopy(), projected);
        } catch (ResponseStatusException error) {
            throw error;
        } catch (Exception error) {
            throw failure("DMN 시험 평가에 실패했습니다: " + ruleId);
        }
    }

    private static String validateXml(String xml, Set<String> roots) {
        if (xml.isBlank() || xml.getBytes(StandardCharsets.UTF_8).length > 1024 * 1024) {
            throw failure("DMN XML은 1MiB 이하여야 합니다");
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            if (!"definitions".equals(document.getDocumentElement().getLocalName())) throw failure("DMN definitions가 필요합니다");
            NodeList nodes = document.getElementsByTagName("*");
            Set<String> outputRoots = new LinkedHashSet<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                Element element = (Element) nodes.item(i);
                String name = element.getLocalName();
                if (!DMN.equals(element.getNamespaceURI()) || !ELEMENTS.contains(name)) {
                    throw failure("지원하지 않는 DMN 요소: " + name);
                }
                var attributes = element.getAttributes();
                for (int a = 0; a < attributes.getLength(); a++) {
                    Node attribute = attributes.item(a);
                    String namespace = attribute.getNamespaceURI();
                    if (namespace != null && !XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(namespace)) {
                        throw failure("DMN 확장 속성은 지원하지 않습니다");
                    }
                    if ("expressionLanguage".equals(attribute.getLocalName())
                            && !FEEL.contains(attribute.getNodeValue())) throw failure("DMN 시험 언어는 FEEL만 지원합니다");
                }
                if ("decisionTable".equals(name) && !"FIRST".equals(element.getAttribute("hitPolicy"))) {
                    throw failure("DMN 시험은 FIRST 표만 지원합니다");
                }
                if (Set.of("inputExpression", "inputEntry", "outputEntry").contains(name)) {
                    String expression = expression(element);
                    if (expression.length() > 1024) throw failure("DMN 식은 1024자 이하여야 합니다");
                    if ("inputExpression".equals(name)) {
                        inputPath(expression, roots);
                    } else if ("inputEntry".equals(name)) {
                        if (!UNARY.matcher(expression).matches()) throw failure("지원하지 않는 DMN 조건식");
                    } else if (!LITERAL.matcher(expression).matches()) inputPath(expression, outputRoots);
                    element.setAttribute("expressionLanguage", "feel");
                }
            }
            if (!roots.containsAll(outputRoots)) throw failure("DMN 출력 참조는 선언된 DMN 입력만 지원합니다");
            if (document.getElementsByTagNameNS(DMN, "decision").getLength() != 1
                    || document.getElementsByTagNameNS(DMN, "decisionTable").getLength() != 1) {
                throw failure("DMN 시험은 단일 decision/table만 지원합니다");
            }
            if (document.getElementsByTagNameNS(DMN, "rule").getLength() > 1000
                    || document.getElementsByTagNameNS(DMN, "input").getLength() > 64
                    || document.getElementsByTagNameNS(DMN, "output").getLength() > 64) {
                throw failure("DMN 시험 상한은 1000행, 입력/출력 각 64개입니다");
            }
            TransformerFactory transformers = TransformerFactory.newInstance();
            transformers.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            transformers.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            transformers.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            StringWriter writer = new StringWriter();
            transformers.newTransformer().transform(new DOMSource(document), new StreamResult(writer));
            return writer.toString();
        } catch (ResponseStatusException error) {
            throw error;
        } catch (Exception error) {
            throw failure("안전한 DMN XML을 해석할 수 없습니다");
        }
    }

    private static String expression(Element element) {
        NodeList texts = element.getElementsByTagNameNS(DMN, "text");
        if (texts.getLength() != 1 || texts.item(0).getParentNode() != element) throw failure("DMN 식의 text가 필요합니다");
        return texts.item(0).getTextContent().trim();
    }

    private static void inputPath(String value, Set<String> roots) {
        if (!PATH.matcher(value).matches() || RESERVED.matcher(value).find()) throw failure("지원하지 않는 DMN 입력/출력식");
        for (String token : value.split("[ .]+")) {
            if (EXPRESSION_TOKENS.contains(token)) throw failure("DMN 단순 변수 경로에는 제어/연산 토큰을 사용할 수 없습니다");
        }
        roots.add(value.split("\\.", 2)[0]);
    }

    private static Object plain(Object value, Set<Object> visited, int depth) {
        if (value == null || value.getClass() == String.class || value.getClass() == Boolean.class
                || value.getClass() == Integer.class || value.getClass() == Long.class || value.getClass() == Short.class
                || value.getClass() == Byte.class || value.getClass() == BigDecimal.class || value.getClass() == BigInteger.class) return value;
        if (value.getClass() == Double.class && Double.isFinite((Double) value)) return value;
        if (value.getClass() == Float.class && Float.isFinite((Float) value)) return value;
        if (depth > 30 || !visited.add(value)) throw failure("DMN 입력의 깊이/순환은 지원하지 않습니다");
        try {
            if (value instanceof Map<?, ?> map) {
                if (!value.getClass().getName().startsWith("java.util.")) throw failure("DMN 입력 map은 일반 JSON map이어야 합니다");
                Map<String, Object> copy = new LinkedHashMap<>();
                for (var entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String key)) throw failure("DMN 입력 map은 문자열 키여야 합니다");
                    copy.put(key, plain(entry.getValue(), visited, depth + 1));
                }
                return copy;
            }
            if (value instanceof List<?> list) {
                if (!value.getClass().getName().startsWith("java.util.")) throw failure("DMN 입력 list는 일반 JSON list여야 합니다");
                List<Object> copy = new ArrayList<>();
                for (Object item : list) copy.add(plain(item, visited, depth + 1));
                return copy;
            }
            throw failure("DMN 시험 입력은 단순 JSON 값만 지원합니다");
        } finally {
            visited.remove(value);
        }
    }

    public static ResponseStatusException failure(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "DMN 시뮬레이션 지원 제한: " + reason);
    }
}

import fs from 'node:fs';
import path from 'node:path';

const inputDir = process.argv[2];
const outputDir = process.argv[3] || path.resolve('test-assets/sds-export-runnable');
if (!inputDir) throw new Error('Usage: node tools/prepare-sds-export.mjs <input-bpmn-dir> [output-dir]');

const specs = [
  ['1. 여신신규_주택담보대출.bpmn', [['Gateway_004tb5u','본부승인대상여부'],['Gateway_0wp1fd9','승인 여부'],['Gateway_128wycp','금리승인요청여부'],['Gateway_1b02bhr','수수료 예외적용 여부'],['Gateway_0c9w4dp','주택소유수 조회도의여부'],['Gateway_1ruix1g','동의방식',['대면','비대면']],['Gateway_0xxj6e9','시세조회가능여부'],['Gateway_058201h','보증서 발급 가능 여부'],['Gateway_1ck1yit','집단잔금대출 여부']]],
  ['2. 수출환어음_매입.bpmn', [['Gateway_0jzmkj7','연체 여부'],['Gateway_0xw42jy','보완가능 여부'],['Gateway_1iydw4x','하자 여부'],['Gateway_1wk69f5','하자 매입 한도내여부',['N','Y']],['Gateway_1hg2bsm','매입 방법',['보증부매입','일반매입']]]],
  ['3. 기업신용카드 신규발급.bpmn', [['Gateway_17z2x07','승인여부']]],
  ['4. 일반계좌신규.bpmn', [['Gateway_17r3n8x','신규고객여부']]],
  ['5. 예금잔액 통보.bpmn', [['Gateway_021de48','본부 일괄발송 예금계좌'],['Gateway_136sdqh','DM 반송여부'],['Gateway_1lr8r88','예금잔액 이상']]],
  ['수출환어음_매입_및_추심_결재.bpmn', [['Gateway_0o4q3n9','입금여부']]],
  ['여신심사.bpmn', [['Gateway_1l81ivs','서류충족여부']]],
];

const esc = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&#60;').replace(/>/g, '&#62;').replace(/"/g, '&#34;');
const unesc = (s) => s.replace(/&#34;/g, '"').replace(/&#60;/g, '<').replace(/&#62;/g, '>').replace(/&amp;/g, '&');
const slug = (name) => name.replace(/\.bpmn$/, '').replace(/[^A-Za-z0-9]+/g, '_').replace(/^_|_$/g, '').toLowerCase();

function parseFlows(xml) {
  const incoming = new Map();
  const re = /<bpmn:sequenceFlow\b([^>]*?)(?:\/>|>[\s\S]*?<\/bpmn:sequenceFlow>)/g;
  for (let m; (m = re.exec(xml));) {
    const source = /sourceRef="([^"]+)"/.exec(m[1])?.[1];
    const target = /targetRef="([^"]+)"/.exec(m[1])?.[1];
    if (source && target) incoming.set(target, [...(incoming.get(target) || []), source]);
  }
  return incoming;
}

function upstreamTasks(gateway, incoming, taskIds, seen = new Set()) {
  if (seen.has(gateway)) return [];
  seen.add(gateway);
  return (incoming.get(gateway) || []).flatMap(source => taskIds.has(source)
    ? [source]
    : upstreamTasks(source, incoming, taskIds, seen));
}

function updateTask(xml, taskId, formId, decisions) {
  const taskRe = new RegExp(`<bpmn:(task|userTask)\\b([^>]*\\bid="${taskId}"[^>]*)>([\\s\\S]*?)<\\/bpmn:\\1>`);
  const match = taskRe.exec(xml);
  if (!match) throw new Error(`Task ${taskId} was not found`);
  const propertiesRe = /<uengine:properties\s+json="([\s\S]*?)"\s*\/>/;
  const propertyMatch = propertiesRe.exec(match[3]);
  let props = {};
  if (propertyMatch) {
    try { props = JSON.parse(unesc(propertyMatch[1])); } catch { props = {}; }
  }
  props.tool = `formHandler:${formId}`;
  props.eventSynchronization = { mappingContext: { mappingElements: decisions.map(({ variable }) => ({
    argument: { text: variable }, direction: 'out', variable: { name: `[Arguments].${variable}` }, isKey: false
  })) } };
  const replacement = `<uengine:properties json="${esc(JSON.stringify(props))}" />`;
  const body = propertyMatch ? match[3].replace(propertiesRe, replacement)
    : `<bpmn:extensionElements>${replacement}</bpmn:extensionElements>${match[3]}`;
  return xml.replace(match[0], `<bpmn:userTask${match[2]}>${body}</bpmn:userTask>`);
}

function addVariables(xml, variables) {
  const processExt = /(<bpmn:process\b[^>]*>\s*<bpmn:extensionElements>\s*)<uengine:properties\s+json="([\s\S]*?)"\s*\/>\s*<\/bpmn:extensionElements>/;
  const entries = variables.map(v => `<uengine:variable name="${v}" type="Text" />`).join('');
  if (!processExt.test(xml)) throw new Error('Process extension properties were not found');
  return xml.replace(processExt, `$1<uengine:properties json="$2">${entries}</uengine:properties></bpmn:extensionElements>`);
}

function normalizeTaskProperties(xml) {
  return xml.replace(/<bpmn:(?:task|userTask)\b[\s\S]*?<\/bpmn:(?:task|userTask)>/g, task => task.replace(/<uengine:properties\s+json="([\s\S]*?)"\s*\/>/, (property, encoded) => {
    let props;
    try { props = JSON.parse(unesc(encoded)); } catch { return property; }
    if (typeof props.description === 'string') delete props.description;
    return `<uengine:properties json="${esc(JSON.stringify(props))}" />`;
  }));
}

function normalizeProperties(xml) {
  return xml.replace(/<uengine:properties\s+json="([\s\S]*?)"\s*\/>/g, (property, encoded) => {
    let props;
    try { props = JSON.parse(unesc(encoded)); } catch { return property; }
    if (typeof props.description === 'string') delete props.description;
    return `<uengine:properties json="${esc(JSON.stringify(props))}" />`;
  });
}

function makeRunnable(xml) {
  // 반출본의 비실행 모델도 Process service에서 실행하도록 명시한다.
  // 외부에 없는 호출 프로세스는 사용자가 허용한 테스트 응답(즉시 완료 task)으로 대체한다.
  return xml
    .replace(/(<bpmn:process\b[^>]*\bisExecutable=")false("[^>]*>)/g, '$1true$2')
    .replace(/<bpmn:callActivity\b/g, '<bpmn:task')
    .replace(/<\/bpmn:callActivity>/g, '</bpmn:task>');
}

function addConditions(xml, gateway, variable, options) {
  let optionIndex = 0;
  const flowRe = new RegExp(`(<bpmn:sequenceFlow\\b)([^>]*sourceRef="${gateway}"[^>]*)(\\/>|>[\\s\\S]*?<\\/bpmn:sequenceFlow>)`, 'g');
  return xml.replace(flowRe, (_, open, attrs, tail) => {
    let value = /\bname="([^"]*)"/.exec(attrs)?.[1];
    if (!value && options.includes('일반매입')) value = options[optionIndex];
    optionIndex += 1;
    if (!value) throw new Error(`${gateway} has an unnamed branch`);
    if (!options.includes(value)) throw new Error(`${gateway} has unexpected branch '${value}'`);
    if (!/\bname=/.test(attrs)) attrs += ` name="${value}"`;
    return `${open}${attrs}><bpmn:extensionElements><uengine:properties json="{&#34;condition&#34;:{&#34;_type&#34;:&#34;org.uengine.kernel.Evaluate&#34;,&#34;key&#34;:&#34;${variable}&#34;,&#34;value&#34;:&#34;${value}&#34;}}" /></bpmn:extensionElements></bpmn:sequenceFlow>`;
  });
}

function branchTargets(xml, gateway, options) {
  const targets = [];
  const flowRe = new RegExp(`<bpmn:sequenceFlow\\b([^>]*sourceRef="${gateway}"[^>]*)(?:\\/>|>[\\s\\S]*?<\\/bpmn:sequenceFlow>)`, 'g');
  let optionIndex = 0;
  for (let m; (m = flowRe.exec(xml));) {
    const attrs = m[1];
    const value = /\bname="([^"]*)"/.exec(attrs)?.[1] || options[optionIndex];
    const targetId = /\btargetRef="([^"]+)"/.exec(attrs)?.[1];
    optionIndex += 1;
    if (value && targetId) targets.push({ value, targetId });
  }
  return targets;
}

function setDefaultBranch(xml, gateway, fallbackValue) {
  const flow = new RegExp(`<bpmn:sequenceFlow\\b(?=[^>]*\\bid="([^"]+)")(?=[^>]*\\bname="${fallbackValue}")(?=[^>]*\\bsourceRef="${gateway}")[^>]*(?:\\/>|>)`).exec(xml);
  if (!flow) throw new Error(`Default branch '${fallbackValue}' was not found for ${gateway}`);
  const gatewayRe = new RegExp(`(<bpmn:exclusiveGateway\\b[^>]*\\bid="${gateway}")([^>]*)(>)`);
  return xml.replace(gatewayRe, (_, start, attrs, end) => `${start}${/\\bdefault=/.test(attrs) ? attrs : `${attrs} default="${flow[1]}"`}${end}`);
}

fs.rmSync(outputDir, { recursive: true, force: true });
fs.mkdirSync(outputDir, { recursive: true });
const branchCases = [];
for (const [file, gateways] of specs) {
  let xml = fs.readFileSync(path.join(inputDir, file), 'utf8');
  xml = makeRunnable(xml);
  xml = normalizeProperties(xml);
  xml = normalizeTaskProperties(xml);
  const incoming = parseFlows(xml);
  const taskIds = new Set([...xml.matchAll(/<bpmn:(?:task|userTask)\b[^>]*\bid="([^"]+)"/g)].map(m => m[1]));
  const decisionTasks = new Map();
  for (const [gateway, variable, values = ['Y', 'N']] of gateways) {
    for (const taskId of new Set(upstreamTasks(gateway, incoming, taskIds))) {
      decisionTasks.set(taskId, [...(decisionTasks.get(taskId) || []), { variable, label: variable, values }]);
    }
    xml = addConditions(xml, gateway, variable, values);
    if (values.length > 1) xml = setDefaultBranch(xml, gateway, values[1]);
  }
  xml = addVariables(xml, gateways.map(([, variable]) => variable));
  for (const [taskId, decisions] of decisionTasks) {
    const formId = `sds_${slug(file)}_${taskId.toLowerCase()}_decision`;
    const html = `<section>${decisions.map(d => `<select-field name="${d.variable}" alias="${d.label}" is_dynamic_load="fixed" items='${JSON.stringify(d.values.map(value => ({ [value]: value })))}' disabled="false" readonly="false"></select-field>`).join('')}</section>`;
    fs.writeFileSync(path.join(outputDir, `${formId}.form`), html, 'utf8');
    xml = updateTask(xml, taskId, formId, decisions);
  }
  for (const [gateway, variable, values = ['Y', 'N']] of gateways) {
    const taskId = [...new Set(upstreamTasks(gateway, incoming, taskIds))][0];
    if (!taskId) throw new Error(`No upstream task found for ${gateway}`);
    branchCases.push({
      definitionId: file,
      gatewayId: gateway,
      variable,
      taskId,
      inputDefaults: Object.fromEntries((decisionTasks.get(taskId) || []).map(d => [d.variable, d.values[0]])),
      branches: branchTargets(xml, gateway, values),
    });
  }
  fs.writeFileSync(path.join(outputDir, file), xml, 'utf8');
}
fs.writeFileSync(path.join(outputDir, 'branch-cases.json'), JSON.stringify(branchCases, null, 2), 'utf8');
console.log(`Prepared ${specs.length} BPMN files in ${outputDir}`);

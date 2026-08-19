/**
 * uEngine6 BPM — "접속 → 기본 프로세스 만들기 → 실행" 데모 (Playwright)
 *
 *  Part 1. 화면에서 프로세스 정의 만들기
 *    1) http://localhost:8288 접속 → Keycloak 로그인
 *    2) Process Definition Map (등록된 정의 목록)
 *    3) 새 정의 경로 진입 → 기본 템플릿(Start → User Task → End)
 *    4) 편집 모드 → User Task 이름 변경
 *    5) 정의 저장
 *
 *  Part 2. 실행
 *    6) 정의 상세 화면(Simulation / Edit / Execute)
 *    7) Execute → 시뮬레이션 실행기에서 작업 완료 (DB 인스턴스는 생기지 않음)
 *    8) 실제 인스턴스 시작 (POST /instance)
 *    9) Task List 에서 실제 작업 확인 → 완료
 *
 * 사용:  node demo-basic-process.mjs             (headed)
 *        HEADLESS=1 node demo-basic-process.mjs  (headless)
 */
import { chromium } from '@playwright/test';
import fs from 'node:fs';

const BASE = process.env.BASE || 'http://localhost:8288';
const USER = process.env.U || 'hong';
const PASS = process.env.P || '1234';
const ENDPOINT = process.env.ENDPOINT || 'hong@uengine.org'; // JWT email 클레임과 일치해야 한다
const DEF_PATH = process.env.DEF || 'demo/basic-process';
const TASK_NAME = process.env.TASK || '신청서 작성';
const SHOT_DIR = process.env.SHOT_DIR || './demo-shots';
const HEADLESS = process.env.HEADLESS === '1';

fs.mkdirSync(SHOT_DIR, { recursive: true });
let step = 0;
const browser = await chromium.launch({ headless: HEADLESS, slowMo: HEADLESS ? 0 : 350 });
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });

const shot = async (name) => {
    step += 1;
    const file = `${SHOT_DIR}/${String(step).padStart(2, '0')}-${name}.png`;
    await page.screenshot({ path: file });
    console.log(`   📸 ${file}`);
};
const say = async (msg) => {
    console.log(`\n▶ ${msg}`);
    await page.waitForTimeout(HEADLESS ? 0 : 700);
};

// ───────── 1. 접속 & 로그인 ─────────
await say(`1. 게이트웨이 접속: ${BASE}  (프론트/백엔드 모두 이 주소 하나로)`);
await page.goto(`${BASE}/todolist`, { waitUntil: 'domcontentloaded', timeout: 60000 });
await page.waitForTimeout(2500);
const userInput = page.locator('#username, input[name="username"]').first();
if (await userInput.count()) {
    await shot('keycloak-login');
    await say(`   Keycloak 로그인: ${USER} / ${PASS}`);
    await userInput.fill(USER);
    await page.locator('#password, input[name="password"]').first().fill(PASS);
    await page.locator('#kc-login, input[type=submit], button[type=submit]').first().click();
}
await page.waitForTimeout(7000);
await shot('todolist-before');
console.log(`   로그인 사용자: ${await page.evaluate(() => localStorage.getItem('userName'))}`);

// ───────── 2. 정의 목록 ─────────
await say('2. Process Definition Map — 등록된 프로세스 정의 한눈에 보기');
await page.goto(`${BASE}/definition-map`, { waitUntil: 'domcontentloaded' });
await page.waitForTimeout(7000);
await shot('definition-map');

// ───────── 3. 새 정의 (기본 템플릿) ─────────
await say(`3. 새 프로세스 만들기 — /definitions/${DEF_PATH} 로 들어가면 기본 템플릿이 열린다`);
await page.goto(`${BASE}/definitions/${DEF_PATH}`, { waitUntil: 'domcontentloaded' });
await page.waitForTimeout(8000);
await shot('designer-template');
console.log('   기본 템플릿: Start → User Task → End (Lane 1)');

// ───────── 4. 편집 ─────────
await say('4. 편집 모드 (연필) → 팔레트가 열리고 정의가 잠긴다(lock)');
await page.mouse.click(1330, 184);
await page.waitForTimeout(4000);
await shot('designer-edit');

await say(`   User Task 더블클릭 → 속성 패널에서 이름을 "${TASK_NAME}" 으로 변경`);
const task = page.locator('.djs-element').filter({ hasText: 'User Task' }).first();
const box = await task.boundingBox();
await page.mouse.dblclick(box.x + box.width / 2, box.y + box.height / 2);
await page.waitForTimeout(2500);
for (const inp of await page.locator('input').all()) {
    if ((await inp.inputValue().catch(() => '')) === 'User Task') {
        await inp.fill(TASK_NAME);
        break;
    }
}
await page.waitForTimeout(1200);
await shot('task-properties');
await page.mouse.click(1531, 145); // 패널 저장
await page.waitForTimeout(1500);
await page.mouse.click(1567, 145); // 패널 닫기
await page.waitForTimeout(2500);
await shot('task-renamed');

// ───────── 5. 저장 ─────────
await say('5. 정의 저장 (디스켓) → Save Process 다이얼로그');
await page.mouse.click(1366, 184);
await page.waitForTimeout(3000);
await shot('save-dialog');
await page.getByRole('button', { name: /^(Save|저장)$/i }).last().click();
await page.waitForTimeout(6000);
await shot('saved');
console.log(`   저장 위치: definitions/${DEF_PATH}.bpmn  +  archive/${DEF_PATH}.bpmn/<버전>.bpmn`);

// ───────── 6. 정의 상세 ─────────
await say('6. 정의 상세 화면 — Simulation / Edit / Execute');
await page.goto(`${BASE}/definition-map/sub/${DEF_PATH}`, { waitUntil: 'domcontentloaded' });
await page.waitForTimeout(7000);
await shot('definition-detail');

// ───────── 7. Execute (시뮬레이션 실행기) ─────────
await say('7. Execute → 시뮬레이션 실행기에서 흐름 확인 (주의: DB 인스턴스는 생기지 않는다)');
await page.getByRole('button', { name: /^(Execute|실행)$/i }).first().click();
await page.waitForTimeout(7000);
await shot('simulation-running');

const completeBtn = page.getByRole('button', { name: /^(Complete|완료)$/i }).first();
if (await completeBtn.count()) {
    await say('   시뮬레이션에서 "Complete" 눌러 다음 단계로 진행');
    await completeBtn.click();
    await page.waitForTimeout(7000);
    await shot('simulation-completed');
}
await page.keyboard.press('Escape');
await page.waitForTimeout(1500);

// ───────── 8. 실제 인스턴스 시작 ─────────
await say('8. 실제 인스턴스 시작 — 화면의 Execute 는 시뮬레이션이므로 API 로 시작한다');
const started = await page.evaluate(
    async ({ defPath, endpoint }) => {
        const res = await fetch('/instance', {
            method: 'POST',
            headers: {
                Authorization: 'Bearer ' + localStorage.getItem('accessToken'),
                'Content-Type': 'application/json;charset=UTF-8'
            },
            body: JSON.stringify({
                processDefinitionId: defPath,
                roleMappings: [{ name: 'Lane 1', endpoints: [endpoint], resourceNames: ['Lane 1'] }]
            })
        });
        return { status: res.status, body: (await res.text()).slice(0, 300) };
    },
    { defPath: DEF_PATH, endpoint: ENDPOINT }
);
console.log(`   POST /instance → HTTP ${started.status}`);
console.log(`   ${started.body}`);

// ───────── 9. Task List 에서 확인 & 완료 ─────────
await say('9. Task List 에서 실제 생성된 작업 확인');
await page.goto(`${BASE}/todolist`, { waitUntil: 'domcontentloaded' });
await page.waitForTimeout(9000);
await shot('todolist-after');

const card = page.locator(`text=${TASK_NAME}`).first();
if (await card.count()) {
    console.log(`   ✅ "${TASK_NAME}" 카드가 In Progress 에 있다`);
} else {
    console.log(`   ⚠ "${TASK_NAME}" 카드가 Task List 에 보이지 않음`);
}

await say('10. 작업 완료 처리 → Done 으로 이동');
const completed = await page.evaluate(
    async ({ taskName }) => {
        const auth = { Authorization: 'Bearer ' + localStorage.getItem('accessToken') };
        const list = await (await fetch('/worklist', { headers: auth })).json();
        const items = (list._embedded && list._embedded.worklist) || [];
        const mine = items.filter((w) => w.title === taskName && w.status === 'NEW').pop();
        if (!mine) return { skipped: true };
        const id = mine._links.self.href.split('/').pop();
        const res = await fetch(`/work-item/${id}/complete`, {
            method: 'POST',
            headers: { ...auth, 'Content-Type': 'application/json;charset=UTF-8' },
            body: JSON.stringify({ desiredState: 'complete' })
        });
        return { workItemId: id, status: res.status };
    },
    { taskName: TASK_NAME }
);
console.log(`   POST /work-item/${completed.workItemId}/complete → HTTP ${completed.status}`);
console.log('   ※ 카드를 클릭해 상세 화면을 여는 경로는 GET /work-item/{id} 가 500 이라 아직 동작하지 않는다 (install-issues #28)');

await page.reload({ waitUntil: 'domcontentloaded' });
await page.waitForTimeout(9000);
await shot('todolist-done');

await say('데모 종료');
await page.waitForTimeout(HEADLESS ? 0 : 4000);
await browser.close();

import fs from 'node:fs/promises';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {fileURLToPath,pathToFileURL} from 'node:url';
const here=path.dirname(fileURLToPath(import.meta.url));
const project=path.resolve(here,'../../..');
const {default:playwright}=await import(pathToFileURL(path.join(project,'process-gpt-vue3-hli/node_modules/playwright/index.js')).href);
const {chromium}=playwright;
const config=JSON.parse(await fs.readFile(path.join(here,'demo-windows.json'),'utf8'));
const stateDir=path.join(process.env.LOCALAPPDATA,'uEngineDemoWindows');
await fs.mkdir(stateDir,{recursive:true});
const contexts=[];
const closedContexts=[];
const windows=[];
const diagnosticsPath=path.join(here,'logs','demo-browser-last.jsonl');
await fs.mkdir(path.dirname(diagnosticsPath),{recursive:true});
await fs.writeFile(diagnosticsPath,'');
let phase='initializing';
let activeItem;
let activePage;
const recentErrors=[];
function safeUrl(value) {
  try { const url=new URL(value); return url.origin+url.pathname+(url.hash.startsWith('#/')?url.hash.split('?')[0]:''); }
  catch { return '[invalid URL]'; }
}
function safeMessage(value) {
  let message=String(value).replace(/https?:\/\/[^\s"'<>]+/g,safeUrl);
  if(process.env.UBANK_DEMO_PASSWORD) message=message.split(process.env.UBANK_DEMO_PASSWORD).join('[redacted]');
  return message.slice(0,4000);
}
async function diagnostic(stage,details={},updatePhase=true) {
  if(updatePhase) phase=stage;
  const record={time:new Date().toISOString(),window:activeItem?.id,stage,...details};
  console.log(JSON.stringify(record));
  await fs.appendFile(diagnosticsPath,JSON.stringify(record)+'\n');
}
let screen;
async function arrange() {
  const columns=[0,Math.floor(screen.width/3),Math.floor(screen.width*2/3),screen.width];
  const rows=[0,Math.floor(screen.height/2),screen.height];
  const tiles={customer:[0,0],branch:[1,0],dmn:[2,0],head:[0,1],admin:[1,1],mail:[2,1]};
  for(let i=0;i<windows.length;i++) {
    const w=windows[i];
    if(w.page.isClosed()) continue;
    const [column,row]=tiles[w.id] || [0,0];
    const bounds=w.background
      ? {left:screen.left,top:screen.top,width:screen.width,height:screen.height}
      : {left:screen.left+columns[column],top:screen.top+rows[row],
        width:columns[column+1]-columns[column],height:rows[row+1]-rows[row]};
    const physical={left:Math.round(bounds.left*screen.scale),top:Math.round(bounds.top*screen.scale)};
    physical.width=Math.round((bounds.left+bounds.width)*screen.scale)-physical.left;
    physical.height=Math.round((bounds.top+bounds.height)*screen.scale)-physical.top;
    const powershell=path.join(process.env.USERPROFILE,'.cache/codex-runtimes/codex-primary-runtime/dependencies/native/powershell/pwsh.exe');
    w.bounds=JSON.parse(execFileSync(powershell,['-NoProfile','-File',path.join(here,'arrange-demo-window.ps1'),
      '-ProfilePath',path.join(stateDir,w.id),'-Left',String(physical.left),'-Top',String(physical.top),
      '-Width',String(physical.width),'-Height',String(physical.height)],{encoding:'utf8',windowsHide:true}).trim());
  }
  await fs.writeFile(path.join(stateDir,'layout.json'),JSON.stringify({screen,windows:windows.map(({name,username,bounds,page})=>({name,username,bounds,url:page.url().replace(/#state=.*/,'')}))},null,2));
}
try {
  for(const item of config.windows) {
    activeItem=item;
    activePage=undefined;
    recentErrors.length=0;
    await diagnostic('launch-browser');
    const userDataDir=path.join(stateDir,item.id);
    const preferencesFile=path.join(userDataDir,'Default','Preferences');
    await fs.mkdir(path.dirname(preferencesFile),{recursive:true});
    const preferences=await fs.readFile(preferencesFile,'utf8').then(JSON.parse).catch(error=>{
      if(error.code==='ENOENT') return {};
      throw error;
    });
    preferences.credentials_enable_service=false;
    preferences.translate={...preferences.translate,enabled:false};
    preferences.profile={...preferences.profile,password_manager_enabled:false};
    await fs.writeFile(preferencesFile,JSON.stringify(preferences));
    const context=await chromium.launchPersistentContext(userDataDir,{
      channel:'msedge',headless:false,viewport:null,chromiumSandbox:true,
      args:['--app=data:text/html,<title>uBank Demo</title>','--no-first-run','--no-default-browser-check','--window-size=600,800']
    });
    contexts.push(context);
    closedContexts.push(new Promise(resolve=>context.once('close',resolve)));
    // Chromium can create an extra startup tab alongside the app window.
    await new Promise(resolve=>setTimeout(resolve,1000));
    const pages=context.pages();
    const page=pages.find(p=>p.url().startsWith('data:text/html,'))??pages[0];
    activePage=page;
    const remember=record=>{recentErrors.push(record);if(recentErrors.length>20)recentErrors.shift();};
    page.on('pageerror',error=>remember({type:'pageerror',message:safeMessage(error.message)}));
    page.on('requestfailed',request=>remember({type:'requestfailed',url:safeUrl(request.url()),message:safeMessage(request.failure()?.errorText)}));
    let rejectAuthentication;
    let authenticationComplete=false;
    const authenticationFailure=new Promise((resolve,reject)=>{rejectAuthentication=reject;});
    authenticationFailure.catch(()=>{});
    page.on('response',response=>{
      if(response.status()<400) return;
      const record={type:'http',status:response.status(),url:safeUrl(response.url())};
      remember(record);
      diagnostic('HTTP_ERROR',{window:item.id,...record},false).catch(error=>console.error(safeMessage(error.message)));
      if(!authenticationComplete && new URL(response.url()).pathname==='/api/session') {
        rejectAuthentication(new Error(`Session initialization failed: HTTP ${response.status()} ${record.url}`));
      }
    });
    page.once('close',()=>context.close().catch(()=>{}));
    for(const extra of pages.filter(p=>p!==page)) await extra.close();
    const cdp=await context.newCDPSession(page);
    const {windowId}=await cdp.send('Browser.getWindowForTarget');
    if(!screen) screen=await page.evaluate(()=>({left:window.screen.availLeft??0,top:window.screen.availTop??0,width:window.screen.availWidth,height:window.screen.availHeight,scale:window.devicePixelRatio}));
    windows.push({...item,page,cdp,windowId});
    await diagnostic('arrange-windows');
    await arrange();
    await diagnostic('navigate',{url:safeUrl(item.url)});
    await page.goto(item.url,{waitUntil:'domcontentloaded',timeout:60000});
    if(item.authenticate===false) {
      await diagnostic('opened',{url:safeUrl(page.url())});
      continue;
    }
    const username=page.locator('#username, input[name="username"]').first();
    const authenticated=new URL(item.url).port==='5374'
      ? page.getByText('로그아웃',{exact:true}).first()
      : page.locator('.v-main').first();
    await diagnostic('wait-login-or-authenticated');
    const loginState=await Promise.race([
      username.waitFor({state:'visible',timeout:60000}).then(()=>'login'),
      authenticated.waitFor({state:'visible',timeout:60000}).then(()=>'authenticated'),
      authenticationFailure
    ]);
    if(loginState==='login') {
      await diagnostic('submit-login');
      await username.fill(item.username);
      if(!process.env.UBANK_DEMO_PASSWORD) throw new Error(`Login password not configured: ${item.id}`);
      await page.locator('#password, input[name="password"]').first().fill(process.env.UBANK_DEMO_PASSWORD);
      await page.locator('[type="submit"]').first().click();
    }
    await diagnostic('wait-authenticated');
    await Promise.race([authenticated.waitFor({state:'visible',timeout:60000}),authenticationFailure]);
    authenticationComplete=true;
    if(item.id==='dmn') {
      await page.locator('.br-editor-card').waitFor();
      await page.addStyleTag({content:`
        .br-body-row > .br-col:first-child { display:none; }
        .br-body-row > .br-col:last-child { min-width:0!important; flex:1 1 100%; max-width:100%; }
        .br-editor-header, .br-editor-actions { flex-wrap:wrap; }
      `});
    }
    await diagnostic('authenticated',{url:safeUrl(page.url())});
    console.log(`Opened ${item.id}`);
  }
  delete process.env.UBANK_DEMO_PASSWORD;
  await arrange();
  const dmnWindow=windows.find(w=>w.id==='dmn');
  if(dmnWindow && !dmnWindow.page.isClosed()) await dmnWindow.page.bringToFront();
  if(process.env.DEMO_VERIFY==='1') {
    const evidence=path.join(project,'.codex-tmp/demo-windows');
    await fs.mkdir(evidence,{recursive:true});
    for(const w of windows) {
      await w.page.screenshot({path:path.join(evidence,`${w.id}.png`)});
      console.log(w.id,(await w.page.locator('body').innerText()).slice(0,300));
    }
  }
  console.log(`${windows.length} demo windows are ready. Close all demo windows to stop the launcher.`);
  await Promise.all(closedContexts);
} catch(error) {
  const failedStage=phase;
  const loginError=activePage&&!activePage.isClosed()
    ? await activePage.locator('#input-error, #kc-feedback-wrapper, .alert-error').first().textContent({timeout:1000}).catch(()=>null)
    : null;
  await diagnostic('FAILED',{failedStage,url:activePage?safeUrl(activePage.url()):undefined,error:safeMessage(error.stack||error),loginError:loginError?safeMessage(loginError):undefined,recentErrors});
  console.error(`Failure details: ${diagnosticsPath}`);
  await Promise.all(contexts.map(context=>context.close().catch(()=>{})));
  process.exitCode=1;
}

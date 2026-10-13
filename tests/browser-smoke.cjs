// Node/Playwright são necessários só para esta verificação, não para usar o GameShelf.
const {chromium}=require('playwright');
const {spawn}=require('node:child_process');
const fs=require('node:fs');const os=require('node:os');const path=require('node:path');const net=require('node:net');const assert=require('node:assert/strict');
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function port(){const s=net.createServer();await new Promise(r=>s.listen(0,'127.0.0.1',r));const p=s.address().port;await new Promise(r=>s.close(r));return p;}
(async()=>{
  const work=fs.mkdtempSync(path.join(os.tmpdir(),'gameshelf-browser-'));const serverPort=await port();const base=`http://127.0.0.1:${serverPort}`;
  const jar=path.resolve(__dirname,'../target/gameshelf-0.3.0.jar');
  const app=spawn('java',['-jar',jar,`--server.port=${serverPort}`,'--app.admin.username=ui_admin','--app.admin.password=BrowserAdminPassword42!',
    '--ggdeals.api-key=','--app.recovery.enabled=false','--app.security.rate-limit-enabled=false',`--app.media.directory=${path.join(work,'uploads')}`],{cwd:work,env:process.env});
  let logs='';app.stdout.on('data',d=>logs+=d);app.stderr.on('data',d=>logs+=d);
  let browser;
  try {
    let ready=false;
    for(let i=0;i<100;i++){try{const r=await fetch(base+'/api/games');if(r.ok&&(await r.json()).length>=12){ready=true;break;}}catch{}if(app.exitCode!==null)throw new Error('Java encerrado: '+logs.slice(-2000));await pause(300);}
    assert(ready,'Java não iniciou');
    browser=await chromium.launch({headless:true});const context=await browser.newContext({viewport:{width:1440,height:1000}});
    await context.route('**/*',route=>new URL(route.request().url()).origin===base?route.continue():route.abort());
    const page=await context.newPage();const errors=[];page.on('pageerror',error=>errors.push(error.message));
    await page.goto(base);await page.locator('.card').first().waitFor();
    await page.locator('#account-login').click();await page.locator('#username').fill('ui_admin');await page.locator('#password').fill('BrowserAdminPassword42!');await page.locator('#auth-submit').click();
    await page.locator('#add-game').waitFor({state:'visible'});
    await page.locator('[data-view=lists]').click();await page.locator('#create-list').click();await page.locator('#list-name').fill('Cooperativos');await page.locator('#list-description').fill('Para jogar com amigos');
    await page.locator('#list-form button[type=submit]').click();await page.locator('#list-dialog').waitFor({state:'hidden'});await page.getByRole('heading',{name:'Cooperativos',exact:true}).waitFor();
    await page.locator('[data-view=catalog]').click();await page.getByRole('button',{name:'Abrir Elden Ring',exact:true}).click();
    await page.locator('#play-status').selectOption('COMPLETED');await page.locator('#rating').selectOption('5');await page.locator('#review').fill('Uma jornada que valeu cada descoberta.');
    await page.locator('#favorite').check();await page.locator('#diary-wishlist').check();await page.locator('#diary-form button[type=submit]').click();
    await page.waitForFunction(()=>document.querySelector('#form-message').textContent.includes('salva'));
    await page.locator('#add-to-list').click();await page.waitForFunction(()=>document.querySelector('#toast').textContent.includes('adicionado à lista'));
    await page.locator('#close-detail').click();await page.locator('[data-view=wishlist]').click();assert.equal(await page.locator('.card').count(),1);
    await page.locator('[data-view=profile]').click();await page.getByText('Uma jornada que valeu cada descoberta.',{exact:true}).waitFor();
    await page.locator('#profile-name').fill('Jogador de teste');await page.locator('#profile-bio').fill('Minha coleção de histórias.');
    await page.locator('#profile-avatar-file').setInputFiles({name:'avatar.png',mimeType:'image/png',buffer:Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAoAAAAKCAIAAAACUFjqAAAAFElEQVR4nGN0yzNiwA2Y8MiNYGkAIi0A+uIAXFoAAAAASUVORK5CYII=','base64')});
    await page.waitForFunction(()=>document.querySelector('#profile-message').textContent.includes('Foto enviada'));
    await page.locator('#profile-form button[type=submit]').click();await page.waitForFunction(()=>document.querySelector('#profile-message').textContent.includes('atualizado'));
    assert.equal(await page.locator('#profile-summary h2').textContent(),'Jogador de teste');assert.equal(await page.locator('#profile-summary img').count(),1);
    const qa=process.env.GAMESHELF_QA_DIR;if(qa){fs.mkdirSync(qa,{recursive:true});await page.screenshot({path:path.join(qa,'profile-desktop.png'),fullPage:true});}
    await page.locator('[data-view=lists]').click();await page.locator('.list-game-row').first().waitFor();assert.equal(await page.locator('.list-game-row').count(),1);
    await page.locator('#add-game').click();await page.locator('#game-title').fill('Jogo criado no navegador');await page.locator('#game-genre').fill('Aventura');await page.locator('#game-description').fill('Um jogo cadastrado pelo administrador através da interface.');
    await page.locator('#game-submit').click();await page.locator('#admin-dialog').waitFor({state:'hidden'});await page.getByRole('button',{name:'Abrir Jogo criado no navegador',exact:true}).waitFor();
    await page.getByRole('button',{name:'Abrir Jogo criado no navegador',exact:true}).click();await page.locator('#edit-game').click();await page.locator('#game-title').fill('Jogo editado no navegador');await page.locator('#game-submit').click();
    await page.locator('#admin-dialog').waitFor({state:'hidden'});await page.getByRole('button',{name:'Abrir Jogo editado no navegador',exact:true}).waitFor();
    await page.setViewportSize({width:390,height:844});await page.locator('[data-view=profile]').click();await page.locator('#profile-summary h2').waitFor();
    assert(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth+1),'Perfil possui overflow horizontal no celular');
    if(qa)await page.screenshot({path:path.join(qa,'profile-mobile.png'),fullPage:true});
    await page.locator('#account-logout').click();await page.locator('#account-login').waitFor();
    await page.locator('#account-login').click();await page.locator('#register-tab').click();await page.locator('#display-name').fill('Outra pessoa');await page.locator('#username').fill('browser_user');await page.locator('#password').fill('BrowserUserPassword42!');await page.locator('#auth-submit').click();
    await page.locator('#auth-dialog').waitFor({state:'hidden'});assert(await page.locator('#add-game').isHidden());
    await page.locator('[data-view=profile]').click();await page.waitForFunction(()=>document.querySelector('#profile-summary h2')?.textContent==='Outra pessoa');assert.equal(await page.locator('.review-card').count(),0);
    await page.locator('[data-view=lists]').click();await page.getByText('Crie sua primeira lista e reúna os jogos do seu jeito.',{exact:true}).waitFor();
    assert.deepEqual(errors,[],'Erros JavaScript no navegador');
    console.log('Browser smoke aprovado: login, listas, resenha, desejos, perfil/avatar, cadastro/edição administrativa, celular e isolamento entre contas.');
  } finally {if(browser)await browser.close();app.kill('SIGTERM');await Promise.race([new Promise(r=>app.once('exit',r)),pause(10000)]);if(app.exitCode===null)app.kill('SIGKILL');}
})().catch(error=>{console.error(error);process.exitCode=1;});

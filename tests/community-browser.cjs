const {chromium}=require('playwright');
const {spawn}=require('node:child_process');const fs=require('node:fs');const path=require('node:path');const os=require('node:os');const net=require('node:net');const assert=require('node:assert/strict');
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
(async()=>{
  const work=fs.mkdtempSync(path.join(os.tmpdir(),'critplay-community-'));
  const listener=net.createServer();await new Promise(resolve=>listener.listen(0,'127.0.0.1',resolve));const port=listener.address().port;await new Promise(resolve=>listener.close(resolve));const base=`http://127.0.0.1:${port}`;
  const app=spawn('java',['-jar',path.resolve(__dirname,'../target/critplay-0.5.3.jar'),`--server.port=${port}`,'--app.admin.username=story_admin','--app.admin.password=StoryAdminPassword42!',
    '--ggdeals.api-key=','--rawg.api-key=','--app.security.rate-limit-enabled=false',`--app.media.directory=${path.join(work,'uploads')}`],{cwd:work});
  let logs='';app.stdout.on('data',data=>logs+=data);app.stderr.on('data',data=>logs+=data);let browser;
  try{
    for(let i=0;i<100;i++){try{if((await fetch(base+'/api/games')).ok)break;}catch{}if(app.exitCode!==null)throw Error(logs.slice(-2000));await pause(300);}
    browser=await chromium.launch({headless:true});const context=await browser.newContext({viewport:{width:1440,height:1000}});await context.route('**/*',route=>new URL(route.request().url()).origin===base?route.continue():route.abort());
    const page=await context.newPage();const errors=[];page.on('pageerror',error=>errors.push(error.message));await page.goto(base);await page.locator('.card').first().waitFor();
    const original=await page.locator('#games .card h3').allTextContents();await page.locator('#game-sort').selectOption('title');const ordered=await page.locator('#games .card h3').allTextContents();
    assert.deepEqual(ordered,[...original].sort((a,b)=>a.localeCompare(b,'pt-BR',{sensitivity:'base',numeric:true})));await page.locator('#game-sort').selectOption('default');assert.deepEqual(await page.locator('#games .card h3').allTextContents(),original);
    await page.locator('#account-login').click();await page.locator('#username').fill('story_admin');await page.locator('#password').fill('StoryAdminPassword42!');await page.locator('#auth-submit').click();await page.locator('#account-name').waitFor();
    await page.locator('[data-view=library]').click();await page.locator('#games-empty').waitFor();await page.locator('#empty-explore').click();
    async function review(title,status,rating){await page.getByRole('button',{name:'Abrir '+title,exact:true}).click();assert(await page.locator('#detail-cover').isVisible());assert.equal(await page.locator('#tab-experience').getAttribute('aria-selected'),'true');
      await page.locator('#play-status').selectOption(status);await page.locator('#rating').selectOption(rating);await page.locator('#review').fill('Uma experiência para compartilhar: '+title);await page.locator('#diary-form button[type=submit]').click();await page.locator('#diary-summary').waitFor();
      assert((await page.locator('#diary-summary').textContent()).includes(rating.replace('.',',')+'/5'));await page.locator('#tab-prices').click();assert(await page.locator('#prices').isVisible());assert(await page.locator('#diary-form').isHidden());
      await page.locator('#tab-community').click();await page.waitForFunction(()=>!document.querySelector('#community-average').textContent.includes('Carregando'));assert(await page.locator('#detail-community-panel').isVisible());
      await page.locator('#tab-community').press('Home');assert.equal(await page.locator('#tab-experience').getAttribute('aria-selected'),'true');await page.locator('#close-detail').click();}
    await review('Elden Ring','PLAYING','4.5');await review('Hades','COMPLETED','3.5');
    await page.locator('[data-view=library]').click();assert.equal(await page.locator('#games .game-card').count(),2);await page.locator('#library-status [data-status=PLAYING]').click();assert.equal(await page.locator('#games .game-card').count(),1);
    await page.locator('#library-status [data-status=DROPPED]').click();assert(await page.locator('#games-empty').isVisible());await page.locator('#library-status [data-status=""]').click();
    await page.locator('#game-sort').selectOption('rating');assert.equal(await page.locator('#games .card h3').first().textContent(),'Elden Ring');await page.locator('#layout-compact').click();assert(await page.locator('#games').evaluate(node=>node.classList.contains('compact-library')));
    await page.setViewportSize({width:390,height:844});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));await page.setViewportSize({width:1440,height:1000});
    await page.locator('[data-view=profile]').click();await page.locator('#profile-summary h2').waitFor();await page.locator('#toggle-profile-settings').click();await page.locator('#featured-game-0').selectOption({label:'Elden Ring'});await page.locator('#featured-game-1').selectOption({label:'Hades'});
    const image=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAoAAAAKCAIAAAACUFjqAAAAFElEQVR4nGN0yzNiwA2Y8MiNYGkAIi0A+uIAXFoAAAAASUVORK5CYII=','base64');
    await page.locator('#profile-banner-file').setInputFiles({name:'banner.png',mimeType:'image/png',buffer:image});await page.waitForFunction(()=>document.querySelector('#gamer-message').textContent.includes('Banner enviado'));
    await page.locator('#gamer-form button[type=submit]').click();await page.waitForFunction(()=>document.querySelector('#gamer-message').textContent.includes('salva'));assert.equal(await page.locator('#profile-featured .featured-game').count(),2);assert.equal(await page.locator('#profile-banner img').count(),1);
    await page.locator('#profile-visibility').selectOption('PUBLIC');await page.locator('#profile-discoverable').check();await page.locator('#privacy-form button[type=submit]').click();await page.waitForFunction(()=>document.querySelector('#privacy-message').textContent.includes('atualizada'));
    await page.locator('[data-view=feed]').click();await page.locator('#feed-items .feed-card').first().waitFor();assert.equal(await page.locator('#feed-items .feed-card').count(),2);
    // A separate context is required because sessions are shared within a browser context.
    const second=await browser.newContext({viewport:{width:1440,height:1000}});await second.route('**/*',route=>new URL(route.request().url()).origin===base?route.continue():route.abort());const user=await second.newPage();user.on('pageerror',error=>errors.push(error.message));
    await user.goto(base);await user.locator('#account-login').click();await user.locator('#register-tab').click();await user.locator('#display-name').fill('Outro jogador');await user.locator('#username').fill('story_friend');await user.locator('#password').fill('StoryFriendPassword42!');await user.locator('#auth-submit').click();await user.locator('#account-name').waitFor();
    await user.locator('[data-view=feed]').click();await user.getByRole('button',{name:'Encontrar jogadores',exact:true}).waitFor();await user.getByRole('button',{name:'Encontrar jogadores',exact:true}).click();await user.locator('#people-search').fill('story_admin');await user.locator('#people-search-form button').click();await user.locator('#people-results button').click();
    await user.getByRole('button',{name:'Seguir',exact:true}).click();await user.getByRole('button',{name:'Deixar de seguir',exact:true}).waitFor();assert.equal(await user.locator('#public-profile-content .featured-game').count(),2);
    await user.locator('#close-public-profile').click();await user.locator('[data-view=feed]').click();await user.locator('#feed-items .feed-card').first().waitFor();assert.equal(await user.locator('#feed-items .feed-card').count(),2);
    await user.locator('#feed-items .mini-game').first().click();await user.locator('#tab-community').click();await user.locator('.review-like').waitFor();await user.locator('.review-like').click();await user.locator('.review-like[aria-pressed=true]').waitFor();assert((await user.locator('.review-like').textContent()).includes('1'));
    await user.locator('.review-like').click();await user.locator('.review-like[aria-pressed=false]').waitFor();assert((await user.locator('.review-like').textContent()).includes('0'));
    await user.setViewportSize({width:390,height:844});assert(await user.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));assert.deepEqual(errors,[]);
    console.log('Community browser: catalogue order, half stars, tabs, status filters, compact library, gamer profile, feed, follows, likes and mobile passed.');
  }finally{if(browser)await browser.close();app.kill('SIGTERM');await pause(500);if(app.exitCode===null)app.kill('SIGKILL');fs.rmSync(work,{recursive:true,force:true});}
})().catch(error=>{console.error(error);process.exitCode=1;});

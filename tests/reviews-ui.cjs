// Deterministic interaction checks for reviews and card shortcuts (no Java server required).
const {chromium}=require('playwright');
const http=require('node:http');const fs=require('node:fs');const path=require('node:path');const assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../src/main/resources/static');
(async()=>{
  const server=http.createServer((req,res)=>{
    const filename=path.join(root,req.url==='/'?'index.html':req.url.split('?')[0]);
    if(!filename.startsWith(root+path.sep)||!fs.existsSync(filename)){res.writeHead(404);res.end();return;}
    const mime={'.html':'text/html','.js':'text/javascript','.css':'text/css','.png':'image/png','.ico':'image/x-icon'};
    res.setHeader('Content-Type',mime[path.extname(filename)]||'application/octet-stream');res.end(fs.readFileSync(filename));
  });
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const base=`http://127.0.0.1:${server.address().port}`;let browser;
  try{
    browser=await chromium.launch({headless:true});const context=await browser.newContext({viewport:{width:1440,height:1000},hasTouch:true});
    const game={id:1,title:'Jogo de teste',genre:'RPG',releaseYear:2026,platforms:'PC',description:'Uma aventura para testar.'};
    let entry=null,failSave=false,authenticated=true,averageOverride;
    await context.route('**/*',async route=>{
      const req=route.request(),url=new URL(req.url());
      if(url.origin!==base)return route.abort();
      if(!url.pathname.startsWith('/api/'))return route.continue();
      const json=value=>route.fulfill({json:value});
      switch(url.pathname){
        case '/api/auth/csrf':return json({headerName:'X-CSRF-TOKEN',token:'test'});
        case '/api/auth/me':return json({authenticated,user:authenticated?{username:'tester',displayName:'Tester',role:'USER'}:null});
        case '/api/diary':return json(entry?[entry]:[]);
        case '/api/lists':return json([]);
        case '/api/games':return json([game]);
        case '/api/games/1/prices':return json({status:'UNAVAILABLE',message:'Sem preços',sourceUrl:'https://gg.deals/'});
        case '/api/games/1/reviews':return json({averageRating:averageOverride===undefined?(entry?.rating?(entry.rating+3)/2:3):averageOverride,ratingCount:averageOverride===null?0:(entry?.rating?2:1),reviewCount:1,
          reviews:[{username:'other',displayName:'Outro jogador',rating:3,review:'<img src=x onerror=alert(1)>',updatedAt:'2026-10-14T12:00:00Z'}]});
        case '/api/diary/1':
          if(failSave)return route.fulfill({status:500,json:{message:'Falha simulada'}});
          entry={...req.postDataJSON(),gameId:1,updatedAt:'2026-10-14T12:00:00Z'};return json(entry);
        default:return json({});
      }
    });
    const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
    await page.goto(base);await page.locator('.card').waitFor();
    await page.locator('.game-card').hover();await page.waitForTimeout(250);
    assert.notEqual(await page.locator('.game-card').evaluate(n=>getComputedStyle(n).transform),'none');
    await page.getByRole('button',{name:'Adicionar a favoritos: Jogo de teste',exact:true}).click();
    await page.getByRole('button',{name:'Remover de favoritos: Jogo de teste',exact:true}).waitFor();assert.equal(entry.favorite,true);assert.equal(entry.wishlist,false);
    assert(await page.locator('#detail').isHidden());
    averageOverride=null;
    await page.getByRole('button',{name:'Abrir Jogo de teste',exact:true}).click();
    await page.waitForFunction(()=>document.querySelector('#community-average').textContent.includes('Sem notas ainda'));
    assert.equal(await page.locator('#community-average .stars-empty').textContent(),'★★★★★');
    assert.equal(await page.locator('#community-average .stars-fill').evaluate(node=>node.style.width),'0%');
    await page.locator('#close-detail').click();averageOverride=2.25;
    await page.getByRole('button',{name:'Abrir Jogo de teste',exact:true}).click();
    await page.waitForFunction(()=>document.querySelector('#community-average .stars-fill')?.style.width==='45%');
    await page.locator('#close-detail').click();averageOverride=undefined;
    await page.getByRole('button',{name:'Abrir Jogo de teste',exact:true}).click();
    await page.locator('#tab-community').click();
    await page.getByText('Outro jogador · @other',{exact:true}).waitFor();assert.equal(await page.locator('#community-reviews img').count(),0);
    await page.locator('#tab-experience').click();
    assert.equal(await page.locator('#rating-track input[type=radio]').count(),10);
    assert.equal(await page.locator('#rating').inputValue(),'');
    await page.getByRole('radio',{name:'3,5 de 5 estrelas',exact:true}).hover();
    assert((await page.locator('#rating-value').textContent()).includes('3,5/5 · Prévia'));
    assert.equal(await page.locator('#rating').inputValue(),'','Hover não pode gravar a nota');
    await page.locator('#detail-title').hover();assert.equal(await page.locator('#rating-value').textContent(),'Sem nota');
    await page.getByRole('radio',{name:'3,5 de 5 estrelas',exact:true}).check();assert.equal(await page.locator('#rating').inputValue(),'3.5');
    assert.equal(await page.locator('.picker-star-fill').nth(3).evaluate(node=>node.style.width),'50%');
    await page.getByRole('radio',{name:'3,5 de 5 estrelas',exact:true}).press('ArrowRight');assert.equal(await page.locator('#rating').inputValue(),'4');
    await page.getByRole('radio',{name:'4 de 5 estrelas',exact:true}).press('End');assert.equal(await page.locator('#rating').inputValue(),'5');
    await page.getByRole('radio',{name:'5 de 5 estrelas',exact:true}).press('Home');assert.equal(await page.locator('#rating').inputValue(),'0.5');
    await page.locator('#clear-rating').click();assert.equal(await page.locator('#rating').inputValue(),'');assert(await page.locator('#clear-rating').isDisabled());
    await page.locator('#play-status').selectOption('COMPLETED');await page.getByRole('radio',{name:'5 de 5 estrelas',exact:true}).check();await page.locator('#review').fill('Minha experiência');
    await page.locator('#start-date').fill('2026-01-01');await page.locator('#finish-date').fill('2026-02-01');
    await page.locator('#diary-form button[type=submit]').click();await page.locator('#diary-summary').waitFor();
    assert(await page.locator('#diary-form').isHidden());await page.waitForFunction(()=>document.querySelector('#community-average').textContent.includes('4/5'));
    assert.equal(await page.locator('#community-average .stars-fill').evaluate(node=>node.style.width),'80%');
    await page.locator('#edit-review').click();assert.equal(await page.locator('#review').inputValue(),'Minha experiência');assert(await page.getByRole('radio',{name:'5 de 5 estrelas',exact:true}).isChecked());
    await page.locator('#review').fill('Alteração');failSave=true;await page.locator('#diary-form button[type=submit]').click();
    await page.getByText('Falha simulada',{exact:true}).waitFor();assert(await page.locator('#diary-form').isVisible());assert.equal(entry.review,'Minha experiência');
    failSave=false;await page.locator('#cancel-review-edit').click();assert.equal(await page.locator('#review').inputValue(),'Minha experiência');
    await page.locator('#edit-review').click();await page.getByRole('radio',{name:'4 de 5 estrelas',exact:true}).check();await page.locator('#diary-form button[type=submit]').click();await page.locator('#diary-summary').waitFor();
    await page.locator('#close-detail').click();await page.getByRole('button',{name:'Adicionar a lista de desejos: Jogo de teste',exact:true}).click();
    await page.getByRole('button',{name:'Remover de lista de desejos: Jogo de teste',exact:true}).waitFor();assert.equal(entry.rating,4);assert.equal(entry.review,'Minha experiência');assert.equal(entry.finishDate,'2026-02-01');assert.equal(entry.favorite,true);
    await page.reload();await page.getByRole('button',{name:'Abrir Jogo de teste',exact:true}).click();await page.locator('#diary-summary').waitFor();assert(await page.locator('#diary-form').isHidden());
    await page.setViewportSize({width:390,height:844});await page.locator('#edit-review').click();await page.getByRole('radio',{name:'4,5 de 5 estrelas',exact:true}).tap();assert.equal(await page.locator('#rating').inputValue(),'4.5');await page.locator('#cancel-review-edit').click();assert.equal(await page.locator('#rating').inputValue(),'4');assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));
    await page.locator('#close-detail').click();
    await page.emulateMedia({reducedMotion:'reduce'});await page.locator('.game-card').hover();assert.equal(await page.locator('.game-card').evaluate(n=>getComputedStyle(n).transform),'none');
    assert.equal((await page.request.get(base+'/favicon.ico')).status(),200);
    authenticated=false;await page.reload();await page.getByRole('button',{name:'Adicionar a favoritos: Jogo de teste',exact:true}).click();await page.locator('#auth-dialog').waitFor();
    assert.deepEqual(errors,[]);console.log('Reviews UI: save, edit, cancel, failed save, community, shortcuts, reload, mobile, interactive stars (hover/half/keyboard/clear/touch), average stars, reduced motion, favicon and login passed.');
  }finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
})().catch(error=>{console.error(error);process.exitCode=1;});

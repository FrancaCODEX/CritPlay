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
    let entry=null,failSave=false,authenticated=true,averageOverride,delayGames=true,delayReviews=false,failReviews=false,privateSharedProfile=false;
    await context.route('**/*',async route=>{
      const req=route.request(),url=new URL(req.url());
      if(url.origin!==base)return route.abort();
      if(!url.pathname.startsWith('/api/'))return route.continue();
      const json=value=>route.fulfill({json:value});
      switch(url.pathname){
        case '/api/people/other':return json({visible:!privateSharedProfile,user:{username:'other',displayName:'Angel Gamer',visibility:privateSharedProfile?'FRIENDS':'PUBLIC',...(!privateSharedProfile?{bio:'Uma jornada pelos meus jogos favoritos.',featuredGames:[]}:{} )},relationship:{following:false,friendship:'NONE'},...(!privateSharedProfile?{stats:{library:1,completed:0,favorites:0,wishlist:0,reviews:0},social:{followers:12,following:8,friends:3},reviews:[],collections:{library:[{game,status:'PLAYING',rating:4.5}],favorites:[],wishlist:[],lists:[]}}:{})});
        case '/api/auth/csrf':return json({headerName:'X-CSRF-TOKEN',token:'test'});
        case '/api/auth/me':return json({authenticated,user:authenticated?{username:'tester',displayName:'Tester',role:'USER'}:null});
        case '/api/diary':return json(entry?[entry]:[]);
        case '/api/lists':return json([]);
        case '/api/games':if(delayGames)await new Promise(resolve=>setTimeout(resolve,700));return json([game]);
        case '/api/games/1/prices':return json({status:'UNAVAILABLE',message:'Sem preços',sourceUrl:'https://gg.deals/'});
        case '/api/games/1/reviews':if(delayReviews)await new Promise(resolve=>setTimeout(resolve,700));if(failReviews)return route.fulfill({status:503,json:{message:'Avaliações indisponíveis'}});return json({averageRating:averageOverride===undefined?(entry?.rating?(entry.rating+3)/2:3):averageOverride,ratingCount:averageOverride===null?0:(entry?.rating?2:1),reviewCount:1,
          reviews:[{username:'other',displayName:'Outro jogador',rating:3,review:'<img src=x onerror=alert(1)>',updatedAt:'2026-10-14T12:00:00Z'}]});
        case '/api/diary/1':
          if(failSave)return route.fulfill({status:500,json:{message:'Falha simulada'}});
          entry={...req.postDataJSON(),gameId:1,updatedAt:'2026-10-14T12:00:00Z'};return json(entry);
        default:return json({});
      }
    });
    const page=await context.newPage();
    async function assertHalfStar(index){await page.waitForFunction(index=>{const star=document.querySelectorAll('.picker-star')[index];const box=star.querySelector('.picker-star-fill').getBoundingClientRect(),shape=star.querySelector('.picker-star-empty svg').getBoundingClientRect(),filled=star.querySelector('.picker-star-fill svg').getBoundingClientRect();return Math.abs(box.right-(shape.x+shape.width/2))<.15&&Math.abs(filled.width-shape.width)<.15&&Math.abs(filled.x-shape.x)<.15;},index);}
    const errors=[];page.on('pageerror',e=>errors.push(e.message));
    await page.goto(base);await page.locator('#account-name .avatar-initials').waitFor();assert.equal(await page.locator('#account-name img').count(),0);assert.equal(await page.locator('#account-name').getAttribute('aria-label'),'Abrir perfil de Tester');await page.locator('#games[aria-busy=true] .skeleton-game').first().waitFor();assert.equal(await page.locator('#games .skeleton-game').count(),8);await page.locator('.card').waitFor();assert.equal(await page.locator('#games').getAttribute('aria-busy'),null);delayGames=false;
    await page.locator('.game-card').hover();await page.waitForTimeout(250);
    assert.notEqual(await page.locator('.game-card').evaluate(n=>getComputedStyle(n).transform),'none');
    await page.getByRole('button',{name:'Adicionar a favoritos: Jogo de teste',exact:true}).click();
    await page.getByRole('button',{name:'Remover de favoritos: Jogo de teste',exact:true}).waitFor();assert.equal(entry.favorite,true);assert.equal(entry.wishlist,false);
    assert(await page.locator('#detail').isHidden());
    averageOverride=null;delayReviews=true;
    await page.getByRole('button',{name:'Abrir Jogo de teste',exact:true}).click();
    await page.locator('#tab-community').click();await page.locator('#community-reviews[aria-busy=true] .skeleton-review').first().waitFor();await page.waitForFunction(()=>document.querySelector('#community-average').textContent.includes('Sem notas ainda'));assert.equal(await page.locator('#community-reviews .skeleton-card').count(),0);delayReviews=false;
    assert.equal(await page.locator('#community-average .stars-empty svg').count(),5);
    assert.equal(await page.locator('#community-average .stars-fill').evaluate(node=>node.style.width),'0%');
    await page.locator('#close-detail').click();failReviews=true;await page.getByRole('button',{name:'Abrir Jogo de teste',exact:true}).click();await page.waitForFunction(()=>document.querySelector('#community-average').textContent==='Avaliações indisponíveis');assert.equal(await page.locator('#community-reviews .skeleton-card').count(),0);assert.equal(await page.locator('#community-reviews').getAttribute('aria-busy'),null);await page.locator('#close-detail').click();failReviews=false;averageOverride=2.25;
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
    assert.equal(await page.locator('.picker-star-fill').nth(3).evaluate(node=>node.style.width),'50%');await assertHalfStar(3);
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
    await page.setViewportSize({width:390,height:844});await page.locator('#edit-review').click();await page.getByRole('radio',{name:'4,5 de 5 estrelas',exact:true}).tap();assert.equal(await page.locator('#rating').inputValue(),'4.5');await assertHalfStar(4);await page.locator('#cancel-review-edit').click();assert.equal(await page.locator('#rating').inputValue(),'4');assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));
    await page.locator('#close-detail').click();
    await page.emulateMedia({reducedMotion:'reduce'});await page.locator('.game-card').hover();assert.equal(await page.locator('.game-card').evaluate(n=>getComputedStyle(n).transform),'none');
    assert.equal((await page.request.get(base+'/favicon.ico')).status(),200);
    await page.goto(base+'/#profile=other');await page.locator('#public-profile-content .public-name-row h3').waitFor();
    assert.equal(await page.locator('#public-profile-content .public-name-row h3').textContent(),'Angel Gamer');
    assert.equal(await page.locator('#public-profile-content .public-profile-identity').evaluate(node=>node.tagName),'DIV');
    assert.equal(await page.locator('#public-profile-content .public-profile-identity').evaluate(node=>getComputedStyle(node).opacity),'1');
    assert.equal(await page.locator('#public-profile-content input').count(),0);assert.equal(await page.locator('#public-profile-content .shared-game').count(),1);
    assert(await page.locator('#public-profile-content .profile-banner').isVisible());assert(await page.evaluate(()=>{const dialog=document.querySelector('#public-profile-dialog');return dialog.scrollWidth<=dialog.clientWidth+1;}));
    await page.evaluate(()=>{window.sharedData=null;Object.defineProperty(navigator,'share',{configurable:true,value:async data=>{window.sharedData=data;}});});
    await page.getByRole('button',{name:'Compartilhar',exact:true}).click();await page.getByText('Perfil compartilhado.',{exact:true}).waitFor();
    assert.deepEqual(await page.evaluate(()=>window.sharedData),{title:'Perfil de Angel Gamer no CritPlay',url:base+'/#profile=other'});
    await page.evaluate(()=>{window.copyCount=0;Object.defineProperty(navigator,'clipboard',{configurable:true,value:{writeText:async()=>window.copyCount++}});Object.defineProperty(navigator,'share',{configurable:true,value:async()=>{throw new DOMException('Cancelled','AbortError');}});});
    await page.getByRole('button',{name:'Compartilhar',exact:true}).click();assert.equal(await page.evaluate(()=>window.copyCount),0);assert.equal(await page.locator('.public-share-feedback').textContent(),'');
    await page.evaluate(()=>{Object.defineProperty(navigator,'share',{configurable:true,value:undefined});Object.defineProperty(navigator,'clipboard',{configurable:true,value:{writeText:async text=>window.copiedProfile=text}});});
    await page.getByRole('button',{name:'Compartilhar',exact:true}).click();await page.getByText('Link copiado!',{exact:true}).waitFor();assert.equal(await page.evaluate(()=>window.copiedProfile),base+'/#profile=other');
    await page.evaluate(()=>{Object.defineProperty(navigator,'clipboard',{configurable:true,value:{writeText:async()=>{throw new Error('Not allowed');}}});document.execCommand=()=>{window.fallbackCopy=document.querySelector('#public-profile-dialog textarea')?.value;return true;};});
    await page.getByRole('button',{name:'Compartilhar',exact:true}).click();assert.equal(await page.evaluate(()=>window.fallbackCopy),base+'/#profile=other');assert.equal(await page.locator('#public-profile-content textarea').count(),0);assert.equal(await page.locator('#public-profile-dialog textarea').count(),0);
    await page.evaluate(()=>{document.execCommand=()=>false;});await page.getByRole('button',{name:'Compartilhar',exact:true}).click();await page.getByText('Não foi possível compartilhar. Tente novamente.',{exact:true}).waitFor();assert(await page.getByRole('button',{name:'Compartilhar',exact:true}).isEnabled());
    privateSharedProfile=true;await page.locator('#public-profile-content').getByRole('button',{name:'Seguir',exact:true}).click();await page.getByText('Este perfil está disponível apenas para amigos aceitos.',{exact:true}).waitFor();
    assert.equal(await page.locator('#public-profile-content .shared-collections').count(),0);assert.equal(await page.locator('#public-profile-content .public-profile-stats').count(),0);assert.equal(await page.locator('#public-profile-content .profile-bio').count(),0);
    await page.locator('#close-public-profile').click();await page.evaluate(()=>history.replaceState(null,'','/'));
    authenticated=false;await page.reload();await page.getByRole('button',{name:'Adicionar a favoritos: Jogo de teste',exact:true}).click();await page.locator('#auth-dialog').waitFor();
    assert.deepEqual(errors,[]);console.log('Reviews UI: save, edit, cancel, failed save, community, shortcuts, reload, mobile, interactive stars (hover/half/keyboard/clear/touch), average stars, reduced motion, favicon, login and shared profile design/share/native/cancel/clipboard/fallback/privacy passed.');
  }finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
})().catch(error=>{console.error(error);process.exitCode=1;});

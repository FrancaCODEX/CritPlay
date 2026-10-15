'use strict';
const $ = selector => document.querySelector(selector);
function coverSources(game) {
  return [...new Set([game.coverUrl,game.steamAppId?`https://cdn.akamai.steamstatic.com/steam/apps/${game.steamAppId}/library_600x900.jpg`:null,game.metadataCoverUrl].filter(Boolean))];
}
function coverFor(game) {return coverSources(game)[0]||null;}
function gameCoverImage(game,onMissing) {
  const sources=coverSources(game);if(!sources.length)return null;
  const image=el('img');image.alt='';image.loading='lazy';let index=0;
  const steamCover=game.steamAppId?`https://cdn.akamai.steamstatic.com/steam/apps/${game.steamAppId}/library_600x900.jpg`:null;
  const selectSource=()=>{image.dataset.coverFit=sources[index]===steamCover?'contain':'fill';image.src=sources[index];};
  image.addEventListener('error',()=>{if(++index<sources.length)selectSource();else {image.remove();onMissing?.();}});
  selectSource();return image;
}
const statusLabels = { WANT_TO_PLAY: 'Quero jogar', PLAYING: 'Jogando', COMPLETED: 'Finalizado', DROPPED: 'Abandonado' };
const state = { games: [], diary: new Map(), view: 'catalog', selected: null, searchSequence: 0, detailSequence: 0, communitySequence: 0, saving: false, user: null, csrf: null, authMode: 'login', authenticating: false, addingGame: false, quickSaving: new Set(), lists: [], profile: null, profileDirty: false, gamerDirty: false, profileBannerUrl: null, profileGames: [], catalogSort: 'default', librarySort: 'updated', libraryStatus: '', libraryLayout: 'grid', editGameId: null, gameCoverUrl: null, profileAvatarUrl: null, workspaceSequence: 0 };
async function csrfToken() {
  if (!state.csrf) {
    const response = await fetch('/api/auth/csrf', { credentials: 'same-origin', cache: 'no-store' });
    if (!response.ok) throw new Error('Não foi possível iniciar uma sessão segura. Recarregue a página.');
    state.csrf = await response.json();
  }
  return state.csrf;
}
async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...options.headers };
  if(options.body instanceof FormData) delete headers['Content-Type'];
  if (!['GET', 'HEAD', 'OPTIONS'].includes(options.method || 'GET')) {
    const csrf = await csrfToken(); headers[csrf.headerName] = csrf.token;
  }
  const response = await fetch(path, { credentials: 'same-origin', ...options, headers });
  if (!response.ok) {
    const data = await response.json().catch(() => ({}));
    if (response.status === 401 && path !== '/api/auth/login') resetAccount();
    const details = data.errors?.length ? ' ' + data.errors.join(' · ') : '';
    throw new Error((data.message || 'Não foi possível completar a ação. Tente novamente.') + details);
  }
  return response.status === 204 ? null : response.json();
}
function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text != null) node.textContent = text; // Avaliações e títulos nunca viram HTML.
  return node;
}
async function loadGames() {
  const sequence = ++state.searchSequence;
  const params = new URLSearchParams({ q: $('#search').value, genre: $('#genre').value, platform: $('#platform').value });
  $('#catalog-message').textContent = 'Buscando jogos…';
  try {
    const games = await api('/api/games?' + params);
    if (sequence !== state.searchSequence) return;
    state.games = games;
    for(const game of games){if(![...$('#genre').options].some(o=>o.value===game.genre))$('#genre').append(el('option',null,game.genre));}
    if(!params.get('q')&&!params.get('genre')&&!params.get('platform'))updateHeroGames(games);
    render();
  } catch (error) {
    if (sequence !== state.searchSequence) return;
    $('#games').replaceChildren(); $('#count').textContent = '';
    $('#catalog-message').textContent = error.message;
  }
}
function render() {
  const personal=['profile','lists','people','admin','feed'].includes(state.view);
  $('.workspace:not(.personal-workspace)').hidden=personal;
  $('.hero').hidden=personal;$('.journey').hidden=personal;scheduleHero();
  $('#feed-section').hidden=state.view!=='feed';
  $('#admin-section').hidden=state.view!=='admin';$('#people-section').hidden=state.view!=='people';$('#profile-section').hidden=state.view!=='profile';$('#lists-section').hidden=state.view!=='lists';
  if(personal) {if(state.view==='feed')refreshFeed();else if(state.view==='people')refreshPeople();else if(state.view==='admin')refreshAdmin();else refreshWorkspace(state.view);return;}
  let games = state.games.filter(game => state.view === 'catalog' ||
    (state.view === 'library' ? state.diary.has(game.id) : state.view==='wishlist'?state.diary.get(game.id)?.wishlist:state.diary.get(game.id)?.favorite));
  renderLibraryControls(games);
  if(state.view==='library'&&state.libraryStatus)games=games.filter(game=>state.diary.get(game.id)?.status===state.libraryStatus);
  const sort=state.view==='catalog'?state.catalogSort:state.librarySort;
  const titleOrder=(a,b)=>a.title.localeCompare(b.title,'pt-BR',{sensitivity:'base',numeric:true})||a.id-b.id;
  if(sort==='title')games.sort(titleOrder);
  if(sort==='rating')games.sort((a,b)=>(state.diary.get(b.id)?.rating??-1)-(state.diary.get(a.id)?.rating??-1)||titleOrder(a,b));
  if(sort==='updated'||sort==='finished')games.sort((a,b)=>{
    const field=sort==='finished'?'finishDate':'updatedAt';
    return (state.diary.get(b.id)?.[field]||'').localeCompare(state.diary.get(a.id)?.[field]||'')||titleOrder(a,b);
  });
  $('#section-title').textContent = { catalog: 'Explore o catálogo', library: 'Sua biblioteca de jogos', favorites: 'Seus jogos favoritos', wishlist:'Sua lista de desejos' }[state.view];
  $('#count').textContent = `${games.length} ${games.length === 1 ? 'jogo' : 'jogos'}`;
  $('#catalog-message').textContent = '';
  $('#games-empty').hidden=!!games.length;
  $('#empty-title').textContent=state.view==='catalog'?'Nenhuma aventura encontrada':'Seu próximo capítulo começa aqui';
  $('#empty-description').textContent=state.view==='catalog'?'Tente outro nome ou limpe os filtros para descobrir novos mundos.':
    state.libraryStatus?'Ainda não há jogos neste status. Explore o catálogo e registre sua próxima experiência.':'Salve seus primeiros jogos e construa uma coleção com a sua história.';
  $('#games').classList.toggle('compact-library',state.view!=='catalog'&&state.libraryLayout==='compact');
  $('#games').replaceChildren(...games.map(renderGameCard));
}
function ratingDisplay(value,suffix='') {
  const node=el('p','personal-rating');const stars=el('span','rating-stars');stars.setAttribute('aria-hidden','true');
  stars.append(el('span','stars-empty','★★★★★'));const fill=el('span','stars-fill','★★★★★');fill.style.width=(value/5*100)+'%';stars.append(fill);
  const label=value.toLocaleString('pt-BR',{maximumFractionDigits:1})+'/5';node.append(stars,el('span',null,label+(suffix?' · '+suffix:'')));return node;
}
function paintRatingPicker(value,preview=false) {
  const rating=Number(value)||0;
  [...$('#rating-track').children].forEach((star,index)=>{star.querySelector('.picker-star-fill').style.width=Math.max(0,Math.min(100,(rating-index)*100))+'%';});
  $('#rating-value').textContent=rating?rating.toLocaleString('pt-BR')+'/5'+(preview?' · Prévia':''):'Sem nota';
  $('#rating-picker').classList.toggle('is-preview',preview);
}
function setRating(value) {
  $('#rating').value=value==null||value===''?'':String(value);
  for(const input of document.querySelectorAll('[name="rating-choice"]'))input.checked=input.value===$('#rating').value;
  $('#clear-rating').disabled=state.saving||!$('#rating').value;
  paintRatingPicker($('#rating').value);
}
function focusRatingPicker(){($('#rating-track input:checked')||$('#rating-track input'))?.focus();}
for(let index=0;index<5;index++) {
  const star=el('span','picker-star');
  const empty=el('span','picker-star-empty','★');empty.setAttribute('aria-hidden','true');
  const fill=el('span','picker-star-fill','★');fill.setAttribute('aria-hidden','true');star.append(empty,fill);
  for(const half of [0.5,1]) {
    const value=index+half;const hit=el('label','rating-hit '+(half===0.5?'rating-half':'rating-full'));
    const input=el('input');input.type='radio';input.name='rating-choice';input.value=String(value);
    input.setAttribute('aria-label',value.toLocaleString('pt-BR')+' de 5 estrelas');
    input.addEventListener('change',()=>{if(input.checked)setRating(value);});
    hit.addEventListener('pointerenter',()=>{if(!input.disabled)paintRatingPicker(value,true);});
    input.addEventListener('keydown',event=>{
      if(event.key!=='Home'&&event.key!=='End')return;
      event.preventDefault();const target=event.key==='Home'?$('#rating-track input'):$('#rating-track input[value="5"]');
      target.checked=true;setRating(target.value);target.focus();
    });
    hit.append(input);star.append(hit);
  }
  $('#rating-track').append(star);
}
$('#rating-track').addEventListener('pointerleave',()=>paintRatingPicker($('#rating').value));
$('#clear-rating').addEventListener('click',()=>{setRating(null);focusRatingPicker();});
function renderAverageRating(summary) {
  const node=$('#community-average');node.classList.add('personal-rating');
  const display=ratingDisplay(summary.averageRating??0,`${summary.ratingCount} ${summary.ratingCount===1?'nota':'notas'}`);
  if(summary.averageRating==null)display.lastElementChild.textContent='Sem notas ainda';
  node.replaceChildren(...display.childNodes);
}
function renderGameCard(game) {
  const entry=state.diary.get(game.id);const pending=state.quickSaving.has(game.id);
  const wrapper=el('article','game-card');wrapper.dataset.gameId=game.id;wrapper.setAttribute('aria-busy',String(pending));
  const card=el('button','card');card.type='button';card.setAttribute('aria-label','Abrir '+game.title);
  const cover=el('div','cover');const missing=()=>cover.prepend(el('span','image-fallback',game.title));
  const image=gameCoverImage(game,missing);if(image)cover.append(image);else missing();
  if(entry){const badge=el('span','badge status-badge',statusLabels[entry.status]);badge.dataset.status=entry.status;cover.append(badge);}
  if(entry?.favorite)cover.append(el('span','favorite-mark','♥'));
  const info=el('div','card-info');info.append(el('h3',null,game.title),el('p','card-meta',`${game.genre} · ${game.releaseYear}`));
  if(entry?.rating)info.append(ratingDisplay(entry.rating,'Sua nota'));
  const extra=el('p','compact-meta',entry?`${statusLabels[entry.status]}${entry.finishDate?' · Concluído em '+entry.finishDate.split('-').reverse().join('/'):''}`:game.platforms);
  info.append(extra);card.append(cover,info);card.addEventListener('click',()=>openDetail(game));wrapper.append(card);
  const actions=el('div','card-actions');
  for(const [field,icon,label] of [['favorite',entry?.favorite?'♥':'♡','favoritos'],['wishlist',entry?.wishlist?'✓':'＋','lista de desejos']]) {
    const active=!!entry?.[field];const action=el('button','card-action',pending?'…':icon);action.type='button';action.dataset.action=field;
    action.setAttribute('aria-label',`${active?'Remover de':'Adicionar a'} ${label}: ${game.title}`);action.setAttribute('aria-pressed',String(active));action.title=action.getAttribute('aria-label');action.disabled=pending;
    action.addEventListener('click',()=>quickToggle(game,field));actions.append(action);
  }
  wrapper.append(actions);return wrapper;
}
function renderLibraryControls(games) {
  const catalog=state.view==='catalog';$('#library-layout').hidden=catalog;$('#library-status').hidden=state.view!=='library';
  const options=catalog?[['default','Padrão'],['title','Ordem alfabética']]:[['updated','Última atualização'],['title','Ordem alfabética'],['rating','Maior nota'],['finished','Data de conclusão']];
  $('#game-sort').replaceChildren(...options.map(([value,label])=>{const option=el('option',null,label);option.value=value;return option;}));
  $('#game-sort').value=catalog?state.catalogSort:state.librarySort;
  $('#layout-grid').setAttribute('aria-pressed',String(state.libraryLayout==='grid'));$('#layout-compact').setAttribute('aria-pressed',String(state.libraryLayout==='compact'));
  if(state.view==='library')$('#library-status').replaceChildren(...[['','Todos'],...Object.entries(statusLabels)].map(([value,label])=>{
    const count=games.filter(game=>!value||state.diary.get(game.id)?.status===value).length;const chip=el('button','status-chip',label+' · '+count);chip.type='button';chip.dataset.status=value;chip.setAttribute('aria-pressed',String(state.libraryStatus===value));
    chip.addEventListener('click',()=>{state.libraryStatus=value;render();$('#library-status').querySelector(`[data-status="${value}"]`)?.focus();});return chip;
  }));
}
$('#game-sort').addEventListener('change',()=>{if(state.view==='catalog')state.catalogSort=$('#game-sort').value;else state.librarySort=$('#game-sort').value;render();$('#game-sort').focus();});
for(const [id,layout] of [['layout-grid','grid'],['layout-compact','compact']])$('#'+id).addEventListener('click',()=>{state.libraryLayout=layout;render();$('#'+id).focus();});
$('#empty-explore').addEventListener('click',()=>{$('#search').value='';$('#genre').value='';$('#platform').value='';state.libraryStatus='';setView('catalog');loadGames();});

function setView(view) {
  if (view !== 'catalog' && !state.user) { openAuth(); toast('Entre para acessar sua biblioteca.'); return; }
  if(view==='admin'&&state.user?.role!=='ADMIN')return;
  if(state.view==='admin'&&view!=='admin')resetSteam();
  if(state.view!==view)closeProfileSettings();
  state.view = view;
  ++state.workspaceSequence;
  document.querySelectorAll('.nav').forEach(button => { const active = button.dataset.view === view;
    button.classList.toggle('active', active); button.setAttribute('aria-pressed', String(active)); });
  render(); (view==='feed'?$('#feed-section'):view==='profile'?$('#profile-section'):view==='lists'?$('#lists-section'):view==='people'?$('#people-section'):view==='admin'?$('#admin-section'):$('.workspace')).scrollIntoView({ behavior: 'smooth', block: 'start' });
}
async function openDetail(game, editing = false) {
  if (state.saving || state.quickSaving.has(game.id)) return;
  state.selected = game;
  const sequence = ++state.detailSequence;
  const entry = state.diary.get(game.id);
  $('#detail-title').textContent = game.title;
  $('#detail-meta').textContent = `${game.genre} · ${game.releaseYear} · ${game.platforms}`;
  $('#detail-description').textContent = game.description;
  const detailImage=coverFor(game);
  $('#source-language-note').hidden=!game.metadataSourceUrl;$('#detail-metadata-source').hidden=!game.metadataSourceUrl;$('#detail-metadata-source').href=game.metadataSourceUrl||'https://rawg.io/';
  const poster=$('#detail-cover');poster.replaceChildren();poster.style.backgroundImage='none';poster.setAttribute('aria-label','Capa de '+game.title);
  const fallback=()=>poster.append(el('span','image-fallback',game.title));const image=gameCoverImage(game,fallback);
  if(image)poster.append(image);else fallback();
  selectDetailTab('experience');
  renderDiarySummary(entry, editing);
  $('#diary-login-hint').hidden = !!state.user;
  $('#play-status').value = entry?.status || 'WANT_TO_PLAY';
  setRating(entry?.rating ?? null);
  $('#review').value = entry?.review || '';$('#start-date').value=entry?.startDate||'';$('#finish-date').value=entry?.finishDate||'';$('#start-date').max=new Date().toLocaleDateString('en-CA');$('#finish-date').max=$('#start-date').max;
  $('#favorite').checked = !!entry?.favorite;
  $('#diary-wishlist').checked=entry?!!entry.wishlist:true;
  $('#edit-game').hidden=state.user?.role!=='ADMIN';
  $('#game-list-controls').hidden=!state.user;
  updateListPicker();renderConsoleLinks(game);
  $('#remove-entry').hidden = !entry;
  $('#form-message').textContent = '';
  $('#prices').textContent = 'Consultando preços…';
  $('#price-source').href = game.steamAppId ? `https://gg.deals/steam/app/${game.steamAppId}/` : 'https://gg.deals/';
  if (!$('#detail').open) $('#detail').showModal();
  loadCommunity(game.id, sequence);
  if (!state.user) { $('#prices').textContent = 'Entre na sua conta para consultar os preços.'; return; }
  try {
    const quote = await api(`/api/games/${game.id}/prices`);
    if (sequence !== state.detailSequence || !$('#detail').open) return;
    renderPrices(quote);
  } catch (error) {
    if (sequence === state.detailSequence) $('#prices').textContent = error.message;
  }
}
function selectDetailTab(name) {
  for(const tab of document.querySelectorAll('[data-detail-tab]')) {
    const active=tab.dataset.detailTab===name;tab.setAttribute('aria-selected',String(active));tab.tabIndex=active?0:-1;
    $('#'+tab.getAttribute('aria-controls')).hidden=!active;
  }
}
document.querySelectorAll('[data-detail-tab]').forEach(tab=>{
  tab.addEventListener('click',()=>selectDetailTab(tab.dataset.detailTab));
  tab.addEventListener('keydown',event=>{
    const tabs=[...document.querySelectorAll('[data-detail-tab]')];let index=tabs.indexOf(tab);
    if(event.key==='ArrowRight')index=(index+1)%tabs.length;else if(event.key==='ArrowLeft')index=(index+tabs.length-1)%tabs.length;
    else if(event.key==='Home')index=0;else if(event.key==='End')index=tabs.length-1;else return;
    event.preventDefault();selectDetailTab(tabs[index].dataset.detailTab);tabs[index].focus();
  });
});
function reviewLikeButton(review) {
  const node=el('button','review-like',`${review.likedByMe?'♥':'♡'} ${review.likeCount??0}`);node.type='button';node.setAttribute('aria-pressed',String(!!review.likedByMe));
  node.setAttribute('aria-label',(review.likedByMe?'Descurtir':'Curtir')+' resenha de '+review.displayName);
  node.addEventListener('click',async()=>{
    if(!state.user){openAuth();return;}if(node.disabled)return;
    node.disabled=true;node.textContent='Salvando…';
    try {const value=await api(`/api/reviews/${review.entryId}/like`,{method:review.likedByMe?'DELETE':'PUT'});Object.assign(review,value);node.setAttribute('aria-pressed',String(value.likedByMe));node.setAttribute('aria-label',(value.likedByMe?'Descurtir':'Curtir')+' resenha de '+review.displayName);}
    catch(error){toast(error.message);}
    finally{node.disabled=false;node.textContent=`${review.likedByMe?'♥':'♡'} ${review.likeCount??0}`;}
  });return node;
}
function renderDiarySummary(entry, editing = false) {
  const saved = !!entry && (entry.rating != null || !!entry.review?.trim());
  $('#diary-form').hidden = !state.user || (saved && !editing);
  $('#edit-review').hidden = !state.user || !saved || editing;
  $('#cancel-review-edit').hidden = !state.user || !saved || !editing;
  const summary = $('#diary-summary'); summary.hidden = !state.user || !saved || editing;
  summary.replaceChildren();
  if (!saved) return;
  summary.append(el('p', 'eyebrow', 'SUA AVALIAÇÃO'), el('p', 'muted', statusLabels[entry.status]));
  if (entry.rating) summary.append(ratingDisplay(entry.rating));
  if (entry.review) summary.append(el('p', 'review-text', entry.review));
  if (entry.reviewHidden) summary.append(el('p', 'price-note', 'Sua resenha está oculta pela moderação.'));
  if (entry.startDate || entry.finishDate) summary.append(el('p', 'price-note',
    [entry.startDate ? 'Comecei em ' + entry.startDate.split('-').reverse().join('/') : '',
     entry.finishDate ? 'Terminei em ' + entry.finishDate.split('-').reverse().join('/') : ''].filter(Boolean).join(' · ')));
}
$('#edit-review').addEventListener('click', () => {
  renderDiarySummary(state.diary.get(state.selected.id), true); focusRatingPicker();
});
$('#cancel-review-edit').addEventListener('click', () => {
  if (state.saving) return;
  const entry = state.diary.get(state.selected.id);
  $('#play-status').value = entry.status; setRating(entry.rating ?? null); $('#review').value = entry.review || '';
  $('#start-date').value = entry.startDate || ''; $('#finish-date').value = entry.finishDate || '';
  $('#favorite').checked = entry.favorite; $('#diary-wishlist').checked = entry.wishlist;
  $('#form-message').textContent = ''; renderDiarySummary(entry); $('#edit-review').focus();
});
async function loadCommunity(gameId, sequence) {
  const request = ++state.communitySequence;
  const container = $('#community-reviews');
  $('#community-average').textContent = 'Carregando avaliações…'; container.replaceChildren();
  try {
    const summary = await api(`/api/games/${gameId}/reviews`);
    if (request !== state.communitySequence || sequence !== state.detailSequence || !$('#detail').open) return;
    renderAverageRating(summary);
    // Your complete review is already shown in the personal summary.
    const reviews = summary.reviews.filter(review => review.username !== state.user?.username);
    container.replaceChildren(...reviews.map(review => {
      const card = el('article', 'community-review');
      card.append(el('strong', null, review.displayName + ' · @' + review.username));
      if (review.rating) card.append(ratingDisplay(review.rating));
      if (review.review) card.append(el('p', 'review-text', review.review));
      if(review.review)card.append(reviewLikeButton(review));
      card.append(el('p', 'price-note', 'Atualizada em ' + new Date(review.updatedAt).toLocaleDateString('pt-BR')));
      return card;
    }));
    if (!reviews.length) container.append(el('p', 'muted', 'Ainda não há avaliações de outros usuários visíveis para você.'));
    if (summary.ratingCount > 50 || summary.reviewCount > 50) container.append(el('p', 'price-note', 'Mostrando as 50 avaliações mais recentes. A média considera todas as notas visíveis.'));
  } catch (error) {
    if (request === state.communitySequence && sequence === state.detailSequence && $('#detail').open) $('#community-average').textContent = error.message;
  }
}
async function quickToggle(game, field) {
  if (!state.user) { openAuth(); toast('Entre para salvar seus jogos.'); return; }
  if (state.saving || state.quickSaving.has(game.id)) return;
  const account = state.user;
  const entry = state.diary.get(game.id);
  const body = { status: entry?.status || 'WANT_TO_PLAY', rating: entry?.rating ?? null,
    review: entry?.review || '', favorite: !!entry?.favorite, wishlist: !!entry?.wishlist,
    startDate: entry?.startDate || null, finishDate: entry?.finishDate || null };
  body[field] = !body[field]; state.quickSaving.add(game.id); render();
  try {
    const saved = await api(`/api/diary/${game.id}`, { method: 'PUT', body: JSON.stringify(body) });
    if (state.user !== account) return;
    state.diary.set(game.id, saved);
    toast(body[field] ? (field === 'favorite' ? 'Adicionado aos favoritos' : 'Adicionado à lista de desejos') :
      (field === 'favorite' ? 'Removido dos favoritos' : 'Removido da lista de desejos'));
  } catch (error) { toast(error.message); }
  finally { state.quickSaving.delete(game.id); render();if(state.user===account)$('#games').querySelector(`[data-game-id="${game.id}"] [data-action="${field}"]`)?.focus(); }
}
function renderPrices(quote) {
  const container=$('#prices');container.replaceChildren();$('#price-source').href=quote.sourceUrl;
  if(quote.status!=='LIVE'){container.append(el('p','muted',quote.message));return;}
  const money=value=>value==null?'Sem oferta':new Intl.NumberFormat('pt-BR',{style:'currency',currency:quote.currency}).format(value);
  for(const [label,value] of [['Lojas oficiais · menor preço',quote.currentRetail],['Keyshops · menor preço',quote.currentKeyshops],['Mínimo histórico · oficiais',quote.historicalRetail],['Mínimo histórico · keyshops',quote.historicalKeyshops]]){
    const row=el('div','price-row');row.append(el('span',null,label),el('strong',null,money(value)));container.append(row);
  }
  container.append(el('p','price-note',`Dados por GG.deals · Consultado em ${new Date(quote.fetchedAt).toLocaleString('pt-BR')} · Cache de até 1 hora.`));
}
let toastTimeout;
function toast(message) { $('#toast').textContent = message; $('#toast').hidden = false;
  clearTimeout(toastTimeout); toastTimeout = setTimeout(() => { $('#toast').hidden = true; }, 3500); }
function lockForm(locked) {
  state.saving = locked;
  $('#diary-form button[type="submit"]').textContent=locked?'Salvando…':'Salvar no diário';
  $('#diary-form').querySelectorAll('button,input,select,textarea').forEach(node => { node.disabled = locked; });
  $('#rating-picker').setAttribute('aria-disabled',String(locked));
  $('#clear-rating').disabled=locked||!$('#rating').value;
  paintRatingPicker($('#rating').value);
  $('#close-detail').disabled = locked; $('#cancel-review-edit').disabled = locked;
}
$('#diary-form').addEventListener('submit', async event => {
  event.preventDefault(); if (state.saving) return;
  if (!state.user) { openAuth(); return; }
  const gameId = state.selected.id;
  const body = { status: $('#play-status').value, rating: $('#rating').value ? Number($('#rating').value) : null,
    review: $('#review').value, favorite: $('#favorite').checked, wishlist:$('#diary-wishlist').checked,startDate:$('#start-date').value||null,finishDate:$('#finish-date').value||null };
  lockForm(true);
  try {
    const entry = await api(`/api/diary/${gameId}`, { method: 'PUT', body: JSON.stringify(body) });
    state.diary.set(gameId, entry); render(); $('#remove-entry').hidden = false;
    renderDiarySummary(entry); loadCommunity(gameId, state.detailSequence);
    $('#form-message').textContent = 'Sua experiência foi salva.'; toast('Diário atualizado');
  } catch (error) { $('#form-message').textContent = error.message; }
  finally { lockForm(false); }
});
$('#remove-entry').addEventListener('click', async () => {
  if (state.saving || !confirm('Remover este jogo do diário? A nota e a avaliação serão apagadas.')) return;
  const gameId = state.selected.id; lockForm(true);
  try { await api(`/api/diary/${gameId}`, { method: 'DELETE' }); state.diary.delete(gameId); render();
    $('#detail').close(); toast('Jogo removido do diário'); }
  catch (error) { $('#form-message').textContent = error.message; }
  finally { lockForm(false); }
});
$('#close-detail').addEventListener('click', () => $('#detail').close());
$('#detail').addEventListener('cancel', event => { if (state.saving) event.preventDefault(); });
$('#detail').addEventListener('close', () => ++state.detailSequence);
document.querySelectorAll('.nav').forEach(button => button.addEventListener('click', () => setView(button.dataset.view)));
$('#open-library').addEventListener('click', () => setView('library'));
$('#journey-library').addEventListener('click', () => setView('library'));
$('#featured').addEventListener('click',()=>{const game=heroGames[heroIndex];if(game)openDetail(game);});
let searchTimeout;
$('#search').addEventListener('input', () => { ++state.searchSequence; clearTimeout(searchTimeout); searchTimeout = setTimeout(loadGames, 250); });
['#platform', '#genre'].forEach(selector => $(selector).addEventListener('change', loadGames));
function updateAccountUi() {
  $('#account-login').hidden = !!state.user;
  $('#account-logout').hidden = !state.user;
  $('#account-name').hidden = !state.user;
  $('#account-name').textContent = state.user ? state.user.displayName : '';
  $('#account-name').title = state.user?.role === 'ADMIN' ? 'Administrador' : 'Usuário';
  $('#add-game').hidden = state.user?.role !== 'ADMIN';
  $('#import-games').hidden=state.user?.role!=='ADMIN';$('#metadata-import').hidden=state.user?.role!=='ADMIN';$('#admin-nav').hidden=state.user?.role!=='ADMIN';
}
function resetAccount() {
  closeProfileSettings();
  resetFeed();state.gamerDirty=false;state.profileBannerUrl=null;state.profileGames=[];$('#profile-banner').replaceChildren();$('#profile-featured').replaceChildren();$('#featured-pickers').replaceChildren();
  state.user = null; state.csrf = null; state.diary.clear(); state.view = 'catalog';
  state.profile=null;state.profileDirty=false;state.lists=[];++state.workspaceSequence;
  resetSocial();resetAdmin();resetBulk();
  $('#profile-form').reset();$('#change-password-form').reset();$('#profile-reviews').replaceChildren();$('#profile-summary').replaceChildren();$('#profile-stats').replaceChildren();$('#lists-content').replaceChildren();
  ++state.detailSequence;
  $('#review').value = ''; setRating(null); $('#favorite').checked = false;
  $('#detail').close(); $('#admin-dialog').close();
  document.querySelectorAll('.nav').forEach(button => button.classList.toggle('active', button.dataset.view === 'catalog'));
  updateAccountUi(); render();
}
async function syncSession() {
  const session = await api('/api/auth/me');
  state.user = session.authenticated ? session.user : null;
  state.diary.clear();
  if (state.user) {
    const entries = await api('/api/diary'); state.diary = new Map(entries.map(entry => [entry.gameId, entry]));
    state.lists=await api('/api/lists');
  }
  updateAccountUi();
}
function setAuthMode(mode) {
  state.authMode = mode;
  const register = mode === 'register';
  $('#auth-title').textContent = register ? 'Crie sua conta' : 'Entre no CritPlay';
  $('#auth-submit').textContent = register ? 'Criar conta' : 'Entrar';
  $('#display-name-label').hidden = !register; $('#display-name').required = register;
  $('#password-hint').hidden = !register; $('#password').minLength = register ? 10 : 1;
  $('#password').autocomplete = register ? 'new-password' : 'current-password';
  $('#login-tab').setAttribute('aria-pressed', String(!register));
  $('#register-tab').setAttribute('aria-pressed', String(register));
  $('#auth-message').textContent = '';
}
function openAuth() {
  if (state.saving) return;
  $('#detail').close(); setAuthMode('login'); $('#password').value = '';
  if (!$('#auth-dialog').open) $('#auth-dialog').showModal();
}
$('#account-login').addEventListener('click', openAuth);
$('#diary-login').addEventListener('click', openAuth);
$('#login-tab').addEventListener('click', () => { if (!state.authenticating) setAuthMode('login'); });
$('#register-tab').addEventListener('click', () => { if (!state.authenticating) setAuthMode('register'); });
$('#close-auth').addEventListener('click', () => $('#auth-dialog').close());
$('#auth-dialog').addEventListener('cancel', event => { if (state.authenticating) event.preventDefault(); });
$('#auth-form').addEventListener('submit', async event => {
  event.preventDefault(); if (state.authenticating) return;
  const username = $('#username').value.trim(); const password = $('#password').value;
  const displayName = $('#display-name').value.trim();
  if (new TextEncoder().encode(password).length > 72) { $('#auth-message').textContent = 'A senha excedeu o limite de 72 bytes. Use uma senha menor.'; return; }
  state.authenticating = true;
  const controls = $('#auth-dialog').querySelectorAll('button,input'); controls.forEach(node => { node.disabled = true; });
  try {
    if (state.authMode === 'register') await api('/api/auth/register', {
      method: 'POST', body: JSON.stringify({ username, password, displayName })
    });
    await api('/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ username, password }).toString() });
    state.csrf = null; await csrfToken(); await syncSession();
    $('#password').value = ''; $('#auth-dialog').close(); render(); toast('Bem-vindo ao CritPlay!');
  } catch (error) { $('#auth-message').textContent = error.message; }
  finally { state.authenticating = false; controls.forEach(node => { node.disabled = false; }); }
});
$('#account-logout').addEventListener('click', async () => {
  if (state.saving || state.addingGame || workspaceBusy) return;
  $('#account-logout').disabled = true;
  try { await api('/api/auth/logout', { method: 'POST' }); resetAccount(); await csrfToken(); toast('Você saiu da sua conta.'); }
  catch (error) { toast(error.message); }
  finally { $('#account-logout').disabled = false; }
});
$('#add-game').addEventListener('click', () => {
  if (state.user?.role !== 'ADMIN') return;
  openGameEditor(null);
});
$('#close-admin').addEventListener('click', () => $('#admin-dialog').close());
$('#admin-dialog').addEventListener('cancel', event => { if (state.addingGame) event.preventDefault(); });
$('#game-form').addEventListener('submit', async event => {
  event.preventDefault(); if (state.addingGame) return;
  const platforms = [...$('#game-form').querySelectorAll('input[name="game-platform"]:checked')].map(node => node.value);
  if (!platforms.length) { $('#game-message').textContent = 'Selecione ao menos uma plataforma.'; return; }
  const steamAppId = $('#game-steam-id').value ? Number($('#game-steam-id').value) : null;
  if (steamAppId && (!Number.isSafeInteger(steamAppId) || !platforms.includes('PC'))) {
    $('#game-message').textContent = 'Informe um Steam App ID válido e selecione PC.'; return;
  }
  const body = { title: $('#game-title').value.trim(), genre: $('#game-genre').value.trim(), platforms,
    releaseYear: Number($('#game-year').value), steamAppId, description: $('#game-description').value.trim(),coverUrl:state.gameCoverUrl,
    storeLinks:{playstation:$('#game-playstation').value.trim()||null,xbox:$('#game-xbox').value.trim()||null,nintendo:$('#game-nintendo').value.trim()||null} };
  state.addingGame = true;
  const controls = $('#admin-dialog').querySelectorAll('button,input,textarea'); controls.forEach(node => { node.disabled = true; });
  try {
    if(metadataSelectedId)await api('/api/admin/metadata/import',{method:'POST',body:JSON.stringify({rawgId:metadataSelectedId,game:body,targetGameId:state.editGameId})});
    else await api(state.editGameId?`/api/games/${state.editGameId}`:'/api/games', { method: state.editGameId?'PUT':'POST', body: JSON.stringify(body) });
    $('#admin-dialog').close(); $('#search').value = ''; $('#platform').value = ''; $('#genre').value = '';
    state.view = 'catalog'; setView('catalog'); await loadGames(); toast(state.editGameId?'Jogo atualizado.':'Novo jogo adicionado ao catálogo.');
  } catch (error) { $('#game-message').textContent = error.message; }
  finally { state.addingGame = false; controls.forEach(node => { node.disabled = false; }); }
});
async function init() {
  try { await csrfToken(); await syncSession(); await loadGames(); }
  catch (error) { $('#catalog-message').textContent = error.message; toast('Recarregue a página para tentar novamente.'); }
}
init();

let heroGames=[],heroIndex=0,heroTimer=null,heroPaused=false,heroSignature='',heroPointer=null;
const heroMotion=window.matchMedia('(prefers-reduced-motion: reduce)');
function heroImage(game){return game.coverUrl||(game.steamAppId?`https://cdn.akamai.steamstatic.com/steam/apps/${game.steamAppId}/library_hero.jpg`:game.metadataCoverUrl);}
function updateHeroGames(games){
  const selected=games.filter(heroImage).slice(0,6);const signature=JSON.stringify(selected.map(g=>[g.id,g.title,heroImage(g)]));
  if(signature===heroSignature)return;heroSignature=signature;heroGames=selected;heroIndex=0;
  $('#hero-track').replaceChildren(...selected.map(game=>{const slide=el('div','hero-slide');slide.style.backgroundImage='url('+JSON.stringify(heroImage(game))+')';return slide;}));
  $('#hero-dots').replaceChildren(...selected.map((game,index)=>{const dot=el('button','hero-dot');dot.type='button';dot.setAttribute('aria-label','Mostrar '+game.title);dot.addEventListener('click',()=>showHero(index));return dot;}));
  $('#hero-prev').hidden=$('#hero-next').hidden=$('#hero-pause').hidden=selected.length<2;
  showHero(0);
}
function showHero(index){
  if(!heroGames.length)return;heroIndex=(index+heroGames.length)%heroGames.length;const game=heroGames[heroIndex];
  $('#hero-track').style.transform=`translateX(-${heroIndex*100}%)`;$('#hero-title').textContent=game.title;
  $('#featured').textContent='Explorar '+game.title+' ↗';
  $('#hero-meta').replaceChildren(el('span',null,'EM DESTAQUE'),document.createTextNode(' '+game.title+' · '+game.genre+' · '+game.releaseYear));
  [...$('#hero-dots').children].forEach((dot,i)=>{dot.classList.toggle('active',i===heroIndex);dot.setAttribute('aria-current',String(i===heroIndex));});scheduleHero();
}
function scheduleHero(){
  clearTimeout(heroTimer);
  if(heroGames.length<2||heroPaused||heroMotion.matches||document.hidden||state.view!=='catalog'||$('.hero').matches(':hover')||$('.hero').contains(document.activeElement)||document.querySelector('dialog[open]'))return;
  heroTimer=setTimeout(()=>showHero(heroIndex+1),8000);
}
$('#hero-prev').addEventListener('click',()=>showHero(heroIndex-1));$('#hero-next').addEventListener('click',()=>showHero(heroIndex+1));
$('#hero-pause').addEventListener('click',()=>{heroPaused=!heroPaused;$('#hero-pause').textContent=heroPaused?'Retomar destaques':'Pausar destaques';$('#hero-pause').setAttribute('aria-pressed',String(heroPaused));scheduleHero();});
$('.hero').addEventListener('pointerenter',()=>clearTimeout(heroTimer));$('.hero').addEventListener('pointerleave',scheduleHero);
$('.hero').addEventListener('focusin',()=>clearTimeout(heroTimer));$('.hero').addEventListener('focusout',()=>setTimeout(scheduleHero,0));
$('.hero').addEventListener('pointerdown',event=>{if(event.pointerType==='touch'&&!event.target.closest('button'))heroPointer={x:event.clientX,y:event.clientY};});
$('.hero').addEventListener('pointerup',event=>{if(!heroPointer)return;const dx=event.clientX-heroPointer.x,dy=event.clientY-heroPointer.y;heroPointer=null;if(Math.abs(dx)>60&&Math.abs(dx)>Math.abs(dy))showHero(heroIndex+(dx<0?1:-1));});
$('.hero').addEventListener('pointercancel',()=>heroPointer=null);
heroMotion.addEventListener('change',scheduleHero);document.addEventListener('visibilitychange',scheduleHero);
document.addEventListener('click',()=>setTimeout(scheduleHero,0));

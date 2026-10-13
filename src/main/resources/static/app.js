'use strict';
const $ = selector => document.querySelector(selector);
const statusLabels = { WANT_TO_PLAY: 'Quero jogar', PLAYING: 'Jogando', COMPLETED: 'Finalizado', DROPPED: 'Abandonado' };
const state = { games: [], diary: new Map(), view: 'catalog', selected: null, searchSequence: 0, detailSequence: 0, saving: false, user: null, csrf: null, authMode: 'login', authenticating: false, addingGame: false, lists: [], profile: null, editGameId: null, gameCoverUrl: null, profileAvatarUrl: null, workspaceSequence: 0 };
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
    render();
  } catch (error) {
    if (sequence !== state.searchSequence) return;
    $('#games').replaceChildren(); $('#count').textContent = '';
    $('#catalog-message').textContent = error.message;
  }
}
function render() {
  const personal=['profile','lists'].includes(state.view);
  $('.workspace:not(.personal-workspace)').hidden=personal;
  $('.hero').hidden=personal;$('.journey').hidden=personal;
  $('#profile-section').hidden=state.view!=='profile';$('#lists-section').hidden=state.view!=='lists';
  if(personal) {refreshWorkspace(state.view);return;}
  const games = state.games.filter(game => state.view === 'catalog' ||
    (state.view === 'library' ? state.diary.has(game.id) : state.view==='wishlist'?state.diary.get(game.id)?.wishlist:state.diary.get(game.id)?.favorite));
  $('#section-title').textContent = { catalog: 'Explore o catálogo', library: 'Sua biblioteca de jogos', favorites: 'Seus jogos favoritos', wishlist:'Sua lista de desejos' }[state.view];
  $('#count').textContent = `${games.length} ${games.length === 1 ? 'jogo' : 'jogos'}`;
  $('#catalog-message').textContent = games.length ? '' : 'Nenhum jogo por aqui. Experimente outros filtros ou salve um jogo no seu diário.';
  $('#games').replaceChildren(...games.map(game => {
    const entry = state.diary.get(game.id);
    const card = el('button', 'card'); card.type = 'button';
    card.setAttribute('aria-label', 'Abrir ' + game.title);
    const cover = el('div', 'cover');
    const image = el('img'); image.alt = ''; image.loading = 'lazy';
    if(game.coverUrl) image.src=game.coverUrl;else if (game.steamAppId) image.src = `https://cdn.akamai.steamstatic.com/steam/apps/${game.steamAppId}/library_600x900.jpg`;
    image.addEventListener('error', () => { image.remove(); cover.prepend(el('span', 'image-fallback', game.title)); }, { once: true });
    if (game.coverUrl||game.steamAppId) cover.append(image); else cover.append(el('span', 'image-fallback', game.title));
    if (entry) cover.append(el('span', 'badge', `${entry.favorite ? '♥ · ' : ''}${statusLabels[entry.status]}`));
    card.append(cover, el('h3', null, game.title), el('p', null, `${game.genre} · ${game.releaseYear}`));
    if (entry?.rating) card.append(el('p', 'personal-rating', '★'.repeat(entry.rating) + ' · Sua nota'));
    card.addEventListener('click', () => openDetail(game));
    return card;
  }));
}
function setView(view) {
  if (view !== 'catalog' && !state.user) { openAuth(); toast('Entre para acessar sua biblioteca.'); return; }
  state.view = view;
  ++state.workspaceSequence;
  document.querySelectorAll('.nav').forEach(button => { const active = button.dataset.view === view;
    button.classList.toggle('active', active); button.setAttribute('aria-pressed', String(active)); });
  render(); (view==='profile'?$('#profile-section'):view==='lists'?$('#lists-section'):$('.workspace')).scrollIntoView({ behavior: 'smooth', block: 'start' });
}
async function openDetail(game) {
  if (state.saving) return;
  state.selected = game;
  const sequence = ++state.detailSequence;
  const entry = state.diary.get(game.id);
  $('#detail-title').textContent = game.title;
  $('#detail-meta').textContent = `${game.genre} · ${game.releaseYear} · ${game.platforms}`;
  $('#detail-description').textContent = game.description;
  $('#detail-cover').style.backgroundImage = game.coverUrl?`linear-gradient(0deg,#101114,transparent),url("${game.coverUrl}")`:game.steamAppId
    ? `linear-gradient(0deg,#101114,transparent),url("https://cdn.akamai.steamstatic.com/steam/apps/${game.steamAppId}/library_hero.jpg")`
    : 'linear-gradient(135deg,#314329,#242731)';
  $('#diary-form').hidden = !state.user;
  $('#diary-login-hint').hidden = !!state.user;
  $('#play-status').value = entry?.status || 'WANT_TO_PLAY';
  $('#rating').value = entry?.rating || '';
  $('#review').value = entry?.review || '';
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
  if (!state.user) { $('#prices').textContent = 'Entre na sua conta para consultar os preços.'; return; }
  try {
    const quote = await api(`/api/games/${game.id}/prices`);
    if (sequence !== state.detailSequence || !$('#detail').open) return;
    renderPrices(quote);
  } catch (error) {
    if (sequence === state.detailSequence) $('#prices').textContent = error.message;
  }
}
function renderPrices(quote) {
  const container = $('#prices'); container.replaceChildren();
  $('#price-source').href = quote.sourceUrl;
  if (quote.status !== 'LIVE') { container.append(el('p', 'muted', quote.message)); return; }
  const money = value => value == null ? 'Sem oferta' : new Intl.NumberFormat('pt-BR', {
    style: 'currency', currency: quote.currency
  }).format(value);
  const rows = [ ['Lojas oficiais · menor preço', quote.currentRetail], ['Keyshops · menor preço', quote.currentKeyshops],
    ['Mínimo histórico · oficiais', quote.historicalRetail], ['Mínimo histórico · keyshops', quote.historicalKeyshops] ];
  rows.forEach(([label, value]) => {
    const row = el('div', 'price-row'); row.append(el('span', null, label), el('strong', null, money(value))); container.append(row);
  });
  container.append(el('p', 'price-note', `Dados por GG.deals · Consultados em ${new Date(quote.fetchedAt).toLocaleString('pt-BR')}. Cache de até 1 hora.`));
}
let toastTimeout;
function toast(message) { $('#toast').textContent = message; $('#toast').hidden = false;
  clearTimeout(toastTimeout); toastTimeout = setTimeout(() => { $('#toast').hidden = true; }, 3500); }
function lockForm(locked) {
  state.saving = locked;
  $('#diary-form').querySelectorAll('button,input,select,textarea').forEach(node => { node.disabled = locked; });
  $('#close-detail').disabled = locked;
}
$('#diary-form').addEventListener('submit', async event => {
  event.preventDefault(); if (state.saving) return;
  if (!state.user) { openAuth(); return; }
  const gameId = state.selected.id;
  const body = { status: $('#play-status').value, rating: $('#rating').value ? Number($('#rating').value) : null,
    review: $('#review').value, favorite: $('#favorite').checked, wishlist:$('#diary-wishlist').checked };
  lockForm(true);
  try {
    const entry = await api(`/api/diary/${gameId}`, { method: 'PUT', body: JSON.stringify(body) });
    state.diary.set(gameId, entry); render(); $('#remove-entry').hidden = false;
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
$('#featured').addEventListener('click', async () => { try { openDetail(await api('/api/games/1')); } catch (error) { toast(error.message); } });
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
  $('#import-games').hidden=state.user?.role!=='ADMIN';
}
function resetAccount() {
  state.user = null; state.csrf = null; state.diary.clear(); state.view = 'catalog';
  state.profile=null;state.lists=[];++state.workspaceSequence;
  $('#profile-form').reset();$('#change-password-form').reset();$('#profile-reviews').replaceChildren();$('#profile-summary').replaceChildren();$('#profile-stats').replaceChildren();$('#lists-content').replaceChildren();
  ++state.detailSequence;
  $('#review').value = ''; $('#rating').value = ''; $('#favorite').checked = false;
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
  $('#auth-title').textContent = register ? 'Crie sua conta' : 'Entre no GameShelf';
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
    $('#password').value = ''; $('#auth-dialog').close(); render(); toast('Bem-vindo ao GameShelf!');
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
    await api(state.editGameId?`/api/games/${state.editGameId}`:'/api/games', { method: state.editGameId?'PUT':'POST', body: JSON.stringify(body) });
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

'use strict';

let feedSequence = 0;
let feedCursor = null;
let feedBusy = false;

const feedActionLabels = {
  REVIEW: 'publicou uma resenha',
  RATING: 'avaliou um jogo',
};
const feedStatusLabels = {
  COMPLETED: 'finalizou um jogo',
  PLAYING: 'começou uma nova aventura',
};

function resetFeed() {
  ++feedSequence;
  feedCursor = null;
  feedBusy = false;
  $('#feed-items').replaceChildren();
  $('#feed-more').hidden = true;
  $('#feed-message').textContent = '';
}

function renderFeedItem(item) {
  const card = el('article', 'surface feed-card');
  const actor = button(
    item.displayName + ' · @' + item.username,
    'text-button feed-actor',
    () => openPublicProfile(item.username),
  );
  const action = feedActionLabels[item.action]
    || feedStatusLabels[item.status]
    || 'atualizou sua experiência';
  card.append(actor, el('p', 'muted', action), compactGame(item.game));
  if (item.rating) card.append(ratingDisplay(item.rating));
  card.append(el('p', 'price-note', new Date(item.createdAt).toLocaleString('pt-BR')));
  return card;
}

function renderEmptyFeed() {
  const empty = el('div', 'games-empty');
  empty.append(
    el('h3', null, 'Toda comunidade começa com uma conexão'),
    el('p', 'muted', 'Registre uma experiência ou siga outros jogadores para ver novas histórias por aqui.'),
    button('Encontrar jogadores', 'primary', () => setView('people')),
  );
  return empty;
}

async function refreshFeed(append = false) {
  if (!state.user || (append && (feedBusy || !feedCursor))) return;
  const account = state.user;
  const sequence = ++feedSequence;
  feedBusy = true;
  $('#refresh-feed').disabled = true;
  $('#feed-more').disabled = true;
  $('#feed-message').textContent = 'Buscando novas histórias…';
  if (!append) $('#feed-items').replaceChildren();

  try {
    const page = await api('/api/feed' + (append ? '?before=' + feedCursor : ''));
    if (sequence !== feedSequence || state.view !== 'feed' || state.user !== account) return;
    for (const item of page.items) $('#feed-items').append(renderFeedItem(item));
    feedCursor = page.nextCursor;
    $('#feed-more').hidden = !feedCursor;
    $('#feed-message').textContent = '';
    if (!$('#feed-items').children.length) $('#feed-items').append(renderEmptyFeed());
  } catch (error) {
    if (sequence === feedSequence) $('#feed-message').textContent = error.message;
  } finally {
    if (sequence === feedSequence) {
      feedBusy = false;
      $('#refresh-feed').disabled = false;
      $('#feed-more').disabled = false;
    }
  }
}

function renderFeaturedGames(container, games) {
  container.replaceChildren();
  for (const game of games || []) {
    const card = button('', 'featured-game', () => openDetail(game));
    const image = gameCoverImage(game);
    card.append(image || el('span', 'featured-fallback', game.title));
    card.append(el('strong', null, game.title));
    container.append(card);
  }
  if (!games?.length) {
    container.append(el('p', 'muted', 'Os jogos que representam este perfil aparecerão aqui.'));
  }
}

function featuredGamePicker(index, games, selected) {
  const label = el('label', null, 'Destaque ' + (index + 1));
  const picker = el('select');
  picker.id = 'featured-game-' + index;
  const empty = el('option', null, 'Sem destaque');
  empty.value = '';
  picker.append(empty);
  for (const game of games) {
    const option = el('option', null, game.title);
    option.value = game.id;
    picker.append(option);
  }
  picker.value = selected[index] || '';
  label.append(picker);
  return label;
}

function renderGamerProfile(user) {
  const banner = $('#profile-banner');
  banner.replaceChildren();
  banner.append(user.bannerUrl
    ? identityImage(user.bannerUrl, 'Banner de ' + user.displayName)
    : el('span', null, 'CADA JOGO, UMA NOVA HISTÓRIA'));
  renderFeaturedGames($('#profile-featured'), user.featuredGames);
  if (state.gamerDirty) return;

  state.profileBannerUrl = user.bannerUrl || null;
  const selected = (user.featuredGames || []).map(game => game.id);
  const catalog = [...new Map(
    [...state.profileGames, ...(user.featuredGames || [])].map(game => [game.id, game]),
  ).values()].sort((first, second) => first.title.localeCompare(second.title, 'pt-BR'));
  $('#featured-pickers').replaceChildren(
    ...[0, 1, 2, 3].map(index => featuredGamePicker(index, catalog, selected)),
  );
}

async function saveGamerProfile(event) {
  event.preventDefault();
  if (workspaceBusy) return;
  const ids = [...$('#featured-pickers').querySelectorAll('select')]
    .map(node => node.value).filter(Boolean).map(Number);
  if (new Set(ids).size !== ids.length) {
    $('#gamer-message').textContent = 'Escolha quatro jogos diferentes.';
    return;
  }
  workspaceBusy = true;
  busy($('#gamer-form'), true);
  $('#gamer-message').textContent = 'Salvando identidade…';
  try {
    const profile = await api('/api/profile/gamer', {
      method: 'PUT',
      body: JSON.stringify({ bannerUrl: state.profileBannerUrl, featuredGameIds: ids }),
    });
    state.profile = profile;
    state.gamerDirty = false;
    renderProfile(profile);
    $('#gamer-message').textContent = 'Identidade gamer salva.';
    toast('Seu perfil ganhou uma nova identidade');
  } catch (error) {
    $('#gamer-message').textContent = error.message;
  } finally {
    workspaceBusy = false;
    busy($('#gamer-form'), false);
  }
}

async function uploadProfileBanner() {
  if (workspaceBusy) return;
  const file = $('#profile-banner-file').files[0];
  if (!file) return;
  workspaceBusy = true;
  busy($('#gamer-form'), true);
  try {
    state.profileBannerUrl = (await uploadImage(file, true)).url;
    state.gamerDirty = true;
    $('#gamer-message').textContent = 'Banner enviado. Salve a identidade gamer para aplicar.';
  } catch (error) {
    $('#gamer-message').textContent = error.message;
  } finally {
    workspaceBusy = false;
    busy($('#gamer-form'), false);
    $('#profile-banner-file').value = '';
  }
}

function removeProfileBanner() {
  state.profileBannerUrl = null;
  state.gamerDirty = true;
  $('#gamer-message').textContent = 'Salve a identidade gamer para remover o banner.';
}

$('#refresh-feed').addEventListener('click', () => refreshFeed());
$('#feed-more').addEventListener('click', () => refreshFeed(true));
$('#gamer-form').addEventListener('input', () => state.gamerDirty = true);
$('#gamer-form').addEventListener('submit', saveGamerProfile);
$('#profile-banner-file').addEventListener('change', uploadProfileBanner);
$('#remove-banner').addEventListener('click', removeProfileBanner);

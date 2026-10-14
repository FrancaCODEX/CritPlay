'use strict';
let editingListId=null,resetToken=null,workspaceBusy=false;
function button(text,className,action) {const node=el('button',className,text);node.type='button';node.addEventListener('click',action);return node;}
function busy(form,value) {form.querySelectorAll('button,input,textarea,select').forEach(node=>node.disabled=value);}
function compactGame(game,action) {
  const card=button('', 'mini-game',action||(()=>openDetail(game)));
  const image=gameCoverImage(game);if(image)card.append(image);
  card.append(el('strong',null,game.title),el('span','muted',game.genre));return card;
}
async function refreshWorkspace(view) {
  const sequence=++state.workspaceSequence;
  try {
    if(view==='profile') {
      busy($('#privacy-form'),true);
      const profile=await api('/api/profile');
      if(sequence!==state.workspaceSequence||state.view!==view)return;
      state.profile=profile;renderProfile(profile);renderPrivacy(profile.user);busy($('#privacy-form'),false);
    } else {
      const lists=await api('/api/lists');
      if(sequence!==state.workspaceSequence||state.view!==view)return;
      state.lists=lists;renderLists();
    }
  } catch(error) {if(sequence===state.workspaceSequence) toast(error.message);}
}
function renderProfile(profile) {
  const user=profile.user;const summary=$('#profile-summary');summary.replaceChildren();
  const avatar=el('div','avatar');
  if(user.avatarUrl) {const image=el('img');image.src=user.avatarUrl;image.alt='Foto de '+user.displayName;avatar.append(image);}
  else avatar.textContent=user.displayName.slice(0,2).toUpperCase();
  const info=el('div');info.append(el('h2',null,user.displayName),el('p','muted','@'+user.username+(user.role==='ADMIN'?' · Administrador':'')),el('p','profile-bio',user.bio||'Sua próxima história começa aqui.'));
  summary.append(avatar,info);
  const labels={library:'Jogos no diário',completed:'Finalizados',favorites:'Favoritos',wishlist:'Na lista de desejos',reviews:'Resenhas'};
  $('#profile-stats').replaceChildren(...Object.entries(labels).map(([key,label])=>{
    const node=el('div','stat');node.append(el('strong',null,String(profile.stats[key])),el('span',null,label));return node;
  }));
  if(!state.profileDirty) {
    $('#profile-name').value=user.displayName;$('#profile-bio').value=user.bio||'';$('#profile-email').value=user.email||'';
    $('#profile-current-password').value='';state.profileAvatarUrl=user.avatarUrl||null;
  }
  $('#review-count').textContent=profile.reviews.length+(profile.reviews.length===1?' resenha':' resenhas');
  const cards=profile.reviews.map(({game,entry})=>{
    const card=el('article','surface review-card');const head=el('div','review-head');
    head.append(compactGame(game),button('Editar resenha','text-button',()=>openDetail(game)));card.append(head);
    if(entry.rating)card.append(el('p','personal-rating','★'.repeat(entry.rating)+' · '+statusLabels[entry.status]));
    if(entry.reviewHidden)card.append(el('p','price-note','Resenha oculta pela moderação.'));
    card.append(el('p','review-text',entry.review),el('p','price-note','Atualizada em '+new Date(entry.updatedAt).toLocaleDateString('pt-BR')));return card;
  });
  $('#profile-reviews').replaceChildren(...(cards.length?cards:[el('p','muted','Suas resenhas aparecerão aqui. Abra um jogo e registre sua experiência.')]));
}
function renderLists() {
  const cards=state.lists.map(list=>{
    const card=el('article','surface list-card');const head=el('div','list-heading');
    const info=el('div');info.append(el('h3',null,list.name),el('p','muted',list.description||`${list.games.length} jogos nesta coleção`));
    const actions=el('div','list-actions');actions.append(button('Editar','text-button',()=>openListEditor(list)),button('Excluir','text-button',()=>deleteList(list)));
    head.append(info,actions);card.append(head);
    const games=el('div','list-games');
    list.games.forEach(game=>{
      const row=el('div','list-game-row');row.append(compactGame(game),button('Remover','text-button',()=>removeListGame(list.id,game.id)));games.append(row);
    });
    card.append(games);if(!list.games.length)card.append(el('p','price-note','Abra um jogo no catálogo para adicioná-lo a esta lista.'));
    return card;
  });
  $('#lists-content').replaceChildren(...(cards.length?cards:[el('p','muted','Crie sua primeira lista e reúna os jogos do seu jeito.')]));
  updateListPicker();
}
function updateListPicker() {
  $('#game-list-select').replaceChildren(...state.lists.map(list=>{const option=el('option',null,list.name);option.value=list.id;return option;}));
  $('#game-list-select').disabled=!state.lists.length;$('#add-to-list').disabled=!state.lists.length;
}
function openListEditor(list) {
  editingListId=list?.id||null;$('#list-form').reset();$('#list-name').value=list?.name||'';$('#list-description').value=list?.description||'';
  $('#list-dialog-title').textContent=list?'Editar lista':'Criar lista';$('#list-message').textContent='';$('#list-dialog').showModal();
}
$('#create-list').addEventListener('click',()=>openListEditor(null));
$('#close-list').addEventListener('click',()=>{if(!workspaceBusy)$('#list-dialog').close();});
$('#list-dialog').addEventListener('cancel',e=>{if(workspaceBusy)e.preventDefault();});
$('#list-form').addEventListener('submit',async e=>{
  e.preventDefault();if(workspaceBusy)return;workspaceBusy=true;busy($('#list-form'),true);
  try {
    await api(editingListId?`/api/lists/${editingListId}`:'/api/lists',{method:editingListId?'PUT':'POST',body:JSON.stringify({name:$('#list-name').value.trim(),description:$('#list-description').value.trim()})});
    $('#list-dialog').close();state.lists=await api('/api/lists');renderLists();toast('Lista salva.');
  }catch(error){$('#list-message').textContent=error.message;}
  finally{workspaceBusy=false;busy($('#list-form'),false);}
});
async function deleteList(list) {
  if(workspaceBusy||!confirm(`Excluir a lista “${list.name}”? Suas notas e resenhas serão mantidas.`))return;
  workspaceBusy=true;
  try{await api(`/api/lists/${list.id}`,{method:'DELETE'});state.lists=await api('/api/lists');renderLists();toast('Lista excluída.');}
  catch(error){toast(error.message);}finally{workspaceBusy=false;}
}
async function removeListGame(id,gameId) {
  if(workspaceBusy)return;workspaceBusy=true;
  try{await api(`/api/lists/${id}/games/${gameId}`,{method:'DELETE'});state.lists=await api('/api/lists');renderLists();}
  catch(error){toast(error.message);}finally{workspaceBusy=false;}
}
$('#add-to-list').addEventListener('click',async()=>{
  if(workspaceBusy||!state.selected||!$('#game-list-select').value)return;workspaceBusy=true;$('#add-to-list').disabled=true;
  try{await api(`/api/lists/${$('#game-list-select').value}/games/${state.selected.id}`,{method:'PUT'});state.lists=await api('/api/lists');toast('Jogo adicionado à lista.');}
  catch(error){toast(error.message);}finally{workspaceBusy=false;updateListPicker();}
});
$('#account-name').addEventListener('click',()=>setView('profile'));
$('#profile-form').addEventListener('input',()=>{state.profileDirty=true;});
$('#profile-form').addEventListener('submit',async e=>{
  e.preventDefault();if(workspaceBusy)return;workspaceBusy=true;busy($('#profile-form'),true);
  try {
    const profile=await api('/api/profile',{method:'PUT',body:JSON.stringify({displayName:$('#profile-name').value.trim(),bio:$('#profile-bio').value,
      email:$('#profile-email').value.trim()||null,avatarUrl:state.profileAvatarUrl,currentPassword:$('#profile-current-password').value||null})});
    state.user.displayName=profile.user.displayName;state.profile=profile;state.profileDirty=false;renderProfile(profile);updateAccountUi();$('#profile-message').textContent='Perfil atualizado.';
  }catch(error){$('#profile-message').textContent=error.message;}
  finally{workspaceBusy=false;busy($('#profile-form'),false);}
});
async function uploadImage(file) {
  if(!file||file.size>2*1024*1024)throw new Error('Escolha uma imagem PNG ou JPEG de até 2 MB.');
  const body=new FormData();body.append('file',file);return api('/api/images',{method:'POST',body});
}
$('#profile-avatar-file').addEventListener('change',async()=>{
  if(workspaceBusy)return;const file=$('#profile-avatar-file').files[0];if(!file)return;
  workspaceBusy=true;busy($('#profile-form'),true);
  try{state.profileAvatarUrl=(await uploadImage(file)).url;state.profileDirty=true;$('#profile-message').textContent='Foto enviada. Clique em Salvar perfil para aplicá-la.';}
  catch(error){$('#profile-message').textContent=error.message;}
  finally{workspaceBusy=false;busy($('#profile-form'),false);$('#profile-avatar-file').value='';}
});
$('#remove-avatar').addEventListener('click',()=>{state.profileAvatarUrl=null;state.profileDirty=true;$('#profile-message').textContent='Clique em Salvar perfil para remover a foto.';});
$('#change-password-form').addEventListener('submit',async e=>{
  e.preventDefault();if(workspaceBusy)return;
  if($('#new-password').value!==$('#confirm-password').value){$('#change-password-message').textContent='As novas senhas não são iguais.';return;}
  workspaceBusy=true;busy($('#change-password-form'),true);
  try{await api('/api/profile/password',{method:'PUT',body:JSON.stringify({currentPassword:$('#old-password').value,newPassword:$('#new-password').value})});
    resetAccount();await csrfToken();openAuth();toast('Senha alterada. Entre novamente.');}
  catch(error){$('#change-password-message').textContent=error.message;}
  finally{workspaceBusy=false;busy($('#change-password-form'),false);}
});
function openGameEditor(game) {
  if(state.addingGame)return;
  resetSteamDraft();metadataSelectedId=null;
  state.editGameId=game?.id||null;state.gameCoverUrl=game?.coverUrl||null;$('#game-form').reset();
  $('#admin-title').textContent=game?'Editar jogo':'Adicionar um jogo';$('#game-submit').textContent=game?'Salvar alterações':'Adicionar ao catálogo';
  $('#game-title').value=game?.title||'';$('#game-genre').value=game?.genre||'';$('#game-year').value=game?.releaseYear||new Date().getFullYear();
  $('#game-steam-id').value=game?.steamAppId||'';$('#game-description').value=game?.description||'';
  const platformLabels={PC:'PC',PLAYSTATION:'PlayStation',XBOX:'Xbox',NINTENDO:'Nintendo'};
  $('#game-form').querySelectorAll('[name="game-platform"]').forEach(input=>input.checked=game?game.platforms.split(', ').includes(platformLabels[input.value]):input.value==='PC');
  $('#game-playstation').value=game?.storeLinks?.playstation||'';$('#game-xbox').value=game?.storeLinks?.xbox||'';$('#game-nintendo').value=game?.storeLinks?.nintendo||'';
  $('#game-cover-status').textContent=state.gameCoverUrl?'Capa própria salva.':'PNG ou JPEG, até 2 MB.';$('#game-message').textContent='';$('#admin-dialog').showModal();
}
$('#edit-game').addEventListener('click',()=>{if(state.user?.role==='ADMIN'){$('#detail').close();openGameEditor(state.selected);}});
$('#game-cover-file').addEventListener('change',async()=>{
  if(state.addingGame)return;const file=$('#game-cover-file').files[0];if(!file)return;
  state.addingGame=true;busy($('#game-form'),true);$('#close-admin').disabled=true;
  try{state.gameCoverUrl=(await uploadImage(file)).url;$('#game-cover-status').textContent='Capa enviada. Salve o jogo para aplicá-la.';}
  catch(error){$('#game-message').textContent=error.message;}
  finally{state.addingGame=false;busy($('#game-form'),false);$('#close-admin').disabled=false;$('#game-cover-file').value='';}
});
$('#clear-game-cover').addEventListener('click',()=>{state.gameCoverUrl=null;$('#game-cover-status').textContent='A capa padrão será usada ao salvar.';});
function renderConsoleLinks(game) {
  const links=game.storeLinks||{};const labels={playstation:'PlayStation Store',xbox:'Loja Xbox',nintendo:'Nintendo Store'};
  $('#console-links').replaceChildren(...Object.entries(labels).filter(([key])=>links[key]).map(([key,label])=>{
    const anchor=el('a','source-link',label+' ↗');anchor.href=links[key];anchor.target='_blank';anchor.rel='noopener noreferrer';return anchor;
  }));
}
$('#import-games').addEventListener('click',()=>$('#import-file').click());
$('#import-file').addEventListener('change',async()=>{
  const file=$('#import-file').files[0];if(!file)return;
  try {
    if(file.size>1024*1024)throw new Error('O arquivo deve ter até 1 MB.');
    const data=JSON.parse(await file.text());
    if(!Array.isArray(data.games)||!data.games.length||data.games.length>50)throw new Error('Use o formato {"games":[...]}, com 1 a 50 jogos. Veja o exemplo no projeto.');
    if(!confirm(`Importar ${data.games.length} jogos?\n${data.games.slice(0,5).map(g=>g.title||'(sem título)').join('\n')}`))return;
    $('#import-games').disabled=true;await api('/api/games/import',{method:'POST',body:JSON.stringify(data)});
    $('#search').value='';$('#genre').value='';$('#platform').value='';setView('catalog');await loadGames();toast('Catálogo importado.');
  }catch(error){toast(error.message);}finally{$('#import-games').disabled=false;$('#import-file').value='';}
});
$('#forgot-password').addEventListener('click',async()=>{
  $('#auth-dialog').close();resetToken=null;$('#recovery-form').reset();$('#recovery-account-fields').hidden=false;$('#recovery-new-fields').hidden=true;
  $('#recovery-title').textContent='Recuperar senha';$('#recovery-submit').textContent='Enviar link';$('#recovery-message').textContent='';
  $('#recovery-username').required=true;$('#recovery-email').required=true;$('#recovery-new-password').required=false;$('#recovery-confirm-password').required=false;
  $('#recovery-dialog').showModal();$('#recovery-submit').disabled=true;
  try{const status=await api('/api/auth/recovery-status');$('#recovery-submit').disabled=!status.enabled;
    $('#recovery-hint').textContent=status.enabled?'Informe o usuário e o e-mail salvo no seu perfil.':'A recuperação por e-mail ainda não está configurada. Se você tem acesso à conta, pode trocar a senha em Perfil.';
  }catch(error){$('#recovery-message').textContent=error.message;}
});
$('#close-recovery').addEventListener('click',()=>{if(!workspaceBusy)$('#recovery-dialog').close();});
$('#recovery-dialog').addEventListener('cancel',e=>{if(workspaceBusy)e.preventDefault();});
$('#recovery-form').addEventListener('submit',async e=>{
  e.preventDefault();if(workspaceBusy)return;
  if(resetToken&&$('#recovery-new-password').value!==$('#recovery-confirm-password').value){$('#recovery-message').textContent='As senhas não são iguais.';return;}
  workspaceBusy=true;busy($('#recovery-form'),true);
  try {
    const result=await api(resetToken?'/api/auth/reset-password':'/api/auth/forgot-password',{method:'POST',body:JSON.stringify(resetToken
      ?{token:resetToken,newPassword:$('#recovery-new-password').value}:{username:$('#recovery-username').value.trim(),email:$('#recovery-email').value.trim()})});
    $('#recovery-message').textContent=result.message;
    if(resetToken){resetToken=null;$('#recovery-form').reset();$('#recovery-dialog').close();resetAccount();openAuth();toast(result.message);}
  }catch(error){$('#recovery-message').textContent=error.message;}
  finally{workspaceBusy=false;busy($('#recovery-form'),false);}
});
function openResetFromLink() {
  if(!location.hash.startsWith('#reset='))return;
  const token=location.hash.slice(7);history.replaceState(null,'',location.pathname+location.search);
  if(!/^[A-Za-z0-9_-]{43}$/.test(token)){toast('Link de recuperação inválido.');return;}
  resetToken=token;$('#recovery-title').textContent='Escolha uma nova senha';$('#recovery-hint').textContent='Este link tem validade de 15 minutos e pode ser usado apenas uma vez.';
  $('#recovery-account-fields').hidden=true;$('#recovery-new-fields').hidden=false;
  $('#recovery-username').required=false;$('#recovery-email').required=false;$('#recovery-new-password').required=true;$('#recovery-confirm-password').required=true;
  $('#recovery-submit').textContent='Salvar nova senha';$('#recovery-submit').disabled=false;$('#recovery-message').textContent='';$('#recovery-dialog').showModal();
}
openResetFromLink();

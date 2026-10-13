'use strict';
let publicUsername=null,publicSequence=0,socialBusy=false,consoleSources={},consoleGameId=null,consoleSequence=0;
const visibilityLabels={PUBLIC:'Público',FRIENDS:'Só amigos',PRIVATE:'Privado'};
function identityCard(user) {
  const card=button('','surface person-card',()=>openPublicProfile(user.username));
  const avatar=el('div','avatar');if(user.avatarUrl){const image=el('img');image.src=user.avatarUrl;image.alt='';avatar.append(image);}else avatar.textContent=user.displayName.slice(0,2).toUpperCase();
  const info=el('div');info.append(el('strong',null,user.displayName),el('p','muted','@'+user.username),el('p','price-note',visibilityLabels[user.visibility]));card.append(avatar,info);return card;
}
function resetSocial() {
  ++publicSequence;++consoleSequence;publicUsername=null;consoleGameId=null;consoleSources={};
  $('#public-profile-dialog').close();$('#console-source-dialog').close();
  for(const id of ['people-results','social-connections','public-profile-content','console-candidates'])$('#'+id).replaceChildren();
  $('#people-search-form').reset();$('#privacy-form').reset();$('#privacy-message').textContent='';$('#people-message').textContent='';
}
function renderPrivacy(user){$('#profile-visibility').value=user.visibility;$('#profile-discoverable').checked=user.discoverable;}
$('#privacy-form').addEventListener('submit',async event=>{
  event.preventDefault();if(socialBusy)return;socialBusy=true;busy($('#privacy-form'),true);
  try{const value=await api('/api/profile/privacy',{method:'PUT',body:JSON.stringify({visibility:$('#profile-visibility').value,discoverable:$('#profile-discoverable').checked})});
    if(state.profile)Object.assign(state.profile.user,value);$('#privacy-message').textContent='Privacidade atualizada.';
  }catch(error){$('#privacy-message').textContent=error.message;}finally{socialBusy=false;busy($('#privacy-form'),false);}
});
$('#preview-profile').addEventListener('click',()=>openPublicProfile(state.user.username));
$('#close-public-profile').addEventListener('click',()=>{$('#public-profile-dialog').close();++publicSequence;});
$('#public-profile-dialog').addEventListener('cancel',()=>++publicSequence);
async function searchPeople(sequence) {
  const query=$('#people-search').value.trim();if(query.length<2){$('#people-results').replaceChildren();$('#people-message').textContent='Digite pelo menos 2 caracteres para buscar.';return;}
  $('#people-message').textContent='Buscando jogadores…';
  const people=await api('/api/people?q='+encodeURIComponent(query));
  if(sequence!==state.workspaceSequence||state.view!=='people')return;
  $('#people-results').replaceChildren(...people.map(identityCard));$('#people-message').textContent=people.length?`${people.length} perfil(is) encontrado(s)`:'Nenhum perfil encontrado. A pessoa pode ter desativado a busca.';
}
async function refreshPeople(){
  const sequence=++state.workspaceSequence;
  try{await Promise.all([searchPeople(sequence),refreshConnections(sequence)]);}catch(error){if(sequence===state.workspaceSequence)$('#people-message').textContent=error.message;}
}
$('#people-search-form').addEventListener('submit',event=>{event.preventDefault();refreshPeople();});
async function refreshConnections(sequence=state.workspaceSequence){
  const social=await api('/api/social');if(sequence!==state.workspaceSequence||state.view!=='people')return;
  const groups={incoming:'Pedidos recebidos',outgoing:'Pedidos enviados',friends:'Amigos',following:'Seguindo',followers:'Seguidores'};
  $('#social-connections').replaceChildren(...Object.entries(groups).map(([key,label])=>{
    const section=el('section','social-group');section.append(el('h3',null,`${label} · ${social[key].length}`));const grid=el('div','people-grid');
    for(const user of social[key]){const card=el('article','surface connection-card');const identity=identityCard(user);identity.classList.remove('surface');card.append(identity);
      const actions=el('div','social-actions');
      if(key==='incoming')actions.append(button('Aceitar','primary',()=>socialAction(user.username,'ACCEPT')),button('Recusar','text-button',()=>socialAction(user.username,'REJECT')));
      if(key==='outgoing')actions.append(button('Cancelar pedido','text-button',()=>socialAction(user.username,'CANCEL')));
      if(key==='friends')actions.append(button('Desfazer amizade','text-button',()=>socialAction(user.username,'REMOVE')));
      if(key==='following')actions.append(button('Deixar de seguir','text-button',()=>socialAction(user.username,null,false)));
      card.append(actions);grid.append(card);
    }section.append(grid);if(!social[key].length)section.append(el('p','muted','Nenhuma conexão aqui ainda.'));return section;
  }));
}
async function socialAction(username,action,following){
  if(socialBusy)return;if(action==='REMOVE'&&!confirm('Desfazer esta amizade?'))return;socialBusy=true;
  const controls=[...document.querySelectorAll('#social-connections button,#public-profile-content button')];controls.forEach(node=>node.disabled=true);
  try{await api('/api/people/'+encodeURIComponent(username)+(action?'/friendship':'/follow'),{method:action||following?'PUT':'DELETE',...(action?{body:JSON.stringify({action})}:{})});
    if($('#public-profile-dialog').open&&publicUsername===username)await openPublicProfile(username);
    if(state.view==='people')await refreshPeople();toast(action==='REQUEST'?'Pedido enviado.':'Conexão atualizada.');
  }catch(error){toast(error.message);}finally{socialBusy=false;controls.forEach(node=>node.disabled=false);}
}
async function openPublicProfile(username){
  publicUsername=username;const sequence=++publicSequence;$('#public-profile-content').replaceChildren();$('#public-profile-message').textContent='Carregando perfil…';
  if(!$('#public-profile-dialog').open)$('#public-profile-dialog').showModal();
  try{const profile=await api('/api/people/'+encodeURIComponent(username));if(sequence!==publicSequence||!$('#public-profile-dialog').open)return;
    const user=profile.user;const content=$('#public-profile-content');const head=el('div','profile-summary');const identity=identityCard(user);identity.disabled=true;head.append(identity);content.append(head);
    $('#public-profile-title').textContent='Perfil de '+user.displayName;content.append(el('p','price-note','Visibilidade: '+visibilityLabels[user.visibility]));
    if(profile.visible){content.append(el('p','profile-bio',user.bio||'Ainda sem bio.'));
      const stats=el('div','stats-grid');const labels={library:'Jogos no diário',completed:'Finalizados',favorites:'Favoritos',wishlist:'Desejos',reviews:'Resenhas'};
      for(const [key,label]of Object.entries(labels)){const stat=el('div','stat');stat.append(el('strong',null,String(profile.stats[key])),el('span',null,label));stats.append(stat);}content.append(stats);
      content.append(el('p','muted',`${profile.social.followers} seguidores · ${profile.social.following} seguindo · ${profile.social.friends} amigos`));
    }else content.append(el('p','privacy-note',user.visibility==='FRIENDS'?'As resenhas e estatísticas estão disponíveis apenas para amigos aceitos.':'Este perfil mantém bio, resenhas e estatísticas privadas.'));
    const actions=el('div','social-actions');
    if(state.user&&state.user.username!==username){
      const relationship=profile.relationship;actions.append(button(relationship.following?'Deixar de seguir':'Seguir','secondary',()=>socialAction(username,null,!relationship.following)));
      if(relationship.friendship==='NONE')actions.append(button('Adicionar amigo','primary',()=>socialAction(username,'REQUEST')));
      if(relationship.friendship==='OUTGOING')actions.append(button('Cancelar pedido','secondary',()=>socialAction(username,'CANCEL')));
      if(relationship.friendship==='INCOMING')actions.append(button('Aceitar amizade','primary',()=>socialAction(username,'ACCEPT')),button('Recusar pedido','text-button',()=>socialAction(username,'REJECT')));
      if(relationship.friendship==='FRIENDS')actions.append(button('Desfazer amizade','text-button',()=>socialAction(username,'REMOVE')));
    }else if(!state.user)actions.append(button('Entrar para se conectar','primary',()=>{$('#public-profile-dialog').close();openAuth();}));
    const share=el('input');share.readOnly=true;share.value=location.origin+'/#profile='+encodeURIComponent(username);share.setAttribute('aria-label','Link deste perfil');
    actions.append(share);content.append(actions);
    if(profile.visible){const reviews=el('div','review-grid');content.append(el('h3',null,'Resenhas'));
      for(const review of profile.reviews){const card=el('article','surface review-card');card.append(compactGame(review.game));if(review.rating)card.append(el('p','personal-rating','★'.repeat(review.rating)));card.append(el('p','review-text',review.review));reviews.append(card);}content.append(reviews);
      if(!profile.reviews.length)content.append(el('p','muted','Nenhuma resenha publicada ainda.'));
    }
    $('#public-profile-message').textContent='';
  }catch(error){if(sequence===publicSequence)$('#public-profile-message').textContent=error.message;}
}
async function openConsoleSources(){
  const game=state.selected;if(!game||state.user?.role!=='ADMIN')return;consoleGameId=game.id;consoleSources={};const sequence=++consoleSequence;
  $('#detail').close();$('#console-source-query').value=game.title;$('#console-source-message').textContent='Carregando associações…';$('#console-candidates').replaceChildren();
  const labels={PLAYSTATION:'PlayStation',XBOX:'Xbox',NINTENDO:'Nintendo'};
  $('#console-source-platform').replaceChildren(...Object.entries(labels).filter(([,label])=>game.platforms.split(',').map(x=>x.trim()).includes(label)).map(([code,label])=>{const option=el('option',null,label);option.value=code;return option;}));
  $('#console-source-dialog').showModal();busy($('#console-search-form'),true);
  try{const data=await api(`/api/admin/games/${game.id}/price-sources`);if(sequence!==consoleSequence)return;consoleSources=data.sources;renderConsoleMappings();$('#console-source-message').textContent=data.configured?'Busque e selecione a edição exata.':'Configure PSPRICES_API_KEY no .env para consultar esta fonte.';}
  catch(error){$('#console-source-message').textContent=error.message;}finally{busy($('#console-search-form'),false);if(!$('#console-source-platform').options.length)busy($('#console-search-form'),true);}
}
function renderConsoleMappings(){$('#console-mappings').textContent=Object.entries(consoleSources).map(([platform,id])=>`${platform}: ${id}`).join(' · ')||'Nenhuma edição associada.';}
$('#configure-console-prices').addEventListener('click',openConsoleSources);
$('#close-console-source').addEventListener('click',()=>{if(!socialBusy){++consoleSequence;$('#console-source-dialog').close();}});
$('#console-source-dialog').addEventListener('cancel',event=>{if(socialBusy)event.preventDefault();else ++consoleSequence;});
async function saveConsoleSource(id){
  if(socialBusy)return;socialBusy=true;busy($('#console-search-form'),true);const platform=$('#console-source-platform').value;
  const sources={...consoleSources};if(id)sources[platform]=id;else delete sources[platform];
  try{const data=await api(`/api/admin/games/${consoleGameId}/price-sources`,{method:'PUT',body:JSON.stringify({sources})});consoleSources=data.sources;renderConsoleMappings();$('#console-source-message').textContent='Associação salva.';$('#console-candidates').replaceChildren();}
  catch(error){$('#console-source-message').textContent=error.message;}finally{socialBusy=false;busy($('#console-search-form'),false);}
}
$('#clear-console-source').addEventListener('click',()=>saveConsoleSource(null));
$('#console-search-form').addEventListener('submit',async event=>{
  event.preventDefault();if(socialBusy)return;socialBusy=true;busy($('#console-search-form'),true);const sequence=++consoleSequence;
  $('#console-source-message').textContent='Buscando edições…';$('#console-candidates').replaceChildren();
  try{const data=await api('/api/admin/console-search?'+new URLSearchParams({q:$('#console-source-query').value,platform:$('#console-source-platform').value}));if(sequence!==consoleSequence)return;
    $('#console-candidates').replaceChildren(...data.map(item=>{const node=button(item.name+' · '+(item.edition||'Edição não informada')+' · '+item.platforms.join(', '),'secondary',()=>saveConsoleSource(item.id));return node;}));$('#console-source-message').textContent=data.length?'Selecione a edição que corresponde a este jogo.':'Nenhuma edição encontrada nesta região.';
  }catch(error){$('#console-source-message').textContent=error.message;}finally{socialBusy=false;busy($('#console-search-form'),false);}
});
function openSharedProfile(){const match=location.hash.match(/^#profile=([a-zA-Z0-9_.-]{3,30})$/);if(match)openPublicProfile(match[1]);}
window.addEventListener('hashchange',openSharedProfile);openSharedProfile();

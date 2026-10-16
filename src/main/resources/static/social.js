'use strict';
let privacyDirty=false;
let publicUsername=null,publicSequence=0,socialBusy=false;
const visibilityLabels={PUBLIC:'Público',FRIENDS:'Só amigos',PRIVATE:'Privado'};
function identityCard(user) {
  const card=button('','surface person-card',()=>openPublicProfile(user.username));
  const avatar=el('div','avatar');renderAvatar(avatar,user);
  const info=el('div');info.append(el('strong',null,user.displayName),el('p','muted','@'+user.username),el('p','price-note',visibilityLabels[user.visibility]));card.append(avatar,info);return card;
}
function resetSocial() {
  privacyDirty=false;++publicSequence;publicUsername=null;
  $('#public-profile-dialog').close();
  for(const id of ['people-results','social-connections','public-profile-content'])$('#'+id).replaceChildren();
  $('#people-search-form').reset();$('#privacy-form').reset();$('#privacy-message').textContent='';$('#people-message').textContent='';
}
function renderPrivacy(user){if(privacyDirty)return;$('#profile-visibility').value=user.visibility;$('#profile-discoverable').checked=user.discoverable;for(const key of ['library','favorites','wishlist','lists'])$('#privacy-'+key).checked=user.collectionPrivacy?.[key]??false;}
$('#privacy-form').addEventListener('input',()=>privacyDirty=true);
$('#privacy-form').addEventListener('submit',async event=>{
  event.preventDefault();if(socialBusy)return;socialBusy=true;busy($('#privacy-form'),true);$('#privacy-message').textContent='Salvando privacidade…';
  try{const value=await api('/api/profile/privacy',{method:'PUT',body:JSON.stringify({visibility:$('#profile-visibility').value,discoverable:$('#profile-discoverable').checked,...Object.fromEntries(['library','favorites','wishlist','lists'].map(key=>[key+'Public',$('#privacy-'+key).checked]))})});
    if(state.profile)Object.assign(state.profile.user,value);privacyDirty=false;renderPrivacy(value);$('#privacy-message').textContent='Privacidade atualizada.';
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
  const social=await api('/api/social');const blocked=await api('/api/blocks');if(sequence!==state.workspaceSequence||state.view!=='people')return;
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
  const blockSection=el('section','social-group');blockSection.append(el('h3',null,'Usuários bloqueados'));for(const user of blocked){const row=el('div','surface');row.append(el('p',null,user.displayName+' · @'+user.username),button('Desbloquear','text-button',async()=>{try{await api('/api/blocks/'+encodeURIComponent(user.username),{method:'DELETE'});await refreshPeople();}catch(error){toast(error.message);}}));blockSection.append(row);}$('#social-connections').append(blockSection);
}
async function socialAction(username,action,following){
  if(socialBusy)return;if(action==='REMOVE'&&!confirm('Desfazer esta amizade?'))return;socialBusy=true;
  const controls=[...document.querySelectorAll('#social-connections button,#public-profile-content button')];controls.forEach(node=>node.disabled=true);
  try{await api('/api/people/'+encodeURIComponent(username)+(action?'/friendship':'/follow'),{method:action||following?'PUT':'DELETE',...(action?{body:JSON.stringify({action})}:{})});
    if($('#public-profile-dialog').open&&publicUsername===username)await openPublicProfile(username);
    if(state.view==='people')await refreshPeople();toast(action==='REQUEST'?'Pedido enviado.':'Conexão atualizada.');
  }catch(error){toast(error.message);}finally{socialBusy=false;controls.forEach(node=>node.disabled=false);}
}
function profileShareButton(user) {
  const feedback=el('span','public-share-feedback');feedback.setAttribute('role','status');
  const label=el('span',null,'Compartilhar');const control=button('','secondary profile-share',async()=>{
    if(control.disabled)return;control.disabled=true;feedback.textContent='';
    const url=new URL('/#profile='+encodeURIComponent(user.username),location.origin).href;
    try {
      if(typeof navigator.share==='function') {
        try{await navigator.share({title:'Perfil de '+user.displayName+' no CritPlay',url});feedback.textContent='Perfil compartilhado.';return;}
        catch(error){if(error.name==='AbortError')return;}
      }
      try {
        if(!navigator.clipboard?.writeText)throw new Error('Clipboard unavailable');
        await navigator.clipboard.writeText(url);
      }catch(error){
        const previous=document.activeElement;const copy=el('textarea');copy.value=url;copy.readOnly=true;copy.tabIndex=-1;
        copy.setAttribute('aria-hidden','true');copy.style.cssText='position:fixed;left:-9999px;top:0;width:1px;height:1px;opacity:0';
        (control.closest('dialog')||document.body).append(copy);
        try{copy.select();if(!document.execCommand('copy'))throw new Error('Copy failed');}
        finally{copy.remove();previous?.focus({preventScroll:true});}
      }
      feedback.textContent='Link copiado!';
    }catch(error){feedback.textContent='Não foi possível compartilhar. Tente novamente.';}
    finally{control.disabled=false;}
  });
  const icon=document.createElementNS('http://www.w3.org/2000/svg','svg');icon.setAttribute('viewBox','0 0 24 24');icon.setAttribute('aria-hidden','true');
  for(const [tag,attributes]of [['path',{d:'M8.6 10.5l6.8-4M8.6 13.5l6.8 4'}],...[[6,12],[18,5],[18,19]].map(([cx,cy])=>['circle',{cx,cy,r:3}])]) {
    const node=document.createElementNS(icon.namespaceURI,tag);for(const [key,value]of Object.entries(attributes))node.setAttribute(key,value);icon.append(node);
  }
  control.append(icon,label);const group=el('div','public-share-group');group.append(control,feedback);return group;
}
function publicProfileHeading(title,description) {
  const heading=el('div','public-section-heading');heading.append(el('h3',null,title));
  if(description)heading.append(el('p','muted',description));return heading;
}
async function openPublicProfile(username){
  publicUsername=username;const sequence=++publicSequence;showSkeletons($('#public-profile-content'));$('#public-profile-message').textContent='Carregando perfil…';
  $('#public-profile-title').textContent='Perfil do jogador';
  if(!$('#public-profile-dialog').open)$('#public-profile-dialog').showModal();
  $('#public-profile-dialog').scrollTop=0;
  try{const profile=await api('/api/people/'+encodeURIComponent(username));if(sequence!==publicSequence||!$('#public-profile-dialog').open)return;
    const user=profile.user;const content=$('#public-profile-content');finishLoading(content);content.replaceChildren();
    $('#public-profile-title').textContent='Perfil de '+user.displayName;
    const header=el('header','public-profile-header');const banner=el('div','profile-banner');
    if(profile.visible&&user.bannerUrl)banner.append(identityImage(user.bannerUrl,'Banner de '+user.displayName));
    else banner.append(el('span','public-banner-signature','CADA JOGO, UMA NOVA HISTÓRIA'));
    header.append(banner);
    const identity=el('div','person-card public-profile-identity');const avatar=el('div','avatar');renderAvatar(avatar,user);
    const info=el('div','public-identity-info');const name=el('div','public-name-row');
    name.append(el('h3',null,user.displayName),el('span','public-visibility',visibilityLabels[user.visibility]));
    info.append(name,el('p','public-username','@'+user.username));identity.append(avatar,info);header.append(identity);
    if(profile.visible&&user.bio)header.append(el('p','profile-bio public-profile-bio',user.bio));
    if(profile.visible){const social=el('div','public-social-counts');
      for(const [key,label]of Object.entries({followers:'seguidores',following:'seguindo',friends:'amigos'})){
        const item=el('span');item.append(el('strong',null,String(profile.social[key])),document.createTextNode(' '+label));social.append(item);
      }header.append(social);
    }
    content.append(header);
    const actions=el('div','social-actions public-profile-actions'),connections=el('div','public-connect-actions'),tools=el('div','public-profile-tools');
    if(state.user&&state.user.username!==username){
      tools.append(button('Bloquear','text-button',async()=>{if(!confirm('Bloquear esta pessoa e remover suas conexões com ela?'))return;try{await api('/api/blocks/'+encodeURIComponent(username),{method:'PUT'});$('#public-profile-dialog').close();if(state.view==='people')await refreshPeople();toast('Usuário bloqueado.');}catch(error){toast(error.message);}}),button('Denunciar perfil','text-button',()=>sendReport(username,null)));
      const relationship=profile.relationship;connections.append(button(relationship.following?'Deixar de seguir':'Seguir','primary',()=>socialAction(username,null,!relationship.following)));
      if(relationship.friendship==='NONE')connections.append(button('Adicionar amigo','secondary',()=>socialAction(username,'REQUEST')));
      if(relationship.friendship==='OUTGOING')connections.append(button('Cancelar pedido','secondary',()=>socialAction(username,'CANCEL')));
      if(relationship.friendship==='INCOMING')connections.append(button('Aceitar amizade','primary',()=>socialAction(username,'ACCEPT')),button('Recusar pedido','text-button',()=>socialAction(username,'REJECT')));
      if(relationship.friendship==='FRIENDS')connections.append(button('Desfazer amizade','secondary',()=>socialAction(username,'REMOVE')));
    }else if(!state.user)connections.append(button('Entrar para se conectar','primary',()=>{$('#public-profile-dialog').close();openAuth();}));
    actions.append(connections,profileShareButton(user));content.append(actions);
    if(profile.visible){
      const stats=el('div','stats-grid public-profile-stats');const labels={library:'Jogos na biblioteca',completed:'Finalizados',favorites:'Favoritos',wishlist:'Desejos',reviews:'Resenhas'};
      for(const [key,label]of Object.entries(labels)){if(profile.stats[key]===undefined)continue;const stat=el('div','stat');stat.append(el('strong',null,String(profile.stats[key])),el('span',null,label));stats.append(stat);}if(stats.children.length)content.append(stats);
      if(user.featuredGames?.length){const section=el('section','public-profile-section');const featured=el('div','featured-games');renderFeaturedGames(featured,user.featuredGames);
        section.append(publicProfileHeading('Jogos em destaque','Os mundos que representam este jogador.'),featured);content.append(section);
      }
      renderPublicCollections(content,profile.collections||{});
      const section=el('section','public-profile-section public-profile-reviews'),reviews=el('div','review-grid');section.append(publicProfileHeading('Resenhas',profile.reviews.length+' experiências compartilhadas'));
      for(const review of profile.reviews){const card=el('article','surface review-card');card.append(compactGame(review.game));if(review.rating)card.append(ratingDisplay(review.rating));card.append(el('p','review-text',review.review));if(state.user?.username!==username)card.append(reviewLikeButton({...review,displayName:user.displayName}));if(state.user&&state.user.username!==username)card.append(button('Denunciar resenha','text-button',()=>sendReport(username,review.game.id)));reviews.append(card);}
      if(!profile.reviews.length){const empty=el('div','public-profile-empty');empty.append(el('span','public-empty-icon','✦'),el('p','muted','Nenhuma resenha publicada ainda.'));section.append(empty);}else section.append(reviews);content.append(section);
    }else {const privacy=el('section','public-profile-empty public-profile-private');privacy.append(el('span','public-empty-icon','◇'),el('h3',null,user.visibility==='FRIENDS'?'Uma jornada entre amigos':'Uma jornada reservada'),el('p','privacy-note',user.visibility==='FRIENDS'?'Este perfil está disponível apenas para amigos aceitos.':'Este perfil é privado.'));content.append(privacy);}
    if(tools.children.length)content.append(tools);
    $('#public-profile-message').textContent='';
  }catch(error){if(sequence===publicSequence){finishLoading($('#public-profile-content'));$('#public-profile-content').replaceChildren();$('#public-profile-message').textContent=error.message;}}
}
function openSharedProfile(){const match=location.hash.match(/^#profile=([a-zA-Z0-9_.-]{3,30})$/);if(match)openPublicProfile(match[1]);}
window.addEventListener('hashchange',openSharedProfile);openSharedProfile();

function renderPublicCollections(content,collections) {
  const section=el('section','shared-collections public-profile-section');section.append(publicProfileHeading('Coleções','Explore os jogos que fazem parte desta jornada.'));
  const navigation=el('div','profile-navigation');navigation.setAttribute('role','group');navigation.setAttribute('aria-label','Coleções compartilhadas');
  const panel=el('div','shared-collection-panel');
  const labels={library:'Biblioteca',favorites:'Favoritos',wishlist:'Desejos',lists:'Listas'};
  function select(key) {
    navigation.querySelectorAll('button').forEach(node=>{const active=node.dataset.collection===key;node.classList.toggle('active',active);node.setAttribute('aria-pressed',String(active));});
    panel.replaceChildren();
    if(!Object.hasOwn(collections,key)){const empty=el('div','public-profile-empty');empty.append(el('span','public-empty-icon','◇'),el('p','privacy-note','Esta coleção é privada.'));panel.append(empty);return;}
    const items=collections[key];
    if(!items.length){const empty=el('div','public-profile-empty');empty.append(el('span','public-empty-icon','✦'),el('p','muted','Ainda não há jogos nesta coleção.'));panel.append(empty);return;}
    if(key==='lists')for(const list of items){const card=el('article','surface shared-list');card.append(el('h4',null,list.name),el('p','muted',list.description));
      const games=el('div','shared-games');for(const game of list.games)games.append(compactGame(game));if(!list.games.length)games.append(el('p','muted','Esta lista ainda não tem jogos.'));card.append(games);panel.append(card);}
    else {const games=el('div','shared-games');for(const item of items){const card=el('article','shared-game');card.append(compactGame(item.game));
      if(item.status)card.append(el('p','price-note',statusLabels[item.status]));if(item.rating)card.append(ratingDisplay(item.rating));games.append(card);}panel.append(games);}
  }
  for(const [key,label]of Object.entries(labels)){const control=button(label,'profile-nav-button',()=>select(key));control.dataset.collection=key;navigation.append(control);}
  section.append(navigation,panel);content.append(section);select('library');
}

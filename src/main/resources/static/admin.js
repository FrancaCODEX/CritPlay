'use strict';
let metadataSelectedId=null,metadataSequence=0,metadataCatalogue=[],adminBusy=false,cleanupPreview=null;
function resetAdmin(){metadataSelectedId=null;++metadataSequence;metadataCatalogue=[];cleanupPreview=null;$('#metadata-dialog').close();$('#metadata-results').replaceChildren();$('#metadata-preview').replaceChildren();$('#accounts-results').replaceChildren();$('#accounts-search-form').reset();$('#run-cleanup').disabled=true;$('#cleanup-message').textContent='';$('#accounts-message').textContent='';}
async function refreshAdmin(){
  const sequence=++state.workspaceSequence;$('#accounts-message').textContent='Carregando contas…';
  try{const accounts=await api('/api/admin/accounts?q='+encodeURIComponent($('#accounts-search').value));if(sequence!==state.workspaceSequence||state.view!=='admin')return;
    $('#accounts-message').textContent=accounts.length?`${accounts.length} conta(s)`:'Nenhuma conta encontrada.';
    $('#accounts-results').replaceChildren(...accounts.map(account=>{
      const card=el('article','surface account-card');card.append(el('h4',null,account.displayName),el('p','muted','@'+account.username),el('p','price-note',account.enabled?'Conta ativa':'Conta suspensa'));
      const form=el('form');const label=el('label',null,'Papel');const select=el('select');select.setAttribute('aria-label','Papel de '+account.username);
      for(const [role,name] of [['USER','Usuário'],['ADMIN','Administrador']]){const option=el('option',null,name);option.value=role;select.append(option);}select.value=account.role;label.append(select);
      const active=el('label','checkbox');const checkbox=el('input');checkbox.type='checkbox';checkbox.checked=account.enabled;active.append(checkbox,document.createTextNode(' Conta ativa'));
      const save=el('button','secondary','Salvar permissões');save.type='submit';form.append(label,active,save);form.addEventListener('submit',event=>{event.preventDefault();saveAccount(account,select.value,checkbox.checked);});
      if(account.id===state.user.id){select.disabled=true;checkbox.disabled=true;save.disabled=true;card.append(el('p','price-note','Você não pode retirar seu próprio acesso administrativo.'));}
      card.append(form);return card;
    }));
  }catch(error){if(sequence===state.workspaceSequence)$('#accounts-message').textContent=error.message;}
}
$('#accounts-search-form').addEventListener('submit',event=>{event.preventDefault();refreshAdmin();});
async function saveAccount(account,role,enabled){
  if(adminBusy||!confirm(`Alterar @${account.username} para ${role==='ADMIN'?'administrador':'usuário'} e ${enabled?'ativar':'suspender'} a conta? As sessões dessa conta serão encerradas.`))return;
  adminBusy=true;$('#accounts-results').querySelectorAll('button').forEach(node=>node.disabled=true);
  try{await api(`/api/admin/accounts/${account.id}`,{method:'PUT',body:JSON.stringify({role,enabled})});await refreshAdmin();toast('Permissões atualizadas.');}
  catch(error){$('#accounts-message').textContent=error.message;await refreshAdmin();toast(error.message);}finally{adminBusy=false;}
}
$('#preview-cleanup').addEventListener('click',async()=>{
  if(adminBusy)return;adminBusy=true;$('#preview-cleanup').disabled=true;$('#run-cleanup').disabled=true;
  try{cleanupPreview=await api('/api/admin/images/cleanup');$('#cleanup-message').textContent=`${cleanupPreview.count} imagem(ns) sem uso · ${(cleanupPreview.bytes/1024/1024).toFixed(2)} MB · Prazo mínimo: 24 horas.`;$('#run-cleanup').disabled=cleanupPreview.count===0;}
  catch(error){$('#cleanup-message').textContent=error.message;}finally{adminBusy=false;$('#preview-cleanup').disabled=false;}
});
$('#run-cleanup').addEventListener('click',async()=>{
  if(adminBusy||!cleanupPreview||!confirm(`Excluir até ${cleanupPreview.count} imagens sem uso? A verificação será refeita para preservar avatares e capas vinculados.`))return;
  adminBusy=true;$('#run-cleanup').disabled=true;$('#preview-cleanup').disabled=true;
  try{const result=await api('/api/admin/images/cleanup',{method:'POST',body:JSON.stringify({confirm:true})});cleanupPreview=null;$('#cleanup-message').textContent=`${result.removed} imagem(ns) removida(s). ${result.failed} falha(s).`;}
  catch(error){$('#cleanup-message').textContent=error.message;}finally{adminBusy=false;$('#preview-cleanup').disabled=false;}
});
$('#metadata-import').addEventListener('click',async()=>{
  metadataSelectedId=null;const sequence=++metadataSequence;$('#metadata-message').textContent='Verificando configuração…';$('#metadata-results').replaceChildren();$('#metadata-preview').replaceChildren();$('#metadata-dialog').showModal();busy($('#metadata-search-form'),true);
  try{const [status,games]=await Promise.all([api('/api/admin/metadata/status'),api('/api/games')]);if(sequence!==metadataSequence)return;
    metadataCatalogue=games;$('#metadata-message').textContent=status.configured?'Busque pelo título do jogo.':'Configure RAWG_API_KEY no seu .env e reinicie. A chave é gratuita para projetos pessoais.';
  }catch(error){$('#metadata-message').textContent=error.message;}finally{if(sequence===metadataSequence)busy($('#metadata-search-form'),false);}
});
$('#close-metadata').addEventListener('click',()=>{++metadataSequence;$('#metadata-dialog').close();});$('#metadata-dialog').addEventListener('cancel',()=>++metadataSequence);
$('#metadata-search-form').addEventListener('submit',async event=>{
  event.preventDefault();const sequence=++metadataSequence;busy($('#metadata-search-form'),true);$('#metadata-results').replaceChildren();$('#metadata-preview').replaceChildren();$('#metadata-message').textContent='Buscando metadados…';
  try{const games=await api('/api/admin/metadata/search?q='+encodeURIComponent($('#metadata-search').value));if(sequence!==metadataSequence)return;
    $('#metadata-results').replaceChildren(...games.map(game=>button(game.title+' · '+(game.releaseYear||'Ano não informado'),'secondary',()=>previewMetadata(game.id))));$('#metadata-message').textContent=games.length?'Escolha um jogo para ver a prévia.':'Nenhum jogo encontrado.';
  }catch(error){if(sequence===metadataSequence)$('#metadata-message').textContent=error.message;}finally{if(sequence===metadataSequence)busy($('#metadata-search-form'),false);}
});
async function previewMetadata(id){
  const sequence=++metadataSequence;$('#metadata-message').textContent='Carregando prévia…';
  try{const game=await api('/api/admin/metadata/'+id);if(sequence!==metadataSequence)return;const preview=el('section','metadata-preview');
    if(game.coverUrl){const image=el('img');image.src=game.coverUrl;image.alt='Imagem de '+game.title;preview.append(image);}
    preview.append(el('h3',null,game.title),el('p','muted',(game.genre||'Gênero não informado')+' · '+(game.releaseYear||'Ano não informado')),el('p','privacy-note',game.description||'Sem descrição. Preencha no editor antes de importar.'));
    const label=el('label',null,'Destino da importação');const target=el('select');target.id='metadata-target';const create=el('option',null,'Criar um novo jogo');create.value='';target.append(create);
    for(const existing of metadataCatalogue){const option=el('option',null,`Atualizar: ${existing.title} (#${existing.id})`);option.value=existing.id;target.append(option);}
    const linked=metadataCatalogue.find(existing=>existing.rawgId===game.id);if(linked)target.value=linked.id;label.append(target);preview.append(label);
    preview.append(el('p','price-note','Atualizar preserva o ID, diários, listas, capa própria e Steam App ID. Revise a prévia antes de salvar.'),button('Revisar no editor','primary',()=>{
      const selected=metadataCatalogue.find(existing=>String(existing.id)===target.value)||null;$('#metadata-dialog').close();openGameEditor(selected);metadataSelectedId=game.id;
      $('#admin-title').textContent='Revisar importação da RAWG';$('#game-submit').textContent='Salvar importação';$('#game-title').value=game.title;$('#game-genre').value=game.genre||'';$('#game-year').value=game.releaseYear||'';$('#game-description').value=game.description||'';
      const labels={PC:'PC',PLAYSTATION:'PlayStation',XBOX:'Xbox',NINTENDO:'Nintendo'};
      $('#game-form').querySelectorAll('[name="game-platform"]').forEach(input=>input.checked=game.platforms.includes(input.value)||!!selected?.platforms.split(',').map(x=>x.trim()).includes(labels[input.value]));
      $('#game-message').textContent='Metadados e imagem: RAWG. Revise os dados; informações ausentes devem ser preenchidas. A importação preserva o vínculo da fonte.';
    }));$('#metadata-preview').replaceChildren(preview);$('#metadata-message').textContent='Prévia pronta. Ainda não foi salvo no catálogo.';
  }catch(error){if(sequence===metadataSequence)$('#metadata-message').textContent=error.message;}
}

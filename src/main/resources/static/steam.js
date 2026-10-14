'use strict';
let steamSequence=0,steamScan=false,steamScanSequence=0;
function resetSteamDraft(){++steamSequence;$('#steam-resolution').textContent='';$('#resolve-steam').disabled=false;}
function resetSteam(){resetSteamDraft();++steamScanSequence;steamScan=false;$('#backfill-steam').disabled=false;$('#stop-steam').hidden=true;}
async function resolveSteamDraft(){
  const title=$('#game-title').value.trim(),sequence=++steamSequence,existing=$('#game-steam-id').value;
  if(title.length<2){$('#steam-resolution').textContent='Informe o título primeiro.';return;}
  if(!$('#game-form [name="game-platform"][value="PC"]').checked){$('#steam-resolution').textContent='Selecione PC para buscar na Steam.';return;}
  if(existing){$('#steam-resolution').textContent='ID informado preservado. Apague-o primeiro se quiser buscar outro.';return;}
  $('#resolve-steam').disabled=true;$('#steam-resolution').textContent='Procurando o mesmo jogo na Steam…';
  try{
    const result=await api('/api/admin/steam/resolve',{method:'POST',body:JSON.stringify({title})});
    if(sequence!==steamSequence||!$('#admin-dialog').open||title!==$('#game-title').value.trim())return;
    if(!$('#game-steam-id').value&&$('#game-form [name="game-platform"][value="PC"]').checked&&result.appId)$('#game-steam-id').value=result.appId;
    $('#steam-resolution').textContent=result.message;
  }catch(error){if(sequence===steamSequence)$('#steam-resolution').textContent=error.message;}
  finally{if(sequence===steamSequence)$('#resolve-steam').disabled=false;}
}
$('#resolve-steam').addEventListener('click',resolveSteamDraft);
$('#stop-steam').addEventListener('click',()=>{steamScan=false;$('#backfill-message').textContent='Parando após a consulta atual…';});
$('#backfill-steam').addEventListener('click',async()=>{
  if(steamScan||!confirm('Procurar e salvar Steam IDs dos jogos de PC sem ID? Os IDs existentes serão preservados.'))return;
  const scan=++steamScanSequence;steamScan=true;$('#backfill-steam').disabled=true;$('#stop-steam').hidden=false;
  let checked=0,found=0,pending=0,unavailable=false;
  try{
    const games=(await api('/api/games')).filter(g=>!g.steamAppId&&g.platforms.split(',').map(x=>x.trim()).includes('PC'));
    for(const game of games){
      if(scan!==steamScanSequence||!steamScan||state.user?.role!=='ADMIN'||state.view!=='admin')break;
      $('#backfill-message').textContent=`${checked}/${games.length} verificados · ${found} IDs salvos · ${pending} para revisão · ${game.title}`;
      const result=await api('/api/admin/steam/games/'+game.id,{method:'POST'});if(scan!==steamScanSequence)return;checked++;
      if(result.status==='FOUND')found++;else if(result.status!=='PRESERVED')pending++;
      if(result.status==='UNAVAILABLE'){steamScan=false;unavailable=true;break;}
    }
    $('#backfill-message').textContent=`${checked}/${games.length} verificados · ${found} IDs salvos · ${pending} sem associação. ${unavailable?'Busca interrompida: Steam indisponível; tente novamente em um minuto.':checked<games.length?'Busca interrompida; pode iniciar novamente.':'Busca concluída.'}`;
  }catch(error){$('#backfill-message').textContent=error.message;}
  finally{if(scan===steamScanSequence){steamScan=false;$('#backfill-steam').disabled=false;$('#stop-steam').hidden=true;}}
});

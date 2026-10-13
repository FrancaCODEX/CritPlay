'use strict';
let bulkTimer=null,bulkBusy=false;
function resetBulk(){clearTimeout(bulkTimer);$('#bulk-jobs').replaceChildren();$('#moderation-reports').replaceChildren();}
async function loadJobs(){
  if(state.user?.role!=='ADMIN'||state.view!=='admin')return;
  try{const jobs=await api('/api/admin/import-jobs');if(state.view!=='admin')return;
    $('#bulk-jobs').replaceChildren(...jobs.map(job=>{
      const card=el('article','import-job');const labels={RUNNING:'Em andamento',PAUSED:'Pausada',COMPLETED:'Concluída'};
      card.append(el('h4',null,`Lote #${job.id} · ${labels[job.status]||job.status}`));const progress=el('progress');progress.max=job.target;progress.value=job.added;card.append(progress);
      card.append(el('p','muted',`${job.added}/${job.target} novos · ${job.existing} já existentes · ${job.skipped} incompletos/ausentes · ${job.processed} verificados`),el('p','price-note',job.message));
      const actions=el('div','form-actions');if(job.status==='RUNNING')actions.append(button('Pausar','secondary',()=>jobAction(job.id,'pause')));if(job.status==='PAUSED')actions.append(button('Retomar','primary',()=>jobAction(job.id,'resume')));card.append(actions);return card;
    }));
    clearTimeout(bulkTimer);if(jobs.some(j=>j.status==='RUNNING'))bulkTimer=setTimeout(loadJobs,2000);
  }catch(error){$('#bulk-message').textContent=error.message;}
}
$('#bulk-form').addEventListener('submit',async event=>{
  event.preventDefault();if(bulkBusy||!confirm('Iniciar a importação? Ela utilizará a cota de consultas da sua chave RAWG.'))return;bulkBusy=true;busy($('#bulk-form'),true);
  try{await api('/api/admin/import-jobs',{method:'POST',body:JSON.stringify({count:Number($('#bulk-count').value)})});$('#bulk-message').textContent='Importação iniciada. Você pode sair desta página; o servidor continua trabalhando.';await loadJobs();}
  catch(error){$('#bulk-message').textContent=error.message;}finally{bulkBusy=false;busy($('#bulk-form'),false);}
});
async function jobAction(id,action){try{await api(`/api/admin/import-jobs/${id}/${action}`,{method:'POST'});await loadJobs();}catch(error){toast(error.message);}}
async function sendReport(username,gameId){const reason=prompt('Descreva o motivo da denúncia (10 a 500 caracteres):');if(!reason)return;
  try{await api('/api/reports',{method:'POST',body:JSON.stringify({username,gameId,reason})});toast('Denúncia enviada para a moderação.');}catch(error){toast(error.message);}
}
async function loadReports(){
  if(state.user?.role!=='ADMIN'||state.view!=='admin')return;
  try{const reports=await api('/api/admin/reports');if(state.view!=='admin')return;
    $('#moderation-reports').replaceChildren(...reports.map(report=>{
      const card=el('article','report-card');card.append(el('h4',null,`#${report.id} · @${report.username} · ${report.status}`),el('p',null,report.reason));if(report.review)card.append(el('p','review-text',report.review));
      const actions=el('div','form-actions');actions.append(button('Resolver','secondary',()=>resolveReport(report,'RESOLVED',null)),button('Descartar','text-button',()=>resolveReport(report,'DISMISSED',null)));
      if(report.gameId)actions.append(button(report.reviewHidden?'Restaurar resenha':'Ocultar resenha','text-button',()=>resolveReport(report,'RESOLVED',!report.reviewHidden)));card.append(actions);return card;
    }));if(!reports.length)$('#moderation-reports').append(el('p','muted','Nenhuma denúncia recebida.'));
  }catch(error){toast(error.message);}
}
async function resolveReport(report,status,hideReview){if(!confirm('Aplicar esta decisão de moderação?'))return;try{await api('/api/admin/reports/'+report.id,{method:'PUT',body:JSON.stringify({status,hideReview})});await loadReports();toast('Denúncia analisada.');}catch(error){toast(error.message);}}
$('#refresh-reports').addEventListener('click',loadReports);

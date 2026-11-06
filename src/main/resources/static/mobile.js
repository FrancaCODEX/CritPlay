'use strict';
// Move the existing filters instead of maintaining a second search state.
const mobileViewport=matchMedia('(max-width:680px)');
const mobileFilters=$('.filters');
const mobileFilterHome=document.createComment('catalog filters');
mobileFilters.before(mobileFilterHome);
const mobileSortLabel=$('#game-sort').closest('label');
const mobileSortHome=document.createComment('catalog sort');mobileSortLabel.before(mobileSortHome);
const mobilePaths={catalog:'<circle cx="12" cy="12" r="9"/><path d="m16 8-2 6-6 2 2-6Z"/>',people:'<circle cx="9" cy="8" r="3"/><path d="M3 21v-3a6 6 0 0 1 12 0v3M16 5a3 3 0 0 1 0 6M18 15a5 5 0 0 1 3 5"/>',profile:'<circle cx="12" cy="7" r="4"/><path d="M4 22v-3a8 8 0 0 1 16 0v3Z"/>'};
for(const button of document.querySelectorAll('[data-mobile-view]')){
  button.querySelector('.mobile-nav-icon').innerHTML='<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">'+mobilePaths[button.dataset.mobileView]+'</svg>';
  button.addEventListener('click',()=>{closeMobileMenu();setView(button.dataset.mobileView);});
}
let mobileGenreSignature='';
function renderMobileGenres(){
 const signature=Array.from($('#genre').options,option=>option.value).join('|');
 if(signature===mobileGenreSignature)return;mobileGenreSignature=signature;$('#mobile-genres').replaceChildren();
 for(const option of $('#genre').options){
  const button=el('button','mobile-genre',option.value?option.textContent:'Todos');button.type='button';button.dataset.genre=option.value;
  button.addEventListener('click',()=>{$('#genre').value=option.value;syncMobileLayout();$('#genre').dispatchEvent(new Event('change',{bubbles:true}));});
  $('#mobile-genres').append(button);
 }
}
function closeMobileMenu(){
  $('.topbar').classList.remove('mobile-menu-open');$('#mobile-menu-toggle').setAttribute('aria-expanded','false');
}
$('#mobile-menu-toggle').addEventListener('click',()=>{
  const open=$('.topbar').classList.toggle('mobile-menu-open');$('#mobile-menu-toggle').setAttribute('aria-expanded',String(open));
});
$('#mobile-main-menu').addEventListener('click',event=>{if(event.target.closest('button'))closeMobileMenu();});
document.addEventListener('keydown',event=>{if(event.key==='Escape'&&$('.topbar').classList.contains('mobile-menu-open')){closeMobileMenu();$('#mobile-menu-toggle').focus();}});
function syncMobileLayout(){
  const discovery=mobileViewport.matches&&state.view==='catalog';
  $('#mobile-discovery').hidden=!discovery;document.body.classList.toggle('mobile-discovery-active',discovery);
  if(discovery){if(mobileFilters.parentNode!==$('#mobile-search-slot'))$('#mobile-search-slot').append(mobileFilters);}
  else if(mobileFilters.previousSibling!==mobileFilterHome)mobileFilterHome.after(mobileFilters);
  for(const button of document.querySelectorAll('[data-mobile-view]')){
    const active=button.dataset.mobileView===state.view||button.dataset.mobileView==='people'&&state.view==='feed'||button.dataset.mobileView==='profile'&&['library','favorites','wishlist','lists'].includes(state.view);
    button.setAttribute('aria-current',active?'page':'false');
  }
  if(discovery){if(mobileSortLabel.parentNode!==mobileFilters)mobileFilters.append(mobileSortLabel);}
  else if(mobileSortLabel.previousSibling!==mobileSortHome)mobileSortHome.after(mobileSortLabel);
  renderMobileGenres();
  for(const button of $('#mobile-genres').children)button.setAttribute('aria-pressed',String(button.dataset.genre===$('#genre').value));
}
mobileViewport.addEventListener('change',()=>{closeMobileMenu();syncMobileLayout();});
$('#genre').addEventListener('change',syncMobileLayout);
syncMobileLayout();

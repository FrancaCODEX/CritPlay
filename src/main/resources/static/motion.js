// CritPlay — interações de movimento (spotlight, tilt e revelação ao rolar).
// Apenas efeitos visuais: não altera dados, eventos de negócio nem o DOM das telas.
(() => {
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
  const finePointer = window.matchMedia('(hover: hover) and (pointer: fine)');
  const SPOTLIGHT = '.game-card, .surface, .journey, .profile-summary, .people-grid > *, .lists-grid > *, .review-grid > *';
  const MAX_TILT = 6;

  const trackPointer = (event) => {
    if (!finePointer.matches) return;
    const target = event.target.closest?.(SPOTLIGHT);
    if (!target) return;
    const rect = target.getBoundingClientRect();
    const x = event.clientX - rect.left;
    const y = event.clientY - rect.top;
    target.style.setProperty('--mx', `${x}px`);
    target.style.setProperty('--my', `${y}px`);
    if (reducedMotion.matches || !target.classList.contains('game-card')) return;
    const cover = target.querySelector('.cover');
    if (!cover) return;
    const coverRect = cover.getBoundingClientRect();
    const px = (event.clientX - coverRect.left) / coverRect.width - 0.5;
    const py = (event.clientY - coverRect.top) / coverRect.height - 0.5;
    const inside = Math.abs(px) <= 0.5 && Math.abs(py) <= 0.5;
    target.style.setProperty('--rx', inside ? `${(-py * MAX_TILT).toFixed(2)}deg` : '0deg');
    target.style.setProperty('--ry', inside ? `${(px * MAX_TILT).toFixed(2)}deg` : '0deg');
  };

  const resetTilt = (event) => {
    const card = event.target.closest?.('.game-card');
    if (!card || card.contains(event.relatedTarget)) return;
    card.style.setProperty('--rx', '0deg');
    card.style.setProperty('--ry', '0deg');
  };

  // No máximo um cálculo por quadro, mesmo com mouses de alta taxa de eventos.
  let pendingPointer = null;
  const onPointerMove = (event) => {
    if (!finePointer.matches) return;
    const scheduled = pendingPointer !== null;
    pendingPointer = event;
    if (scheduled) return;
    requestAnimationFrame(() => {
      const latest = pendingPointer;
      pendingPointer = null;
      trackPointer(latest);
    });
  };
  document.addEventListener('pointermove', onPointerMove, { passive: true });
  document.addEventListener('pointerout', resetTilt, { passive: true });

  // Revelação escalonada dos cards quando entram na tela.
  const REVEAL = '.game-card, .people-grid > *, .lists-grid > *, .review-grid > *, .journey';
  const seen = new WeakSet();
  const observer = 'IntersectionObserver' in window
    ? new IntersectionObserver((entries) => {
        let index = 0;
        for (const entry of entries) {
          if (!entry.isIntersecting) continue;
          const node = entry.target;
          node.style.setProperty('--cp-delay', `${Math.min(index++, 8) * 45}ms`);
          node.classList.add('cp-reveal');
          node.addEventListener('animationend', () => node.classList.remove('cp-reveal'), { once: true });
          observer.unobserve(node);
        }
      }, { rootMargin: '0px 0px -40px 0px' })
    : null;

  const watch = (root) => {
    if (!observer || reducedMotion.matches) return;
    const nodes = root.matches?.(REVEAL) ? [root] : root.querySelectorAll?.(REVEAL) ?? [];
    for (const node of nodes) {
      if (seen.has(node)) continue;
      seen.add(node);
      observer.observe(node);
    }
  };

  const start = () => {
    watch(document.body);
    new MutationObserver((mutations) => {
      for (const mutation of mutations) for (const node of mutation.addedNodes) if (node.nodeType === 1) watch(node);
    }).observe(document.body, { childList: true, subtree: true });
  };

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start);
  else start();
})();

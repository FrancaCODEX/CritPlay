# MVP mobile — Roxo essencial

Direção aprovada: fundo berinjela, acentos lilás, capas em destaque e navegação acessível ao polegar. A implementação usa os dados e as imagens reais do catálogo; não incorpora pessoas, avaliações ou capas fictícias dos mockups.

Até 680 px, a busca e os gêneros aparecem antes do destaque. A ordenação aparece junto do filtro de plataforma, antes das capas. Os filtros existentes são movidos entre posições, sem duplicar IDs ou estado. Ao trocar de tela ou aumentar a janela, retornam ao catálogo/biblioteca. Biblioteca, favoritos, desejos e listas ficam juntos dentro do Perfil, com todas as opções visíveis em grade no celular. A criação de listas tem ação de largura completa e a confirmação aparece acima da barra inferior. A barra inferior oferece Explorar, Comunidade e Perfil; Atividades e Administração continuam no menu Mais opções, assim como sair e ações administrativas. O acesso continua sujeito à autenticação e permissões do backend.

Capas usam duas colunas, cartões roxos, texto legível e ações de toque com 44 px. Biblioteca mantém ordenação, status e lista compacta. Perfil mantém coleções e o diálogo de edição. A barra inferior fica oculta enquanto um diálogo estiver aberto. Safe areas e redução de movimento são respeitadas. Desktop conserva a estrutura existente.

## Validação

A suíte tests/mobile-browser.cjs verifica 320, 390 e 430 px, ausência de rolagem horizontal, navegação, login, busca/gênero, biblioteca, edição do perfil, menu administrativo, Escape e mudança para desktop sem duplicar a busca. Integra o CI junto aos fluxos existentes. As capturas automáticas com fontes externas bloqueadas podem mostrar capas de fallback; a aplicação mantém a cadeia de imagens real já existente.

Não há alteração de banco, migração, credenciais, serviços ou plano pago nesta etapa. O MVP deve ser revisado no preview antes de integrar à main. Melhorias futuras: testar em aparelhos iOS/Android reais, refinar a tela de detalhes e coletar feedback sobre os tamanhos e o menu de opções.

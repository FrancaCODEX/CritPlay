# Verificação da entrega v0.3 — 13/10/2026

- Build `clean verify` em JDK 17.
- 32 testes locais de Java passaram. No GitHub Actions, os 33 testes passaram, incluindo PostgreSQL 17 real, e a navegação em Chromium também foi aprovada.
- API: autenticação, CSRF, rotação/invalidação de sessões, bloqueio de cadastro/edição/importação de jogos para usuário comum, validação e conflitos.
- Perfil: edição, resenhas e estatísticas da conta correta, e-mail alterado somente com senha atual, sem campos de senha/role editáveis na resposta.
- Listas: criar, editar, excluir, adicionar/remover, rejeitar acesso a lista de outra conta e impedir itens duplicados.
- Desejos independentes do status do diário, mantendo notas e favoritos.
- Importação transacional: se um jogo do lote conflita com o catálogo, nada do lote é gravado.
- Avatar: PNG válido servido, arquivo disfarçado rejeitado, atribuição de imagem de outra conta e traversal rejeitados.
- Senha: senha atual obrigatória, alteração revoga outros navegadores, token de recuperação genérico/expirável/uso único, token antigo revogado por alteração de e-mail.
- Recuperação testada com entrega de e-mail substituída por mock; nenhum e-mail externo foi enviado. Transporte SMTP real depende do provedor do usuário.
- Migração real v0.2→v0.3: conta/senha, resenha, nota e favorito preservados; Quero jogar migrou para wishlist; Perfil e coleção nova funcionaram.
- Teste em Chromium local: login, criar lista, resenha, adicionar jogo à lista, desejos, perfil/avatar, cadastrar/editar jogo, celular sem overflow horizontal e segunda conta sem dados da primeira. Sem erros JavaScript.
- Telas do perfil inspecionadas em desktop e celular usando contas fictícias. As imagens da Steam e fontes externas foram bloqueadas nesse teste para eliminar dependência externa; as capas próprias foram servidas pela aplicação.
- JavaScript passou em `node --check` e alterações passaram em `git diff --check`.

O CI inclui PostgreSQL 17, `PostgresSmokeTest` e a mesma verificação de navegador. A execução com o ajuste de preservação do rascunho do perfil foi aprovada: https://github.com/FrancaCODEX/gameshelf/actions/runs/38437593681. Não foi feita execução em Windows/IntelliJ, teste de envio SMTP real ou comparação automática de preços de consoles. PostgreSQL é opcional e não transfere dados H2 automaticamente. As limitações e configurações estão no README.

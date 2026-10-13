# Verificação da entrega v0.4 — 13/10/2026

- Build `verify` em JDK 17: 39 testes Java aprovados; 1 teste PostgreSQL reservado ao CI (40 casos ao todo).
- Autenticação, CSRF, isolamento de diários/listas, edição/importação administrativa, upload e recuperação de senha mantêm a cobertura da v0.3.
- Social: seguir não libera resenhas de perfis só amigos; pedidos podem ser aceitos/recusados apenas pelo destinatário; cancelar/desfazer revoga a relação. Conexões pertencem à sessão atual.
- Privacidade: visitantes acessam conteúdo somente de perfis públicos e descobríveis; contas fora da busca ficam ocultas a desconhecidos. E-mail/senha e a entidade completa do diário não aparecem no perfil compartilhado.
- Busca trata curingas como texto literal e limita os resultados a 30 perfis descobríveis.
- Preços por plataforma: apenas plataformas presentes no catálogo; associação de edições e busca de fonte restritas a ADMIN. Sem chave, não são exibidos preços fictícios.
- Parser PSprices testado com fixtures: preço zero, moeda/região, edição, desconto, produto ausente, plataforma errada, links não confiáveis e compra condicionada. Não houve consulta real com chave licenciada PSprices.
- Migração real v0.3 → v0.4 usando a aplicação anterior e H2 em arquivo: conta/senha, diário, nota, resenha, favorito, desejos e lista preservados; perfil antigo permaneceu privado e fora da busca.
- Navegador: login, listas, diário, perfil/avatar, edição de jogos, preços separados, privacidade, busca, seguir, pedido/aceite de amizade, leitura por amigo e perfil público aberto por visitante.
- Verificação de sessões independentes em contextos separados do navegador. Desktop e celular (390 px); telas roxas e comunidade inspecionadas, sem overflow horizontal.
- Imagens/fontes externas bloqueadas no teste de interface; imagens próprias servidas pelo Java. Nenhuma conta real nem e-mail externo foi usado.
- JavaScript: `node --check`; alterações: `git diff --check`.

O CI executa os 40 casos, incluindo PostgreSQL 17 real, migrações e relações sociais, além do teste de navegação em Chromium. Não houve execução em Windows/IntelliJ, transporte SMTP real ou preço ao vivo de console com uma chave paga. PostgreSQL é opcional e não transfere dados H2 automaticamente. Veja configurações e limitações no README.

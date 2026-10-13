# Verificação da entrega CritPlay v0.5 — 13/10/2026

- Java 17: 41 testes locais aprovados; 1 teste PostgreSQL reservado ao CI (42 ao todo).
- Catálogo, autenticação, CSRF, isolamento de diários/listas, imagens, recuperação de senha e recursos sociais mantiveram sua cobertura.
- Metadados: importação exige ADMIN/CSRF, cria ou atualiza sem mudar o ID, preserva resenhas/listas e rejeita ID RAWG duplicado. Parser valida famílias de plataforma, data, descrição e domínio da imagem. API externa substituída por fixtures; não foi utilizada uma chave RAWG real.
- Administração: usuário comum não administra; mudança de papel revoga sessões; suspensão bloqueia login e busca/perfil públicos; reativação exige novo login. Auto-rebaixamento e auto-suspensão administrativa são recusados. Respostas não expõem e-mail/senha.
- Limpeza: conta comum não acessa, confirmação obrigatória, arquivos recentes/vinculados/desconhecidos preservados, órfãos antigos removidos. Imagem vinculada depois da prévia é preservada na execução. Testes usam diretório temporário exclusivo.
- Migração real GameShelf v0.4 → CritPlay v0.5: `.env` antigo, administrador/senha, diário, nota, resenha, desejos e lista preservados. O banco manteve o mesmo nome; o endpoint pago foi removido.
- Navegador Chromium: nome CritPlay, login, listas, diário, perfil/avatar, edição de jogos, privacidade, comunidade/amigos, administração, prévia de limpeza e importação com revisão no editor. Promoção pela interface revoga a sessão da conta e libera Administração após novo login.
- Na verificação visual da importação, a fonte RAWG foi substituída por fixture e o cadastro real foi exercitado; os testes Java verificam a persistência e proveniência da importação no servidor.
- Desktop e celular de 390 px inspecionados; sem overflow horizontal ou erros JavaScript. Imagens/fontes externas bloqueadas; nenhuma conta real nem e-mail externo usado.
- `node --check` e `git diff --check` aprovados.

O CI executa também PostgreSQL 17 real, incluindo importação, gestão de estado de conta, trava da limpeza e relações sociais. Não houve execução em Windows/IntelliJ, envio SMTP real, consulta RAWG com chave pessoal nem deploy. Veja README e OPERACAO.md.

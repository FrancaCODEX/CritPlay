# Verificação da entrega CritPlay v0.5 — 13/10/2026

- Java 17: 41 testes locais aprovados; 1 teste PostgreSQL reservado ao CI (42 ao todo).
- Catálogo, autenticação, CSRF, isolamento de diários/listas, imagens, recuperação de senha e recursos sociais mantiveram sua cobertura.
- Metadados: importação exige ADMIN/CSRF, cria ou atualiza sem mudar o ID, preserva resenhas/listas e rejeita ID RAWG duplicado. Parser valida famílias de plataforma, data, descrição e domínio da imagem. API externa substituída por fixtures; não foi utilizada uma chave RAWG real.
- Administração: usuário comum não administra; mudança de papel revoga sessões; suspensão bloqueia login e busca/perfil públicos; reativação exige novo login. Auto-rebaixamento e auto-suspensão administrativa são recusados. Respostas não expõem e-mail/senha.
- Limpeza: conta comum não acessa, confirmação obrigatória, arquivos recentes/vinculados/desconhecidos preservados, órfãos antigos removidos. Imagem vinculada depois da prévia é preservada na execução. Testes usam diretório temporário exclusivo.
- Migração real v0.4 → CritPlay v0.5: `.env` antigo, administrador/senha, diário, nota, resenha, desejos e lista preservados. O banco manteve o mesmo nome; o endpoint pago foi removido.
- Navegador Chromium: nome CritPlay, login, listas, diário, perfil/avatar, edição de jogos, privacidade, comunidade/amigos, administração, prévia de limpeza e importação com revisão no editor. Promoção pela interface revoga a sessão da conta e libera Administração após novo login.
- Na verificação visual da importação, a fonte RAWG foi substituída por fixture e o cadastro real foi exercitado; os testes Java verificam a persistência e proveniência da importação no servidor.
- Desktop e celular de 390 px inspecionados; sem overflow horizontal ou erros JavaScript. Imagens/fontes externas bloqueadas; nenhuma conta real nem e-mail externo usado.
- `node --check` e `git diff --check` aprovados.

Os 42 testes passaram no GitHub Actions, incluindo PostgreSQL 17 real, importação, gestão de estado de conta, trava da limpeza e relações sociais. A navegação em Chromium também foi aprovada: https://github.com/FrancaCODEX/CritPlay/actions/runs/38450871002. Não houve execução em Windows/IntelliJ, envio SMTP real, consulta RAWG com chave pessoal nem deploy. Veja README e OPERACAO.md.

## Ampliação v0.5.1

- Suíte ampliada para 48 casos, incluindo PostgreSQL e transferência real H2 → PostgreSQL no CI. Os 46 casos locais disponíveis passaram.
- Importação de 500 jogos verificada com fixtures num banco de teste: progresso, pausa/retomada, duplicatas, registros ausentes e gêneros PT-BR. Nenhum jogo real foi importado sem a chave RAWG.
- Datas do diário: persistência, fim sem início, ordem inválida e datas futuras.
- Bloqueios: invisibilidade nos dois sentidos para usuários autenticados, remoção de amizades/seguidores e desbloqueio sem restauração automática.
- Denúncias: restrição de administrador, rejeição de denúncia aberta duplicada, ocultação/restauração e edição do autor sem contornar moderação.
- Navegador: datas, importação sem chave com mensagem controlada, denúncia/bloqueio/desbloqueio e decisão de moderação pela interface, além dos fluxos anteriores.
- Migração usa base H2 de teste e esquema PostgreSQL separado: IDs/hash de senha/datas/listas preservados, sequências ajustadas e destino ocupado recusado sem apagar registros.
- Dockerfile/configuração/guia de hospedagem preparados; Docker e deploy ainda não executados neste ambiente.

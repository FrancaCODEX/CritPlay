# Configurações do perfil e e-mails da conta

A edição abre no botão **Editar perfil**, em uma janela com rolagem própria. Os mesmos campos de perfil, avatar, banner, jogos em destaque, privacidade e senha continuam disponíveis. Cada seção mantém seu botão Salvar. Escape ou Fechar devolve o foco ao botão; rascunhos continuam ao reabrir na mesma visita ao perfil. Campos de senha são apagados ao fechar. Durante uma gravação, aguarde sua conclusão antes de fechar.

## Ativar agora sem domínio próprio

O site pode continuar em https://critplay.vercel.app. O domínio do site e a conta remetente são independentes. Para começar com poucos usuários, crie uma conta Gmail dedicada ao projeto. Não é necessário contratar Google Workspace. O Gmail impõe limites e pode bloquear envios; não é um serviço transacional com garantia de entrega.

1. Crie a conta e ative a verificação em duas etapas na [Conta Google](https://myaccount.google.com/security).
2. Gere uma [senha de app](https://myaccount.google.com/apppasswords) chamada CritPlay. Essa opção depende das políticas da conta. Use essa senha, nunca a senha normal de login.
3. Na Vercel, projeto CritPlay → Settings → Environment Variables, configure os valores abaixo no ambiente **Production**. Salve senhas e chaves como tipo Secret (ou Sensitive, em interfaces antigas). Não as publique no GitHub nem neste chat.
4. Após revisar e integrar o PR, faça deploy com essas variáveis. Se as adicionar depois do deploy, faça Redeploy para aplicá-las. Cache de build não substitui a atualização das variáveis.

| Variável | Valor |
| --- | --- |
| APP_BASE_URL | https://critplay.vercel.app |
| EMAIL_PROVIDER | smtp |
| EMAIL_NOTIFICATIONS_ENABLED | true |
| RECOVERY_ENABLED | true |
| MAIL_HOST | smtp.gmail.com |
| MAIL_PORT | 587 |
| MAIL_USERNAME | endereço completo da conta dedicada |
| MAIL_FROM | o mesmo endereço da conta dedicada |
| MAIL_PASSWORD | senha de app gerada pelo Google |
| MAIL_AUTH | true |
| MAIL_STARTTLS | true |

A Vercel permite SMTP de saída, exceto na porta 25. O código aguarda o envio na requisição, pois trabalho em background pode ser suspenso após a resposta. [Orientação oficial sobre SMTP](https://vercel.com/kb/guide/serverless-functions-and-smtp).

As credenciais ficam apenas no backend. Não altere DB_URL, DB_USERNAME ou DB_PASSWORD para configurar e-mail. Não adicione prefixos de variáveis públicas às credenciais. Em Preview, deixe os envios desativados ou use um remetente de teste separado; não compartilhe segredos nem o banco de produção com previews não confiáveis.

Documentação oficial: [SMTP Gmail e porta 587/TLS](https://support.google.com/mail/answer/7104828?hl=en), [requisitos e limitações das senhas de app](https://support.google.com/accounts/answer/185833?hl=pt-BR). Se o Google não disponibilizar senha de app, use outro provedor SMTP compatível com STARTTLS ou a alternativa HTTPS abaixo.

## Alternativa por API HTTPS: Resend

O transporte também aceita EMAIL_PROVIDER=resend, RESEND_API_KEY e MAIL_FROM com remetente autorizado. As demais variáveis de ativação e APP_BASE_URL permanecem. Não precisa definir MAIL_HOST/USERNAME/PASSWORD. Para enviar a usuários reais, é necessário um domínio próprio verificado no Resend; critplay.vercel.app não dá acesso ao DNS de vercel.app. O domínio de teste resend.dev tem restrições e não libera envios gerais. Não contrate domínio ou plano pago sem avaliar custos. [Limitações do domínio de teste](https://resend.com/changelog/improved-logs-visibility).

## Comportamento implementado

- Cadastro aceita e-mail opcional, único e validado. Quem informar e-mail recebe boas-vindas; contas antigas podem cadastrá-lo em Editar perfil, com senha atual.
- Cadastro/troca/remoção de e-mail gera aviso ao endereço correspondente. Na troca, o anterior e o novo recebem avisos.
- Alteração ou redefinição de senha gera aviso, sem incluir senha. Sessões anteriores são invalidadas pelo mecanismo já existente.
- Recuperação requer usuário e e-mail cadastrado correspondentes. Resposta pública é genérica; o link vale 15 minutos e é de uso único. Só o hash do token é persistido. O link usa APP_BASE_URL, nunca o host enviado pelo visitante.
- Avisar sobre cadastro de e-mail **não verifica sua propriedade**. Não há selo de e-mail verificado nem bloqueio por confirmação nesta entrega. Digitação incorreta ainda impede receber recuperação; um fluxo de verificação por token é uma próxima melhoria.

## Falhas, reenvio e operação

Avisos de conta usam uma tabela de saída transacional: são registrados junto com a alteração e enviados somente após o commit. Uma falha de envio preserva a conta/alteração e o aviso pendente. Na Administração → E-mails da conta, consulte a configuração e reenvie até 10 avisos por clique, com intervalo mínimo de 2 minutos por aviso, limite de 5 tentativas e validade de 7 dias. Não há cron nem reenvio automático periódico nesta versão. O primeiro envio ocorre na própria requisição após o commit, com timeout do transporte. O painel requer ADMIN e o reenvio exige CSRF.

Avisos podem duplicar se o provedor aceitar um envio e a confirmação não chegar ao banco. A API Resend recebe chave de idempotência estável para reduzir esse risco dentro da janela do provedor; SMTP não oferece essa garantia. A tabela guarda destinatário, usuário e tipo de evento, nunca senha ou token de recuperação. Registros enviados são mantidos; planeje retenção/limpeza antes de aumentar o volume.

Links de recuperação não entram nessa fila: se o envio falhar, solicite outro link. O fluxo mantém resposta genérica e revoga o token que não conseguiu enviar. Os logs não incluem resposta bruta do provedor, destinatário ou credenciais. Estado configurado significa que os campos existem; não comprova conectividade nem entrega.

As migrações V14 (H2) e V13 (PostgreSQL) apenas criam a tabela e seu índice. Não removem dados existentes. Com as flags de e-mail false por padrão, atualizar o código não começa envios por conta própria.

## Conferir após configurar

Use contas e caixas de entrada sob seu controle:

1. Cadastre usuário novo com e-mail e confira boas-vindas na entrada e no spam.
2. Faça login; abra Editar perfil no computador e no celular. Salve nome/bio/privacidade e confira persistência após recarregar.
3. Cadastre/troque o e-mail com senha atual; confira avisos nas duas caixas.
4. Troque a senha; confira aviso e rejeição da senha anterior.
5. Saia, abra Esqueci minha senha e solicite recuperação. Abra o link recebido, redefina e confirme login com a nova senha. Reutilizar o link deve falhar.
6. Reinicie/republique a aplicação e confira que perfil e credenciais continuam funcionando.
7. Confira Administração → E-mails da conta. Se houver pendentes, corrija o provedor antes de reenviar.

Testes automatizados usam transportes simulados, sem enviar e-mails. A entrega real e limites do remetente dependem dessa configuração e devem ser conferidos em produção. Nenhuma conta de e-mail, domínio ou serviço pago é criado pelo código.

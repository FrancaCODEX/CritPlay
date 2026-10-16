# Ativar a recuperação de senha

A tela “Esqueci minha senha” depende de um serviço de envio de e-mail no servidor. Digitar um endereço de destino não configura esse serviço. O projeto vem com `RECOVERY_ENABLED=false`; por isso, uma instalação nova informa que a recuperação está indisponível. A nova tela mostra o motivo e permite verificar novamente ou voltar ao login.

## Configurar no computador que executa o CritPlay

Edite o `.env` que fica na raiz do projeto, junto ao `pom.xml`. Preserve as outras configurações e substitua as linhas existentes de recuperação (evite duplicá-las):

```properties
RECOVERY_ENABLED=true
APP_BASE_URL=http://localhost:8080
MAIL_HOST=smtp.seu-provedor.com
MAIL_PORT=587
MAIL_USERNAME=conta_que_envia@seu-provedor.com
MAIL_PASSWORD=credencial_de_envio_do_provedor
MAIL_FROM=conta_que_envia@seu-provedor.com
MAIL_AUTH=true
MAIL_STARTTLS=true
```

Os valores acima são exemplos. Use os dados SMTP reais do provedor, sem aspas ao redor dos valores. `MAIL_USERNAME` e `MAIL_FROM` identificam a conta que envia; o e-mail salvo no perfil é o destino. `APP_BASE_URL` precisa ser o endereço pelo qual o destinatário abre o CritPlay; no computador local pode ser `http://localhost:8080`, mas em hospedagem use a URL HTTPS pública.

Pare a aplicação e execute novamente:

```powershell
.\mvnw.cmd spring-boot:run
```

O `.env` é carregado na inicialização. Variáveis do sistema ou da configuração de execução do IntelliJ têm precedência: se `RECOVERY_ENABLED=false` estiver definido lá, atualize essa configuração também.

## Exemplo com Gmail

Para uma conta Gmail que permite senhas de app:

1. Ative a verificação em duas etapas da conta que enviará as mensagens.
2. Crie uma [senha de app para o CritPlay](https://myaccount.google.com/apppasswords). A disponibilidade dessa opção depende das políticas da conta; consulte a [ajuda oficial do Google](https://support.google.com/accounts/answer/185833?hl=pt-BR).
3. No `.env`, use a configuração abaixo e coloque a senha de app em `MAIL_PASSWORD` (sem espaços de apresentação). Use o endereço completo da conta remetente nos dois campos de e-mail.

```properties
RECOVERY_ENABLED=true
APP_BASE_URL=http://localhost:8080
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=sua_conta@gmail.com
MAIL_PASSWORD=sua_senha_de_app
MAIL_FROM=sua_conta@gmail.com
MAIL_AUTH=true
MAIL_STARTTLS=true
```

Referência do [servidor SMTP e porta STARTTLS do Gmail](https://support.google.com/mail/answer/7104828?hl=pt-BR). Use a credencial de app fornecida para essa finalidade, mantendo-a somente no seu ambiente local. Não envie `.env` ou senhas ao GitHub ou em mensagens.

## Confirmar o funcionamento

1. Se estiver conectado, salve o e-mail correto em Perfil → Editar perfil, confirmando sua senha atual ao alterar o endereço.
2. Depois de reiniciar, abra `http://localhost:8080/api/auth/recovery-status`. `{"enabled":true}` significa que a função foi habilitada e os campos básicos de configuração foram informados; isso não testa conexão nem autenticação no provedor.
3. Abra “Esqueci minha senha” (ou clique em “Verificar novamente”), informe usuário e e-mail cadastrados e envie. A mensagem de retorno é genérica para proteger a identificação das contas.
4. Confira entrada e spam. Abra o link e informe a nova senha duas vezes. O link vale 15 minutos, tem uso único e a troca encerra sessões anteriores.

Se o status continuar falso, confira `RECOVERY_ENABLED`, `MAIL_HOST`, `MAIL_FROM`, o diretório de execução e as variáveis do IntelliJ/sistema. Se o status for verdadeiro, mas não houver entrega, confira as credenciais, remetente autorizado, acesso de rede à porta SMTP e o aviso de falha de envio no console do Java. Pedidos repetidos têm intervalo mínimo de dois minutos; aguarde antes de testar outra vez. A resposta genérica não confirma que uma mensagem foi entregue nem se usuário/e-mail coincidem.

Sem serviço de e-mail configurado, o link não pode ser enviado. Se você ainda tem uma sessão aberta, pode trocar a senha pelo Perfil com a senha atual. Este ajuste não redefine senhas sem comprovação nem retorna tokens na tela, API ou logs. Os testes automatizados simulam o envio; a entrega real precisa ser validada no seu provedor.

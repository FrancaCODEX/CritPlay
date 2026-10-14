# Preparar a hospedagem do CritPlay

A preparação específica Oracle está em [ORACLE_CLOUD.md](ORACLE_CLOUD.md). Ela inclui rede privada, proxy confiável, volumes, backup e atualização; as observações abaixo continuam aplicáveis às demais implantações.

Esta versão continua preparada para desenvolvimento local. Não houve deploy. O GitHub guarda o código, não executa o servidor Java.

## Banco, arquivos e segredos

- Em produção, use PostgreSQL e uma conta de banco dedicada. `compose.yaml` é uma opção de banco local; não transfere dados do H2 automaticamente.
- Configure segredos no ambiente do serviço. `.env` é ignorado pelo Git; não coloque chaves ou senhas em commits, logs ou imagens Docker.
- Configure `APP_BASE_URL` com a URL HTTPS e `SESSION_COOKIE_SECURE=true`. A API já exige CSRF e cookies HttpOnly/SameSite.
- Preserve `data/uploads/` num volume persistente. Imagens RAWG são externas; imagens próprias ficam nesse diretório.

## HTTPS e IP confiável

Mantenha o Java em `127.0.0.1:8080` e coloque um proxy HTTPS na frente, com certificado válido e renovação. O banco também deve ficar numa rede privada. Encaminhe somente as rotas públicas/API necessárias, com limite de corpo compatível com uploads (3 MB).

Não confie indiscriminadamente em `X-Forwarded-For`. Antes de liberar logins via proxy, configure identificação do IP apenas a partir de proxies confiáveis e confirme o endereço que chega aos limites de tentativa. O limiter usa `request.getRemoteAddr()`. No profile `oracle`, o Tomcat resolve esse endereço pelos cabeçalhos enviados apenas pelo Caddy confiável; no ambiente local, cabeçalhos encaminhados não são ativados. Sem essa preparação, usuários atrás de um proxy podem compartilhar o mesmo limite.

Os limites atuais (login, registro, recuperação) são em memória de uma instância. Para escalar ou preservar limites em reinicializações, substitua por armazenamento compartilhado, por exemplo Redis, e valide a implantação. Isso ainda não está implementado nesta versão.

## Backups e serviço

Faça backups periódicos do PostgreSQL (por exemplo `pg_dump`), uploads e configuração secreta por meios separados e protegidos. Para H2 local, pare o Java antes de copiar `data/`; nunca copie um banco aberto como se fosse um backup consistente. Teste a restauração em uma base separada antes de confiar no procedimento.

Execute Java com usuário sem privilégios, reinício controlado (systemd ou equivalente), logs com rotação, monitoração de espaço/erros e alertas. Teste login/logout, migrações, recuperação por SMTP, upload e restauração. A limpeza de imagens é manual pela Administração e não substitui backups.

Uma implantação real ainda depende do domínio, provedor de hospedagem, certificado, configuração de proxy/IP, limites compartilhados e rotina de operação escolhidos pelo responsável.

## Artefatos preparados na v0.5.1

Dockerfile e `.env.production.example` foram acrescentados, com processo sem privilégios, profile PostgreSQL e diretório persistente de uploads. `/api/health` verifica o banco. A construção da imagem e a configuração de proxy/IP/limites devem ser verificadas no provedor escolhido. Veja HOSPEDAGEM.md e MIGRACAO_POSTGRES.md. Importações usam somente uma instância do Java; não configure várias réplicas para o worker atual.

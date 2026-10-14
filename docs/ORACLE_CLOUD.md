# Hospedar o CritPlay na Oracle Cloud Always Free

Preparação de 14/10/2026. Os arquivos estão prontos para revisão e validação; este guia não significa que uma VM foi criada ou que o site foi publicado.

## Configuração escolhida

Uma VM Ubuntu 24.04 ARM (`VM.Standard.A1.Flex`), inicialmente 2 OCPUs e 6 GB de RAM, com boot volume de 50 GB. Java 21, PostgreSQL 17 e Caddy 2 rodam via Docker Compose. O Java tem limite de 2 GB e o banco de 1 GB; a memória restante fica para o sistema, proxy e builds. As imagens usadas têm suporte ARM e a construção ocorre nativamente na VM; a validação inicial de containers no CI usa AMD64.

A documentação Oracle consultada informa 2 OCPUs/12 GB totais para Ampere A1 Always Free, 200 GB de boot/block volumes combinados na região principal e possibilidade de recuperar instâncias ociosas. Confira a elegibilidade e o consumo total da sua conta no painel antes de criar recursos. Falta de capacidade pode impedir a criação da VM. Não confunda Always Free com créditos temporários do trial.

Fonte: [Recursos Always Free](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm). Um domínio próprio pode ter custo separado; você pode usar um domínio ou subdomínio que já possua.

## 1. Criar a VM e a rede

No painel Oracle, escolha a região principal e um compartimento para o CritPlay. Crie a VM com imagem Ubuntu ARM elegível, shape A1 e recursos dentro da franquia disponível. Use chave SSH e preserve a chave privada no seu computador.

Associe a VM a uma subnet pública com acesso por Internet Gateway e atribua um IP público. No NSG ou na Security List que controla a VM, autorize entrada:

| Porta | Origem | Uso |
| --- | --- | --- |
| TCP 22 | Seu IP público `/32` | SSH administrativo. Atualize a regra se seu IP mudar. |
| TCP 80 | Internet | Redirecionamento HTTP e emissão do certificado. |
| TCP 443 | Internet | Site e API por HTTPS. |
| UDP 443 | Internet, opcional | HTTP/3. Sem esta regra, HTTPS por TCP continua funcionando. |

Não abra 8080 nem 5432. Mantenha saída para DNS, registro de imagens, dependências de build, ACME e APIs RAWG/GG.deals. As regras do firewall do Ubuntu também precisam permitir SSH e web. Docker pode contornar regras UFW ao publicar portas; mantenha as restrições na Oracle e publique somente o proxy.

Conecte pelo Windows:

```powershell
ssh -i C:\caminho\chave-oracle.key ubuntu@IP_PUBLICO
```

Instale Docker Engine e o plugin Compose seguindo [as instruções oficiais para Ubuntu](https://docs.docker.com/engine/install/ubuntu/), que incluem ARM64. Instale também `git`. Para os comandos abaixo, use uma conta com acesso ao Docker; no exemplo, `ubuntu` pertence ao grupo `docker`. Esse grupo tem privilégios elevados sobre a VM.

```sh
sudo usermod -aG docker ubuntu
```

Saia e conecte novamente. Confira `docker version` e `docker compose version`.

## 2. Configurar domínio e aplicação

Crie um registro DNS A do domínio/subdomínio para o IP público. Só crie AAAA se a VM tiver IPv6 funcionando com firewall e roteamento correspondentes. Confirme o DNS antes de subir o proxy.

```sh
git clone --branch oracle-cloud https://github.com/FrancaCODEX/gameshelf.git critplay
cd critplay
cp .env.oracle.example .env.oracle
chmod 600 .env.oracle
nano .env.oracle
```

Preencha domínio sem protocolo/caminho, e-mail ACME, senha do banco e credenciais do administrador. Use uma senha forte para o administrador, de 10 caracteres a 72 bytes UTF-8; use uma senha longa e aleatória para o banco. RAWG/GG.deals são opcionais. Para valores que contenham `$`, use aspas simples no arquivo Compose `.env.oracle` para preservar o texto literal.

`compose.oracle.yaml` define profile `postgres,oracle`, URL HTTPS, cookies seguros, banco interno e caminho dos uploads. Não copie seu `.env` local para o servidor nem publique `.env.oracle` no Git. A senha do PostgreSQL é aplicada ao criar um volume novo: alterá-la depois no arquivo não troca a senha dentro do banco. As credenciais iniciais do administrador também não trocam uma conta existente.

Para começar com um banco novo:

```sh
docker compose --env-file .env.oracle -f compose.oracle.yaml up -d --build --wait --wait-timeout 180
docker compose --env-file .env.oracle -f compose.oracle.yaml ps
curl -fsS https://SEU_DOMINIO/api/health
```

O primeiro build pode demorar. O resultado do health check deve indicar `UP`. O Caddy emite/renova o certificado e redireciona HTTP para HTTPS; domínio, DNS, portas e saída ACME precisam funcionar. Acesse o site, entre com a conta configurada e verifique cadastro, diário, nota/resenha e upload. Valores de API só funcionam após configurar as chaves.

Fonte HTTPS: [Caddy — Automatic HTTPS](https://caddyserver.com/docs/automatic-https).

## 3. Banco, uploads e IP confiável

Os volumes `postgres-data`, `uploads`, `caddy-data` e `caddy-config` sobrevivem à recriação dos containers. Não execute `docker compose down -v` em produção: isso remove os volumes. Não compartilhe diretórios/volumes com o H2 local.

Só o Caddy expõe portas. O banco está em rede Docker interna. O profile Oracle confia nos cabeçalhos de IP/protocolo apenas quando a conexão vem do Caddy em `172.30.10.10`; as subnets Docker devem estar livres na VM. Se mudar a rede/IP, atualize também a expressão do proxy confiável.

O Caddy usa o cliente diretamente conectado e descarta o cabeçalho `Forwarded`. Não coloque Cloudflare/CDN/proxy adicional na frente sem configurar e testar a cadeia de IPs confiáveis. O limite de tentativas continua em memória em uma única aplicação; reiniciar limpa essas contagens. Não aumente para várias réplicas nesta configuração.

Fonte proxy: [Spring Boot — Embedded Web Servers](https://docs.spring.io/spring-boot/how-to/webserver.html).

## 4. Trazer os dados locais, se desejar

A migração é opcional. Para preservar suas contas, jogos e resenhas, siga [MIGRACAO_POSTGRES.md](MIGRACAO_POSTGRES.md) antes da primeira inicialização da aplicação no destino; ele deve estar vazio. Inicie somente o banco:

```sh
docker compose --env-file .env.oracle -f compose.oracle.yaml up -d postgres
```

A ferramenta precisa de uma conexão acessível ao PostgreSQL. A porta permanece fechada por padrão; use um túnel SSH com publicação temporária apenas em `127.0.0.1` e remova-a ao terminar. Planeje essa etapa antes de executar o `up` completo. Copie também os uploads para o volume com proprietário UID/GID 10001. A migração não é feita automaticamente pelo deploy.

## 5. Backup e atualização

```sh
bash deploy/oracle/backup.sh
bash deploy/oracle/update.sh
```

O backup interrompe brevemente o Java para produzir um checkpoint consistente de banco e uploads. Grava dump PostgreSQL, arquivo de uploads, commit e checksums em `data/oracle-backups/`, com permissões restritas. O Java é reiniciado mesmo quando a cópia falha, se estava em execução. Backups incompletos usam prefixo `.partial-` e não são marcados como concluídos.

Copie backups concluídos para outro dispositivo/servidor. Uma cópia na mesma VM não protege contra perda do disco. Guarde `.env.oracle` por um meio separado e protegido; ele e os certificados não entram no pacote. Defina retenção segundo o espaço disponível. Para agendar, use o agendador do servidor com caminhos absolutos e logs protegidos; nada foi agendado automaticamente por esta preparação.

O script de atualização exige branch `oracle-cloud` e árvore limpa, faz backup antes do `git pull --ff-only`, reconstrói e espera os serviços saudáveis. Rebuild causa uma breve interrupção. Caso o build falhe, o serviço anterior continua quando possível; confira `docker compose ps` e os logs. Restaurar apenas código não reverte migrações do banco.

Para ver logs sem revelar configuração:

```sh
docker compose --env-file .env.oracle -f compose.oracle.yaml logs --tail=100 app
```

Os logs Docker têm rotação. Não publique a saída de `docker compose config`, que pode conter segredos.

## 6. Testar restauração antes de confiar no backup

Use uma VM/stack separada e um banco vazio, preservando a mesma versão principal do PostgreSQL. Não restaure por cima do banco usado pelos usuários.

1. Confira o pacote: `sha256sum -c SHA256SUMS` na pasta do backup.
2. Configure a stack de teste e suba somente PostgreSQL.
3. Restaure o dump no banco vazio, pela raiz da stack de teste:

```sh
docker compose --env-file .env.oracle -f compose.oracle.yaml exec -T postgres sh -c 'exec pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --exit-on-error --no-owner --no-privileges' < /caminho/postgres.dump
```

4. Com o Java parado e volume de uploads vazio, copie os arquivos:

```sh
docker compose --env-file .env.oracle -f compose.oracle.yaml run --rm --no-deps -T --entrypoint tar app -C /app/data -xzf - < /caminho/uploads.tar.gz
```

5. Inicie a aplicação e confira login, notas, resenhas, listas e imagens. O Caddy pode recriar certificados ao iniciar com o domínio configurado; não use o domínio de produção para uma stack de restauração concorrente.

## Próximo passo para publicar

É necessário ter acesso à VM por SSH, IP público e domínio apontado. Os arquivos de preparação e testes não comprovam funcionamento na sua tenancy. Valide ARM, DNS, emissão real do certificado, permissões dos volumes, chaves externas e restauração nessa VM antes de abrir o site aos usuários.

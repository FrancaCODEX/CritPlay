# Branches do CritPlay

| Branch | Finalidade | Como executar |
| --- | --- | --- |
| `main` | Base comum de funcionalidades e correções revisadas. | Também mantém a execução local padrão. |
| `local` | Execução no Windows/IntelliJ ou WSL, com seu H2 e uploads locais. | `.\mvnw.cmd spring-boot:run` ou `./mvnw spring-boot:run`. |
| `oracle-cloud` | Versão destinada à VM Oracle, com PostgreSQL, Caddy/HTTPS e volumes. | `docker compose --env-file .env.oracle -f compose.oracle.yaml up -d --build --wait`. |

“Remoto” foi interpretado aqui como a versão executada no seu computador. A branch `local` começa na mesma base da `main`, com os ajustes de avaliações, cards e favicon já incorporados. Configuração de ambiente não altera a lógica das funcionalidades.

## Trabalho no computador

Use pastas separadas para evitar alternar código enquanto o Java está aberto ou usar o banco errado:

```powershell
git clone --branch local https://github.com/FrancaCODEX/gameshelf.git critplay-local
git clone --branch oracle-cloud https://github.com/FrancaCODEX/gameshelf.git critplay-oracle
```

Na pasta local, mantenha seu `.env` e `data/`. Na hospedada, use `.env.oracle` e os volumes do Docker. Esses dados e segredos não entram no Git e não são sincronizados entre ambientes. Dados locais só vão para o servidor por migração explícita, conforme [Migração PostgreSQL](MIGRACAO_POSTGRES.md).

## Manter as funcionalidades iguais

1. Crie branches de funcionalidades a partir de `main`. Cada alteração deve entrar no `CHANGELOG.md`, incluindo correções, configuração, testes e documentação.
2. Revise e incorpore o PR em `main`.
3. Atualize `local` e `oracle-cloud` com merges da `main`, resolvendo conflitos antes do envio. Não substitua os arquivos de configuração secreta.

Exemplo em uma cópia usada para manutenção, com Java parado e árvore de trabalho limpa:

```sh
git fetch origin
git switch local
git merge --ff-only origin/local
git merge origin/main
git push origin local
git switch oracle-cloud
git merge --ff-only origin/oracle-cloud
git merge origin/main
git push origin oracle-cloud
```

Use PRs para esses merges se preferir revisão antes de atualizar cada ambiente. Não rebase nem force-push branches usadas pelo servidor. O script de atualização da VM aceita apenas avanço direto da branch hospedada.

A preparação Oracle pode ser incorporada à `main`: usar os arquivos Oracle é opcional e a execução local continua com H2. `local` deve receber a `main` depois dessa incorporação para receber também os guias e o histórico completo.

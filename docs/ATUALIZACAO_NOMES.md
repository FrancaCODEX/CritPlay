# Atualizar os nomes para CritPlay

O projeto usa CritPlay na apresentação e `critplay` em identificadores técnicos.
Links do GitHub, configurações, CI, exemplos Docker, testes e documentação seguem
esse nome. O histórico de commits e branches anteriores continua sendo histórico;
esta alteração não reescreve commits antigos nem altera outras branches.

## Se já usa H2 local

1. Pare a aplicação Java e faça backup da pasta `data/` e do `.env`.
2. Localize o arquivo `.mv.db` que já contém suas contas e jogos. Renomeie esse
   arquivo para **`critplay.mv.db`**, na mesma pasta `data/`. Não renomeie nem
   apague a pasta `uploads/`. Não substitua um arquivo de destino que já existe.
3. Inicie o projeto e confira seu login, biblioteca, resenhas e imagens.

Se preferir manter o nome físico atual, adicione ao `.env`:

```properties
CRITPLAY_LOCAL_DB_URL=jdbc:h2:file:./data/nome_atual
```

Substitua `nome_atual` pelo nome real, **sem `.mv.db`**. O código não precisa ter
o nome físico de cada instalação. Não copie o exemplo literalmente se seu arquivo
tem outro nome. Variáveis de ambiente do sistema/IntelliJ têm prioridade sobre
`.env`; URLs explícitas em `spring.datasource.url` também substituem o padrão.

Se `critplay.mv.db` ainda não existir e houver outro banco `.mv.db` nessa pasta,
a aplicação interrompe a inicialização **antes de criar o datasource e executar
Flyway**. A mensagem orienta a renomear o arquivo ou configurar sua URL. Não
apaga, move ou sobrescreve arquivos. Essa verificação evita aparentar perda de
dados por abrir um banco novo durante a troca de nome.

## Variáveis da aplicação

Use `CRITPLAY_ADMIN_USERNAME`, `CRITPLAY_ADMIN_PASSWORD`, `CRITPLAY_ADMIN_NAME`
e `CRITPLAY_MEDIA_DIRECTORY`. Aliases anteriores foram removidos; copie seus
valores para as chaves atuais no `.env` ou nas variáveis do IntelliJ. O diretório
padrão de imagens continua `data/uploads/`. A configuração do administrador não
redefine a senha de uma conta existente.

Capturas opcionais de QA usam `CRITPLAY_QA_DIR`.

## PostgreSQL e Docker Compose existentes

Os exemplos novos usam banco/usuário `critplay` e volume `critplay-postgres`.
Um banco já inicializado **não muda de nome** por alterar variáveis do Compose.
Preserve sua conexão atual em `DB_URL`, `DB_USERNAME` e `DB_PASSWORD`.

Antes de executar `docker compose up`, defina também:

```properties
DB_NAME=nome_real_do_banco
POSTGRES_VOLUME_NAME=nome_exato_do_volume_existente
```

Você pode consultar os volumes sem alterar nada:

```sh
docker volume ls
```

Para conferir o volume de um contêiner existente:

```sh
docker inspect --format '{{range .Mounts}}{{println .Name .Destination}}{{end}}' nome_do_container
```

Use o nome do volume montado em `/var/lib/postgresql/data`. O Compose exige os
nomes explícitos para evitar criar outro volume vazio por conta da troca do
identificador. Não substitua seu `.env` pelo exemplo nem execute `down -v`
durante a atualização. Esta mudança não renomeia volumes, bancos ou usuários
PostgreSQL existentes e não apaga seus dados.

Novas instalações podem usar os valores `critplay`/`critplay-postgres` de
`.env.example`. Configure uma senha própria antes de iniciar.

## Ferramenta H2 → PostgreSQL

O caminho de origem padrão passa a `jdbc:h2:file:./data/critplay`.
Se mantiver o arquivo atual com outro nome, informe sua URL em `H2_SOURCE_URL`
antes de executar a ferramenta. O destino continua usando `DB_URL`,
`DB_USERNAME` e `DB_PASSWORD`. Veja [o guia de migração](MIGRACAO_POSTGRES.md).

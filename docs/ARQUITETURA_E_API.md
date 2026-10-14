# Arquitetura e API — v0.5

## Biblioteca e comunidade

| Rota | Acesso | Comportamento |
| --- | --- | --- |
| PUT /api/diary/{gameId} | Conta autenticada + CSRF | `rating`: null ou número de 0.5 a 5, múltiplo de 0.5. Preserva a API de notas inteiras. |
| GET /api/games/{gameId}/reviews | Público, filtrado pelo visitante | Média, contadores e até 50 avaliações. Inclui entryId, likeCount e likedByMe. |
| PUT /api/reviews/{entryId}/like | Conta autenticada + CSRF | Curtida idempotente em resenha visível de outro autor. |
| DELETE /api/reviews/{entryId}/like | Conta autenticada + CSRF | Remove a curtida de forma idempotente, respeitando o acesso atual. |
| GET /api/feed?before={id} | Conta autenticada | Até 25 atividades e nextCursor. Ordenação por ID decrescente; cursor exclusivo. |
| PUT /api/profile/gamer | Conta autenticada + CSRF | bannerUrl próprio ou null; featuredGameIds obrigatório, até quatro IDs distintos em ordem. |

Feed, avaliações e curtidas compartilham o predicado de visibilidade atual. Os eventos guardam status/nota no momento da ação, sem copiar o texto da resenha. Quando o diário é apagado, suas atividades e curtidas são removidas por cascata. Eventos não expõem favoritos/desejos e não são gerados por mudanças somente nessas preferências.

H2 V7 e PostgreSQL V5 criam profile_featured_games, review_likes, activity_events e banner_url, e convertem rating para double com restrição de meia estrela. A migração entre bancos inclui essas tabelas e reinicia a sequência das atividades.

Ordenação e visualização da biblioteca são feitas no cliente, sobre o catálogo filtrado e o diário da conta; o modo padrão do catálogo conserva a ordem do backend. Apenas o profile Oracle, guardado em outra branch, altera a configuração de hospedagem.

Monólito modular: interface estática e API REST no mesmo processo Java. Controller recebe HTTP; Service implementa regras; Repository usa JPA; Flyway versiona o banco. O frontend nunca decide permissões sozinho.

## Modelo

`app_users`: ID, username único normalizado em minúsculas, nome de exibição, hash de senha, papel `USER`/`ADMIN` e data de criação. Entidades de usuário não são serializadas: `UserView` inclui apenas ID, username, nome e papel.

`games`: ID automático, título, gênero, plataformas, ano, Steam App ID opcional e descrição. Plataformas são validadas por enum na entrada e armazenadas em texto neste MVP.

`diary_entries`: ID, proprietário, jogo, status, nota opcional, avaliação, favorito e instante de atualização. Há FKs para usuário e jogo, índice único `(user_id, game_id)` e limite da nota no banco. O usuário não envia um proprietário: ele vem da sessão autenticada. Buscar, editar e remover sempre incluem esse proprietário.

A coluna de proprietário permite null exclusivamente para preservar o diário da v0.1 até o bootstrap administrativo. Esses registros não são retornados para contas comuns.

## Autenticação no Postman

1. GET `/api/auth/csrf`. Preserve o cookie `JSESSIONID` e anote `token`/`headerName`.
2. Para escritas, envie esse token no cabeçalho indicado (normalmente `X-CSRF-TOKEN`).
3. POST `/api/auth/login` com **x-www-form-urlencoded**, campos `username` e `password`. Não é JSON.
4. Após login, GET `/api/auth/csrf` novamente, porque o Spring troca a sessão/token. Mantenha o cookie atualizado.
5. GET `/api/auth/me` mostra a conta e papel. O JSON não inclui senha ou hash.
6. POST `/api/auth/logout` também exige CSRF. Após logout, obtenha novo token antes de registrar/entrar de novo.

No navegador, `app.js` faz esse fluxo automaticamente. Sessão tem timeout por inatividade de 30 minutos, cookie HttpOnly e SameSite=Lax. Em HTTPS, habilitar `SESSION_COOKIE_SECURE=true`. Não há JWT nem token em localStorage.

## Rotas

| Método | Rota | Acesso |
| --- | --- | --- |
| GET | `/api/games` | Público |
| GET | `/api/games?q=elden&genre=RPG&platform=PC` | Público |
| GET | `/api/games/{id}` | Público |
| POST | `/api/games` | ADMIN + CSRF |
| GET | `/api/games/{id}/prices` | Conta autenticada |
| GET | `/api/diary` | Diário da conta autenticada |
| PUT | `/api/diary/{gameId}` | Conta autenticada + CSRF |
| DELETE | `/api/diary/{gameId}` | Conta autenticada + CSRF |
| GET | `/api/auth/csrf` | Público; token da sessão atual |
| GET | `/api/auth/me` | Público; `authenticated:false` sem login |
| POST | `/api/auth/register` | Público + CSRF; cria USER |
| POST | `/api/auth/login` | Público + CSRF; formulário |
| POST | `/api/auth/logout` | CSRF; encerra sessão |

Sem login, rotas protegidas retornam 401. Um usuário comum tentando cadastrar jogo recebe 403. Escrita sem CSRF também recebe 403. Validação inválida retorna 400; jogo inexistente 404; usuário duplicado/conflito de dados 409. Cadastro de conta e jogo retorna 201. Campos desconhecidos são recusados, incluindo `role` no registro e `userId` no diário.

## Criar conta

```json
{
  "username": "jogador",
  "displayName": "Nome do jogador",
  "password": "uma_senha_forte_de_exemplo"
}
```

O bootstrap de ADMIN fica nas variáveis do servidor, fora da API de registro.

## Adicionar jogo como administrador

```json
{
  "title": "Meu jogo",
  "genre": "Aventura",
  "platforms": ["PC", "PLAYSTATION", "XBOX"],
  "releaseYear": 2026,
  "steamAppId": 420,
  "description": "Descrição com pelo menos vinte caracteres."
}
```

Plataformas permitidas: `PC`, `PLAYSTATION`, `XBOX`, `NINTENDO`; ao menos uma. Steam App ID pode ser null, mas, quando informado, deve ser positivo e a seleção deve incluir PC. Não se envia ID interno do jogo: o banco gera. O ID 420 é um exemplo de formato; informe o ID correto do jogo que cadastrar.

## Diário

```json
{
  "status": "PLAYING",
  "rating": 5,
  "review": "Uma experiência incrível.",
  "favorite": true
}
```

Status: `WANT_TO_PLAY`, `PLAYING`, `COMPLETED`, `DROPPED`. Nota opcional de 1 a 5; texto até 2000 caracteres. PUT substitui a experiência da conta naquele jogo. O mesmo jogo pode aparecer em diários de pessoas diferentes sem conflito.

## Preços

`PriceService` verifica o jogo e consulta o cache por jogo; `GgDealsClient` chama a API com chave no servidor. Não há conversão de moeda. Zero é preço zero, null é ausência de dado. `fetchedAt` é o instante da consulta, não a última alteração da loja.

Estados: `LIVE`, `NOT_CONFIGURED`, `NOT_FOUND`, `RATE_LIMITED`, `UNAVAILABLE`, `UNSUPPORTED`. A consulta de um jogo válido retorna HTTP 200 com o estado do provedor; jogo inexistente retorna 404. Os mínimos são de PC, separados entre oficiais e keyshops; não são ofertas individuais de consoles.

## Migrações

V1 representa o catálogo/diário legado. Um banco novo executa V1 e V2; um banco da v0.1 sem histórico recebe baseline 1 e executa V2. V2 acrescenta usuários, IDs automáticos, proprietários, unicidade e FKs. JPA usa `validate`, sem alterar tabelas silenciosamente. Não edite uma migração já aplicada; crie V3 para uma futura mudança.

Antes de produção: migrar para PostgreSQL com migrações próprias, normalizar gêneros/plataformas, paginar a busca no banco, adicionar limites de autenticação e orçamento de chamadas externas. O catálogo pequeno ainda é filtrado em memória.

## Perfil, listas e imagens na v0.3

| Método | Rota | Regra |
| --- | --- | --- |
| GET | `/api/profile` | Conta atual, dados privados, estatísticas e resenhas |
| PUT | `/api/profile` | Editar nome/bio/avatar; mudança de e-mail exige senha atual |
| PUT | `/api/profile/password` | Senha atual + nova; revoga todas as sessões |
| GET/POST | `/api/lists` | Listar/criar coleções da conta |
| GET/PUT/DELETE | `/api/lists/{id}` | Ler/editar/excluir somente a coleção própria |
| PUT/DELETE | `/api/lists/{id}/games/{gameId}` | Adicionar/remover jogos da coleção própria |
| POST | `/api/images` | Conta autenticada, multipart `file`, PNG/JPEG até 2 MB |
| GET | `/media/{arquivo}` | Imagem pública pelo link, nome gerado pelo servidor |
| PUT | `/api/games/{id}` | ADMIN; mantém ID e referências |
| POST | `/api/games/import` | ADMIN; JSON `{games:[...]}`, lote de 1 a 50, transacional |
| GET | `/api/auth/recovery-status` | Informa se SMTP de recuperação foi configurado |
| POST | `/api/auth/forgot-password` | Usuário + e-mail; resposta genérica, requer CSRF |
| POST | `/api/auth/reset-password` | Token + nova senha; token expirável/uso único, requer CSRF |

PUT `/api/profile` aceita `displayName`, `bio`, `email`, `avatarUrl`, `currentPassword`; username/role não são editáveis. Avatar/coverUrl precisam corresponder a uma imagem enviada pela conta. ADMIN pode preservar a capa já existente de outro administrador ao editar um jogo, mas não atribuir arbitrariamente uma imagem de outra pessoa.

`DiaryRequest` acrescenta `wishlist`, independente de status/favorite. Para clientes da v0.2 que omitem o campo, `WANT_TO_PLAY` implica wishlist=true; os outros status implicam false. A interface nova sempre envia o campo explícito. As resenhas permanecem no diário e são agregadas no Perfil, sem duplicar armazenamento.

`CreateGameRequest` acrescenta `coverUrl` e `storeLinks:{playstation,xbox,nintendo}`. Links só aceitam HTTPS e os hosts oficiais definidos em `CatalogService`; não são consultados pelo servidor. Não há scraping, conversão de moedas ou garantia de menor preço para consoles.

`game_lists` e `game_list_items` têm FKs e unicidade (lista,jogo). A propriedade é verificada antes de cada leitura/escrita; IDs de listas de outra pessoa retornam 404. Excluir uma lista não altera os diários. `password_reset_tokens` guarda apenas SHA-256 do token, validade e estado de uso. Consumo usa bloqueio pessimista para impedir reutilização concorrente.

O principal da sessão inclui `sessionVersion`. Trocar/resetar senha incrementa essa versão no banco; um filtro invalida as sessões antigas na próxima requisição. Não se envia token de sessão para JavaScript nem se grava senha no navegador.

V3 migra o H2 existente com perfil, wishlist, imagens, listas e tokens. PostgreSQL tem um esquema inicial próprio em `db/postgresql` e é um banco separado; não recebe os dados H2 automaticamente. Migrações já aplicadas V1/V2 permanecem inalteradas.

## Social e administração na v0.5

A API social permanece: GET `/api/people?q=nome`, GET `/api/people/{username}`, GET `/api/social`, PUT `/api/profile/privacy`, PUT/DELETE `/api/people/{username}/follow`, PUT `/api/people/{username}/friendship`. A autorização de visibilidade é aplicada em cada leitura. Suspensão esconde o perfil e bloqueia conexões novas; relações existentes podem ser removidas.

| Método | Rota | Função (ADMIN) |
| --- | --- | --- |
| GET | `/api/admin/accounts?q=nome` | Até 50 contas; identidade, papel e estado; sem e-mail/senha |
| PUT | `/api/admin/accounts/{id}` | `{role:"USER"/"ADMIN",enabled:true/false}` |
| GET | `/api/admin/images/cleanup` | Prévia: count, bytes, graceHours, limit |
| POST | `/api/admin/images/cleanup` | `{confirm:true}`; recalcula e exclui elegíveis |
| GET | `/api/admin/metadata/status` | Configuração RAWG, sem chave |
| GET | `/api/admin/metadata/search?q=titulo` | Busca de até 10 jogos na RAWG |
| GET | `/api/admin/metadata/{id}` | Prévia completa; cache de 10 minutos, até 200 registros |
| POST | `/api/admin/metadata/import` | `{rawgId:123,targetGameId:null,game:{...CreateGameRequest}}` |

Todas as escritas exigem CSRF. `targetGameId` ausente cria um jogo; presente atualiza mantendo referências. Jogos expõem `rawgId`, `metadataCoverUrl` e `metadataSourceUrl`; a imagem do provedor não substitui a capa própria. Chave RAWG permanece no servidor e nunca aparece nas mensagens/logs do cliente.

Preços permanecem em GET `/api/games/{id}/prices` para PC. As rotas de preços por plataforma/associação/busca de consoles foram removidas. V5 H2 / V3 PostgreSQL eliminam `game_console_sources` e acrescentam metadados, estado de conta, versões das entidades, `account_audit` e `operation_locks`. Migrações anteriores mantêm seus checksums.

`AccountAdminService` trava a operação administrativa até o commit, preserva um administrador ativo, impede auto-rebaixamento/suspensão e incrementa `sessionVersion`. `AccountPrincipal` respeita `enabled` no login; `AccountSessionFilter` verifica estado/versão nas sessões existentes. A recuperação de senha também recusa contas suspensas. `@Version` em usuários/jogos evita sobrescritas concorrentes.

Limpeza de imagens mantém a trava `media` até o commit; criação/edição de jogos e atualização de perfis obtêm a mesma trava antes de alterar referências. A prévia não decide exclusões: referências e idade são verificadas novamente na execução. Uploads recentes ficam protegidos por 24 horas.

## Ampliação v0.5.1

V6 H2 / V4 PostgreSQL: datas e estado de moderação no diário, `user_blocks`, `content_reports`, `import_jobs` e trava `imports`. Migrações já aplicadas permanecem intactas.

| Método | Rota | Acesso |
| --- | --- | --- |
| GET | `/api/health` | Público, verifica conexão com o banco |
| GET | `/api/blocks` | Bloqueados da conta atual |
| PUT / DELETE | `/api/blocks/{username}` | Bloquear/desbloquear; CSRF |
| POST | `/api/reports` | `{username,gameId:null/ouID,reason}`; CSRF |
| GET | `/api/admin/reports` | ADMIN; últimas 100 denúncias |
| PUT | `/api/admin/reports/{id}` | ADMIN; `{status:RESOLVED/DISMISSED,hideReview:null/true/false}` |
| GET / POST | `/api/admin/import-jobs` | ADMIN; histórico/iniciar `{count:500..2000}` |
| POST | `/api/admin/import-jobs/{id}/pause` | ADMIN |
| POST | `/api/admin/import-jobs/{id}/resume` | ADMIN; exige chave RAWG |

Datas: `DiaryRequest` acrescenta `startDate` e `finishDate` no formato ISO yyyy-MM-dd. `reviewHidden` é somente leitura para o autor; mudanças só pela moderação. Diário usa versão otimista para impedir uma edição concorrente de sobrescrever moderação.

Bloqueios travam o par de usuários como amizades e removem relações existentes. A busca e leitura autenticada verificam bloqueio nos dois sentidos; visitante anônimo continua sujeito à visibilidade pública, sem identificar a pessoa bloqueada. Denunciar uma resenha exige poder lê-la no momento da denúncia. A moderação não apaga o texto do diário.

Lotes usam um executor único e estado persistido no banco. Consultas de rede ocorrem fora da transação de consumo; jogo/associação/cursor/contadores são gravados juntos. Fonte sem preço ou sem Steam ID não muda a integração de preços de PC. Interrupções e falhas pausam o processo; reinício exige retomada. Não há leasing distribuído para múltiplas réplicas.

Migração é um comando local `--migrate-h2-to-postgres`, não uma rota. Abre H2 existente em modo leitura e grava o destino novo/vazio em uma transação, verifica contagens/colunas e ajusta sequências. Flyway configura o esquema PostgreSQL; tokens de recuperação não são copiados.

## Steam App ID — v0.5.2

ADMIN e CSRF obrigatórios. `POST /api/admin/steam/resolve` com `{ "title": "Hades" }` retorna `status`, `appId` opcional e `message`, sem gravar. `POST /api/admin/steam/games/{id}` consulta e associa somente se o jogo ainda não tem ID e está em PC. Estados: FOUND, PRESERVED, NOT_FOUND, AMBIGUOUS, CONFLICT, UNAVAILABLE. Falha da fonte não inventa IDs. Não há chave adicional; endpoints públicos da loja podem mudar sem aviso.

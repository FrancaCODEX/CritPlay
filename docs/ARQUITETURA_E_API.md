# Arquitetura e API — v0.4

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

## Social e preços por plataforma na v0.4

| Método | Rota | Acesso e função |
| --- | --- | --- |
| GET | `/api/people?q=nome` | Autenticado; busca literal, mínimo 2 caracteres, até 30 perfis descobríveis |
| GET | `/api/people/{username}` | Visitante ou autenticado; aplica descoberta/visibilidade; nunca retorna e-mail |
| GET | `/api/social` | Conexões e pedidos da conta atual |
| PUT | `/api/profile/privacy` | Própria conta; `{visibility:PUBLIC/FRIENDS/PRIVATE,discoverable:true/false}` |
| PUT / DELETE | `/api/people/{username}/follow` | Seguir/deixar de seguir, idempotente |
| PUT | `/api/people/{username}/friendship` | `{action:REQUEST/ACCEPT/REJECT/CANCEL/REMOVE}` |
| GET | `/api/games/{id}/prices/platforms` | Autenticado; array de cotações para plataformas presentes no catálogo |
| GET / PUT | `/api/admin/games/{id}/price-sources` | ADMIN; consulta/configura `{sources:{PLAYSTATION:123,XBOX:456}}` |
| GET | `/api/admin/console-search?q=titulo&platform=XBOX` | ADMIN; busca edições no PSprices |

O endpoint anterior `/api/games/{id}/prices` continua retornando apenas a cotação PC para compatibilidade. Os preços de console acrescentam `region`, `originalPrice`, `discountPercent`, `edition` e `storeUrl`; `NOT_MAPPED` indica chave configurada sem uma edição associada. Preços não são persistidos no banco.

H2: V4. PostgreSQL: V2, sem alterar migrações já aplicadas. Novas tabelas `user_follows`, `friendships`, `game_console_sources`. O par de amizade tem IDs ordenados e chave única; operações sociais travam os dois usuários em ordem consistente para evitar pedidos duplicados/recíprocos concorrentes. Seguir é independente da amizade. Privacidade é avaliada em cada consulta, sem cache de perfis.

Perfis autorizados retornam bio, estatísticas e resenhas sanitizadas, sem a entidade completa do usuário/diário. Pedidos só podem ser aceitos/recusados pelo destinatário. Não há compartilhamento de listas personalizadas nesta versão. IDs de fontes são validados quanto à plataforma cadastrada; a resposta do provedor é validada novamente antes de exibir o preço.

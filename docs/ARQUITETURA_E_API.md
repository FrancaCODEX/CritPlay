# Arquitetura e API — v0.2

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

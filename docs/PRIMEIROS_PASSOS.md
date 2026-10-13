# Vamos construir juntos — v0.2

## Rodar e testar o que você pediu

Configure `.env` conforme o README, entre como administrador e clique em **Adicionar jogo**. Faça um cadastro com Steam App ID correto se quiser usar capas e preços de PC. Depois saia, crie uma conta comum e confira que ela consegue usar o diário, mas não cadastrar jogos.

Abra um jogo, marque “Jogando”, dê uma nota e salve. Reinicie o Java e confira se o registro permanece. Uma segunda conta começa com um diário vazio.

## Seu primeiro exercício

Cadastre um jogo pela tela e acompanhe estas partes:

1. `app.js`: o formulário monta o JSON e chama `POST /api/games` com o token CSRF.
2. `SecurityConfig`: decide se a sessão é de ADMIN antes de deixar a requisição chegar ao controller.
3. `CatalogController`: recebe o DTO e aplica validação.
4. `CatalogService`: transforma plataformas e cria o objeto Game.
5. `GameRepository`: o Spring Data grava o objeto; o banco gera seu ID.
6. A resposta JSON volta para o navegador, que atualiza o catálogo.

Um controller corresponde à camada que você conhece no Laravel; o service organiza regras; o repository faz acesso aos dados. JPA não é idêntico ao Eloquent, mas a comparação ajuda a localizar responsabilidades.

## Entender o login

Spring Security verifica a senha usando BCrypt e mantém a identidade em uma sessão. O navegador envia um cookie HttpOnly automaticamente. O papel ADMIN vem da conta no banco, nunca de um campo preenchido pelo usuário.

O cadastro comum aceita apenas nome de usuário, nome de exibição e senha. O administrador inicial é criado por configuração explícita do servidor. Não confunda usuário do GameShelf com conta do GitHub: são sistemas separados.

## Entender o diário

O controlador obtém a pessoa autenticada e passa seu ID ao serviço. O serviço busca sempre por usuário **e** jogo. Por isso duas pessoas podem avaliar o mesmo jogo sem compartilhar registros. Não confie num `userId` enviado pelo frontend para escolher o proprietário.

## Próximas entregas

| Etapa | O que vamos construir | O que você pratica |
| --- | --- | --- |
| Atual | Cadastro de jogos por ADMIN e contas com diário próprio | Spring Security, sessões, autorização e migrações |
| Próxima | Edição de jogos e troca de senha | PUT, DTOs, formulários e verificação da senha atual |
| Depois | Listas personalizadas e página de perfil | Relacionamentos e consultas |
| Depois | PostgreSQL e Docker Compose | Ambientes, banco e infraestrutura |
| Depois | Importação de catálogo | API externa, paginação e cache |
| Depois | Alertas e novas fontes de preço | Jobs e adapters |
| Depois | Hospedar o projeto | Deploy, HTTPS e operação |

Faça uma etapa por vez. Seu próximo exercício pode ser criar a edição de um jogo usando o cadastro atual como referência. Podemos revisar juntos antes de ampliar.

Ao pedir ajuda, envie o arquivo alterado ou o erro completo, sem incluir `.env` ou senhas. O projeto inclui exemplos de configuração sem credenciais reais.

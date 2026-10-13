# GameShelf

Catálogo e diário de jogos em Java, com interface inspirada em serviços de streaming. Um projeto para aprender construindo, organizado por funcionalidades.

## Versão 0.4

- Catálogo inicial de 12 jogos, busca e filtros por gênero/plataforma.
- Cadastro de jogos pela interface, restrito a administradores também na API.
- Criação de contas comuns, login e logout com Spring Security.
- Senhas armazenadas como hash BCrypt; autenticação por sessão HTTP.
- Diário individual: quero jogar, jogando, finalizado, abandonado, nota de 1 a 5, avaliação e favorito.
- Banco H2 em arquivo; migrações Flyway preservam os dados da versão 0.1.
- Integração GG.deals para menores preços e históricos de PC, região Brasil.
- Proteção CSRF para login, registro, logout e demais operações de escrita.
- Perfil editável com nome, bio, avatar, e-mail e suas resenhas.
- Lista de desejos independente do progresso de jogo, além de favoritos.
- Listas personalizadas particulares: criar, editar, excluir, adicionar/remover jogos.
- Edição administrativa de jogos e importação JSON de até 50 jogos por lote.
- Upload de avatar e capas próprias, validado e reencodificado pelo servidor.
- Troca de senha com revogação das sessões; recuperação por e-mail com token único/expirável.
- Tema roxo, com layout responsivo.
- Comunidade: busca de usuários, seguir, pedidos de amizade, aceitar/recusar/cancelar/desfazer.
- Perfis compartilháveis, com privacidade público/só amigos/privado e controle de descoberta.
- Painel de preços separado por PC, PlayStation, Xbox e Nintendo, conforme o catálogo.
- Adaptador PSprices B2B opcional para preços de consoles; exige chave/plano próprios e associação da edição pelo administrador.
- Profile PostgreSQL e Compose opcionais.
- Maven Wrapper e CI com testes HTTP, PostgreSQL real e navegação no navegador.

Java 17 ou 21, Spring Boot 3.5.16, Spring Data JPA, Spring Security, Flyway, Bean Validation, H2, HTML/CSS/JavaScript. O mesmo processo Java serve as telas e a API; não precisa de Node, Docker ou banco separado para começar.

## Rodar no Windows

1. Instale um JDK 17 ou 21 (`java -version`).
2. Abra a pasta que contém `pom.xml` no IntelliJ IDEA ou VS Code com as extensões Java.
3. Copie `.env.example` para `.env` na raiz do projeto:

```powershell
Copy-Item .env.example .env
```

4. Edite `.env` localmente. Use valores sem aspas:

```properties
GGDEALS_API_KEY=cole_sua_chave_aqui
GAMESHELF_ADMIN_USERNAME=franca
GAMESHELF_ADMIN_PASSWORD=escolha_uma_senha_forte_e_unica
GAMESHELF_ADMIN_NAME=Vinicius
SESSION_COOKIE_SECURE=false
```

A senha deve ter pelo menos 10 caracteres e no máximo 72 bytes UTF-8. Não existe uma senha padrão. O nome de usuário aceita 3 a 30 letras, números, ponto, hífen ou sublinhado. O `.env` é importado pelo Spring Boot e excluído do Git e do ZIP de entrega. Variáveis de ambiente também são aceitas e têm precedência sobre esse arquivo.

5. Execute no terminal, na pasta do projeto:

```powershell
.\mvnw.cmd spring-boot:run
```

6. Abra http://localhost:8080 e entre com a conta de administrador configurada. O botão **Adicionar jogo** aparecerá no cabeçalho. Outras pessoas podem usar **Criar conta**, mas não recebem permissão de administrador.

A primeira execução precisa de internet para baixar Maven e as dependências. A importação da IDE pode levar alguns minutos. Se usar uma Run Configuration da IDE, configure o diretório de trabalho como a raiz do projeto para encontrar `.env` e o banco.

## Linux / WSL

```bash
cp .env.example .env
# Preencha .env no seu editor
chmod +x mvnw
./mvnw spring-boot:run
```

Sempre inicie pela raiz do projeto. O banco fica em `data/gameshelf.mv.db`. Não apague essa pasta para atualizar a versão. Pare a aplicação antes de copiar o banco ou substituir os arquivos do projeto.

## Como funciona o administrador

O bootstrap só cria um administrador quando usuário e senha foram configurados pelo responsável pelo servidor. Registro público sempre cria `USER`. O JSON de cadastro não aceita um campo `role` e não existe endpoint público para elevar permissões.

Depois que a conta administrativa foi criada, mudar a senha no `.env` **não** altera a senha existente. Pode retirar as variáveis de bootstrap após a criação: a conta já está no banco. Para trocar a senha, use Perfil; recuperação por e-mail depende do SMTP configurado. Gerenciamento de papéis pela interface continua previsto para outra etapa.

Se o nome configurado já pertence a um usuário comum, a aplicação recusa o bootstrap em vez de promovê-lo silenciosamente. Não configure mais de uma instância para migrar/inicializar o mesmo banco H2 ao mesmo tempo.

## Atualizar da v0.1

Faça uma cópia de segurança de `data/` com o Java parado. Substitua o código, mantenha `data/` e configure o administrador antes de iniciar a versão atual.

Flyway identifica o esquema antigo, mantém os jogos e acrescenta usuários/proprietários ao diário. As notas antigas permanecem preservadas e são atribuídas ao administrador configurado. Sem administrador configurado, ficam sem proprietário e invisíveis para contas comuns; serão atribuídas quando o bootstrap administrativo for executado.

O cadastro de novos jogos usa IDs automáticos a partir de 1000, mantendo os IDs dos 12 jogos iniciais. Esta migração contempla o esquema original da entrega v0.1; para alterações manuais de esquema ou IDs que conflitem com essa faixa, revise a migração antes de rodar.

## GG.deals

Gere a chave em https://gg.deals/api/ e preencha `GGDEALS_API_KEY`. A chave nunca é enviada para o navegador nem registrada nos logs do cliente. Para reduzir consultas, os preços ficam em cache em memória por 1 hora; falhas por 1 minuto. A consulta de preços exige login.

O endpoint utilizado, `https://api.gg.deals/v1/prices/by-steam-app-id/`, com `region=br`, retorna menores preços de **PC** por categoria de loja, valores históricos, moeda e link para o GG.deals. Não fornece uma lista de cada loja nem comparação de preços de consoles. O painel separa plataformas disponíveis no catálogo; a seção de PC usa o GG.deals. O link leva à comparação detalhada no provedor.

Sem chave ou sem resposta válida, a interface mostra a indisponibilidade. Não há preços fictícios. Steam App ID é opcional para jogos sem versão Steam; nesses casos, o card mostra o título e o painel não tenta consultar esse endpoint.

O plano gratuito exige uso pessoal/hobby/open source, atribuição e preservação de links retornados. A interface inclui o crédito e mantém os parâmetros dos links. Limites documentados em 13/10/2026: 100 registros por minuto e 1000 por hora. Antes de aumentar catálogo/tráfego, implementar orçamento de consultas e cache compartilhado. Para uso comercial, consultar o provedor.

Fontes: https://gg.deals/api/ e https://gg.deals/api/prices/.

## Testar e empacotar

```powershell
.\mvnw.cmd clean verify
java -jar target/gameshelf-0.4.0.jar
```

No Linux, use `./mvnw`. Testes usam banco em memória e credenciais fictícias exclusivas de teste. Não fazem chamadas ao GG.deals e não modificam seu banco pessoal. Detalhes da verificação estão em `docs/VALIDACAO.md`.

## Estrutura

```text
src/main/java/br/com/gameshelf/
  GameShelfApplication.java
  catalog/    → jogos, busca e cadastro administrativo
  diary/      → registros limitados ao proprietário autenticado
  users/      → cadastro, conta e bootstrap administrativo
  security/   → sessões, BCrypt, CSRF e autorização de rotas
  prices/     → cliente GG.deals e cache
  shared/     → erros controlados
src/main/resources/
  db/migration/            → versões do esquema
  application.properties   → configuração
  static/                  → HTML, CSS e JavaScript
src/test/                  → testes de HTTP, permissões e preços
.github/workflows/         → CI no GitHub
```

Veja `docs/PRIMEIROS_PASSOS.md` para aprender o fluxo e `docs/ARQUITETURA_E_API.md` para usar o Postman.

## GitHub

Siga `docs/GITHUB.md` para enviar ao repositório `FrancaCODEX/gameshelf`. Não envie `.env`, `data/` ou `target/`. O workflow testa e empacota; não publica um site. Apenas criar o repositório não coloca o Java em um servidor.

## Atualizar da v0.2/v0.3 pelo IntelliJ

Pare o Java. Faça backup de `data/` e preserve seu `.env`. Em **Git → Pull** atualize a branch main; no terminal, pode usar `git pull --ff-only`. Se você tiver mudanças locais, salve-as em um commit ou use o stash do IntelliJ antes de atualizar; não use reset forçado. Rode novamente. As migrações V3/V4 acrescentam os dados novos sem apagar contas, jogos ou resenhas. Contas existentes permanecem privadas e fora da busca até seu dono ativar essas opções.

As funcionalidades de perfil/listas/troca de senha funcionam com seu `.env` atual. Campos novos de SMTP e PostgreSQL são opcionais. Não substitua seu arquivo de credenciais por `.env.example` ao atualizar.

## Usar as funcionalidades novas

- **Perfil:** edite nome/bio/foto. Para acrescentar ou alterar o e-mail, informe sua senha atual. As resenhas aparecem abaixo, com botão para editar a experiência do jogo.
- **Desejos:** marque a opção no diário. Ela não muda seu status de jogando/finalizado. Registros antigos com “Quero jogar” são incluídos automaticamente durante a migração.
- **Listas:** crie coleções e adicione jogos no painel de detalhes. Cada conta só vê/edita suas listas; excluir uma lista não apaga notas ou resenhas.
- **Editar jogo:** ADMIN abre o detalhe e clica em Editar jogo. O ID é preservado, mantendo as referências dos diários/listas. Steam App IDs repetidos são recusados.
- **Importar:** ADMIN seleciona um JSON no formato de `docs/examples/catalogo.json`; de 1 a 50 jogos. O lote inteiro é validado e qualquer conflito cancela a gravação de todos. Não é importação automática de uma API externa.
- **Imagens:** PNG/JPEG até 2 MB, no máximo 4096 pixels e 8 megapixels. Avatares e capas ficam em `data/uploads/`, fora do Git. Após enviar, salve o perfil/jogo para aplicar a imagem. URLs das imagens podem ser abertas por quem tiver seu link; a visibilidade do perfil/resenhas segue sua configuração de privacidade.
- **Consoles:** links oficiais continuam disponíveis. A consulta de preços exige configurar o PSprices e associar a edição correta; veja abaixo.

Não há exclusão de contas/jogos nem elevação de papéis pela interface. **Perfil** continua sendo sua área de edição; **Comunidade** permite encontrar e abrir perfis compartilhados.

## Senhas e recuperação

Em Perfil, **Trocar senha** exige a senha atual e confirmação da nova senha. Todas as sessões são revogadas e você entra novamente. O nome de usuário permanece fixo para manter a identificação do login.

Para **Esqueci minha senha**, configure SMTP no `.env`:

```properties
RECOVERY_ENABLED=true
APP_BASE_URL=http://localhost:8080
MAIL_HOST=servidor.smtp.do.seu.provedor
MAIL_PORT=587
MAIL_USERNAME=usuario_do_smtp
MAIL_PASSWORD=senha_de_aplicativo_do_smtp
MAIL_FROM=endereco_remetente_autorizado
MAIL_AUTH=true
MAIL_STARTTLS=true
```

Use os dados reais do provedor; placeholders não enviam e-mails. Cadastre seu e-mail no perfil antes de precisar recuperar a conta. O pedido usa usuário + e-mail e devolve uma mensagem genérica; o link vale 15 minutos, é de uso único, fica como hash no banco e não é devolvido pela API nem impresso nos logs. Nova solicitação invalida os links anteriores, com intervalo de pelo menos 2 minutos. Não foi enviado e-mail real durante os testes: a entrega SMTP precisa ser validada com o seu provedor. Sem SMTP, a tela informa que a recuperação está desativada.

Login (40), registro (20) e recuperação (5) têm limites por IP/rota a cada 10 minutos. A implementação é em memória de uma única instância e não confia em `X-Forwarded-For` para determinar o IP. Em um deploy com proxy/múltiplas instâncias, configurar identificação de IP confiável e limites compartilhados antes de liberar o público.

## PostgreSQL opcional

O modo padrão continua sendo H2, sem instalar nada. Para testar PostgreSQL local, coloque uma senha forte em `DB_PASSWORD` no `.env` e use:

```powershell
docker compose up -d postgres
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=postgres"
```

O Compose expõe o banco somente em `127.0.0.1:5432` e usa volume persistente. O profile usa `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` e migrações próprias em `db/postgresql/`. Ele cria um **banco separado**, não transfere automaticamente seus dados H2. Se quiser levar seus dados atuais, planejaremos essa transferência com backup; não apague `data/` e não use `docker compose down -v` para atualizar o banco.

## Testes de interface

Rodar a aplicação não requer Node. O teste opcional `tests/browser-smoke.cjs` usa Playwright e sobe seu próprio Java/banco temporário com contas fictícias. Ele cobre navegação, listas, resenha, desejos, perfil/avatar, cadastro/edição administrativa e isolamento entre contas. O CI instala as ferramentas e o executa após os testes Java. `PostgresSmokeTest` roda quando `TEST_POSTGRES=true` e há banco de testes configurado; localmente é ignorado quando essa configuração está ausente.

## Próximas etapas

Importação por API externa de metadados, remoção/limpeza de imagens sem uso e gerenciamento de contas/papéis. A parte social e o adaptador documentado para consoles entraram na v0.4; preços ao vivo de consoles dependem da contratação/configuração do provedor. Para hospedar: preparar HTTPS, `SESSION_COOKIE_SECURE=true`, banco, segredos, IP confiável, limites compartilhados, backups e operação. Não houve deploy desta aplicação Java; GitHub guarda o código.

O catálogo inicial é editorial; não é uma importação automática. Capas vêm do CDN da Steam e fontes tipográficas de serviço externo, com alternativas locais. Não redistribuímos as imagens no repositório. A interface tem testes de navegação e verificação de overflow no viewport de celular. A revisão visual no seu IntelliJ/navegador continua sendo útil para ajustar preferências de layout.

## Comunidade e privacidade

Em **Perfil → Privacidade e segurança**, escolha **Somente eu**, **Amigos aceitos** ou **Todos, inclusive visitantes**. Ative **Aparecer na busca de usuários** para que seu nome, @usuário e foto apareçam na Comunidade. Contas novas aparecem na busca por padrão, mas começam com resenhas privadas. Contas migradas continuam privadas e fora da busca. E-mail e dados de segurança nunca aparecem no perfil compartilhado; listas personalizadas continuam particulares.

**Seguir** é unilateral e não libera um perfil restrito a amigos. **Adicionar amigo** envia um pedido; só o destinatário pode aceitar ou recusar. O remetente pode cancelar e qualquer amigo pode desfazer a amizade. A aba Comunidade reúne os pedidos e suas conexões. Perfis públicos podem ser compartilhados pelo link `/#profile=nome_de_usuario`. Desativar a busca impede visitantes e desconhecidos de abrir o perfil; amigos aceitos continuam podendo acessá-lo conforme a visibilidade.

## Preços de consoles: PSprices B2B

Documentação oficial: https://psprices.com/b2b/docs/ e condições/planos: https://psprices.com/b2b/.

A chave do GG.deals é exclusiva daquela integração; ela não autentica no PSprices. A integração B2B do PSprices requer um plano próprio, com acesso à região escolhida. Não é necessário contratar para usar a parte social e o restante do projeto. Nenhuma compra ou assinatura foi feita durante o desenvolvimento.

Para habilitar, acrescente ao seu `.env` local e reinicie o Java:

```properties
PSPRICES_API_KEY=sua_chave_psprices_b2b
PSPRICES_REGION=br
```

Com ADMIN, abra um jogo salvo e clique em **Associar preços de consoles**. Escolha a plataforma, busque pelo título e selecione a edição correspondente, conferindo geração/conteúdo. O servidor guarda o ID regional do produto e consulta `/api/b2b/games/verify/`, enviando a chave em `X-API-Key`. Não escolhe automaticamente o primeiro resultado por título. A chave fica no servidor.

São exibidos preço atual, moeda, edição, desconto e preço anterior quando o provedor os fornece, com atribuição **Powered by PSprices** e link oficial. O adaptador valida plataforma, região e compra direta; produtos incompatíveis, condicionados a outra compra ou sem preço não viram uma oferta válida. Cache de preços válidos: até 1 hora; falhas: 1 minuto. Sem chave ou sem associação, o painel informa a situação e mantém links de lojas preenchidos pelo administrador. Somente plataformas cadastradas no jogo aparecem.

Não há scraping, conversão cambial nem garantia de menor preço entre lojas de consoles. A oferta de console corresponde à edição associada e à região configurada. PC mantém menores preços por categoria e históricos do GG.deals. PSprices documenta PS5/PS4/PS3/PSVita/PSP, Xbox One/360, Switch/Switch 2/Wii U/3DS; a disponibilidade de uma edição depende do catálogo regional do provedor. Se o catálogo não separar uma versão de geração mais nova, revise a compatibilidade na loja.

Respeite limites, atribuição e condições do provedor, inclusive restrições de revenda dos dados e de serviços concorrentes. As consultas reais do PSprices ainda não foram verificadas com uma chave licenciada; os testes usam respostas fictícias conforme o contrato documentado.

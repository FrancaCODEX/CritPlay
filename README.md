# CritPlay

Uma comunidade de jogos para registrar experiências, publicar resenhas e descobrir o que jogar. Backend Java e interface web servidos pela mesma aplicação.

## Funcionalidades

- Catálogo com busca, filtros, edição administrativa e importação RAWG individual ou em lotes de 500, 1.000 e 2.000 jogos.
- Diário com progresso, notas de meia estrela (0,5–5), resenha e datas; biblioteca por status, ordenação e modo compacto; favoritos, desejos e listas reunidos no perfil, com controles de compartilhamento.
- Perfil com avatar, banner e até quatro jogos em destaque; resenhas, privacidade, seguidores e amizades; feed de atividades e curtidas em resenhas.
- Carrossel de destaques com navegação e troca automática.
- Preços de PC via GG.deals; jogos de consoles no catálogo com links manuais para lojas.
- Administração de contas/papéis, suspensão, denúncias, moderação de resenhas, bloqueios e limpeza de imagens sem uso.
- Login por sessão, troca de senha e recuperação por e-mail mediante configuração SMTP.
- H2 local, PostgreSQL opcional e ferramenta de migração entre os bancos.

## Requisitos

JDK **17 ou 21** e internet na primeira execução. Spring Boot, Maven Wrapper, JPA, Security e Flyway; HTML/CSS/JavaScript. Node e Docker não são necessários para executar localmente.

## Iniciar no Windows / IntelliJ

1. Abra a pasta que contém `pom.xml` e selecione o JDK.
2. Na primeira instalação, copie `.env.example` para `.env`:

```powershell
Copy-Item .env.example .env
```

3. Preencha o `.env` local, sem aspas:

```properties
CRITPLAY_ADMIN_USERNAME=seu_usuario
CRITPLAY_ADMIN_PASSWORD=sua_senha_forte
CRITPLAY_ADMIN_NAME=Seu nome
GGDEALS_API_KEY=sua_chave_ggdeals
RAWG_API_KEY=sua_chave_rawg
SESSION_COOKIE_SECURE=false
```

Use uma senha de pelo menos 10 caracteres e no máximo 72 bytes UTF-8. As chaves são opcionais: sem elas, preços/importação ficam indisponíveis. Não envie `.env` ao GitHub. Variáveis de ambiente têm precedência sobre o arquivo.

4. Execute pela raiz do projeto:

```powershell
.\mvnw.cmd spring-boot:run
```

5. Acesse **http://localhost:8080**. No IntelliJ, também pode executar `br.com.critplay.CritPlayApplication`, com o diretório de trabalho na raiz do projeto.

O administrador é criado na primeira inicialização com as credenciais configuradas. Alterar a senha no `.env` depois disso não altera a conta: use Perfil → Trocar senha. Cadastro público cria usuários comuns.

## Linux / WSL

```bash
cp .env.example .env
# Preencha o arquivo no seu editor
chmod +x mvnw
./mvnw spring-boot:run
```

## Importar jogos

Configure `RAWG_API_KEY`, reinicie e entre como administrador. Em **Administração → Importação em lote**, escolha o tamanho e inicie. Progresso fica salvo; há pausa/retomada e prevenção de duplicatas. Gêneros são mapeados para PT-BR; descrições mantêm o idioma da fonte. Steam App ID é buscado automaticamente nos novos lotes. Na importação individual, aparece no editor para revisão; jogos já cadastrados podem ser associados em Administração → Buscar IDs do catálogo. Correspondências incertas ficam sem ID; preenchimento manual continua disponível.

## Atualizar sem perder dados

Pare o Java, faça backup de `data/` e preserve seu `.env`. Atualize a `main` por **Git → Pull** ou `git pull --ff-only` e reinicie. Salve alterações locais em commit/stash antes do Pull. Não substitua seu `.env` pelo exemplo.

O H2 permanece em `data/gameshelf.mv.db`, e uploads em `data/uploads/`. O nome antigo do arquivo/repositório foi mantido para compatibilidade. Variáveis `GAMESHELF_*` antigas continuam aceitas. Migrações Flyway atualizam o esquema; mudar para PostgreSQL não transfere automaticamente os dados.

## Testar e empacotar

```powershell
.\mvnw.cmd clean verify
java -jar target/critplay-0.5.3.jar
```

No Linux, use `./mvnw`. Testes locais usam dados fictícios; o CI também verifica PostgreSQL e navegação. Servidor local escuta em `127.0.0.1`; configuração de hospedagem está no guia abaixo.

## Documentação

- [Guia das funcionalidades](docs/FUNCIONALIDADES.md)
- [Primeiros passos](docs/PRIMEIROS_PASSOS.md)
- [Arquitetura e API](docs/ARQUITETURA_E_API.md)
- [Migração H2 → PostgreSQL](docs/MIGRACAO_POSTGRES.md)
- [Hospedagem gratuita e paga](docs/HOSPEDAGEM.md)
- [Operação e backups](docs/OPERACAO.md)
- [Verificação dos testes](docs/VALIDACAO.md)
- [Histórico de versões](CHANGELOG.md)

## Fontes dos dados

Metadados: [RAWG](https://rawg.io/apidocs). Preços de PC: [GG.deals](https://gg.deals/api/). O catálogo inicial é editorial; importações exigem configuração. Respeite condições, atribuição e cotas dos provedores. Capas externas não são redistribuídas no repositório. Não há preços automáticos de consoles.

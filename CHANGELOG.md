# Changelog

Toda alteração no projeto deve ser registrada neste arquivo, incluindo funcionalidades, correções, configuração, testes e documentação.

## Não lançado

### Biblioteca, perfil gamer e comunidade

- Biblioteca com filtros por status e contadores: todos, quero jogar, jogando, finalizados e abandonados.
- Ordenação da biblioteca por última atualização, título, maior nota e data de conclusão; catálogo principal com ordem padrão ou alfabética em PT-BR.
- Alternância entre capas e lista compacta, com informações de status e conclusão, mantendo os filtros e a ordenação durante a navegação.
- Notas de 0,5 a 5 em passos de meia estrela, sem arredondamento silencioso; migrações H2 V7/PostgreSQL V5 preservam notas inteiras existentes.
- Estrelas com preenchimento parcial e nota numérica no catálogo, biblioteca, resumo, resenhas e perfis compartilhados; médias aceitam notas fracionadas.
- Perfil gamer com banner próprio e até quatro jogos em destaque, em ordem definida pelo usuário, além das estatísticas existentes. Duplicatas, jogos inexistentes e imagens de outro usuário são recusados.
- Novo endpoint de identidade gamer; banner e destaques compartilhados respeitam a visibilidade do perfil. Limpeza de imagens preserva banners em uso.
- Feed de atividades com paginação por cursor para suas experiências, pessoas seguidas e amigos aceitos. Status, notas e resenhas geram eventos persistentes; alterações apenas em favoritos/desejos e salvamentos sem mudanças não geram atividade.
- Feed e resenhas são filtrados pela privacidade atual, descoberta de perfis, amizades, bloqueios, suspensão e moderação; exclusão do diário remove atividades e curtidas associadas.
- Curtir/descurtir resenhas com contagem e indicação da sua curtida, no jogo e nos perfis compartilhados; operações idempotentes, autenticação/CSRF e proteção contra curtidas na própria resenha ou em conteúdo inacessível.
- Cards com status discreto, nota pessoal e marca de favorito; atalhos independentes no hover, foco e toque, coração preenchido, indicação de gravação e foco preservado após a ação.
- Detalhes com capa vertical, título e média no topo e abas de experiência, comunidade e preços, com navegação por teclado e layout responsivo.
- Estados vazios com mensagens contextualizadas e ações para explorar o catálogo/encontrar jogadores; feedback de salvamento e respeito à preferência de movimento reduzido.
- Migração H2 → PostgreSQL ampliada para banner, destaques, curtidas e atividades, preservando IDs e sequências.
- Fixtures de navegador usam Elden Ring e Hades do catálogo inicial para validar notas, perfil e feed.
- Testes de API para meia estrela, identidade/privacidade, feed, curtidas e exclusão; testes de migração e navegador ampliados para ordenação, status, modo compacto, abas, banner, destaques, feed, curtidas e celular.
- README e guias de funcionalidades, API e migração atualizados; toda preparação Oracle continua isolada na branch oracle-cloud.


### Reversão da hospedagem Oracle — 14/10/2026

- Revertido na `main` o merge do PR #2 (`c87f519`), adiando a preparação Oracle Cloud por solicitação do responsável.
- Removidos da `main` os arquivos específicos Oracle, scripts de backup/atualização, profile, CI e guias adicionados pelo PR; Dockerfile, regras Git, README e guias gerais voltam ao estado anterior ao merge.
- Todo o trabalho de hospedagem permanece preservado na branch `oracle-cloud`, sem alterações; a branch `local` também permanece intacta.
- Avaliações, notas da comunidade, atalhos nos cards e favicon do PR #1 permanecem na `main`.
- Para retomar a incorporação futura, reverta este commit de reversão em uma nova branch e abra um novo PR; um merge simples da antiga branch não reaplica os commits já presentes no histórico.

### Avaliações e interface

- Avaliação salva vira um resumo com opção de editar e cancelar a edição.
- Detalhes exibem notas/resenhas da comunidade e média de 1 a 5, respeitando privacidade, amizades, bloqueios e moderação.
- Cards com zoom suave e atalhos para favoritos/desejos, acessíveis por teclado e toque.
- Favicon CritPlay em ICO/PNG e ícone para dispositivos Apple.

Histórico das entregas do CritPlay. O [README](README.md) descreve a instalação e as funcionalidades atuais; detalhes de uso ficam em [Funcionalidades](docs/FUNCIONALIDADES.md).

## 0.5.3

- Configurações do perfil recolhidas por padrão, abertas pelo botão Editar perfil, mantendo os rascunhos durante edição de resenhas.
- Destaques em carrossel com até seis jogos, setas, indicadores, pausa, navegação por toque e respeito a movimento reduzido.

### Interface

- Imagens sem pôster Steam preenchem todo o card com recorte central, eliminando faixas vazias; alternativas após falha da Steam seguem a mesma regra.

- Capas verticais da Steam priorizadas após capas próprias, com alternativa RAWG se a imagem falhar.
- Imagens inteiras e centralizadas no catálogo, listas e resenhas, sem ampliação no hover; cards alinhados ao topo.

### Documentação

- README reorganizado em apresentação, funções atuais, configuração, execução e links de documentação.
- Guia de funcionalidades separado e histórico concentrado neste changelog.
- Pesquisa ampliada para hospedagem gratuita, com limitações de persistência e operação.

## 0.5.2

- Busca gratuita de Steam App ID para jogos de PC, usando correspondência exata/única de título e confirmação de tipo/ID na Steam.
- Novos lotes RAWG procuram IDs automaticamente; importações individuais preenchem o editor para revisão.
- Botão de busca no editor e associação sequencial dos jogos já cadastrados, com progresso/parada.
- Preservação de IDs existentes, proteção contra duplicatas e associação sem alterar diários, listas ou capas próprias.
- Timeout, espaçamento, cache e pausa após indisponibilidade; a importação de metadados continua quando a Steam falha.

## 0.5.1

- Importação RAWG em lotes de 500, 1.000 e 2.000 novos jogos, com progresso persistido, pausa/retomada, duplicatas e gêneros PT-BR.
- Bloqueios entre usuários, remoção de conexões e controles na Comunidade.
- Denúncias de perfis/resenhas e moderação administrativa; edição ou recriação não contorna ocultação.
- Datas de início/fim no diário, com validação de ordem e datas futuras.
- Ferramenta local H2 → PostgreSQL, preservando IDs, contas, hashes, diários, listas e relações; destino ocupado é recusado.
- Dockerfile, configuração de produção, health check e guias de migração/hospedagem.
- 48 testes aprovados no CI, incluindo PostgreSQL real e migração, além do teste de navegador.

A importação de 500 registros foi validada com fixtures. Isso não significa que 500 jogos reais estejam incluídos no repositório. A importação real depende da chave e da execução no banco do operador. Não houve deploy.

## 0.5

- Nome alterado de GameShelf para CritPlay, incluindo pacote Java e artefato Maven, mantendo compatibilidade com dados e variáveis anteriores.
- Importação RAWG individual com prévia, revisão e atualização preservando IDs.
- Administração de contas e papéis, suspensão/reativação e revogação de sessões.
- Limpeza de uploads sem uso, com prévia, confirmação e preservação de arquivos vinculados/recentes.
- Remoção do adaptador pago de preços de consoles; catálogo e links oficiais mantidos.

## 0.4

- Tema roxo e comunidade com busca de usuários, seguir e pedidos de amizade.
- Perfis compartilháveis com privacidade público/amigos/privado e controle de descoberta.
- Fontes por plataforma e adaptador documentado para consoles, posteriormente removido na 0.5.

## 0.3

- Perfil editável, resenhas no perfil, favoritos, desejos e listas personalizadas.
- Edição administrativa de jogos, importação JSON e upload de avatar/capas próprias.
- Troca/recuperação de senha, proteção de contas e suporte opcional a PostgreSQL.

## Entregas iniciais

- Catálogo editorial, busca, filtros e interface inspirada em streaming.
- Integração de preços PC via GG.deals e diário de jogos.
- Contas, sessões, BCrypt, CSRF e cadastro de jogos exclusivo de administradores.
- Banco H2 persistente, migrações e testes automatizados.

As entregas iniciais foram agrupadas para não atribuir mudanças a versões sem registro confirmado. Novas alterações entram em “Não lançado” e são agrupadas na versão correspondente quando entregues.

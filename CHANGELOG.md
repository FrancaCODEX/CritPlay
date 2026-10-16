# Changelog

Toda alteração no projeto deve ser registrada neste arquivo, incluindo funcionalidades, correções, configuração, testes e documentação.

## Não lançado

### Variedade nas avaliações de demonstração

- Corrigida correlação entre escolha do jogo e nota, que repetia as opiniões dos perfis para o mesmo título. Notas de 1,5 a 5 passam a depender da pessoa, do jogo e de afinidades de gênero, sem depender da posição na seleção.
- Resenhas com vozes e prioridades individuais, observações por jogo e críticas coerentes com notas baixas; textos distintos para diferentes autores do mesmo jogo.
- Atualização opcional da carga já instalada ao reativar `DEMO_DATA_ENABLED`: reconhece textos/notas originais, preserva avaliações editadas e não recria contas, conexões, curtidas ou atividades. Atualiza apenas nota/texto com controle de concorrência, mantendo datas/status/favoritos e histórico de atividades.
- Cargas novas já registram a variedade aplicada; atualizações ignoram entradas cujo resultado não mudaria, evitando gravações e contagens artificiais.
- Migrações H2 V10/PostgreSQL V8 criam marca da atualização sem alterar resenhas automaticamente; ferramenta H2 → PostgreSQL preserva ambas as marcas.
- Guia de demonstração atualizado; testes cobrem variedade por jogo, seleção de um único jogo, preservação de edições, atualização única, contagens e execução em PostgreSQL.


### Dados fictícios para demonstração

- Carga local opcional por `DEMO_DATA_ENABLED`, desligada por padrão, no banco atual H2/PostgreSQL, executada após catálogo e bootstrap do administrador.
- 16 perfis identificados como fictícios (14 com conteúdo, um vazio e um privado), bibliotecas com quatro status, notas de meia estrela, resenhas por jogo, favoritos, desejos, listas, destaques, seguidores, amizades, atividades e curtidas; sem senhas compartilhadas, e-mails, chamadas externas ou alterações em contas existentes.
- Seleção opcional de jogos por título via `DEMO_GAME_TITLES`; seleção padrão dos jogos originais, com distribuição determinística e textos de exemplo identificados.
- Migrações H2 V9/PostgreSQL V7 criam apenas controle de execução; carga transacional com trava, marca persistente e recusa a colisões de nomes, sem duplicar ou sobrescrever edições em reinicializações.
- Ferramenta H2 → PostgreSQL preserva a marca da carga ao migrar um banco já populado, com teste de regressão.
- Guia de execução pelo `.env`/IntelliJ, backup, roteiro de apresentação e limites da remoção; README e `.env.example` atualizados. Testes H2/PostgreSQL cobrem ativação, seleção, integridade, preservação, repetição e rollback integral após falha durante as interações.



### Perfil como central de coleções

- Navegação superior reduzida: biblioteca, favoritos, desejos e listas ficam dentro do Perfil, com visão geral, banner, identidade, estatísticas e resenhas; configurações continuam recolhidas.
- Coleções de outros jogadores podem ser consultadas no perfil compartilhado, com controles independentes de compartilhamento para biblioteca/status, favoritos, desejos e listas. A visibilidade geral (público/amigos/privado), descoberta, bloqueios, suspensão e moderação continuam sendo aplicadas no servidor.
- Coleções privadas e suas contagens são omitidas das respostas públicas; dados compartilhados usam campos explícitos, sem expor diário completo, e-mail, datas pessoais ou conteúdo moderado.
- Novas contas começam públicas, encontráveis e com coleções compartilháveis. Migrações H2 V8/PostgreSQL V6 preservam visibilidade existente e mantêm as coleções antigas particulares até o titular optar por compartilhar.
- Blocos de prévia no catálogo, avaliações, perfil inicial e listas; capas reservam espaço e mostram fundo de carregamento. Falhas limpam placeholders, perfil/listas permitem tentar novamente, e preferência por movimento reduzido é respeitada.
- Cadastro informa o padrão público e onde alterá-lo; salvamento da privacidade mostra andamento. Testes de API ampliados para padrão público, privacidade por coleção e bloqueios; testes de navegador adaptados à navegação e às mensagens de visibilidade e ampliados para carregamento lento/falha, compartilhamento e celular. README, guia de funcionalidades e contrato da API atualizados.


### Estrelas interativas nas avaliações

- Campo de nota substituído por cinco estrelas com duas áreas de seleção em cada uma, permitindo escolher de 0,5 a 5 por clique ou toque.
- Passar o mouse pré-visualiza o preenchimento sem alterar a nota; sair da área restaura a seleção confirmada. Indicador numérico diferencia prévia e seleção.
- Controle acessível com opções de rádio, setas do teclado, Home/End, foco visível e ação de limpar a nota; seleção restaurada ao abrir/editar/cancelar e bloqueada durante o salvamento.
- Média abaixo do título passa a mostrar cinco estrelas preenchidas proporcionalmente ao valor real, com nota numérica e contagem de avaliações. Sem notas, as estrelas ficam vazias sem atribuir nota zero ao jogo.
- CSS responsivo, foco/hover e respeito à preferência de movimento reduzido; sem dependência de biblioteca externa e sem alterações na API ou banco.
- Testes de navegador adaptados para estrelas e ampliados com prévia sem gravação, meia estrela, teclado, limpeza, edição/cancelamento, toque e preenchimento da média; guia de funcionalidades atualizado.


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
- Fixtures de navegador usam Elden Ring e Hades do catálogo inicial; seletores de curtidas e relações sociais são limitados ao painel ativo para validar notas, perfil e feed sem ambiguidade com perfis recolhidos.
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

### Avatar no cabeçalho e identidade com GIF

- Foto do usuário substitui o nome no cabeçalho e abre o perfil; círculo com iniciais quando não há foto ou o arquivo falha. Rótulo acessível preserva o nome; foto atualiza ao salvar, remover e recarregar a sessão.
- Respostas de autenticação incluem somente a URL do avatar como novo campo da projeção segura de usuário.
- Avatar e banner aceitam GIF animado, além de PNG/JPEG, até 2 MB. Quadros, posição, transparência, descarte, tempos e repetição são preservados ao reencodificar; comentários, texto e extensões de aplicação não necessárias são removidos.
- GIFs limitados a 120 quadros, dimensões de até 4096 pixels/8 megapixels e 60 milhões de pixels de tela acumulados; quadros fora da tela e arquivos inválidos são recusados. Saída acima de 2 MB é recusada e arquivos incompletos são removidos.
- Primeiro quadro composto em PNG para avatar/banner quando a preferência de reduzir movimento estiver ativa; mudanças nessa preferência atualizam as imagens sem recarregar.
- URLs de GIF seguem a verificação de propriedade; limpeza preserva GIFs e versões estáticas em uso e inclui ambos após ficarem sem referência por mais de 24 horas.
- Testes de API para animação, metadados, limites, propriedade, tipos de mídia, projeção de sessão e limpeza; navegador valida foto no topo, persistência, GIF em perfis compartilhados e movimento reduzido. Guias e seletores de upload atualizados.



### Perfis compartilhados e botão de compartilhar

- Perfil de outros jogadores reorganizado com banner (ou fundo padrão), avatar sobreposto, nome em destaque, bio, contadores sociais e estatísticas agrupadas; identidade deixa de ser um botão desativado e apagado.
- Seguir/amizade e Compartilhar ficam junto à identidade; bloquear e denunciar perfil passam ao rodapé. Destaques vazios deixam de ocupar uma seção inteira; coleções ganham capas maiores e estados vazios/privados, com resenhas em seção própria.
- Campo com URL aparente substituído por botão Compartilhar com ícone. Usa o compartilhamento nativo quando disponível, ou copia o link com confirmação; cancelamento não gera erro nem copia automaticamente, falhas permitem nova tentativa e há alternativa de cópia para navegadores sem Clipboard API.
- Link temporário usado pela alternativa de cópia fica fora da área visível, dentro do diálogo para compatibilidade com foco modal, e é removido logo após a ação. Conteúdo privado continua condicionado à resposta autorizada da API.
- Layout responsivo, rótulo acessível do diálogo, feedback de compartilhamento e retorno ao topo ao abrir outro perfil; avatar/banner GIF e movimento reduzido preservados.
- Testes de navegador ampliados para apresentação, celular, compartilhamento nativo, cancelamento, cópia/alternativa/falha e perfil privado; guia de funcionalidades atualizado.

### Meias estrelas alinhadas e conexões pelo perfil

- Estrelas do seletor e das notas/médias substituídas por SVG simétrico, eliminando a diferença entre largura do caractere da fonte e área de preenchimento. Cada meia estrela termina no centro geométrico também no celular; seleção por mouse, toque e teclado preservada.
- Perfil próprio e compartilhado exibem contadores clicáveis de Seguidores e Seguindo. Um diálogo lista nome, usuário e avatar, permite abrir cada perfil e carregar páginas seguintes; inclui carregamento, estado vazio, falha e nova tentativa.
- GET `/api/people/{username}/followers` e `/following` com cursor `after`, páginas de 30, total e próximo cursor; GET/PUT do perfil e atualização gamer passam a incluir contagens sociais.
- Listas seguem visibilidade geral do perfil, descoberta, amizades e bloqueios. Membros suspensos ou bloqueados para o visitante são omitidos; perfis fora da busca só aparecem para o próprio titular, amigos autorizados ou nas conexões do dono da lista. Contagens de seguidores/seguindo usam os mesmos filtros das listas e não expõem dados de conta.
- Testes de API para contagens, paginação, privacidade, descoberta, amizades, bloqueios e suspensão; navegador verifica o centro do preenchimento no desktop/celular e navegação por seguidores/seguindo. Documentação atualizada; sem alterações de esquema.

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

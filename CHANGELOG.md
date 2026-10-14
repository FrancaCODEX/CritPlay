# Changelog

Histórico das entregas do CritPlay. O [README](README.md) descreve a instalação e as funcionalidades atuais; detalhes de uso ficam em [Funcionalidades](docs/FUNCIONALIDADES.md).

Toda alteração no projeto deve ser registrada neste arquivo, incluindo funcionalidades, correções, interface, testes, configuração e documentação. Mudanças ainda não entregues entram em “Não lançado” e são agrupadas na versão correspondente quando entregues.

## Não lançado

### Avaliações

- Após salvar nota ou resenha, o formulário é recolhido e substituído por um resumo com status, nota, texto e datas.
- Botão “Editar avaliação” para reabrir o formulário; cancelar restaura os dados salvos. Falhas no salvamento mantêm a edição aberta.
- Botão “Editar resenha” no perfil abre diretamente o formulário de edição.
- Novo endpoint GET /api/games/{gameId}/reviews e seção de avaliações da comunidade nos detalhes do jogo.
- Média de 1 a 5 sobre todas as notas visíveis, incluindo avaliações sem texto; listagem limitada às 50 avaliações mais recentes.
- Média e listagem respeitam privacidade, descoberta de perfis, amizades, bloqueios, contas suspensas e moderação. Preferências privadas do diário não são compartilhadas.
- Atualização da média após salvar e descarte de respostas antigas ao trocar de jogo ou recarregar avaliações.

### Interface

- Cards com zoom suave ao passar o mouse ou focar pelo teclado, respeitando a preferência de movimento reduzido.
- Atalhos independentes para adicionar/remover favoritos e desejos sem abrir o jogo, preservando nota, resenha, status e datas.
- Atalhos acessíveis por teclado e visíveis em dispositivos de toque; gravações repetidas no mesmo card são bloqueadas enquanto a ação está em andamento.
- Favicon CritPlay derivado do ícone fornecido, em ICO com tamanhos 16/32/48/64 e PNG de 64 px; ícone Apple de 180 px.
- Referências dos ícones no HTML e acesso público aos arquivos na configuração de segurança.

### Testes e documentação

- Teste de integração para média, avaliações sem texto, privacidade, amizades, bloqueios, moderação, suspensão, jogo inexistente e acesso aos ícones.
- Teste de navegador para salvar, editar, cancelar, falha de gravação, comunidade, atalhos, preservação de dados, recarregamento, celular, movimento reduzido, favicon e login.
- CI ampliado para executar o novo teste de interface, além da regressão geral existente.
- Regressão geral adaptada ao resumo recolhido e seletor de resenha limitado à seção do perfil para evitar ambiguidade com o resumo do jogo.
- Validação aprovada no GitHub: 55 testes Java sem falhas, empacotamento e testes de navegador.
- Regra de registro de toda alteração explicitada neste changelog.

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

# Changelog

Toda alteração no projeto deve ser registrada neste arquivo: funcionalidades, correções, interface, configuração, testes e documentação.

## Não lançado

### Hospedagem Oracle e branches

- Separação operacional em `local` (computador) e `oracle-cloud` (VM), com `main` como base comum e guia de sincronização por merges.
- Docker Compose específico para Oracle com Java 21, PostgreSQL 17, Caddy/HTTPS, volumes persistentes, limites de memória, rotação de logs e health checks.
- Aplicação e banco sem portas públicas; proxy com IP fixo e profile Oracle que aceita cabeçalhos encaminhados somente do Caddy confiável.
- Exemplo `.env.oracle` e regras Git para proteger segredos de cada ambiente.
- Dockerfile com curl para health checks e cópia do JAR sem fixar o número da versão; compatibilidade com execução local preservada.
- Backup consistente do banco e uploads com pausa breve, checksums, metadados, bloqueio concorrente e retomada da aplicação; atualização faz backup antes do pull e rebuild.
- Guia Oracle para VM ARM, DNS, firewall, HTTPS, migração opcional, backups, atualização e restauração em ambiente separado.
- README e guias de hospedagem/operação atualizados com a escolha Oracle e o novo profile de proxy.
- CI específico para construir a stack, validar proxy/cookies/IP, persistência e ciclo de backup/restauração; implantação real depende da VM e domínio.

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

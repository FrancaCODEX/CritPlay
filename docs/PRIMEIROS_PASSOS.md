# Evoluir o CritPlay juntos — v0.5.1

## Atualizar no IntelliJ

Pare o Java, copie `data/` como backup e preserve `.env`. Atualize main em Git → Pull. Rode `CritPlayApplication` ou `.\mvnw.cmd spring-boot:run`. A migração V6 é automática; não apague o banco para ver as novidades.

## Exercício de uso

1. Abra **Listas** e crie “Para jogar juntos”.
2. No catálogo, abra um jogo e adicione-o à lista.
3. Escreva uma resenha, dê nota e marque favorito/desejo.
4. Em **Perfil**, confira as estatísticas e a resenha; edite nome/bio/foto.
5. Como ADMIN, edite um jogo e confira que sua resenha continua associada.
6. Saia e crie outra conta: ela começa sem listas e resenhas da primeira.

7. Em Perfil, escolha “Amigos aceitos” e ative a busca. Crie outra conta em uma janela anônima.
8. Em Comunidade, busque o primeiro @usuário e siga: as resenhas permanecem ocultas.
9. Envie um pedido de amizade e aceite com a primeira conta. Confira as resenhas com a segunda.
10. Para compartilhar com visitantes, mude para “Todos”, mantendo a busca ativa, e copie o link do perfil compartilhado.

## O que aprender no código

- `ProfileService`: combina conta + diário em uma resposta de perfil, sem expor senha.
- `ListService`: verifica proprietário antes de cada operação.
- `CatalogService.update`: mantém o ID do jogo e, portanto, os diários/listas associados.
- `DiaryEntry.wishlist`: uma propriedade independente do progresso.
- `ImageService`: valida os bytes, tamanho e dimensão de imagem; não confia na extensão informada.
- `PasswordResetService`: gera token aleatório, guarda só hash e bloqueia a linha ao consumir.
- `AccountSessionFilter`: revoga sessões antigas depois de trocar senha.
- `SocialService`: verifica a visibilidade em cada leitura e quem pode aceitar pedidos.
- `RawgClient`/`MetadataService`: busca metadados, revisa e associa a fonte sem perder IDs.
- `AccountAdminService`: administra papel/suspensão com revogação de sessões.
- `ImageCleanupService`: revalida referências antes de apagar imagens antigas.
- `social.js`: busca, conexões, perfil compartilhado.
- `V5__...sql`: evolui o banco sem descartar registros anteriores.
- `workspace.js`: páginas de Perfil/Listas, upload, formulários e recuperação.

As responsabilidades correspondem a camadas que você já conhece do Laravel: controller recebe HTTP, service organiza regras e repository acessa dados. Ao modelar algo privado, obtenha sempre o proprietário da sessão, não do JSON enviado pelo cliente.

## Seu próximo exercício sugerido

Acrescentar data de início e fim à experiência de um jogo. Primeiro pense em como um usuário edita isso, depois crie uma migração V7, campos no DTO/entidade, validação (fim não pode anteceder início), formulário e um teste que preserve a data após atualização. Podemos revisar etapa por etapa.

## Novidades para experimentar

1. Como ADMIN, abra Administração e examine imagens sem uso.
2. Crie uma conta fictícia e altere seu papel; confira que ela precisa entrar novamente.
3. Cadastre sua chave gratuita RAWG e reinicie. Use Buscar na RAWG, abra uma prévia e revise antes de importar.
4. Atualize um jogo já existente pela importação e confira que sua resenha/lista continuam associadas.

## Depois

Moderação/bloqueios, datas do diário e migração dos dados H2 para PostgreSQL. Recuperação por e-mail depende do SMTP configurado. Preços automáticos são só de PC. Consulte OPERACAO.md antes de hospedar.

## Lote e comunidade

Em Administração, inicie um lote de 500 jogos com sua chave RAWG, acompanhe os contadores e experimente Pausar/Retomar. No diário, preencha as datas de início/fim. Com duas contas fictícias, envie uma denúncia, oculte a resenha como ADMIN e confira que o autor ainda a vê no próprio diário; editar não a torna pública. Bloqueie/desbloqueie um perfil e confira a remoção das conexões.

Para PostgreSQL, siga MIGRACAO_POSTGRES.md em uma base de teste antes de migrar os dados reais. Compare os planos em HOSPEDAGEM.md antes de contratar.

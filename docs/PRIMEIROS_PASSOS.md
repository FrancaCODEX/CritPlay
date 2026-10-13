# Evoluir o GameShelf juntos — v0.3

## Atualizar no IntelliJ

Pare o Java, copie `data/` como backup e preserve `.env`. Atualize main em Git → Pull. Rode `GameShelfApplication` ou `.\mvnw.cmd spring-boot:run`. A migração V3 é automática; não apague o banco para ver as novidades.

## Exercício de uso

1. Abra **Listas** e crie “Para jogar juntos”.
2. No catálogo, abra um jogo e adicione-o à lista.
3. Escreva uma resenha, dê nota e marque favorito/desejo.
4. Em **Perfil**, confira as estatísticas e a resenha; edite nome/bio/foto.
5. Como ADMIN, edite um jogo e confira que sua resenha continua associada.
6. Saia e crie outra conta: ela começa sem listas e resenhas da primeira.

## O que aprender no código

- `ProfileService`: combina conta + diário em uma resposta de perfil, sem expor senha.
- `ListService`: verifica proprietário antes de cada operação.
- `CatalogService.update`: mantém o ID do jogo e, portanto, os diários/listas associados.
- `DiaryEntry.wishlist`: uma propriedade independente do progresso.
- `ImageService`: valida os bytes, tamanho e dimensão de imagem; não confia na extensão informada.
- `PasswordResetService`: gera token aleatório, guarda só hash e bloqueia a linha ao consumir.
- `AccountSessionFilter`: revoga sessões antigas depois de trocar senha.
- `V3__...sql`: evolui o banco sem descartar registros anteriores.
- `workspace.js`: páginas de Perfil/Listas, upload, formulários e recuperação.

As responsabilidades correspondem a camadas que você já conhece do Laravel: controller recebe HTTP, service organiza regras e repository acessa dados. Ao modelar algo privado, obtenha sempre o proprietário da sessão, não do JSON enviado pelo cliente.

## Seu próximo exercício sugerido

Acrescentar data de início e fim à experiência de um jogo. Primeiro pense em como um usuário edita isso, depois crie uma migração V4, campos no DTO/entidade, validação (fim não pode anteceder início), formulário e um teste que preserve a data após atualização. Podemos revisar etapa por etapa.

## Depois

Perfil público com opção de privacidade, API de metadados do catálogo, limpeza de imagens sem referência e migração dos dados H2 para PostgreSQL. Recuperação real por e-mail depende do SMTP configurado; fontes automáticas de consoles dependem de uma API apropriada. Não publique o serviço Java antes de preparar HTTPS, segredos, limites compartilhados e backup.

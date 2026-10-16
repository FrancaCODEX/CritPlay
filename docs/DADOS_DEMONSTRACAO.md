# Dados fictícios para testes e apresentação

A carga usa o **mesmo banco configurado pela aplicação** (H2 ou PostgreSQL).
O Git recebe o código, as migrações, os testes e este guia. O banco, `.env`,
senhas e arquivos locais de usuários continuam fora do Git.

## Como carregar

1. Com a aplicação parada, faça um backup do banco que você já usa. No H2 local,
   copie `data/gameshelf.mv.db` para um local seguro. Se usa imagens enviadas,
   guarde também `data/uploads`.
2. Depois de integrar a branch e atualizar seu projeto, adicione ao `.env` da raiz:

   ```properties
   DEMO_DATA_ENABLED=true
   DEMO_GAME_TITLES=
   ```

3. Inicie o CritPlay normalmente, inclusive pelo IntelliJ. O catálogo inicial
   será carregado primeiro, depois os dados de demonstração. Aguarde a mensagem
   `Demonstração criada: 16 perfis fictícios` no console.
4. Volte `DEMO_DATA_ENABLED=false` no `.env`. Os dados ficam salvos. Não precisa
   manter a opção ligada para vê-los nem para apresentar.

A opção começa desligada, inclusive após atualizar o projeto. Só fazer o merge
ou baixar a branch **não popula seu banco**. Se configurar variáveis no IntelliJ
ou no sistema, elas têm prioridade sobre o `.env`.

## O que aparece

- 16 perfis identificados como fictícios: 14 com conteúdo, um público vazio e um privado vazio.
- Cada perfil ativo mantém suas cinco resenhas variadas dos jogos originais (quando há jogos suficientes), com gostos individuais e notas de meia estrela.
- Bibliotecas com **7 a 11 jogos**, combinando experiências já avaliadas e outras seleções individuais do catálogo; status, favoritos, desejos e destaques diferentes entre as pessoas.
- **Uma a três listas temáticas por perfil**, 27 no conjunto padrão, com nomes e jogos próprios: histórias, desafios, descanso, exploração, backlog e conquistas.
- Favoritos selecionados entre experiências bem avaliadas; desejos variam em jogos e quantidade; dois a quatro destaques por pessoa. O perfil vazio e o privado são mantidos para demonstrar esses estados.
- Seguidores, amizades, curtidas e resenhas existentes são preservados. A reorganização remove apenas entradas sem resenha que reconhece como geradas e as substitui pelas seleções pessoais, com atividades dos novos status.

As quantidades são menores quando a seleção contém poucos jogos. Se escolher apenas
um jogo, não haverá como diferenciar a seleção de títulos entre as pessoas, embora
os nomes das listas, as notas, opiniões e algumas preferências continuem diferentes.
Por padrão, as seleções adicionais podem usar **todo o catálogo já cadastrado**, sem
baixar jogos novos ou consultar APIs externas. Não é necessária chave RAWG.
Os perfis usam o avatar de iniciais e o fundo padrão; capas já cadastradas continuam
sendo usadas. As marcações de demonstração continuam visíveis no nome, bio e resenhas.

## Escolher jogos específicos

Opcionalmente, antes da primeira carga, informe os títulos exatos já cadastrados,
separados por vírgula (sem aspas; maiúsculas/minúsculas são ignoradas):

```properties
DEMO_DATA_ENABLED=true
DEMO_GAME_TITLES=Hades,Celeste,Hollow Knight
```

Sem essa seleção, são usados os jogos originais reconhecidos pelo título. O
programa não depende dos IDs dos jogos. Jogos fora da seleção original recebem
um texto genérico de exemplo que menciona seu título. Títulos com vírgula não são
suportados nessa configuração. Se um título informado não existir, a carga é
cancelada inteira, sem criar perfis pela metade; corrija a configuração e reinicie.

## Como mostrar na apresentação

Entre com **sua conta normal**, abra Comunidade e procure `demo_luna` ou `demo`.
Visite os perfis, veja bibliotecas, listas e seguidores. Abra Hades ou outro jogo
usado para mostrar resenhas, curtidas e média em estrelas. Siga alguns perfis
públicos para ver suas atividades no seu feed. `demo_sol` demonstra um perfil
vazio; `demo_noah`, um perfil privado.

As contas fictícias não têm e-mail ou senha compartilhada. Recebem hashes de
senhas aleatórias descartadas, não publicados no código. Você não precisa fazer
login nelas para navegar pela demonstração; use sua conta para criar/editar sua
própria resenha durante a apresentação.

## Execuções repetidas e dados existentes

Uma marca persistida em `demo_seed_runs` impede repetir a carga no mesmo banco,
mesmo que a opção fique ligada. Reiniciar ou mudar os títulos depois da primeira
carga não duplica dados e não sobrescreve edições feitas nos perfis fictícios.
A gravação inteira acontece numa transação, com trava para duas instâncias não
carregarem simultaneamente.

Contas reais, suas bibliotecas, listas e conexões não são alteradas. Se algum dos
16 nomes `demo_*` já existir antes da primeira execução, o programa cancela a
carga em vez de assumir que aquela conta pode ser editada. Desligue a opção para
iniciar normalmente e investigue o conflito.

Desligar a opção **não remove** dados já criados. Esta versão não inclui limpeza
automática. Para voltar exatamente ao estado anterior, restaure o backup com a
aplicação parada; isso também reverte alterações feitas depois do backup.
A ferramenta de migração H2 → PostgreSQL preserva essa marca de controle.
Não apague a marca de controle para tentar executar de novo num banco já populado.

## Corrigir uma carga antiga com opiniões repetidas

Depois de atualizar o projeto com a correção de variedade, ative novamente
`DEMO_DATA_ENABLED=true` no `.env` e inicie a aplicação. Não apague o banco nem
as marcas de execução. A carga original não é repetida: uma atualização separada
reconhece os textos e notas exatos da primeira versão e diversifica essas entradas.
Depois, volte a opção para `false`.

Resenhas com texto ou nota editados são preservadas. Nas entradas sem texto,
são reconhecidas apenas as combinações originais de nota e status Jogando/Abandonado.
Não são criados usuários, curtidas ou atividades extras. Favoritos, listas, datas,
status e conexões existentes permanecem como estão; favoritos antigos podem ter
uma nota diferente depois dessa correção. Atividades históricas mantêm a nota
registrada na época. O console informa quantas entradas antigas foram atualizadas.
Essa atualização também é transacional e roda uma única vez. Nas cargas novas,
as avaliações variadas já são criadas desde o início.

## Diversificar bibliotecas, favoritos, desejos, listas e destaques já carregados

Depois de atualizar o projeto, ative novamente `DEMO_DATA_ENABLED=true`, inicie e
aguarde o console informar quantas bibliotecas foram personalizadas. Volte a opção
para `false`. Não apague o banco nem as marcas de controle. Se usou uma seleção
específica em `DEMO_GAME_TITLES`, mantenha a mesma seleção durante essa atualização.

A aplicação reconhece o padrão da carga anterior e personaliza cada biblioteca
uma única vez. As resenhas e suas curtidas são mantidas; não são recriadas contas,
amizades ou relações de seguir. Somente entradas sem resenha reconhecidas como
geradas são substituídas. As datas de início das experiências avaliadas ficam
preservadas; datas de conclusão acompanham o novo status. Eventos antigos de
resenha continuam como histórico da experiência; entradas vazias removidas levam
suas atividades de status, e novos status geram atividades correspondentes.

Se detectar biblioteca alterada (resenha, nota, favorito, desejo, status, datas,
jogo extra/removido ou versão incompatível), a atualização preserva aquela
biblioteca e suas coleções. Listas genéricas só são substituídas quando nome,
descrição e jogos ainda correspondem ao padrão original; listas editadas e
adicionadas manualmente são preservadas. Destaques manuais também são mantidos.
A execução é transacional, com controle independente de versão; a migração
H2 → PostgreSQL preserva essa marca. Novas instalações recebem a personalização
no mesmo fluxo de ativação da demonstração.

Para o vídeo, compare `demo_luna`, `demo_rafa` e `demo_nina`: os temas das listas,
as seleções e os planos de cada pessoa ficam diferentes. Você pode continuar
usando seu próprio perfil personalizado para abrir e encerrar a gravação.

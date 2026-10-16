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

- 16 perfis com nomes `demo_*`, nome visível com `[Demo]` e bio explicando que são
  fictícios; 14 têm biblioteca, um é público sem jogos e um é privado sem jogos.
- Nos 12 jogos originais do catálogo: 112 entradas, 70 resenhas, 98 atividades,
  notas entre 3 e 5 com meias estrelas, quatro status, favoritos e desejos.
- 14 listas, destaques de jogos no perfil, 42 relações de seguir, sete amizades
  e 139 curtidas em resenhas. As conexões envolvem apenas os perfis fictícios.
- Resenhas identificadas como fictícias, com observações específicas por jogo e
  diferentes opiniões. São conteúdo de exemplo, sem importação de pessoas reais.

As quantidades de entradas/resenhas diminuem se escolher menos jogos. Não são
baixadas imagens, não são consultadas APIs externas e não é necessária chave
RAWG. Os perfis usam o avatar de iniciais e o fundo padrão do site; capas já
cadastradas continuam sendo usadas.

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

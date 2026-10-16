# Guia de funcionalidades

## Foto no topo e GIF no perfil

A foto salva aparece no círculo do cabeçalho; clique nela para abrir seu perfil. Sem foto, aparecem suas iniciais. Salvar ou remover a foto atualiza esse círculo imediatamente, e a sessão mantém a imagem após recarregar.

Em Editar perfil, os campos Foto do perfil e Banner aceitam PNG, JPEG e GIF de até 2 MB. Escolha o arquivo e salve o perfil ou a identidade gamer para aplicar. GIFs mantêm a animação no próprio perfil, no cabeçalho e nos perfis compartilhados. Prefira animações curtas: até 120 quadros; arquivos de alta resolução podem precisar ser reduzidos. O servidor também limita dimensões a 4096 pixels/8 megapixels e a soma das áreas da tela de todos os quadros a 60 milhões de pixels.

Quando o navegador/sistema pede movimento reduzido, avatar e banner usam uma versão estática do primeiro quadro. As imagens continuam com URLs públicas; compartilhamento do perfil segue as configurações existentes. Capas de jogos continuam oferecendo PNG/JPEG no seletor.

## Perfil e coleções

A navegação principal reúne Descobrir, Perfil, Comunidade e Atividades (Administração aparece para administradores). Dentro do Perfil, use Visão geral, Minha biblioteca, Favoritos, Desejos e Listas. A visão geral apresenta identidade, destaques e resenhas; as coleções mantêm busca, filtros e modos de visualização. Editar perfil continua abrindo as configurações sob demanda.

Em Editar perfil → Privacidade e segurança, escolha a visibilidade geral e quais coleções compartilhar. Uma coleção marcada só aparece para quem pode ver o perfil; desmarcada, seus jogos e contagens não são enviados a visitantes ou outros jogadores. O dono continua vendo suas coleções. Listas compartilham nome, descrição e jogos, sem permitir edição por terceiros. Biblioteca compartilha jogo, status e nota, sem datas pessoais ou texto do diário. Resenhas seguem a privacidade geral e a moderação.

Novas contas começam públicas e com as quatro coleções compartilháveis; você pode torná-las privadas a qualquer momento. Atualizações preservam escolhas de visibilidade anteriores e mantêm coleções de contas antigas particulares até o titular marcar o compartilhamento. E-mail e dados de segurança continuam privados.

Catálogo, resenhas, perfil inicial e listas mostram blocos de prévia durante consultas. As capas reservam espaço enquanto carregam; mensagens substituem os blocos em caso de erro, com nova tentativa no perfil e listas. Movimento reduzido desativa a animação.

## Novidades: biblioteca e comunidade

A biblioteca oferece filtros de status com contadores e visualização por capas ou lista compacta. Você pode ordenar por atualização, título, nota ou conclusão; o catálogo mantém a ordem padrão e permite ordem alfabética. A nota vai de 0,5 a 5 em passos de meia estrela, mantendo as notas já salvas. Escolha diretamente nas cinco estrelas: cada metade representa 0,5. O mouse mostra uma prévia; clique ou toque confirma a escolha, que é gravada ao salvar o diário. Use as setas no teclado, Home/End para os extremos e “Limpar nota” para deixar sem avaliação.

Nos detalhes, capa vertical e média ficam no topo. A média é exibida com cinco estrelas proporcionais, valor numérico e quantidade de notas; ainda sem avaliações, aparecem estrelas vazias. As abas “Sua experiência”, “Comunidade” e “Onde comprar” separam diário, avaliações e preços. Ao salvar, o formulário vira um resumo; edição e cancelamento continuam disponíveis. Corações/atalhos mostram o estado atual e o andamento da gravação.

Em Perfil → Editar perfil → Sua identidade gamer, envie um banner e escolha até quatro jogos em destaque. Esses jogos não precisam estar no diário e não mudam sua lista de favoritos. Banner e seleções são salvos pelo botão “Salvar identidade gamer”, separado dos dados pessoais. As imagens enviadas têm URLs públicas, como os avatares existentes; a divulgação do banner no perfil segue a visibilidade configurada.

Atividades mostra experiências novas suas, de amigos e de pessoas seguidas. O feed começa com os eventos registrados depois desta atualização; não inventa um histórico retroativo. Alterações em notas/resenhas/status geram eventos; favoritos/desejos sozinhos não geram atividade. Você pode atualizar e carregar páginas anteriores. Seguir um perfil privado não dá acesso às suas atividades.

Resenhas de outros usuários podem ser curtidas e descurtidas na aba Comunidade ou no perfil compartilhado. Avaliações só com nota não recebem curtidas. Perfil privado, bloqueio, suspensão, moderação ou exclusão impedem o acesso. As listas personalizadas seguem o controle de compartilhamento nas configurações do perfil. Preferências de visualização são mantidas durante a sessão; os dados do diário, perfil, feed e curtidas são persistidos no banco.

Uso das funções atuais do CritPlay. Para instalação, consulte o [README](../README.md). O histórico fica no [CHANGELOG](../CHANGELOG.md).

## Como funciona o administrador

O bootstrap só cria um administrador quando usuário e senha foram configurados pelo responsável pelo servidor. Registro público sempre cria `USER`. O JSON de cadastro não aceita um campo `role` e não existe endpoint público para elevar permissões.

Depois que a conta administrativa foi criada, mudar a senha no `.env` **não** altera a senha existente. Pode retirar as variáveis de bootstrap após a criação: a conta já está no banco. Para trocar a senha, use Perfil; recuperação por e-mail depende do SMTP configurado. Administradores gerenciam papéis em Administração → Contas e permissões.

Se o nome configurado já pertence a um usuário comum, o bootstrap preserva seu papel e não o promove. Isso também respeita um rebaixamento feito pela administração. Não configure mais de uma instância para migrar/inicializar o mesmo banco H2 ao mesmo tempo.

## Usar as funcionalidades novas

- **Perfil:** edite nome/bio/foto. Para acrescentar ou alterar o e-mail, informe sua senha atual. As resenhas aparecem abaixo, com botão para editar a experiência do jogo.
- **Desejos:** marque a opção no diário. Ela não muda seu status de jogando/finalizado. Registros antigos com “Quero jogar” são incluídos automaticamente durante a migração.
- **Listas:** crie coleções e adicione jogos no painel de detalhes. Cada conta edita apenas suas listas; listas compartilhadas podem ser vistas no perfil; excluir uma lista não apaga notas ou resenhas.
- **Editar jogo:** ADMIN abre o detalhe e clica em Editar jogo. O ID é preservado, mantendo as referências dos diários/listas. Steam App IDs repetidos são recusados.
- **Importar:** ADMIN seleciona um JSON no formato de `examples/catalogo.json`; de 1 a 50 jogos. O lote inteiro é validado e qualquer conflito cancela a gravação de todos. Essa opção JSON é separada da importação RAWG.
- **Imagens:** PNG/JPEG (e GIF para avatar/banner) até 2 MB, no máximo 4096 pixels e 8 megapixels. Avatares e capas ficam em `data/uploads/`, fora do Git. Após enviar, salve o perfil/jogo para aplicar a imagem. URLs das imagens podem ser abertas por quem tiver seu link; a visibilidade do perfil/resenhas segue sua configuração de privacidade.
- **Consoles:** continuam no catálogo e podem ter links para lojas oficiais; não há consulta automática de preços de consoles.

Contas podem ser suspensas/reativadas e seus papéis administrados; não há exclusão definitiva de contas/jogos pela interface. **Perfil** continua sendo sua área de edição; **Comunidade** permite encontrar e abrir perfis compartilhados.

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

## Comunidade e privacidade

Em **Perfil → Privacidade e segurança**, escolha **Somente eu**, **Amigos aceitos** ou **Todos, inclusive visitantes**. Ative **Aparecer na busca de usuários** para que seu nome, @usuário e foto apareçam na Comunidade. Contas novas aparecem na busca por padrão e começam com perfil público; as configurações permitem restringir o acesso. Contas anteriores à v0.4 migraram privadas e fora da busca; atualizações seguintes preservam suas preferências. E-mail e dados de segurança nunca aparecem no perfil compartilhado; listas personalizadas seguem seus controles de compartilhamento.

**Seguir** é unilateral e não libera um perfil restrito a amigos. **Adicionar amigo** envia um pedido; só o destinatário pode aceitar ou recusar. O remetente pode cancelar e qualquer amigo pode desfazer a amizade. A aba Comunidade reúne os pedidos e suas conexões. Perfis públicos podem ser compartilhados pelo link `/#profile=nome_de_usuario`. Desativar a busca impede visitantes e desconhecidos de abrir o perfil; amigos aceitos continuam podendo acessá-lo conforme a visibilidade.

## Importar metadados gratuitamente com RAWG

Documentação/condições oficiais: https://rawg.io/apidocs. A RAWG tem plano gratuito para projetos pessoais, com chave própria, limite de consultas e atribuição obrigatória. Sua chave GG.deals não autentica nessa API.

Acrescente ao seu `.env` e reinicie o Java:

```properties
RAWG_API_KEY=sua_chave_rawg
```

Como ADMIN, clique em **Buscar na RAWG**, busque o título, abra a prévia e escolha **Criar um novo jogo** ou **Atualizar** um existente. Clique em **Revisar no editor**, confira título, gênero, plataformas, ano e descrição, preencha dados ausentes e salve. Nada é persistido só por abrir a prévia. Atualizar mantém ID, diários e listas; o editor preserva capa própria, Steam App ID e links existentes. Um ID RAWG só pode pertencer a um jogo; uma associação existente a outro ID não é substituída silenciosamente.

Nome/gênero/ano/descrição/plataformas são importados; a imagem externa é usada como alternativa à capa própria. Imagens externas são limitadas ao domínio HTTPS `media.rawg.io`, não são baixadas nem redistribuídas pelo repositório. A interface mostra links de atribuição RAWG. Os dados podem estar em inglês; revise e traduza se desejar. Steam App ID continua opcional, com busca gratuita na Steam e revisão no editor. Sem chave, a importação informa a configuração pendente; o restante do CritPlay continua funcionando.

## Administrar contas e imagens

**Administração → Contas e permissões:** busque por nome/@usuário, escolha USER/ADMIN e conta ativa/suspensa, e confirme **Salvar permissões**. A API só aceita administradores ativos. Alterações encerram sessões existentes e revogam tokens de recuperação. Contas suspensas não entram, não recuperam senha e não aparecem na busca pública. Reativar não reabre sessões antigas. Você não pode remover seu próprio acesso administrativo; sempre fica pelo menos um ADMIN ativo. Mudanças ficam registradas na tabela `account_audit`, sem senhas/e-mails. Não é exclusão definitiva da conta.

**Administração → Imagens sem uso:** examine a prévia e confirme a exclusão. Só nomes de uploads gerados pelo servidor, arquivos regulares sem referência em avatar/capa e com mais de 24 horas podem ser removidos. Arquivos recentes, vinculados, desconhecidos e links simbólicos são preservados. A execução recalcula as referências sob uma trava de banco compartilhada com as gravações de capas/avatares; uma imagem vinculada depois da prévia não é excluída. Até 1.000 imagens por execução. Nenhuma limpeza ocorre automaticamente.

As entidades de usuário e jogo usam versionamento otimista para rejeitar atualizações concorrentes que poderiam sobrescrever alterações de papel ou metadados. Os dados H2/PostgreSQL e `data/uploads` precisam continuar incluídos em backups. Veja [Operação](OPERACAO.md) para a preparação de hospedagem; esta entrega não fez deploy.

## Importação em lote, moderação e datas


Em Administração, escolha 500, 1.000 ou 2.000 jogos e inicie a importação. É preciso configurar `RAWG_API_KEY`. Nenhum catálogo real é baixado sem essa chave. O processamento ocorre no servidor, em uma única instância, com progresso salvo por jogo; sair da página não interrompe o lote. Ao reiniciar o servidor, lotes ativos ficam pausados para retomada manual. Somente uma importação pode estar ativa.

Os lotes procuram jogos populares nas famílias PC, PlayStation, Xbox e Nintendo, excluindo adições quando a fonte suporta esse filtro. Um ID RAWG existente não é importado de novo. Um título/ano com correspondência única em jogo editorial sem RAWG pode receber a associação sem sobrescrever seus textos, Steam ID, capa própria, resenhas ou listas. Novos registros têm gêneros mapeados para PT-BR; nomes oficiais são preservados e descrições permanecem no idioma da fonte, com aviso na interface. Não existe tradução automática paga ou inventada. Registros sem título, ano ou plataforma compatível são ignorados; descrições ausentes recebem texto neutro identificado como importação.

O alvo é o número de jogos novos. Duplicatas/incompletos aparecem em contadores próprios; o lote pode terminar antes do alvo se a fonte acabar ou se atingir três vezes o alvo em verificações. Erros de quota/conexão pausam o processo, preservando os jogos gravados. As consultas são espaçadas; o operador deve acompanhar a cota mensal da chave. Os IDs do lote são verificados a cada retomada, pois a ordem do catálogo externo pode mudar. Steam App IDs são procurados automaticamente nos novos jogos de PC; IDs existentes são preservados. Associações incertas continuam sem ID.

### Bloqueios, denúncias e datas

No perfil de outra pessoa, **Bloquear** remove amizades/pedidos/seguidores entre os dois e impede busca, leitura autenticada do perfil e novas conexões em ambas as direções. A Comunidade lista seus bloqueados para desbloquear. Desbloquear não restaura conexões. Perfis configurados como públicos ainda podem ser lidos por visitantes sem identificação; bloqueio não torna um perfil público privado.

Use **Denunciar perfil** ou **Denunciar resenha** para explicar o motivo. Administração reúne as denúncias e permite resolver/descartar ou ocultar/restaurar uma resenha. Uma resenha oculta permanece no diário do autor, mas sai do perfil compartilhado; editar não remove a decisão de moderação. Suspensão de conta continua disponível na gestão de contas.

No diário, **Comecei em** e **Terminei em** são opcionais. Fim exige início, não pode antecedê-lo e datas futuras são recusadas. Registros anteriores permanecem sem datas até você preenchê-las.


## Steam App ID automático

- **Novo lote RAWG:** procura o mesmo título na Steam antes de salvar cada jogo de PC. Indisponibilidade ou falta de correspondência não impede cadastrar o jogo sem ID.
- **Importação individual:** ao revisar no editor, o campo vazio é preenchido quando houver resultado confirmado; salve para aplicar.
- **Cadastro/edição manual:** botão **Buscar Steam App ID**, sem substituir valores já informados.
- **Catálogo existente:** Administração → **Buscar IDs do catálogo**. Verifica sequencialmente jogos de PC sem ID e salva cada associação confirmada. Mantenha a página aberta; sair, parar ou perder acesso interrompe as próximas consultas. O que já foi salvo permanece; iniciar de novo ignora jogos com ID.

Critério conservador: título igual, ignorando caixa, espaços repetidos e símbolos ™/®, um único resultado, e resposta de detalhes da Steam confirmando o mesmo App ID, nome e tipo “game”. DLCs, pacotes, trilhas sonoras com nomes diferentes e edições divergentes não são escolhidos. Não é garantia absoluta: homônimos e cadastro incorreto na fonte ainda exigem revisão do administrador.

Não exige chave Steam adicional ou plano RAWG pago. Usa os endpoints públicos da loja `storesearch` e `appdetails`, sem contrato estável de API; mudanças podem interromper a descoberta. Chamadas têm timeout, espaçamento, cache de 24 horas e pausa de um minuto após falha. Quando a loja falha, a importação continua sem inventar IDs. IDs pertencentes a outro jogo são recusados, IDs existentes e capas próprias são preservados. Preços continuam vindo do GG.deals.

## Capas do catálogo

Capas próprias têm prioridade, seguidas dos pôsteres verticais da Steam e das imagens RAWG. Se uma fonte falhar, a próxima é tentada; sem imagem disponível, aparece o título. Os cards mantêm tamanho vertical uniforme. Pôsteres da Steam preservam o enquadramento inteiro; capas próprias e alternativas RAWG preenchem toda a área com recorte central, sem distorção ou faixas vazias. Imagens horizontais podem perder parte das laterais para ocupar o card. A mesma seleção é usada nas miniaturas de listas e resenhas.

## Perfil e destaques

Ao entrar no Perfil, aparecem sua apresentação, estatísticas e resenhas. **Editar perfil** abre os formulários de perfil, privacidade e segurança; **Fechar configurações** os recolhe sem apagar rascunhos. Entrar novamente na seção começa com os formulários recolhidos.

A página inicial alterna até seis jogos com imagem, selecionados da ordem do catálogo completo. Setas, indicadores e gesto lateral em telas de toque trocam o destaque; **Explorar** abre o jogo exibido. A troca automática ocorre a cada oito segundos, pausando ao interagir, abrir um diálogo, sair da seção ou ocultar a aba. Também é possível pausar pelo botão e a preferência de reduzir movimento desativa o avanço automático. Filtros do catálogo não mudam os destaques.

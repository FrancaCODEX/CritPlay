# Deploy do CritPlay — Vercel Hobby e Neon Free

Pesquisa em 04/11/2026. A aplicação continua sendo um monólito Java 17/Spring Boot: HTML, CSS, JavaScript e APIs na mesma origem HTTPS. Não houve reformulação do frontend.

## Decisão de arquitetura

Vercel agora documenta **Container Runtime em beta em todos os planos**. Portanto, a afirmação antiga de que Vercel nunca executa Docker/Java não é válida na data desta pesquisa. `vercel.json` seleciona `framework: container`; o Dockerfile compila o JAR e inicia Java diretamente, com usuário sem privilégios. Defina `PORT=8080` no painel para corresponder ao servidor. O default da Vercel para containers é 80.

O caminho preparado é navegador → Vercel Container → Spring Boot → Neon PostgreSQL 17 em AWS us-east-1 (próximo da região padrão iad1 da Vercel). Segredos ficam nas variáveis do servidor. O mesmo artefato pode rodar em outro host de containers.

As Functions são encerradas quando ociosas (cinco minutos em produção; 30 segundos em preview), com SIGTERM e 30 segundos de tolerância. Não há garantia de JVM sempre ligada. O profile prod configura encerramento gracioso por até 20 segundos. A inicialização e latência após suspensão ainda precisam ser medidas na Vercel real. Não habilitar daemons, serviços pagos ou mecanismos artificiais para manter a aplicação acordada.

Sessões usam Spring Session JDBC, mantendo nome `JSESSIONID`, HttpOnly, Secure e SameSite=Lax. CSRF e proteção de endpoints administrativos permanecem ativos. Não é necessário CORS porque a aplicação usa a mesma origem. O limiter de autenticação em prod é compartilhado atomicamente no PostgreSQL; a janela é fixa de dez minutos. O host deve impedir acesso direto ao container e sanitizar cabeçalhos de proxy, pois o profile prod interpreta forwarded headers.

Imagens PNG/JPEG/GIF continuam nas URLs `/media/...`, incluindo o poster PNG para movimento reduzido. Em prod, bytes sanitizados são armazenados em `media_files`, não no disco efêmero. Há um orçamento de 128 MiB para uploads somados (incluindo posters), além do limite individual de 2 MB. A limpeza administrativa preserva referências e carência de 24 horas. Essa escolha simplifica backups e custo zero de uma comunidade pequena, mas consome a cota de 1 GB do banco; para crescer, mover mídia para object storage com orçamento explícito.

Lotes RAWG salvam progresso no banco. Leases de cinco minutos e tokens por execução impedem que um container novo pause outro ativo ou que um worker antigo continue uma execução retomada. Após morte abrupta, a tela administrativa passa a permitir retomada quando a lease expira. Isso **não transforma a thread em uma fila durável** nem garante execução contínua em Functions: lotes longos podem precisar de retomadas. Para uso frequente/intenso, preferir backend em Render ou uma fila/worker adequados.

## Profiles e variáveis

| Profile | Banco e comportamento |
| --- | --- |
| dev (default) | H2 em `./data/critplay`, `.env` local opcional, imagens locais e catálogo editorial inicial. |
| test | H2 em memória, sem chaves/admin/demonstração provenientes do `.env`. Testes existentes também definem seus próprios bancos. |
| postgres | Compatibilidade com o fluxo PostgreSQL local existente e com o CI. Não usar sozinho na internet. |
| prod | Inclui postgres, exige PostgreSQL/TLS, HTTPS público, cookies seguros, mídia no banco e seeds desligados. Ignora `.env`. |

Configure no painel, sem commit de valores secretos:

| Variável | Obrigatória / finalidade |
| --- | --- |
| SPRING_PROFILES_ACTIVE | `prod` |
| PORT | `8080` na Vercel; porta fornecida pelo host em outros ambientes |
| DB_URL | JDBC: `jdbc:postgresql://HOST:5432/critplay?sslmode=verify-full` |
| DB_USERNAME / DB_PASSWORD | Usuário e senha do Neon, separados da URL |
| APP_BASE_URL | Origem pública HTTPS, sem caminho; usada em links de recuperação |
| DB_POOL_SIZE | Opcional; default 4 por container, mínimo ocioso zero |
| CRITPLAY_ADMIN_USERNAME / CRITPLAY_ADMIN_PASSWORD / CRITPLAY_ADMIN_NAME | Bootstrap opcional de administrador; não promove usuário comum existente nem redefine senha. Remover após primeiro bootstrap e conferir acesso. |
| RAWG_API_KEY / GGDEALS_API_KEY | Opcionais; sem chaves as respectivas integrações retornam indisponibilidade controlada. |
| RECOVERY_ENABLED | `true` somente com SMTP funcional; default false |
| MAIL_HOST / MAIL_PORT / MAIL_USERNAME / MAIL_PASSWORD / MAIL_FROM | SMTP para recuperação; credenciais somente no servidor |
| RATE_LIMIT_ENABLED | Manter `true` |
| DEMO_DATA_ENABLED | Manter `false`; prod também rejeita overrides de seeds |

`sslmode=verify-full` valida criptografia e identidade do servidor. No container, confira a confiança no certificado do Neon antes da abertura pública. Não substitua por modo sem verificação para contornar erros. Para a pequena carga inicial, foi escolhido endpoint direto com pool Hikari pequeno: simplifica Flyway e evita diferenças de locks com PgBouncer. Avaliar endpoint pooled quando houver muitas instâncias, validando migrações em conexão direta e limites totais de conexão antes de trocar.

O Hikari aguarda até 30 segundos por conexão, abre conexões com timeout de 15 segundos e usa socket timeout de 30 segundos. Flyway tenta reconectar três vezes; erros persistentes impedem o startup. Hibernate valida o esquema; Flyway faz mudanças aditivas versionadas. Não há `clean`, baseline automático em prod ou fallback para H2. `GET /api/health` retorna 503 se o banco falhar, sem credenciais. Erros de indisponibilidade no acesso a dados retornam resposta genérica.

## Procedimento de implantação

1. Usar organização Neon no plano **Free**. Criar projeto novo; manter produção e validação separados. Não apontar os testes para produção: eles escrevem fixtures. O projeto preparado chama-se `critplay`, em AWS us-east-1, com compute limitado a 0,25 CU. O plano Free impõe suspensão por inatividade.
2. Validar `./mvnw clean verify` com PostgreSQL descartável e `TEST_POSTGRES=true`. O teste de reinicialização cobre sessão autenticada serializada, imagem, migrações e lease. Os testes de API existentes cobrem os fluxos funcionais; APIs externas usam respostas simuladas.
3. Na Vercel Hobby, importar `FrancaCODEX/CritPlay`, selecionar Container e cadastrar as variáveis acima **antes de iniciar**. Preview usa banco de validação, nunca produção. Manter segredos sensíveis e não expô-los em builds/frontend. Não contratar Pro nem trial pago.
4. Fazer primeiro deploy da branch de implantação para validação. Configurar APP_BASE_URL com a origem do ambiente; em preview protegido, validar também as regras de acesso da Vercel. Não copiar cookies ou usuários reais para previews.
5. Verificar HTTPS, cookie Secure/HttpOnly/Lax, CSRF, cadastro/login/logout, expiração, recuperação SMTP real, páginas, imagens, administração e `/api/health`. Reiniciar e aguardar escala a zero para conferir a persistência e medir cold start. Conferir logs sem segredos. Conferir cotas no painel.
6. Somente após revisão e aprovação, integrar o PR à main. Configurar Git integration com main como produção. Exigir o check `Java CI / test` antes de merge; a integração Git publicará novos commits aprovados da main. O repositório não configura regras de proteção nem integrações de conta sozinho: confirmar esses controles no painel. Não fazer merge automático neste trabalho.

CI executa Java 17, PostgreSQL 17, testes, empacotamento, três suítes Playwright e build Docker. O build Docker usa `-DskipTests` porque a validação já ocorreu no job anterior. O Dockerfile não contém segredos nem profile prod obrigatório, permitindo desenvolvimento e testes da mesma imagem.

## Dados existentes e backups

Nenhum H2 local, volume de produção ou banco preexistente foi alterado. Trocar DB_URL não transfere dados. Faça backup consistente e siga `MIGRACAO_POSTGRES.md` para migração offline H2 → PostgreSQL vazio. A ferramenta não copia sessões/senhas de recuperação: usuários precisam entrar novamente. Ela inclui mídias já armazenadas no banco; arquivos antigos em `data/uploads` ainda exigem uma importação específica, preservando nomes e referências, antes de ativar prod. Não apagar o diretório original. Não executar importação nem reparos de histórico Flyway contra dados reais sem revisão.

O histórico de restauração gratuito é curto; manter exportações criptografadas fora do provedor e testar restore. Sessões expiradas são limpas diariamente às 03:00 no fuso da JVM enquanto o processo está ativo; um container suspenso pode adiar a limpeza. Monitorar tamanho e, se necessário, executar limpeza administrativa de sessões expiradas em manutenção. Nenhum timer externo foi criado para acordar o banco.

## Limites, custos e alternativa

Estimativa: **R$ 0/mês dentro das cotas**, usando subdomínio gratuito e planos Free/Hobby. Não é SLA, capacidade ilimitada ou garantia de disponibilidade profissional.

- Vercel Hobby: uso pessoal/não comercial, 4 CPU-horas, 360 GB-horas de memória, 100 GB de transferência rápida e 1 milhão de invocações incluídos. Functions: 2 GB / 1 vCPU e duração máxima Hobby de 300 segundos. Limites de containers e registry também se aplicam. JVM/cold starts, BCrypt e processamento de GIF consomem CPU. Exceder limites Hobby normalmente suspende recursos até renovação; não habilitar upgrades/add-ons.
- Neon Free: 100 projetos, 100 CU-horas mensais e 1 GB por projeto, até 2 CU, suspensão após cinco minutos ocioso, 5 GB de transferência pública por projeto, restauração até seis horas ou 1 GB de alterações. Com 0,25 CU, 100 CU-horas equivalem a aproximadamente 400 horas ativas; banco sempre ativo ultrapassaria a cota. Health checks frequentes e polling podem mantê-lo acordado. Não mudar para Launch/Scale.
- Render Free é alternativa para o **mesmo backend Docker + Neon**, caso a beta da Vercel não esteja liberada na conta ou a inicialização/lotes sejam inadequados: 750 horas compartilhadas no workspace, suspensão após 15 minutos, disco efêmero, cold start e 512 MB de memória a validar. Mídia/sessão no Neon continuam persistentes. Não usar Render Postgres gratuito para dados duradouros (expira em 30 dias). SMTP 25/465/587 é bloqueado no Free, exigindo adaptar envio por HTTPS para recuperação. Se houver método de pagamento, excedentes de tráfego/build podem cobrar; sem método, os recursos são suspensos. Não criar serviços pagos.
- Se usar backend Render, Vercel pode servir arquivos estáticos e encaminhar APIs/mídia à mesma origem via rewrites; essa variante requer teste de cookies/proxy e não foi implantada automaticamente. Servir o monólito diretamente no Render é o fallback de menor mudança.

## Fontes oficiais

- [Vercel Container Runtime](https://vercel.com/docs/functions/runtimes/container)
- [Docker na Vercel](https://vercel.com/kb/guide/docker)
- [Vercel Function limits](https://vercel.com/docs/functions/limitations)
- [Vercel Hobby](https://vercel.com/docs/plans/hobby)
- [Neon pricing, atualizado em 31/10/2026](https://neon.com/pricing)
- [Render Free](https://render.com/docs/free)
- [Spring Session JDBC](https://docs.spring.io/spring-session/reference/3.3/configuration/jdbc.html)

## Evidências desta etapa

Consultar o PR e `VALIDACAO_DEPLOY.md` para separar verificações executadas de pendências em contas externas. Um arquivo de configuração pronto não comprova deploy público.

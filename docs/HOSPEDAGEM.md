# Hospedagem do CritPlay — pesquisa de 13/10/2026

Ainda não houve contratação nem deploy. Preços em moeda original, antes de domínio, impostos, câmbio e consumo adicional. Fontes oficiais:

- Railway: https://railway.com/pricing e https://docs.railway.com/pricing/plans
- Render: https://render.com/pricing e https://render.com/docs/free
- Hetzner: https://docs.hetzner.com/general/infrastructure-and-availability/price-adjustment/ e https://docs.hetzner.com/cloud/servers/primary-ips/overview/

| Opção | Referência de custo | Aplicação prática |
| --- | --- | --- |
| Railway Hobby | US$5/mês de uso mínimo, incluindo US$5 em consumo | Java e PostgreSQL cobrados pelo uso; volume persistente para uploads. Minha estimativa inicial para os dois serviços é US$10–20/mês, dependente de RAM, CPU, disco e tráfego. |
| Render pago | Starter US$7 + Postgres básico US$6 = US$13/mês, mais disco/tráfego | Base com 512 MB para Java; pode ficar apertada. Aplicação de 2 GB custa US$25, somando US$31 com o banco básico, antes de extras. |
| Hetzner CX23, Europa | €5,49/mês sem IPv4; IPv4 €0,50/mês extra | VPS de 4 GB; aplicação e banco no mesmo servidor. Você administra Docker, HTTPS, atualização e backups. Backups e outros serviços têm custos próprios. |

Railway é minha primeira opção para começar com menos trabalho de operação, com limite de gasto definido no painel. Hetzner interessa para reduzir custo e aprender infraestrutura, assumindo manutenção do servidor. Render tem custos de recursos mais previsíveis, mas é preciso dimensionar memória do Java.

Render gratuito serve para demonstração: dorme após 15 minutos, não mantém uploads em disco persistente e seu banco PostgreSQL gratuito expira em 30 dias. Não é a configuração indicada para uma comunidade com dados duráveis.

## O que já foi preparado

- `Dockerfile`: compilação Java 21, imagem de execução, usuário sem privilégios, bind/porta por ambiente e memória proporcional ao limite do container.
- `.dockerignore`: exclui credenciais, banco local e artefatos do contexto.
- `.env.production.example`: nomes das configurações com segredos vazios; não substitui seu `.env` local.
- `GET /api/health`: teste simples da aplicação/banco para o health check do provedor.
- Migração local H2 → PostgreSQL, documentada em MIGRACAO_POSTGRES.md.

## Configurar no provedor escolhido

Use o Dockerfile do repositório, profile postgres, banco privado, volume de uploads montado com permissão para UID 10001 e apenas uma instância do Java. Configure segredos no painel, `SESSION_COOKIE_SECURE=true`, `APP_BASE_URL` HTTPS e `CRITPLAY_MEDIA_DIRECTORY` no volume. A porta vem de `PORT`. Nunca use H2 no filesystem temporário de uma hospedagem.

Antes da abertura pública, configure identificação de IP confiável no proxy escolhido e revise limites de tentativas. Hoje o limiter usa o IP da conexão e armazenamento em memória; limites compartilhados e uma configuração específica do proxy ainda dependem da infraestrutura escolhida. Configure backups e restauração e confira SMTP se ativar recuperação de senha. Veja OPERACAO.md.

Docker não foi executado neste ambiente; o JAR e os fluxos Java são verificados localmente e no CI. A imagem precisa ser construída e validada no provedor antes do deploy.

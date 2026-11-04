# Validação do deploy — 04/11/2026

## Executado

- `mvn -B verify` em container Linux com Java 17 e PostgreSQL 17 descartável: **94 testes, zero falhas, zero erros, zero ignorados; BUILD SUCCESS**.
- No GitHub Actions, [Java CI #113](https://github.com/FrancaCODEX/CritPlay/actions/runs/41887895572) concluiu com sucesso: testes Java, empacotamento, `reviews-ui.cjs`, `browser-smoke.cjs`, `community-browser.cjs` e build da imagem Docker. O frontend não foi redesenhado.
- O CI foi fixado em Ubuntu 24.04: a execução anterior passou nos 94 testes Java, mas não instalou Playwright 1.58.2 em Ubuntu 26.04, que passou a ser `ubuntu-latest`. A nova execução passou por todas as etapas.
- Migrações em banco vazio: H2 até V13 e PostgreSQL até V12. Históricos existentes não foram editados; as novas migrações são aditivas.
- Migração H2 → PostgreSQL em bancos de teste: senhas, datas, avaliações e relações preservadas; destino já utilizado recusado. O teste agora seleciona explicitamente o schema isolado no Flyway e no JDBC.
- Os 50 testes de API cobrem cadastro/login/logout, CSRF, revogação de sessão, recuperação de senha com e-mail simulado, catálogo, avaliações, favoritos, listas, biblioteca, perfis, interações, moderação e uploads/GIFs.
- Teste de persistência reinicializa o contexto completo da aplicação, relê sessão autenticada serializada e imagem no banco e verifica leitura pela API. Também valida leases e impede um worker antigo de pausar uma execução retomada.
- Limitação de autenticação compartilhada testada entre duas instâncias do filtro em PostgreSQL real. Erros do banco falham de forma fechada.
- Projeto Neon criado em organização Free, com compute limitado a 0,25 CU. Produção e validação estão em branches separadas. Conexão Java ao Neon com `sslmode=verify-full` e truststore da JVM executada com sucesso. Migrações e teste de persistência/reinicialização também foram executados no Neon.
- Execução final no Neon, em branch nova e endpoint direto: **4 testes, zero falhas, zero erros; BUILD SUCCESS**. Cobriu persistência/reinicialização, catálogo/relações e dados fictícios PostgreSQL e limite de autenticação compartilhado. Essa execução adicional demorou cerca de 15 minutos no ambiente local; não representa latência de uma requisição do usuário.
- Profile **prod** iniciado de verdade em container com usuário sem privilégios, limite de **2 GB / 1 CPU** e banco Neon de validação: `/api/health` retornou 200/UP; `/api/auth/csrf` retornou 200 e cookie `JSESSIONID` com Secure, HttpOnly e SameSite=Lax. O contexto levou aproximadamente 168 segundos para iniciar neste host; cold start na Vercel permanece sem medição e é um critério obrigatório antes da abertura pública.
- A branch `production` do projeto Neon permaneceu vazia nesta etapa de preparação (consulta confirmou nenhuma tabela). Nenhum banco preexistente do usuário foi alterado.
- Auditoria OSV de 79 dependências de execução: 22 alertas iniciais; após patches confirmados no JAR, restaram dois alertas do Spring em recursos SSE/XSLT não usados. Detalhes e limites em [SEGURANCA_DEPENDENCIAS.md](SEGURANCA_DEPENDENCIAS.md).

## Limites da verificação

O Java deste Windows falhou ao abrir conexões loopback; as tentativas iniciais locais não passaram. A validação completa foi feita em Linux/Docker e GitHub Actions. Não foi enfraquecida a configuração de segurança para contornar esse problema do host.

As chamadas RAWG/GG.deals e o envio SMTP nos testes usam fixtures/mocks. Consulta real aos provedores depende das chaves e das respectivas cotas; recuperação de senha real depende de SMTP configurado. Esses serviços não foram declarados operacionais em produção.

O teste de reinício fecha e recria a aplicação Spring. Isso comprova que sessão e mídia não dependem do contexto anterior; não substitui a medição de cold start e retomada na infraestrutura da Vercel.

Os testes no Neon escrevem dados fictícios somente nas branches de validação. Tentativas intermediárias identificaram diferenças de schemas no caminho pooled e fixtures já presentes; não são contadas como sucesso da suíte completa. A execução final usa endpoint direto e uma branch nova.

## Implantação

Código preparado em `deploy/neon-vercel`, com [PR #15](https://github.com/FrancaCODEX/CritPlay/pull/15), sem merge. Não foi contratado plano pago.

A publicação, domínio HTTPS, variáveis no provedor e integração Git ficaram a cargo do usuário, conforme a orientação final nesta etapa. A integração Vercel foi reconhecida como instalada, mas não disponibilizou ferramentas no chat; a tentativa pelo navegador integrado também não conseguiu anexar o painel. Nenhum projeto/deploy Vercel foi criado. Não há URL pública validada. Não confundir build/configuração pronta com deploy concluído.

## Próximos checks no ambiente público

1. Confirmar Hobby e disponibilidade da beta Container Runtime na conta; definir PORT, prod, DB_URL/usuário/senha e APP_BASE_URL nos ambientes corretos.
2. Validar HTTPS, cookies, CSRF, login/logout, acesso administrativo e `/api/health`.
3. Repetir navegação, upload e persistência após reinício/escala a zero; medir latência e uso de JVM, conexões e cotas.
4. Configurar chaves externas e SMTP e validar os fluxos reais, com contas de teste.
5. Revisar o PR e exigir CI verde antes de qualquer merge autorizado. Configurar integração da main no painel após a aprovação.

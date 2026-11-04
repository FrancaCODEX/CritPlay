# Auditoria de dependências — 04/11/2026

A árvore Maven de execução foi consultada na API pública OSV (`POST https://api.osv.dev/v1/querybatch`), com 79 coordenadas Maven. A consulta inicial encontrou 22 alertas em seis artefatos. Isso é uma análise de versões conhecidas, não teste de exploração nem varredura da imagem do sistema operacional.

Overrides de patch foram escolhidos dentro das linhas compatíveis com Spring Boot 3.5.16 / Java 17, verificando publicação no Maven Central:

| Família | Versão anterior | Patch aplicado |
| --- | --- | --- |
| PostgreSQL JDBC | 42.7.11 | 42.7.14 |
| Tomcat | 10.1.55 | 10.1.61 |
| Jackson BOM | 2.21.4 | 2.21.8 |
| Log4j BOM/API | 2.24.3 | 2.25.5 |

As propriedades no pom mantêm alinhados os módulos de cada família. Remover overrides quando uma versão mantida do Spring Boot fornecer versões equivalentes ou superiores; repetir testes e auditoria. Não substituir o framework por uma major nova silenciosamente em um trabalho de deploy.

## Alertas residuais do Spring Framework 6.2.19

- [CVE-2026-47890](https://spring.io/security/cve-2026-47890) / GHSA-j9f9-w8pj-32f8: corrupção de stream com SSE e fragmentos de views. O CritPlay usa REST JSON e arquivos estáticos; não usa SSE ou renderização de fragmentos. A condição descrita não está presente no código inspecionado.
- [CVE-2026-47884](https://spring.io/security/cve-2026-47884) / GHSA-pc63-qcmh-9cmg: uso de XsltView com mapeamento abrangente e nome de view implícito. Não há XsltView, resolução XSLT nem controladores de views no CritPlay; controladores são REST. A condição descrita não está presente no código inspecionado.

Na data da consulta, 6.2.19 é o último Spring Framework 6.2 disponível no Maven Central. A linha 7 tem correções, mas exige atualização coordenada do Spring Boot e testes de compatibilidade. Os dois alertas devem permanecer visíveis como risco residual e ser reavaliados antes de introduzir SSE, templates ou XSLT. Recomenda-se uma etapa própria de migração para uma linha mantida do framework.

Fontes primárias adicionais: [pgJDBC security](https://jdbc.postgresql.org/security/), [Tomcat 10 security](https://tomcat.apache.org/security-10.html), [OSV API](https://google.github.io/osv.dev/api/). Ver `VALIDACAO_DEPLOY.md` para resultado da consulta após os overrides. O resultado não significa ausência de vulnerabilidades desconhecidas ou dependentes de configuração.

# Verificação da entrega v0.2 — 13/10/2026

- `clean verify` concluído com sucesso em JDK 17.
- 20 testes automatizados: 0 falhas, 0 erros, 0 ignorados.
- Autenticação HTTP real com cookies e CSRF, rotação da sessão no login e invalidação no logout.
- Cadastro comum cria somente USER; campo de papel administrativo recusado.
- Senhas armazenadas com BCrypt e hash não exposto no JSON.
- ADMIN pode cadastrar jogos; pessoa comum recebe 403, anônimo recebe 401.
- Mesmo o administrador não consegue escrever sem token CSRF.
- Diário isolado por conta: duas pessoas editam o mesmo jogo sem compartilhar os registros; remover um não remove o outro.
- Validação de usuário, senha, ano, plataformas, nota, status, texto e campos desconhecidos.
- Catálogo, filtros, erros de jogo inexistente, parsing e cache dos preços.
- Migração real verificada: iniciar JAR da v0.1, salvar uma nota, parar, iniciar JAR da v0.2 sobre o mesmo banco e entrar como administrador. Catálogo e avaliação foram preservados; cadastro de novo jogo funcionou. Reinicialização manteve conta, diário e jogo e não reaplicou a migração.
- Chave fornecida validada em consulta à API GG.deals. Consulta autenticada também realizada pela própria aplicação Java: estado LIVE, moeda BRL, plataforma PC. A chave não foi incluída no código, banco de testes ou GitHub.
- JavaScript passou em `node --check`.

As verificações de banco em arquivo usaram pastas temporárias, sem dados pessoais nos arquivos de entrega. Os testes automatizados não usam a chave real nem chamam o GG.deals. O CI do GitHub executa o mesmo comando de build, mas seu resultado remoto deve ser conferido em Actions.

Limites: não houve inspeção visual em navegador nem execução em Windows. A interface tem regras responsivas e a distribuição inclui Maven Wrapper para Windows/Linux; a revisão visual na máquina do usuário será uma próxima verificação. O teste de migração cobre o esquema original da v0.1, sem alterações manuais.

Mockito usa `mock-maker-subclass` para dispensar anexação dinâmica de agentes à JVM. Os mocks usados são de classes não finais.

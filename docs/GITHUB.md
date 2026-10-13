# Trabalhar com o repositório no FrancaCODEX

Repositório: https://github.com/FrancaCODEX/gameshelf.

## Baixar a versão atual

```bash
git clone https://github.com/FrancaCODEX/gameshelf.git
cd gameshelf
```

Copie `.env.example` para `.env`, preencha sua chave e os dados do administrador e siga o README. A chave não está no GitHub.

## Enviar suas próximas alterações

```bash
git checkout -b feat/editar-jogos
# Faça suas alterações
./mvnw clean verify
git add .
git status
git commit -m "feat: edição de jogos por administrador"
git push -u origin feat/editar-jogos
```

No Windows, use `.\mvnw.cmd clean verify`. Abra um pull request para revisar a alteração antes de integrar em main. Podemos revisar juntos a implementação e os testes.

Confira no `git status` que `.env`, `data/` e `target/` não aparecem. Nunca cole a chave da API em README, commit, issue ou workflow. Autentique pelo fluxo do seu Git Credential Manager/IDE; não envie token ou senha pelo chat. Não use `push --force` para resolver histórico diferente.

O workflow em `.github/workflows/java-ci.yml` executa `clean verify` em Java 21, sem chave do GG.deals, e com credenciais fictícias exclusivas dos testes. Publicar o código no GitHub não hospeda o serviço Java: o deploy será uma etapa separada.

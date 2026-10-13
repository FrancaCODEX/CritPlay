# Migrar H2 para PostgreSQL

Esta ferramenta é local e não tem endpoint HTTP. Ela não inicia o servidor Java. IDs, hashes de senha, perfis, diários, datas, listas, relações sociais, bloqueios, denúncias, auditoria e progresso de importações são copiados. Sessões são revogadas e tokens de recuperação não são copiados. Lotes ativos passam a pausados.

1. Atualize o código e inicie uma vez a v0.5.1 com seu H2 atual para aplicar V6. Depois pare o Java e faça backup de `data/` e do `.env` por meios protegidos.
2. Prepare um PostgreSQL novo e vazio, acessível à máquina que fará a cópia. Não inicie o CritPlay nesse destino antes da migração: o catálogo inicial tornaria o banco ocupado.
3. Configure `DB_URL` em formato JDBC, `DB_USERNAME` e `DB_PASSWORD` no ambiente ou `.env`. Mantenha usuário/senha fora da URL. O profile local H2 continua ignorando essas configurações até você ativar o profile postgres.
4. Empacote com `./mvnw package` (Windows: `.\mvnw.cmd package`).
5. Execute, pela raiz do projeto:

```sh
java -jar target/critplay-0.5.1.jar --migrate-h2-to-postgres
```

A origem padrão é `jdbc:h2:file:./data/gameshelf`. Para uma cópia em outro local, configure `H2_SOURCE_URL` com uma URL `jdbc:h2:file:` sem parâmetros adicionais; usuário padrão `sa`, senha vazia. Origem deve estar parada, atualizada e existente. É aberta em modo somente leitura.

A ferramenta aplica as migrações PostgreSQL, verifica que as tabelas de negócio estão vazias, trava o destino e copia tudo em uma transação. Compara colunas e contagens e reposiciona as sequências. Se falhar, nenhuma linha de negócio é copiada parcialmente; as tabelas/migrações vazias do destino podem permanecer. O H2 não é apagado nem alterado.

Depois, copie `data/uploads/` para o volume persistente do servidor. Inicie com `SPRING_PROFILES_ACTIVE=postgres`, entre com sua conta existente e confira listas, resenhas, datas e imagens. Só mude o serviço usado pelos usuários depois dessa conferência. Preserve o backup H2 como possibilidade de retorno, evitando escrever simultaneamente nas duas bases.

Em uma hospedagem, use uma conexão PostgreSQL acessível e protegida, ou um túnel autorizado. A URL privada do provedor pode não ser acessível diretamente do seu computador. Isso depende do plano escolhido.

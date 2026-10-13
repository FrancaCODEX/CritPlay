package br.com.gameshelf;

import br.com.gameshelf.catalog.*;
import br.com.gameshelf.users.*;
import br.com.gameshelf.collections.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="TEST_POSTGRES",matches="true")
@ActiveProfiles("postgres")
@SpringBootTest(properties={"spring.config.import=","ggdeals.api-key=","app.admin.username=","app.admin.password=","app.security.rate-limit-enabled=false"})
class PostgresSmokeTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired UserService users;
    @Autowired CatalogService catalog;
    @Autowired ListService lists;
    @Test void migratesRealPostgresAndPersistsRelations() {
        assertTrue(jdbc.queryForObject("select version()",String.class).startsWith("PostgreSQL"));
        var user=users.register(new RegisterRequest("postgres_test","Teste PostgreSQL","PostgresTestPassword42!"));
        var game=catalog.create(new CreateGameRequest("Jogo no PostgreSQL","Aventura",Set.of(Platform.PC),2026,null,
            "Descrição para testar o banco PostgreSQL de verdade.",null,null),user.id());
        var list=lists.create(user.id(),new ListRequest("Lista PostgreSQL","Testando relações"));
        long id=((Number)list.get("id")).longValue();
        lists.add(user.id(),id,game.getId());
        assertEquals(1,((java.util.List<?>)lists.get(user.id(),id).get("games")).size());
    }
}

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
    @Autowired br.com.gameshelf.social.SocialService social;
    @Autowired br.com.gameshelf.prices.PlatformPriceService prices;
    @Test void migratesRealPostgresAndPersistsRelations() {
        assertTrue(jdbc.queryForObject("select version()",String.class).startsWith("PostgreSQL"));
        var user=users.register(new RegisterRequest("postgres_test","Teste PostgreSQL","PostgresTestPassword42!"));
        var game=catalog.create(new CreateGameRequest("Jogo no PostgreSQL","Aventura",Set.of(Platform.PC),2026,null,
            "Descrição para testar o banco PostgreSQL de verdade.",null,null),user.id());
        var list=lists.create(user.id(),new ListRequest("Lista PostgreSQL","Testando relações"));
        long id=((Number)list.get("id")).longValue();
        lists.add(user.id(),id,game.getId());
        assertEquals(1,((java.util.List<?>)lists.get(user.id(),id).get("games")).size());
        var friend=users.register(new RegisterRequest("postgres_friend","Amigo PostgreSQL","PostgresTestPassword42!"));
        social.privacy(user.username(),br.com.gameshelf.social.ProfileVisibility.FRIENDS,true);
        social.follow(friend.username(),user.username(),true);
        social.friendship(friend.username(),user.username(),"REQUEST");
        assertFalse((Boolean)social.profile(friend.username(),user.username()).get("visible"));
        social.friendship(user.username(),friend.username(),"ACCEPT");
        assertTrue((Boolean)social.profile(friend.username(),user.username()).get("visible"));
        assertEquals(1,((java.util.List<?>)social.connections(user.username()).get("friends")).size());
        assertEquals(1,prices.prices(game.getId()).size());
    }
}

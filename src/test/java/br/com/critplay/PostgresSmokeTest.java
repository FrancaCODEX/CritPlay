package br.com.critplay;

import br.com.critplay.catalog.*;
import br.com.critplay.users.*;
import br.com.critplay.collections.*;
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
    @Autowired br.com.critplay.social.SocialService social;
    @Autowired UserRepository accounts;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;
    @Autowired br.com.critplay.admin.AccountAdminService management;
    @Autowired br.com.critplay.metadata.MetadataService metadata;
    @Autowired br.com.critplay.media.ImageCleanupService cleanup;
    @Autowired br.com.critplay.social.BlockService blocks;
    @Autowired br.com.critplay.metadata.ImportJobStore imports;
    @org.springframework.test.context.bean.override.mockito.MockitoBean br.com.critplay.metadata.RawgClient rawg;
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
        social.privacy(user.username(),br.com.critplay.social.ProfileVisibility.FRIENDS,true);
        social.follow(friend.username(),user.username(),true);
        social.friendship(friend.username(),user.username(),"REQUEST");
        assertFalse((Boolean)social.profile(friend.username(),user.username()).get("visible"));
        social.friendship(user.username(),friend.username(),"ACCEPT");
        assertTrue((Boolean)social.profile(friend.username(),user.username()).get("visible"));
        assertEquals(1,((java.util.List<?>)social.connections(user.username()).get("friends")).size());
        accounts.save(new AppUser("pg_admin","Administrador de teste",encoder.encode("PostgresAdminPassword42!"),Role.ADMIN));
        management.update("pg_admin",user.id(),Role.USER,false);
        assertFalse(accounts.findById(user.id()).orElseThrow().isEnabled());
        management.update("pg_admin",user.id(),Role.USER,true);
        var fixture=new br.com.critplay.metadata.MetadataGame(88L,"pg-fixture","Jogo PostgreSQL","Aventura",Set.of(Platform.PC),2026,"Descrição de metadados fictícios.",null);
        org.mockito.Mockito.when(rawg.detail(88)).thenReturn(fixture);
        metadata.importGame(88,new CreateGameRequest("Jogo PostgreSQL importado","Aventura",Set.of(Platform.PC),2026,null,"Descrição para validar a importação no PostgreSQL.",null,null),game.getId(),user.id());
        assertEquals(88L,catalog.find(game.getId()).getRawgId());
        assertEquals(1,((java.util.List<?>)lists.get(user.id(),id).get("games")).size());
        assertNotNull(cleanup.preview().get("count"));
        blocks.change(user.username(),friend.username(),true);assertTrue(blocks.blocked(user.id(),friend.id()));
        assertFalse(social.friends(user.id(),friend.id()));blocks.change(user.username(),friend.username(),false);
        var job=imports.create(accounts.findByUsername("pg_admin").orElseThrow().getId(),500);
        imports.consume(job,new br.com.critplay.metadata.MetadataGame(8899L,"pg-bulk","Importação PG","Adventure",Set.of(Platform.PC),2026,"Descrição fictícia de lote PostgreSQL.",null),40);
        assertEquals(1,imports.get(job.id()).added());imports.pause(job.id(),"Teste concluído");
    }
}

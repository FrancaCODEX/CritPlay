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
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired br.com.critplay.demo.DemoDataService demo;
    @Autowired br.com.critplay.demo.DemoCollectionsService demoCollections;
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
    @Test @org.springframework.transaction.annotation.Transactional void seedsDemoOnPostgresOnce() {
        assertTrue(demo.seed("Hades,Celeste"));
        assertFalse(demo.seed(""));
        long luna=accounts.findByUsername("demo_luna").orElseThrow().getId();
        long hades=jdbc.queryForObject("SELECT id FROM games WHERE title='Hades'",Long.class);
        String legacy="[Resenha fictícia de demonstração] As armas e os diálogos fazem cada tentativa de fuga ter algo novo para descobrir. Gostei, mas não entrou entre meus favoritos. Vale experimentar com calma.";
        entityManager.flush();
        jdbc.update("UPDATE diary_entries SET review=?,rating=3,favorite=FALSE WHERE user_id=? AND game_id=?",legacy,luna,hades);
        entityManager.clear();
        jdbc.update("UPDATE demo_seed_runs SET completed=FALSE WHERE name='community-variety-v2'");
        assertEquals(1,demo.diversifyLegacyReviews());
        assertNotEquals(legacy,jdbc.queryForObject("SELECT review FROM diary_entries WHERE user_id=? AND game_id=?",String.class,luna,hades));
        assertNotEquals(3.0,jdbc.queryForObject("SELECT rating FROM diary_entries WHERE user_id=? AND game_id=?",Double.class,luna,hades));
        assertEquals(0,demo.diversifyLegacyReviews());
        assertTrue(jdbc.queryForObject("SELECT completed FROM demo_seed_runs WHERE name='community-variety-v2'",Boolean.class));
        assertTrue(jdbc.queryForObject("SELECT COUNT(DISTINCT rating) FROM diary_entries d JOIN app_users u ON u.id=d.user_id WHERE LEFT(u.username,5)='demo_'",Long.class)>=5);
        entityManager.clear(); // Startup runners use separate transactions/persistence contexts.
        assertEquals(14,demoCollections.personalize("Hades,Celeste"));
        assertEquals(0,demoCollections.personalize("Hades,Celeste"));
        var reviews=(java.util.List<?>)social.profile("demo_rafa","demo_luna").get("reviews");
        assertEquals(2,reviews.size());
        for(var item:reviews) {
            var review=(java.util.Map<?,?>)item;
            assertEquals(true,review.get("likedByMe"));
            assertTrue(((Number)review.get("likeCount")).longValue()>=1);
        }
        assertFalse(social.profile(null,"demo_luna").toString().contains("passwordHash"));
        assertEquals(27,jdbc.queryForObject("SELECT COUNT(*) FROM game_lists l JOIN app_users u ON u.id=l.user_id WHERE LEFT(u.username,5)='demo_'",Long.class));
        assertEquals(16,jdbc.queryForObject("SELECT COUNT(*) FROM app_users WHERE LEFT(username,5)='demo_'",Long.class));
        assertEquals(28,jdbc.queryForObject("SELECT COUNT(*) FROM diary_entries d JOIN app_users u ON u.id=d.user_id WHERE LEFT(u.username,5)='demo_'",Long.class));
    }
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
        imports.consume(job,new br.com.critplay.metadata.MetadataGame(8899L,"pg-bulk","Importação PG","Adventure",Set.of(Platform.PC),2026,"Descrição fictícia de lote PostgreSQL.",null),40,8899456L);
        assertEquals(8899456L,jdbc.queryForObject("SELECT steam_app_id FROM games WHERE rawg_id=8899",Long.class));
        assertEquals(1,imports.get(job.id()).added());imports.pause(job.id(),"Teste concluído");
    }
}

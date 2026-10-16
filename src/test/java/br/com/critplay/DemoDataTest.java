package br.com.critplay;

import br.com.critplay.demo.*;
import br.com.critplay.users.*;
import br.com.critplay.social.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.config.import=", "spring.datasource.url=jdbc:h2:mem:demo-tests;DB_CLOSE_DELAY=-1",
    "app.admin.username=", "app.admin.password=", "app.demo.enabled=false", "ggdeals.api-key="})
@Transactional
class DemoDataTest {
    @Autowired DemoDataService seed;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate db;
    @Autowired SocialService social;
    @Autowired CommunityService community;
    long count(String table){return db.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}

    @Test void remainsOffUntilExplicitlyEnabled() {
        assertEquals(0,count("app_users"));
        new DemoDataRunner(seed,false,"").run();
        assertEquals(0,count("app_users"));
        assertFalse(db.queryForObject("SELECT completed FROM demo_seed_runs",Boolean.class));
    }
    @Test void seedsPublicCommunityAndPreservesChangesOnRepeat() {
        var real=users.saveAndFlush(new AppUser("real_player","Real player","unusable",Role.USER));
        new DemoDataRunner(seed,true,"").run();
        assertEquals(17,count("app_users"));
        assertEquals(112,count("diary_entries"));
        assertEquals(14,count("game_lists"));
        assertEquals(42,count("user_follows"));
        assertEquals(7,count("friendships"));
        assertEquals(98,count("activity_events"));
        assertEquals(139,count("review_likes"));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM diary_entries WHERE user_id=?",Long.class,real.getId()));
        var luna=users.findByUsername("demo_luna").orElseThrow();
        assertTrue(luna.getBio().contains("fictício"));assertNull(luna.getEmail());
        assertTrue((Boolean)social.profile(null,"demo_luna").get("visible"));
        assertFalse((Boolean)social.profile(null,"demo_noah").get("visible"));
        long sol=users.findByUsername("demo_sol").orElseThrow().getId();
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM diary_entries WHERE user_id=?",Long.class,sol));
        assertFalse(community.feed("demo_luna",null).items().isEmpty());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM diary_entries WHERE rating IS NOT NULL AND (rating<0.5 OR rating>5 OR rating*2<>FLOOR(rating*2))",Long.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM diary_entries WHERE finish_date<start_date",Long.class));
        luna.updateProfile("Nome editado","Bio editada",null,null);users.saveAndFlush(luna);
        long likes=count("review_likes");
        assertFalse(seed.seed("Mesmo uma configuração diferente não recria a carga"));
        assertEquals("Nome editado",users.findByUsername("demo_luna").orElseThrow().getDisplayName());
        assertEquals(17,count("app_users"));assertEquals(likes,count("review_likes"));
    }
    @Test void supportsSpecificTitlesWithoutAssumingIds() {
        var gameId=db.queryForObject("SELECT id FROM games WHERE title='Hades'",Long.class);
        assertTrue(seed.seed("  HADES  "));
        assertEquals(14,count("diary_entries"));
        assertEquals(14,db.queryForObject("SELECT COUNT(*) FROM diary_entries WHERE game_id=?",Long.class,gameId));
        assertTrue(community.reviews(gameId,null).reviews().stream().allMatch(r->r.review().contains("tentativa de fuga")));
    }
    @Test void refusesUnknownGamesBeforeCreatingAccounts() {
        assertThrows(IllegalStateException.class,()->seed.seed("Hades,Jogo inexistente"));
        assertEquals(0,count("app_users"));assertEquals(0,count("diary_entries"));
        assertFalse(db.queryForObject("SELECT completed FROM demo_seed_runs",Boolean.class));
    }
    @Test void neverAdoptsExistingAccountsWithDemoNames() {
        var existing=users.saveAndFlush(new AppUser("demo_otto","Conta existente","unusable",Role.USER));
        assertThrows(IllegalStateException.class,()->seed.seed(""));
        assertEquals(1,count("app_users"));assertEquals(0,count("diary_entries"));
        assertEquals("Conta existente",users.findById(existing.getId()).orElseThrow().getDisplayName());
        assertFalse(db.queryForObject("SELECT completed FROM demo_seed_runs",Boolean.class));
    }
}

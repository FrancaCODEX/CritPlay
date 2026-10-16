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
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired DemoCollectionsService collections;
    @Autowired DemoDataService seed;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate db;
    @Autowired SocialService social;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean CommunityService community;
    long count(String table){return db.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}

    @Test void remainsOffUntilExplicitlyEnabled() {
        assertEquals(0,count("app_users"));
        new DemoDataRunner(seed,collections,false,"").run();
        assertEquals(0,count("app_users"));
        assertFalse(db.queryForObject("SELECT completed FROM demo_seed_runs WHERE name='community-v1'",Boolean.class));
    }
    @Test void seedsPublicCommunityAndPreservesChangesOnRepeat() {
        var real=users.saveAndFlush(new AppUser("real_player","Real player","unusable",Role.USER));
        assertTrue(seed.seed(""));
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
        assertTrue(db.queryForObject("SELECT COUNT(DISTINCT rating) FROM diary_entries",Long.class)>=5);
        assertEquals(14,db.queryForObject("SELECT COUNT(DISTINCT review) FROM diary_entries",Long.class));
        assertEquals(14,db.queryForObject("SELECT COUNT(*) FROM diary_entries WHERE game_id=?",Long.class,gameId));
        assertTrue(community.reviews(gameId,null).reviews().stream().allMatch(r->r.review().contains("Hades")));
    }
    @Test void everyGameHasDifferentVoicesAndRatings() {
        seed.seed("");
        var groups=db.queryForList("SELECT game_id,COUNT(*) AS n,COUNT(DISTINCT review) AS texts,COUNT(DISTINCT rating) AS ratings FROM diary_entries WHERE review<>'' GROUP BY game_id");
        assertEquals(12,groups.size());
        for(var group:groups) {
            assertEquals(((Number)group.get("n")).longValue(),((Number)group.get("texts")).longValue(),"Resenhas duplicadas: "+group);
            assertTrue(((Number)group.get("ratings")).longValue()>=3,"Notas repetitivas: "+group);
        }
        assertTrue(db.queryForObject("SELECT MIN(rating) FROM diary_entries",Double.class)<=2);
        assertEquals(5.0,db.queryForObject("SELECT MAX(rating) FROM diary_entries",Double.class));
    }
    @Test void upgradesLegacyReviewsOnceWithoutOverwritingEditsOrDuplicatingData() {
        seed.seed("Hades");
        long luna=users.findByUsername("demo_luna").orElseThrow().getId();
        long rafa=users.findByUsername("demo_rafa").orElseThrow().getId();
        long bia=users.findByUsername("demo_bia").orElseThrow().getId();
        String legacy="[Resenha fictícia de demonstração] As armas e os diálogos fazem cada tentativa de fuga ter algo novo para descobrir. Gostei, mas não entrou entre meus favoritos. Vale experimentar com calma.";
        // Simulate original installed fixture and two subsequent edits: custom text, or rating only.
        db.update("UPDATE diary_entries SET review=?,rating=3 WHERE user_id=?",legacy,luna);
        db.update("UPDATE diary_entries SET review='Minha resenha editada',rating=2.5 WHERE user_id=?",rafa);
        db.update("UPDATE diary_entries SET review=?,rating=1 WHERE user_id=?",legacy,bia);
        long entries=count("diary_entries"),events=count("activity_events"),likes=count("review_likes");
        var original=db.queryForMap("SELECT updated_at,start_date,finish_date,favorite,row_version FROM diary_entries WHERE user_id=?",luna);
        // JDBC upgrade does not depend on the persisted titles option being provided again.
        entityManager.clear();
        db.update("UPDATE demo_seed_runs SET completed=FALSE WHERE name='community-variety-v2'");
        assertEquals(1,seed.diversifyLegacyReviews());
        var changed=db.queryForMap("SELECT review,updated_at,start_date,finish_date,favorite,row_version FROM diary_entries WHERE user_id=?",luna);
        assertNotEquals(legacy,changed.get("review"));
        for(String field:java.util.List.of("updated_at","start_date","finish_date","favorite"))assertEquals(original.get(field),changed.get(field));
        assertEquals(((Number)original.get("row_version")).longValue()+1,((Number)changed.get("row_version")).longValue());
        assertEquals("Minha resenha editada",db.queryForObject("SELECT review FROM diary_entries WHERE user_id=?",String.class,rafa));
        assertEquals(1.0,db.queryForObject("SELECT rating FROM diary_entries WHERE user_id=?",Double.class,bia));
        assertEquals(entries,count("diary_entries"));assertEquals(events,count("activity_events"));assertEquals(likes,count("review_likes"));
        assertEquals(0,seed.diversifyLegacyReviews());
    }
    @Test void upgradesOriginalRatingsWithoutInventingReviews() {
        seed.seed("");
        long luna=users.findByUsername("demo_luna").orElseThrow().getId();
        long entry=db.queryForObject("SELECT id FROM diary_entries WHERE user_id=? AND status='PLAYING'",Long.class,luna);
        entityManager.flush();
        db.update("UPDATE diary_entries SET rating=3 WHERE id=?",entry);
        entityManager.clear();
        long events=count("activity_events");
        db.update("UPDATE demo_seed_runs SET completed=FALSE WHERE name='community-variety-v2'");
        assertEquals(1,seed.diversifyLegacyReviews());
        assertEquals("",db.queryForObject("SELECT review FROM diary_entries WHERE id=?",String.class,entry));
        assertEquals("PLAYING",db.queryForObject("SELECT status FROM diary_entries WHERE id=?",String.class,entry));
        assertEquals(1,db.queryForObject("SELECT row_version FROM diary_entries WHERE id=?",Long.class,entry));
        assertEquals(events,count("activity_events"));
    }
    @Test void refusesUnknownGamesBeforeCreatingAccounts() {
        assertThrows(IllegalStateException.class,()->seed.seed("Hades,Jogo inexistente"));
        assertEquals(0,count("app_users"));assertEquals(0,count("diary_entries"));
        assertFalse(db.queryForObject("SELECT completed FROM demo_seed_runs WHERE name='community-v1'",Boolean.class));
    }
    @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void rollsBackTheWholeFixtureIfAnInteractionFails() {
        org.mockito.Mockito.doThrow(new IllegalStateException("Falha simulada na curtida"))
            .when(community).like(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.eq(true));
        try {
            assertThrows(IllegalStateException.class,()->seed.seed("Hades"));
            for(String table:java.util.List.of("app_users","diary_entries","game_lists","game_list_items",
                    "profile_featured_games","activity_events","user_follows","friendships","review_likes"))
                assertEquals(0,count(table),table+" não pode ficar parcialmente carregada");
            assertFalse(db.queryForObject("SELECT completed FROM demo_seed_runs WHERE name='community-v1'",Boolean.class));
        } finally {org.mockito.Mockito.reset(community);}
    }
    @Test void neverAdoptsExistingAccountsWithDemoNames() {
        var existing=users.saveAndFlush(new AppUser("demo_otto","Conta existente","unusable",Role.USER));
        assertThrows(IllegalStateException.class,()->seed.seed(""));
        assertEquals(1,count("app_users"));assertEquals(0,count("diary_entries"));
        assertEquals("Conta existente",users.findById(existing.getId()).orElseThrow().getDisplayName());
        assertFalse(db.queryForObject("SELECT completed FROM demo_seed_runs WHERE name='community-v1'",Boolean.class));
    }
}

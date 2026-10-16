package br.com.critplay;

import br.com.critplay.demo.*;
import br.com.critplay.users.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.config.import=","spring.datasource.url=jdbc:h2:mem:demo-collections-tests;DB_CLOSE_DELAY=-1",
    "app.admin.username=","app.admin.password=","app.demo.enabled=false","ggdeals.api-key="})
@Transactional
class DemoCollectionsTest {
    @Autowired DemoDataService seed;
    @Autowired DemoCollectionsService collections;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate db;
    @Autowired jakarta.persistence.EntityManager entityManager;
    long count(String table){return db.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}
    long id(String username){return users.findByUsername(username).orElseThrow().getId();}
    List<Long> games(long owner,String condition){return db.queryForList("SELECT game_id FROM diary_entries WHERE user_id=? "+condition+" ORDER BY game_id",Long.class,owner);}

    @Test void runnerCreatesDistinctCollectionsWithoutLosingReviewsOrLikes() {
        new DemoDataRunner(seed,collections,true,"").run();
        entityManager.clear();
        assertEquals(16,count("app_users"));assertEquals(139,count("review_likes"));
        assertEquals(70,db.queryForObject("SELECT COUNT(*) FROM diary_entries WHERE review<>''",Long.class));
        assertEquals(42,count("user_follows"));assertEquals(7,count("friendships"));
        var librarySets=new HashSet<List<Long>>();var favoriteSets=new HashSet<List<Long>>();var wishSets=new HashSet<List<Long>>();
        var featuredSets=new HashSet<List<Long>>();var sizes=new HashSet<Integer>();var statuses=new HashSet<List<String>>();
        for(String name:List.of("luna","rafa","bia","caio","nina","theo","maya","leo","iris","davi","yuki","alan","zoe","otto")) {
            long user=id("demo_"+name);
            var library=games(user,"");librarySets.add(library);sizes.add(library.size());
            assertTrue(library.size()>=7&&library.size()<=11);
            var favorites=games(user,"AND favorite=TRUE");favoriteSets.add(favorites);assertTrue(library.containsAll(favorites));
            var wishes=games(user,"AND wishlist=TRUE");wishSets.add(wishes);assertFalse(wishes.isEmpty());
            var featured=db.queryForList("SELECT game_id FROM profile_featured_games WHERE user_id=? ORDER BY position",Long.class,user);
            featuredSets.add(featured);assertTrue(library.containsAll(featured));assertTrue(featured.size()>=2&&featured.size()<=4);
            statuses.add(db.queryForList("SELECT status||':'||COUNT(*) FROM diary_entries WHERE user_id=? GROUP BY status ORDER BY status",String.class,user));
        }
        assertEquals(5,sizes.size());assertTrue(librarySets.size()>=10,"Bibliotecas precisam de seleções diferentes");
        assertTrue(favoriteSets.size()>=10,"Favoritos precisam variar");assertTrue(wishSets.size()>=10,"Desejos precisam variar");
        assertTrue(featuredSets.size()>=10);assertTrue(statuses.size()>=8);
        assertEquals(27,count("game_lists"));assertEquals(27,db.queryForObject("SELECT COUNT(DISTINCT name) FROM game_lists",Long.class));
        var listSelections=new HashSet<List<Long>>();
        for(long list:db.queryForList("SELECT id FROM game_lists",Long.class)) {
            var selection=db.queryForList("SELECT game_id FROM game_list_items WHERE list_id=? ORDER BY game_id",Long.class,list);
            assertTrue(selection.size()>=2&&selection.size()<=5);listSelections.add(selection);
        }
        assertTrue(listSelections.size()>=20,"As listas precisam variar também nos jogos, não apenas no nome");
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM diary_entries WHERE finish_date IS NOT NULL AND (start_date IS NULL OR finish_date<start_date)",Long.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM diary_entries WHERE user_id IN (?,?)",Long.class,id("demo_sol"),id("demo_noah")));
        var before=db.queryForList("SELECT * FROM diary_entries ORDER BY id");
        assertEquals(0,collections.personalize(""));assertEquals(before,db.queryForList("SELECT * FROM diary_entries ORDER BY id"));
    }
    @Test void preservesCustomLibraryListsAndFeaturedGamesAndRealAccounts() {
        seed.seed("");entityManager.flush();entityManager.clear();
        long luna=id("demo_luna"),rafa=id("demo_rafa"),bia=id("demo_bia");
        db.update("UPDATE diary_entries SET review='Minha edição pessoal',row_version=row_version+1 WHERE user_id=? AND review<>''",luna);
        var lunaBefore=db.queryForList("SELECT * FROM diary_entries WHERE user_id=? ORDER BY id",luna);
        db.update("UPDATE game_lists SET name='Minha lista editada' WHERE user_id=?",rafa);
        var rafaLists=db.queryForList("SELECT * FROM game_lists WHERE user_id=?",rafa);
        db.update("DELETE FROM profile_featured_games WHERE user_id=?",bia);
        db.update("INSERT INTO profile_featured_games(user_id,game_id,position) VALUES (?,12,1)",bia);
        var real=users.saveAndFlush(new AppUser("real_player","Jogador real","unusable",Role.USER));
        assertEquals(13,collections.personalize(""));
        assertEquals(lunaBefore,db.queryForList("SELECT * FROM diary_entries WHERE user_id=? ORDER BY id",luna));
        assertEquals(rafaLists,db.queryForList("SELECT * FROM game_lists WHERE user_id=?",rafa));
        assertEquals(List.of(12L),db.queryForList("SELECT game_id FROM profile_featured_games WHERE user_id=? ORDER BY position",Long.class,bia));
        assertEquals("Jogador real",users.findById(real.getId()).orElseThrow().getDisplayName());
        assertTrue(games(real.getId(),"").isEmpty());
    }
    @Test void preservesChangedWishlistAndRemovedGames() {
        seed.seed("");entityManager.flush();entityManager.clear();
        long nina=id("demo_nina"),theo=id("demo_theo");
        db.update("UPDATE diary_entries SET wishlist=TRUE WHERE user_id=? AND status='COMPLETED'",nina);
        db.update("DELETE FROM diary_entries WHERE user_id=? AND status='WANT_TO_PLAY'",theo);
        var ninaBefore=db.queryForList("SELECT * FROM diary_entries WHERE user_id=? ORDER BY id",nina);
        var theoBefore=db.queryForList("SELECT * FROM diary_entries WHERE user_id=? ORDER BY id",theo);
        assertEquals(12,collections.personalize(""));
        assertEquals(ninaBefore,db.queryForList("SELECT * FROM diary_entries WHERE user_id=? ORDER BY id",nina));
        assertEquals(theoBefore,db.queryForList("SELECT * FROM diary_entries WHERE user_id=? ORDER BY id",theo));
    }
    @Test void respectsExplicitGameScopeAndWorksWithOneTitle() {
        seed.seed("Hades");entityManager.flush();entityManager.clear();
        assertEquals(14,collections.personalize("Hades"));
        assertEquals(14,count("diary_entries"));
        assertEquals(1,db.queryForObject("SELECT COUNT(DISTINCT game_id) FROM diary_entries",Long.class));
        assertEquals(27,count("game_lists"));
        assertEquals(1,db.queryForObject("SELECT COUNT(DISTINCT game_id) FROM game_list_items",Long.class));
    }
    @Test void leavesEverythingUntouchedWithoutAnInstalledFixture() {
        assertEquals(0,collections.personalize(""));assertEquals(0,count("app_users"));
        assertFalse(db.queryForObject("SELECT completed FROM demo_seed_runs WHERE name='community-collections-v3'",Boolean.class));
    }
}

package br.com.critplay.social;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ReviewLikesRepositoryTest {
    private final AtomicInteger reads = new AtomicInteger();
    private ReviewLikesRepository repository;
    private long firstEntry;
    private long secondEntry;

    @BeforeEach
    void database() {
        var source = new DriverManagerDataSource() {
            @Override public Connection getConnection() throws SQLException {
                reads.incrementAndGet();
                return super.getConnection();
            }
        };
        source.setUrl("jdbc:h2:mem:likes_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        source.setUsername("sa");
        source.setPassword("");
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        var db = new JdbcTemplate(source);
        for (long user = 1; user <= 3; user++) {
            db.update("INSERT INTO app_users(id,username,display_name,password_hash,role,created_at,enabled) VALUES (?,?,?,'unused','USER',CURRENT_TIMESTAMP,?)",
                user, "person_" + user, "Pessoa " + user, user != 3);
        }
        for (long game = 1; game <= 2; game++) {
            db.update("INSERT INTO games(id,title,release_year) VALUES (?, 'Fixture',2026)", game);
            db.update("INSERT INTO diary_entries(user_id,game_id,status,review,favorite,updated_at) VALUES (1,?,'COMPLETED','Resenha de teste',FALSE,CURRENT_TIMESTAMP)", game);
        }
        firstEntry = db.queryForObject("SELECT id FROM diary_entries WHERE game_id=1", Long.class);
        secondEntry = db.queryForObject("SELECT id FROM diary_entries WHERE game_id=2", Long.class);
        db.update("INSERT INTO review_likes(entry_id,user_id) VALUES (?,2),(?,3),(?,3)", firstEntry, firstEntry, secondEntry);
        repository = new ReviewLikesRepository(db);
        reads.set(0);
    }

    @Test
    void countsOnlyActiveAccountsWithoutChangingViewerLikeSemantics() {
        var viewer = repository.summaries(List.of(firstEntry, secondEntry), 2L);
        assertEquals(new ReviewLikesRepository.Likes(1, true), viewer.get(firstEntry));
        assertEquals(new ReviewLikesRepository.Likes(0, false), viewer.get(secondEntry));
        var anonymous = repository.summaries(List.of(firstEntry, secondEntry), null);
        assertEquals(new ReviewLikesRepository.Likes(1, false), anonymous.get(firstEntry));
        assertEquals(ReviewLikesRepository.NONE, anonymous.get(secondEntry));
        // Presence and active-account counts are distinct rules in the existing API.
        var inactive = repository.summaries(List.of(firstEntry, secondEntry), 3L);
        assertEquals(new ReviewLikesRepository.Likes(0, true), inactive.get(secondEntry));
        assertEquals(1, repository.count(firstEntry));
    }

    @Test
    void usesConstantDatabaseRoundTripsForLargeListsAndNoneForEmptyInput() {
        var entries = new ArrayList<Long>();
        entries.add(firstEntry);
        entries.add(secondEntry);
        for (long id = 1000; id < 1200; id++) entries.add(id);
        var summaries = repository.summaries(entries, 2L);
        assertEquals(entries.size(), summaries.size());
        assertEquals(2, reads.get(), "O número de leituras não deve crescer por resenha");
        assertEquals(ReviewLikesRepository.NONE, summaries.get(1199L));
        reads.set(0);
        repository.summaries(entries, null);
        assertEquals(1, reads.get());
        reads.set(0);
        assertTrue(repository.summaries(List.of(), 2L).isEmpty());
        assertEquals(0, reads.get());
    }
}

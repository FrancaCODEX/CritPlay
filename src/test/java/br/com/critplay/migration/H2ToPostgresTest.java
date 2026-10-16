package br.com.critplay.migration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.flywaydb.core.Flyway;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="TEST_POSTGRES",matches="true")
class H2ToPostgresTest {
    @Test void migratesRealH2ToPostgresPreservingPasswordsDatesAndRelationsAndRefusesUsedTarget() throws Exception {
        String h2="jdbc:h2:mem:migration_"+UUID.randomUUID().toString().replace("-","")+";DB_CLOSE_DELAY=-1";
        String url=System.getenv("DB_URL"),user=System.getenv("DB_USERNAME"),password=System.getenv("DB_PASSWORD");
        String schema="migration_"+UUID.randomUUID().toString().replace("-","");
        try(var root=DriverManager.getConnection(url,user,password);var statement=root.createStatement()){
            statement.execute("CREATE SCHEMA "+schema);
            try {
                String targetUrl=url+(url.contains("?")?"&":"?")+"currentSchema="+schema;
                Flyway.configure().dataSource(h2,"sa","").locations("classpath:db/migration").load().migrate();
                Flyway.configure().dataSource(targetUrl,user,password).locations("classpath:db/postgresql").load().migrate();
                var encoder=new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4);String hash=encoder.encode("MigrationPassword42!");
                try(var source=DriverManager.getConnection(h2,"sa","");var target=DriverManager.getConnection(targetUrl,user,password)){
                    try(var insert=source.prepareStatement("INSERT INTO app_users(id,username,display_name,password_hash,role,created_at) VALUES (77,'migration_user','Migração',?,'ADMIN',CURRENT_TIMESTAMP)")){insert.setString(1,hash);insert.executeUpdate();}
                    try(var data=source.createStatement()){
                        data.execute("UPDATE demo_seed_runs SET completed=TRUE WHERE name IN ('community-v1','community-variety-v2')");
                        data.execute("INSERT INTO games(id,title,genre,platforms,release_year,description) VALUES (3010,'Migração de teste','RPG','PC',2026,'Descrição fictícia de migração.')");
                        data.execute("INSERT INTO diary_entries(id,user_id,game_id,status,review,favorite,wishlist,updated_at,start_date,finish_date) VALUES (101,77,3010,'COMPLETED','Resenha preservada',TRUE,TRUE,CURRENT_TIMESTAMP,'2026-01-01','2026-02-01')");
                        data.execute("UPDATE diary_entries SET rating=4.5 WHERE id=101");
                        data.execute("UPDATE app_users SET banner_url='/media/77-banner.png' WHERE id=77");
                        data.execute("INSERT INTO profile_featured_games(user_id,game_id,position) VALUES (77,3010,1)");
                        data.execute("INSERT INTO activity_events(entry_id,actor_id,game_id,action,status,rating) VALUES (101,77,3010,'REVIEW','COMPLETED',4.5)");
                        data.execute("INSERT INTO game_lists(id,user_id,name,description,created_at) VALUES (45,77,'Lista preservada','Descrição',CURRENT_TIMESTAMP)");
                        data.execute("INSERT INTO game_list_items(id,list_id,game_id,added_at) VALUES (49,45,3010,CURRENT_TIMESTAMP)");
                    }
                    var totals=H2ToPostgres.migrate(source,target);assertEquals(1,totals.get("app_users"));assertEquals(1,totals.get("diary_entries"));assertEquals(1,totals.get("profile_featured_games"));assertEquals(1,totals.get("activity_events"));
                    try(var read=target.createStatement();var rows=read.executeQuery("SELECT password_hash,session_version FROM app_users WHERE id=77")){assertTrue(rows.next());assertTrue(encoder.matches("MigrationPassword42!",rows.getString(1)));assertEquals(1,rows.getInt(2));}
                    try(var read=target.createStatement();var rows=read.executeQuery("SELECT finish_date,rating FROM diary_entries WHERE user_id=77")){rows.next();assertEquals("2026-02-01",rows.getString(1));assertEquals(4.5,rows.getDouble(2));}
                    try(var insert=target.createStatement();var rows=insert.executeQuery("INSERT INTO games(title,genre,platforms,release_year,description) VALUES ('Novo','RPG','PC',2026,'Descrição fictícia para teste') RETURNING id")){rows.next();assertTrue(rows.getLong(1)>3010);}
                    try(var read=target.createStatement();var rows=read.executeQuery("SELECT completed FROM demo_seed_runs WHERE name IN ('community-v1','community-variety-v2')")){assertTrue(rows.next());assertTrue(rows.getBoolean(1));assertTrue(rows.next());assertTrue(rows.getBoolean(1));}
                    assertThrows(SQLException.class,()->H2ToPostgres.migrate(source,target));
                    try(var read=target.createStatement();var rows=read.executeQuery("SELECT COUNT(*) FROM games")){rows.next();assertEquals(2,rows.getLong(1));}
                }
            }finally{statement.execute("DROP SCHEMA "+schema+" CASCADE");}
        }
    }
}

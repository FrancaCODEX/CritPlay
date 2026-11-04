package br.com.critplay.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="TEST_POSTGRES",matches="true")
class DatabaseAuthRateLimitTest {
    @Test void sharesLimitsBetweenInstancesAndAllowsNextWindow() {
        var source=new DriverManagerDataSource(System.getenv("DB_URL"),System.getenv("DB_USERNAME"),System.getenv("DB_PASSWORD"));
        org.flywaydb.core.Flyway.configure().dataSource(source).locations("classpath:db/postgresql").load().migrate();
        var jdbc=new JdbcTemplate(source);var one=new DatabaseAuthRateLimitFilter(true,jdbc);var two=new DatabaseAuthRateLimitFilter(true,jdbc);
        String key="test-"+java.util.UUID.randomUUID();long now=System.currentTimeMillis();
        assertTrue(one.increment(key,2,now));assertTrue(two.increment(key,2,now));assertFalse(one.increment(key,2,now));
        assertTrue(two.increment(key,2,now+600_000));
    }
}

package br.com.critplay;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Run the same transactional delivery/recovery cases against the real PostgreSQL service. */
@EnabledIfEnvironmentVariable(named="TEST_POSTGRES",matches="true")
@ActiveProfiles("postgres")
@SpringBootTest(properties={"spring.config.import=","spring.datasource.url=${DB_URL}",
    "app.mail.notifications-enabled=true","app.admin.username=","app.admin.password=","app.demo.enabled=false"})
class AccountEmailPostgresTest extends AccountEmailTest {}

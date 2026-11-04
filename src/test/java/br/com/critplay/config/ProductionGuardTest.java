package br.com.critplay.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class ProductionGuardTest {
    private MockEnvironment valid(){return new MockEnvironment()
        .withProperty("spring.datasource.url","jdbc:postgresql://db.example/critplay?sslmode=verify-full")
        .withProperty("app.media.storage","database").withProperty("server.servlet.session.cookie.secure","true")
        .withProperty("app.recovery.base-url","https://critplay.example")
        .withProperty("spring.jpa.hibernate.ddl-auto","validate").withProperty("spring.flyway.clean-disabled","true");}
    @Test void acceptsSecureProduction(){assertDoesNotThrow(()->ProductionGuard.check(valid()));}
    @Test void rejectsH2InProduction(){assertThrows(IllegalStateException.class,()->ProductionGuard.check(valid().withProperty("spring.datasource.url","jdbc:h2:mem:prod")));}
    @Test void rejectsEncryptionWithoutIdentityValidation(){assertThrows(IllegalStateException.class,()->ProductionGuard.check(valid().withProperty("spring.datasource.url","jdbc:postgresql://db.example/critplay?sslmode=require")));}
    @Test void rejectsDemoAndCatalogSeeds(){for(String key:new String[]{"app.demo.enabled","app.catalog.seed-enabled"})assertThrows(IllegalStateException.class,()->ProductionGuard.check(valid().withProperty(key,"true")));}
    @Test void rejectsUnsafeOverrides(){for(String key:new String[]{"spring.flyway.baseline-on-migrate","server.servlet.session.cookie.secure","spring.flyway.clean-disabled"}){
        String unsafe=key.equals("spring.flyway.baseline-on-migrate")?"true":"false";
        assertThrows(IllegalStateException.class,()->ProductionGuard.check(valid().withProperty(key,unsafe)));
    }}
}

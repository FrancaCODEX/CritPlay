package br.com.critplay.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

/** Refuses unsafe production settings before opening the datasource or running Flyway. */
@Configuration(proxyBeanMethods=false)
@Profile("prod")
public class ProductionGuard {
    @Bean static BeanFactoryPostProcessor validateProduction(Environment env) {
        return factory -> check(env);
    }
    public static void check(Environment env) {
        String url=env.getProperty("spring.datasource.url","");
        if(!url.startsWith("jdbc:postgresql://") || !url.matches(".*[?&]sslmode=verify-full(?:&.*)?$"))
            throw new IllegalStateException("Produção exige DB_URL PostgreSQL com sslmode=verify-full e certificado confiável.");
        if(env.getProperty("app.demo.enabled",Boolean.class,false)
                || env.getProperty("app.catalog.seed-enabled",Boolean.class,false))
            throw new IllegalStateException("Seeds são proibidos no profile prod. Use um banco separado para demonstração.");
        if(!"database".equals(env.getProperty("app.media.storage")))
            throw new IllegalStateException("Produção exige mídia persistente no banco.");
        if(!env.getProperty("server.servlet.session.cookie.secure",Boolean.class,false))
            throw new IllegalStateException("Produção exige cookie Secure.");
        String origin=env.getProperty("app.recovery.base-url","");
        if(!origin.matches("https://[^/\\s]+/?"))
            throw new IllegalStateException("Configure APP_BASE_URL com a origem HTTPS pública, sem caminho.");
        if(!"validate".equals(env.getProperty("spring.jpa.hibernate.ddl-auto"))
                || env.getProperty("spring.flyway.baseline-on-migrate",Boolean.class,false)
                || !env.getProperty("spring.flyway.clean-disabled",Boolean.class,false))
            throw new IllegalStateException("Produção exige Flyway sem baseline automático/clean e Hibernate validate.");
    }
}

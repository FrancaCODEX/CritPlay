package br.com.critplay.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import java.io.IOException;
import java.nio.file.*;

/** Checks the renamed local default before Flyway or the datasource can create a new database. */
@Configuration(proxyBeanMethods=false)
public class LocalDatabaseNameGuard {
    @Bean
    static BeanFactoryPostProcessor checkLocalDatabaseName(Environment environment) {
        return factory->check(environment.getProperty("spring.datasource.url",""));
    }
    public static void check(String url) {
        if(!url.startsWith("jdbc:h2:file:"))return;
        String file=url.substring("jdbc:h2:file:".length()).split(";",2)[0];
        if(file.isBlank())return;
        Path base=Path.of(file).toAbsolutePath().normalize();
        if(!base.getFileName().toString().equals("critplay"))return;
        Path directory=base.getParent();
        if(!Files.isDirectory(directory)||Files.exists(directory.resolve("critplay.mv.db")))return;
        try(var files=Files.list(directory)) {
            boolean previous=files.anyMatch(path->Files.isRegularFile(path)&&path.getFileName().toString().endsWith(".mv.db"));
            if(previous)throw new IllegalStateException("Há um banco local com outro nome na pasta "+directory+
                ". Pare o Java, faça backup e renomeie o arquivo .mv.db para critplay.mv.db, ou aponte CRITPLAY_LOCAL_DB_URL para a URL atual sem .mv.db. Veja docs/ATUALIZACAO_NOMES.md.");
        } catch(IOException failure) {
            throw new IllegalStateException("Não foi possível verificar o banco local antes de iniciar. Confira o acesso à pasta "+directory,failure);
        }
    }
}

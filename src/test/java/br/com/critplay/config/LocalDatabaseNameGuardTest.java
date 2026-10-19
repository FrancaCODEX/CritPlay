package br.com.critplay.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class LocalDatabaseNameGuardTest {
    @TempDir Path directory;
    String url(){return "jdbc:h2:file:"+directory.resolve("critplay");}
    @Test void preventsCreatingAnEmptyDefaultWhenAnExistingDatabaseHasAnotherName() throws Exception {
        Path old=Files.writeString(directory.resolve("banco-anterior.mv.db"),"dados existentes");
        assertThrows(IllegalStateException.class,()->LocalDatabaseNameGuard.check(url()));
        assertFalse(Files.exists(directory.resolve("critplay.mv.db")));
        assertEquals("dados existentes",Files.readString(old));
    }
    @Test void allowsFreshInstallAndExplicitCurrentFileOrExistingNewDefault() throws Exception {
        assertDoesNotThrow(()->LocalDatabaseNameGuard.check(url()));
        Files.writeString(directory.resolve("banco-anterior.mv.db"),"dados existentes");
        assertDoesNotThrow(()->LocalDatabaseNameGuard.check("jdbc:h2:file:"+directory.resolve("banco-anterior")));
        Files.writeString(directory.resolve("critplay.mv.db"),"dados atuais");
        assertDoesNotThrow(()->LocalDatabaseNameGuard.check(url()+";DB_CLOSE_ON_EXIT=FALSE"));
        assertDoesNotThrow(()->LocalDatabaseNameGuard.check("jdbc:h2:mem:tests"));
        assertDoesNotThrow(()->LocalDatabaseNameGuard.check("jdbc:postgresql://localhost:5432/critplay"));
    }
    @Test void stopsContextBeforeDatabaseBeansCanBeCreated() throws Exception {
        Files.writeString(directory.resolve("banco-anterior.mv.db"),"dados existentes");
        var created=new AtomicBoolean();
        try(var context=new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",Map.of("spring.datasource.url",url())));
            context.register(LocalDatabaseNameGuard.class);
            context.registerBean("fakeDatasource",Object.class,()->{created.set(true);return new Object();});
            assertThrows(RuntimeException.class,context::refresh);
            assertFalse(created.get(),"A verificação deve preceder a criação do datasource e as migrações");
        }
    }
}

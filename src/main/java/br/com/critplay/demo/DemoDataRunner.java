package br.com.critplay.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(30) // Catalog and admin bootstrap run first.
public class DemoDataRunner implements CommandLineRunner {
    private final DemoDataService service;
    private final boolean enabled;
    private final String titles;
    public DemoDataRunner(DemoDataService service,@Value("${app.demo.enabled:false}") boolean enabled,
            @Value("${app.demo.game-titles:}") String titles) {
        this.service=service;this.enabled=enabled;this.titles=titles;
    }
    @Override public void run(String... args) {
        if(!enabled)return;
        boolean created=service.seed(titles);
        org.slf4j.LoggerFactory.getLogger(getClass()).info(created?
            "Demonstração criada: 16 perfis fictícios. Desative DEMO_DATA_ENABLED após a carga.":
            "Demonstração já carregada; nenhum dado foi alterado.");
    }
}

package br.com.critplay.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(30) // Catalog and admin bootstrap run first.
public class DemoDataRunner implements CommandLineRunner {
    private final DemoDataService service;
    private final DemoCollectionsService collections;
    private final boolean enabled;
    private final String titles;
    public DemoDataRunner(DemoDataService service,DemoCollectionsService collections,@Value("${app.demo.enabled:false}") boolean enabled,
            @Value("${app.demo.game-titles:}") String titles) {
        this.service=service;this.collections=collections;this.enabled=enabled;this.titles=titles;
    }
    @Override public void run(String... args) {
        if(!enabled)return;
        boolean created=service.seed(titles);
        int updated=service.diversifyLegacyReviews();
        int personalized=collections.personalize(titles);
        org.slf4j.LoggerFactory.getLogger(getClass()).info(created?
            "Demonstração criada: 16 perfis fictícios. Desative DEMO_DATA_ENABLED após a carga.":
            "Demonstração já carregada; "+updated+" avaliações antigas diversificadas; "+personalized+" bibliotecas personalizadas. Desative DEMO_DATA_ENABLED após a carga.");
    }
}

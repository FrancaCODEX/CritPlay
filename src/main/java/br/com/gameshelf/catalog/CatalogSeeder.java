package br.com.gameshelf.catalog;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.annotation.Order;

@Component
@Order(10)
public class CatalogSeeder implements CommandLineRunner {
    private final GameRepository repository;
    private final JdbcTemplate jdbc;
    public CatalogSeeder(GameRepository repository, JdbcTemplate jdbc) { this.repository = repository; this.jdbc = jdbc; }
    @Override public void run(String... args) {
        var games = List.of(
            new Game(1L, "Elden Ring", "RPG", "PC, PlayStation, Xbox", 2022, 1245620L, "Explore as Terras Intermédias e construa sua própria jornada em um mundo de fantasia e desafios."),
            new Game(2L, "Red Dead Redemption 2", "Aventura", "PC, PlayStation, Xbox", 2018, 1174180L, "Acompanhe Arthur Morgan e a gangue Van der Linde no fim da era dos fora da lei."),
            new Game(3L, "Cyberpunk 2077", "RPG", "PC, PlayStation, Xbox", 2020, 1091500L, "Crie seu mercenário e encontre seu lugar entre as luzes, conflitos e implantes de Night City."),
            new Game(4L, "Hollow Knight", "Metroidvania", "PC, PlayStation, Xbox, Nintendo", 2017, 367520L, "Desça ao reino de Hallownest em uma aventura desenhada à mão, cheia de segredos."),
            new Game(5L, "Hades", "Roguelike", "PC, PlayStation, Xbox, Nintendo", 2020, 1145360L, "Enfrente o submundo com Zagreus, descubra novas armas e conheça os deuses do Olimpo."),
            new Game(6L, "Stardew Valley", "Simulação", "PC, PlayStation, Xbox, Nintendo", 2016, 413150L, "Cultive sua fazenda, conheça seus vizinhos e construa uma nova vida no campo."),
            new Game(7L, "Baldur's Gate 3", "RPG", "PC, PlayStation, Xbox", 2023, 1086940L, "Monte seu grupo e tome decisões que mudam a história neste RPG com combate por turnos."),
            new Game(8L, "The Witcher 3: Wild Hunt", "RPG", "PC, PlayStation, Xbox, Nintendo", 2015, 292030L, "Siga Geralt de Rívia em uma jornada de monstros, contratos e escolhas difíceis."),
            new Game(9L, "God of War", "Aventura", "PC, PlayStation", 2018, 1593500L, "Kratos e Atreus percorrem os reinos nórdicos em uma jornada de pai e filho."),
            new Game(10L, "Marvel's Spider-Man Remastered", "Ação", "PC, PlayStation", 2020, 1817070L, "Balance entre os prédios de Nova York e enfrente os desafios de Peter Parker."),
            new Game(11L, "Celeste", "Plataforma", "PC, PlayStation, Xbox, Nintendo", 2018, 504230L, "Ajude Madeline a escalar a montanha Celeste, uma tela e um salto de cada vez."),
            new Game(12L, "The Last of Us Part I", "Aventura", "PC, PlayStation", 2022, 1888930L, "Viaje com Joel e Ellie por um mundo transformado, onde cada encontro tem consequências.")
        );
        // Não sobrescrever os registros em cada inicialização.
        games.stream().filter(g -> !repository.existsById(g.getId())).forEach(g -> jdbc.update(
            "INSERT INTO games (id,title,genre,platforms,release_year,steam_app_id,description) VALUES (?,?,?,?,?,?,?)",
            g.getId(),g.getTitle(),g.getGenre(),g.getPlatforms(),g.getReleaseYear(),g.getSteamAppId(),g.getDescription()));
    }
}

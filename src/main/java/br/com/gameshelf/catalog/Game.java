package br.com.gameshelf.catalog;

import jakarta.persistence.*;

@Entity
@Table(name = "games")
public class Game {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String title;
    private String genre;
    private String platforms;
    private int releaseYear;
    private Long steamAppId;
    @Column(length = 2000) private String description;

    protected Game() {} // Necessário para o JPA reconstruir a entidade.

    public Game(Long id, String title, String genre, String platforms, int releaseYear,
                Long steamAppId, String description) {
        this.id = id; this.title = title; this.genre = genre; this.platforms = platforms;
        this.releaseYear = releaseYear; this.steamAppId = steamAppId; this.description = description;
    }
    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getGenre() { return genre; }
    public String getPlatforms() { return platforms; }
    public int getReleaseYear() { return releaseYear; }
    public Long getSteamAppId() { return steamAppId; }
    public String getDescription() { return description; }
}

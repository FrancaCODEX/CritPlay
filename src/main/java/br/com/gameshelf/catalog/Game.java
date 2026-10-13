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
    private String coverUrl;
    @Column(length=1000) private String playstationUrl;
    @Column(length=1000) private String xboxUrl;
    @Column(length=1000) private String nintendoUrl;

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
    public String getCoverUrl() {return coverUrl;}
    public StoreLinks getStoreLinks() {return new StoreLinks(playstationUrl,xboxUrl,nintendoUrl);}
    public void update(String title,String genre,String platforms,int year,Long steamId,String description,String cover,StoreLinks links) {
        this.title=title;this.genre=genre;this.platforms=platforms;this.releaseYear=year;this.steamAppId=steamId;
        this.description=description;this.coverUrl=cover;
        this.playstationUrl=links==null?null:links.playstation();this.xboxUrl=links==null?null:links.xbox();this.nintendoUrl=links==null?null:links.nintendo();
    }
}

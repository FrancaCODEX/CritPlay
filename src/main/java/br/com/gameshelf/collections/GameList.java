package br.com.gameshelf.collections;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="game_lists")
public class GameList {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long userId;
    @Column(nullable=false,length=80) private String name;
    @Column(nullable=false,length=500) private String description;
    @Column(nullable=false) private Instant createdAt;
    protected GameList() {}
    public GameList(Long userId,String name,String description) {this.userId=userId;update(name,description);this.createdAt=Instant.now();}
    public void update(String name,String description) {this.name=name.strip();this.description=description==null?"":description.strip();}
    public Long getId() {return id;}
    public Long getUserId() {return userId;}
    public String getName() {return name;}
    public String getDescription() {return description;}
    public Instant getCreatedAt() {return createdAt;}
}

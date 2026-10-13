package br.com.critplay.collections;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="game_list_items",uniqueConstraints=@UniqueConstraint(columnNames={"list_id","game_id"}))
public class GameListItem {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long listId;
    @Column(nullable=false) private Long gameId;
    @Column(nullable=false) private Instant addedAt;
    protected GameListItem() {}
    public GameListItem(Long listId,Long gameId) {this.listId=listId;this.gameId=gameId;this.addedAt=Instant.now();}
    public Long getGameId() {return gameId;}
}

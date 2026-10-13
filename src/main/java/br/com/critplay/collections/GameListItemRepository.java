package br.com.critplay.collections;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface GameListItemRepository extends JpaRepository<GameListItem,Long> {
    List<GameListItem> findByListIdOrderByAddedAtAsc(Long listId);
    boolean existsByListIdAndGameId(Long listId,Long gameId);
    void deleteByListIdAndGameId(Long listId,Long gameId);
    void deleteByListId(Long listId);
}

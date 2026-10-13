package br.com.gameshelf.collections;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface GameListRepository extends JpaRepository<GameList,Long> {
    List<GameList> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<GameList> findByIdAndUserId(Long id,Long userId);
}

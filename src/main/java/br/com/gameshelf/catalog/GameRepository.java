package br.com.gameshelf.catalog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRepository extends JpaRepository<Game, Long> {
    boolean existsBySteamAppIdAndIdNot(Long steamAppId,Long id);
    boolean existsBySteamAppId(Long steamAppId);
}

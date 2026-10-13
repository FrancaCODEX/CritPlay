package br.com.critplay.catalog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRepository extends JpaRepository<Game, Long> {
    java.util.List<Game> findByTitleIgnoreCaseAndReleaseYearAndRawgIdIsNull(String title,int year);
    java.util.Optional<Game> findByRawgId(Long rawgId);
    boolean existsBySteamAppIdAndIdNot(Long steamAppId,Long id);
    boolean existsBySteamAppId(Long steamAppId);
}

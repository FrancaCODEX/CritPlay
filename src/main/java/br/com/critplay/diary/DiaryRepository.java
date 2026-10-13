package br.com.critplay.diary;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface DiaryRepository extends JpaRepository<DiaryEntry, Long> {
    List<DiaryEntry> findByUserId(Long userId);
    List<DiaryEntry> findByUserIdIsNull();
    Optional<DiaryEntry> findByUserIdAndGameId(Long userId, Long gameId);
    void deleteByUserIdAndGameId(Long userId, Long gameId);
}

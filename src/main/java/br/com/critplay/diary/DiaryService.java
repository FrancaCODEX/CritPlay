package br.com.critplay.diary;

import br.com.critplay.catalog.CatalogService;
import org.springframework.stereotype.Service;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiaryService {
    private final DiaryRepository repository;
    private final CatalogService catalog;
    private final org.springframework.jdbc.core.JdbcTemplate db;
    public DiaryService(DiaryRepository repository, CatalogService catalog,org.springframework.jdbc.core.JdbcTemplate db) {
        this.repository = repository; this.catalog = catalog;this.db=db;
    }
    public List<DiaryEntry> list(Long userId) { return repository.findByUserId(userId); }
    @Transactional public DiaryEntry save(Long userId, Long gameId, DiaryRequest request) {
        catalog.find(gameId); // Recusar um diário de jogo inexistente.
        var entry = repository.findByUserIdAndGameId(userId, gameId)
            .orElseGet(() -> new DiaryEntry(userId, gameId, request));
        entry.update(request);
        var decisions=db.queryForList("SELECT hide_review FROM content_reports WHERE target_id=? AND game_id=? AND hide_review IS NOT NULL ORDER BY resolved_at DESC,id DESC LIMIT 1",Boolean.class,userId,gameId);
        if(!decisions.isEmpty())entry.moderate(decisions.get(0));
        return repository.save(entry);
    }
    @Transactional public void delete(Long userId, Long gameId) {
        catalog.find(gameId);
        repository.deleteByUserIdAndGameId(userId, gameId);
    }
}

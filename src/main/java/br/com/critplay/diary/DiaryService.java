package br.com.critplay.diary;

import br.com.critplay.catalog.CatalogService;
import org.springframework.stereotype.Service;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiaryService {
    private final br.com.critplay.social.CommunityService community;
    private final DiaryRepository repository;
    private final CatalogService catalog;
    private final org.springframework.jdbc.core.JdbcTemplate db;
    public DiaryService(DiaryRepository repository, CatalogService catalog,org.springframework.jdbc.core.JdbcTemplate db, br.com.critplay.social.CommunityService community) {
        this.repository = repository; this.catalog = catalog;this.db=db;this.community=community;
    }
    public List<DiaryEntry> list(Long userId) { return repository.findByUserId(userId); }
    @Transactional public DiaryEntry save(Long userId, Long gameId, DiaryRequest request) {
        catalog.find(gameId); // Recusar um diário de jogo inexistente.
        var old=repository.findByUserIdAndGameId(userId,gameId).orElse(null);
        var entry=old==null?new DiaryEntry(userId,gameId,request):old;
        var oldStatus=old==null?null:old.getStatus();var oldRating=old==null?null:old.getRating();var oldReview=old==null?null:old.getReview();
        entry.update(request);
        var decisions=db.queryForList("SELECT hide_review FROM content_reports WHERE target_id=? AND game_id=? AND hide_review IS NOT NULL ORDER BY resolved_at DESC,id DESC LIMIT 1",Boolean.class,userId,gameId);
        if(!decisions.isEmpty())entry.moderate(decisions.get(0));
        entry=repository.saveAndFlush(entry);
        community.record(entry,oldStatus,oldRating,oldReview);
        return entry;
    }
    @Transactional public void delete(Long userId, Long gameId) {
        catalog.find(gameId);
        repository.deleteByUserIdAndGameId(userId, gameId);
    }
}

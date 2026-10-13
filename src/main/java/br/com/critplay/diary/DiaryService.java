package br.com.critplay.diary;

import br.com.critplay.catalog.CatalogService;
import org.springframework.stereotype.Service;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiaryService {
    private final DiaryRepository repository;
    private final CatalogService catalog;
    public DiaryService(DiaryRepository repository, CatalogService catalog) {
        this.repository = repository; this.catalog = catalog;
    }
    public List<DiaryEntry> list(Long userId) { return repository.findByUserId(userId); }
    @Transactional public DiaryEntry save(Long userId, Long gameId, DiaryRequest request) {
        catalog.find(gameId); // Recusar um diário de jogo inexistente.
        var entry = repository.findByUserIdAndGameId(userId, gameId)
            .orElseGet(() -> new DiaryEntry(userId, gameId, request));
        entry.update(request);
        return repository.save(entry);
    }
    @Transactional public void delete(Long userId, Long gameId) {
        catalog.find(gameId);
        repository.deleteByUserIdAndGameId(userId, gameId);
    }
}

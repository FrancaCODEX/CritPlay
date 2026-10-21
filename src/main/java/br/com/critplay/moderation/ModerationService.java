package br.com.critplay.moderation;

import br.com.critplay.diary.DiaryRepository;
import br.com.critplay.shared.BadRequestException;
import br.com.critplay.shared.ConflictException;
import br.com.critplay.shared.NotFoundException;
import br.com.critplay.social.SocialAccessService;
import br.com.critplay.users.UserService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ModerationService {
    private final UserService users;
    private final JdbcTemplate db;
    private final DiaryRepository diary;
    private final SocialAccessService access;

    public ModerationService(UserService users, JdbcTemplate db, DiaryRepository diary, SocialAccessService access) {
        this.users = users;
        this.db = db;
        this.diary = diary;
        this.access = access;
    }

    @Transactional
    public void report(String actor, ReportRequest request) {
        var reporter = users.require(actor);
        var target = users.require(request.username());
        if (reporter.getId().equals(target.getId())) {
            throw new BadRequestException("Escolha outro perfil para denunciar.");
        }
        var permission = access.profile(reporter, target);
        if (request.gameId() != null) {
            if (!permission.visible()) throw new NotFoundException("Resenha não disponível.");
            var entry = diary.findByUserIdAndGameId(target.getId(), request.gameId())
                .orElseThrow(() -> new NotFoundException("Resenha não encontrada."));
            if (entry.isReviewHidden() || entry.getReview().isBlank()) throw new NotFoundException("Resenha não disponível.");
        }
        var args = new ArrayList<Object>();
        args.add(reporter.getId());
        args.add(target.getId());
        if (request.gameId() != null) args.add(request.gameId());
        String gameFilter = request.gameId() == null ? "game_id IS NULL" : "game_id=?";
        long open = db.queryForObject("SELECT COUNT(*) FROM content_reports WHERE status='OPEN' AND reporter_id=? AND target_id=? AND "
            + gameFilter, Long.class, args.toArray());
        if (open > 0) throw new ConflictException("Você já tem uma denúncia aberta para esse conteúdo.");
        db.update("INSERT INTO content_reports(reporter_id,target_id,game_id,reason,status,created_at) VALUES (?,?,?,?,'OPEN',?)",
            reporter.getId(), target.getId(), request.gameId(), request.reason().strip(), OffsetDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        // A single join also handles reports whose author has since deleted the diary entry.
        return db.query("""
            SELECT r.id,u.username,r.game_id,r.reason,r.status,d.review,
                   COALESCE(d.review_hidden,FALSE) AS review_hidden
            FROM content_reports r JOIN app_users u ON u.id=r.target_id
            LEFT JOIN diary_entries d ON d.user_id=r.target_id AND d.game_id=r.game_id
            ORDER BY r.id DESC LIMIT 100
            """, (row, index) -> {
                var result = new LinkedHashMap<String, Object>();
                result.put("id", row.getLong("id"));
                result.put("username", row.getString("username"));
                result.put("gameId", row.getObject("game_id", Long.class));
                result.put("reason", row.getString("reason"));
                result.put("status", row.getString("status"));
                result.put("review", row.getString("review"));
                result.put("reviewHidden", row.getBoolean("review_hidden"));
                return result;
            });
    }

    @Transactional
    public void resolve(String actor, long id, ReportResolution resolution) {
        var rows = db.queryForList("SELECT target_id,game_id FROM content_reports WHERE id=? FOR UPDATE", id);
        if (rows.isEmpty()) throw new NotFoundException("Denúncia não encontrada.");
        var report = rows.get(0);
        if (resolution.hideReview() != null) {
            if (report.get("game_id") == null) throw new BadRequestException("Esta denúncia é de perfil, não de resenha.");
            long target = ((Number) report.get("target_id")).longValue();
            long game = ((Number) report.get("game_id")).longValue();
            var entry = diary.findByUserIdAndGameId(target, game)
                .orElseThrow(() -> new NotFoundException("A resenha foi removida pelo autor."));
            entry.moderate(resolution.hideReview());
            diary.save(entry);
            db.update("UPDATE content_reports SET hide_review=? WHERE id=?", resolution.hideReview(), id);
        }
        db.update("UPDATE content_reports SET status=?,moderator_id=?,resolved_at=? WHERE id=?",
            resolution.status(), users.require(actor).getId(), OffsetDateTime.now(), id);
    }
}

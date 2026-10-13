package br.com.critplay.moderation;
import br.com.critplay.users.*;
import br.com.critplay.diary.*;
import br.com.critplay.social.SocialService;
import br.com.critplay.shared.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.util.*;
import java.time.OffsetDateTime;

@RestController
public class ReportController {
    private final UserService users;private final JdbcTemplate db;private final DiaryRepository diary;private final SocialService social;
    public ReportController(UserService users,JdbcTemplate db,DiaryRepository diary,SocialService social){this.users=users;this.db=db;this.diary=diary;this.social=social;}
    public record ReportRequest(@NotBlank @Size(max=30) String username,@Positive Long gameId,@NotBlank @Size(min=10,max=500) String reason){}
    @PostMapping("/api/reports") @Transactional public Map<String,String> report(Principal actor,@Valid @RequestBody ReportRequest body){
        var reporter=users.require(actor.getName());var target=users.require(body.username());
        if(reporter.getId().equals(target.getId()))throw new BadRequestException("Escolha outro perfil para denunciar.");
        var profile=social.profile(actor.getName(),target.getUsername());
        if(body.gameId()!=null){if(!Boolean.TRUE.equals(profile.get("visible")))throw new NotFoundException("Resenha não disponível.");
            var entry=diary.findByUserIdAndGameId(target.getId(),body.gameId()).orElseThrow(()->new NotFoundException("Resenha não encontrada."));
            if(entry.isReviewHidden()||entry.getReview().isBlank())throw new NotFoundException("Resenha não disponível.");}
        var args=new ArrayList<Object>();args.add(reporter.getId());args.add(target.getId());if(body.gameId()!=null)args.add(body.gameId());
        if(db.queryForObject("SELECT COUNT(*) FROM content_reports WHERE status='OPEN' AND reporter_id=? AND target_id=? AND "+(body.gameId()==null?"game_id IS NULL":"game_id=?"),Long.class,args.toArray())>0)throw new ConflictException("Você já tem uma denúncia aberta para esse conteúdo.");
        db.update("INSERT INTO content_reports(reporter_id,target_id,game_id,reason,status,created_at) VALUES (?,?,?,?,'OPEN',?)",reporter.getId(),target.getId(),body.gameId(),body.reason().strip(),OffsetDateTime.now());
        return Map.of("message","Denúncia enviada para a moderação.");
    }
    @GetMapping("/api/admin/reports") public List<Map<String,Object>> list(){
        return db.query("SELECT r.*,u.username FROM content_reports r JOIN app_users u ON u.id=r.target_id ORDER BY r.id DESC LIMIT 100",(rs,row)->{
            var value=new LinkedHashMap<String,Object>();long targetId=rs.getLong("target_id");Long gameId=rs.getObject("game_id",Long.class);
            value.put("id",rs.getLong("id"));value.put("username",rs.getString("username"));value.put("gameId",gameId);value.put("reason",rs.getString("reason"));value.put("status",rs.getString("status"));
            var entry=gameId==null?null:diary.findByUserIdAndGameId(targetId,gameId).orElse(null);value.put("review",entry==null?null:entry.getReview());value.put("reviewHidden",entry!=null&&entry.isReviewHidden());return value;
        });
    }
    public record Resolution(@NotNull @Pattern(regexp="RESOLVED|DISMISSED") String status,Boolean hideReview){}
    @PutMapping("/api/admin/reports/{id}") @Transactional public Map<String,String> resolve(Principal actor,@PathVariable long id,@Valid @RequestBody Resolution body){
        var rows=db.queryForList("SELECT target_id,game_id FROM content_reports WHERE id=? FOR UPDATE",id);if(rows.isEmpty())throw new NotFoundException("Denúncia não encontrada.");
        var row=rows.get(0);if(body.hideReview()!=null){if(row.get("game_id")==null)throw new BadRequestException("Esta denúncia é de perfil, não de resenha.");
            var entry=diary.findByUserIdAndGameId(((Number)row.get("target_id")).longValue(),((Number)row.get("game_id")).longValue()).orElseThrow(()->new NotFoundException("A resenha foi removida pelo autor."));entry.moderate(body.hideReview());diary.save(entry);db.update("UPDATE content_reports SET hide_review=? WHERE id=?",body.hideReview(),id);}
        db.update("UPDATE content_reports SET status=?,moderator_id=?,resolved_at=? WHERE id=?",body.status(),users.require(actor.getName()).getId(),OffsetDateTime.now(),id);
        return Map.of("message","Denúncia analisada.");
    }
}

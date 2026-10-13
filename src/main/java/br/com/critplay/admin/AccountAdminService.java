package br.com.critplay.admin;
import br.com.critplay.users.*;
import br.com.critplay.shared.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.time.OffsetDateTime;

@Service
public class AccountAdminService {
    private final UserService users;private final UserRepository repository;
    private final PasswordResetRepository resets;private final JdbcTemplate db;private final DatabaseLock lock;
    public AccountAdminService(UserService users,UserRepository repository,PasswordResetRepository resets,JdbcTemplate db,DatabaseLock lock){
        this.users=users;this.repository=repository;this.resets=resets;this.db=db;this.lock=lock;
    }
    private Map<String,Object> view(AppUser user){return Map.of("id",user.getId(),"username",user.getUsername(),"displayName",user.getDisplayName(),"role",user.getRole(),"enabled",user.isEnabled(),"createdAt",user.getCreatedAt());}
    public List<Map<String,Object>> search(String query){
        if(query.length()>80)throw new BadRequestException("Busque por até 80 caracteres.");
        String q="%"+query.strip().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        return db.query("SELECT id FROM app_users WHERE LOWER(username) LIKE ? ESCAPE '!' OR LOWER(display_name) LIKE ? ESCAPE '!' ORDER BY username LIMIT 50",(rs,row)->view(repository.findById(rs.getLong(1)).orElseThrow()),q,q);
    }
    @Transactional public Map<String,Object> update(String actorName,Long targetId,Role role,boolean enabled){
        lock.acquire("accounts");var actor=users.require(actorName);
        if(!actor.isEnabled()||actor.getRole()!=Role.ADMIN)throw new BadRequestException("Sua conta não pode mais administrar usuários. Entre novamente.");
        var target=repository.findById(targetId).orElseThrow(()->new NotFoundException("Conta não encontrada."));
        if(actor.getId().equals(targetId)&&(role!=Role.ADMIN||!enabled))throw new BadRequestException("Você não pode remover seu próprio acesso administrativo.");
        if(target.getRole()==Role.ADMIN&&target.isEnabled()&&(role!=Role.ADMIN||!enabled)&&db.queryForObject("SELECT COUNT(*) FROM app_users WHERE role='ADMIN' AND enabled=TRUE",Long.class)<=1)
            throw new BadRequestException("Mantenha pelo menos um administrador ativo.");
        if(target.getRole()==role&&target.isEnabled()==enabled)return view(target);
        db.update("INSERT INTO account_audit(actor_id,target_id,old_role,new_role,old_enabled,new_enabled,changed_at) VALUES (?,?,?,?,?,?,?)",
            actor.getId(),targetId,target.getRole().name(),role.name(),target.isEnabled(),enabled,OffsetDateTime.now());
        target.updateAccess(role,enabled);repository.save(target);resets.revokeUserTokens(targetId);return view(target);
    }
}

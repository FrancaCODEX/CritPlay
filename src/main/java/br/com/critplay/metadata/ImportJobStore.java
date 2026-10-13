package br.com.critplay.metadata;
import br.com.critplay.catalog.*;
import br.com.critplay.shared.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.*;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class ImportJobStore {
    private final JdbcTemplate db;private final DatabaseLock lock;private final CatalogService catalog;private final GameRepository games;
    public ImportJobStore(JdbcTemplate db,DatabaseLock lock,CatalogService catalog,GameRepository games){this.db=db;this.lock=lock;this.catalog=catalog;this.games=games;}
    public ImportJob get(long id){return db.query("SELECT * FROM import_jobs WHERE id=?",(rs,n)->new ImportJob(rs.getLong("id"),rs.getString("status"),rs.getInt("target_count"),rs.getInt("processed"),rs.getInt("added"),rs.getInt("existing_count"),rs.getInt("skipped"),rs.getInt("page_number"),rs.getInt("item_index"),rs.getString("message"),rs.getLong("owner_id")),id).stream().findFirst().orElseThrow(()->new NotFoundException("Importação não encontrada."));}
    public List<ImportJob> list(){return db.query("SELECT id FROM import_jobs ORDER BY id DESC LIMIT 20",(rs,n)->get(rs.getLong(1)));}
    @Transactional public ImportJob create(long owner,int count){
        lock.acquire("imports");if(db.queryForObject("SELECT COUNT(*) FROM import_jobs WHERE status='RUNNING'",Long.class)>0)throw new ConflictException("Já existe uma importação em andamento.");
        var keys=new GeneratedKeyHolder();db.update(connection->{var statement=connection.prepareStatement("INSERT INTO import_jobs(owner_id,target_count,status,created_at) VALUES (?,?,'RUNNING',?)",new String[]{"id"});statement.setLong(1,owner);statement.setInt(2,count);statement.setObject(3,OffsetDateTime.now());return statement;},keys);
        return get(keys.getKey().longValue());
    }
    @Transactional public ImportJob resume(long id,long owner){lock.acquire("imports");if(db.queryForObject("SELECT COUNT(*) FROM import_jobs WHERE status='RUNNING'",Long.class)>0)throw new ConflictException("Já existe uma importação em andamento.");var job=get(id);if(!job.status().equals("PAUSED"))throw new BadRequestException("Esta importação não pode ser retomada.");db.update("UPDATE import_jobs SET status='RUNNING',owner_id=?,message='' WHERE id=?",owner,id);return get(id);}
    @Transactional public void pause(long id,String message){db.queryForList("SELECT id FROM import_jobs WHERE id=? FOR UPDATE",id);db.update("UPDATE import_jobs SET status='PAUSED',message=? WHERE id=? AND status='RUNNING'",message,id);}
    public void recover(){db.update("UPDATE import_jobs SET status='PAUSED',message='Servidor reiniciado. Retome pela Administração.' WHERE status='RUNNING'");}
    public boolean ownerActive(long id){return db.queryForObject("SELECT COUNT(*) FROM app_users WHERE id=? AND role='ADMIN' AND enabled=TRUE",Long.class,id)>0;}
    @Transactional public void finish(long id,String message){db.update("UPDATE import_jobs SET status='COMPLETED',message=? WHERE id=? AND status='RUNNING'",message,id);}
    public static String genre(String value){return switch(value.toLowerCase(Locale.ROOT)){
        case "action"->"Ação";case "adventure"->"Aventura";case "strategy"->"Estratégia";case "shooter"->"Tiro";case "simulation"->"Simulação";case "puzzle"->"Quebra-cabeça";case "platformer"->"Plataforma";case "racing"->"Corrida";case "sports"->"Esportes";case "fighting"->"Luta";case "family"->"Família";case "board games"->"Tabuleiro";case "educational"->"Educativo";case "card"->"Cartas";case "role-playing games rpg","rpg"->"RPG";case "indie"->"Indie";case "casual"->"Casual";case "arcade"->"Arcade";default->"Outros";};}
    @Transactional public void consume(ImportJob snapshot,MetadataGame metadata,int pageSize){consume(snapshot,metadata,pageSize,null);}
    @Transactional public void consume(ImportJob snapshot,MetadataGame metadata,int pageSize,Long steamId){
        lock.acquire("media");if(steamId!=null && games.existsBySteamAppId(steamId))steamId=null;
        db.queryForList("SELECT id FROM import_jobs WHERE id=? FOR UPDATE",snapshot.id());var job=get(snapshot.id());
        if(!job.status().equals("RUNNING")||job.page()!=snapshot.page()||job.index()!=snapshot.index())return;
        String counter="skipped";
        if(metadata!=null&&games.findByRawgId(metadata.id()).isPresent())counter="existing_count";
        else if(metadata!=null&&metadata.title().length()>=2&&metadata.releaseYear()!=null&&!metadata.platforms().isEmpty()){
            var matches=games.findByTitleIgnoreCaseAndReleaseYearAndRawgIdIsNull(metadata.title(),metadata.releaseYear());
            if(matches.size()==1){var game=matches.get(0);game.importMetadata(metadata.id(),metadata.slug(),metadata.coverUrl());game.assignSteamAppId(steamId);games.save(game);counter="existing_count";}
            else {String description=metadata.description().length()>=20?metadata.description():"Metadados importados da RAWG. A fonte não forneceu uma descrição completa deste jogo.";
                var game=catalog.create(new CreateGameRequest(metadata.title(),genre(metadata.genre()),metadata.platforms(),metadata.releaseYear(),metadata.platforms().contains(Platform.PC)?steamId:null,description,null,null),job.owner());
                game.importMetadata(metadata.id(),metadata.slug(),metadata.coverUrl());games.save(game);counter="added";}
        }
        int nextIndex=job.index()+1,nextPage=job.page();if(nextIndex>=pageSize){nextIndex=0;nextPage++;}
        db.update("UPDATE import_jobs SET processed=processed+1,"+counter+"="+counter+"+1,page_number=?,item_index=? WHERE id=?",nextPage,nextIndex,job.id());
        var updated=get(job.id());if(updated.added()>=updated.target())finish(job.id(),"Lote concluído.");else if(updated.processed()>=updated.target()*3)finish(job.id(),"Limite de verificação atingido. Há duplicatas ou registros incompletos; confira os totais.");
    }
}

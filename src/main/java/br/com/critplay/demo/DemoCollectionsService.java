package br.com.critplay.demo;

import br.com.critplay.catalog.*;
import br.com.critplay.collections.*;
import br.com.critplay.diary.*;
import br.com.critplay.users.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

/** One-time optional personalization of identifiable, unedited demo collections. */
@Service
public class DemoCollectionsService {
    private final UserRepository users;
    private final GameRepository games;
    private final DiaryService diary;
    private final ListService lists;
    private final JdbcTemplate db;
    public DemoCollectionsService(UserRepository users,GameRepository games,DiaryService diary,ListService lists,JdbcTemplate db) {
        this.users=users;this.games=games;this.diary=diary;this.lists=lists;this.db=db;
    }
    private static final String DESCRIPTION="Seleção fictícia para demonstrar listas no CritPlay.";
    private static final String[][] THEMES={
        {"Histórias que ficam","Mundos para me perder","Meu próximo capítulo"},
        {"Só mais uma tentativa","Desafios no radar","Vitórias suadas"},
        {"Muito além da missão principal","Caminhos que faltam","Descobertas favoritas"},
        {"Combos e adrenalina","Ação para o fim de semana","Chefes para encarar"},
        {"Meu cantinho sem pressa","Depois de um dia longo","Pequenas descobertas"},
        {"Escolhas que mudam tudo","Próxima campanha","Meu grupo ideal"},
        {"Ideias fora da caixa","Indies no horizonte","Arte em movimento"},
        {"Rumo aos cem por cento","Conquistas pendentes","Vale o replay"},
        {"Cada detalhe conta","Histórias por descobrir","Universos favoritos"},
        {"Precisão e persistência","Aprender outro caminho","Sessões intensas"},
        {"Tirando do backlog","Quando sobrar um tempo","Surpresas da biblioteca"},
        {"Novas ideias, velhos hábitos","Para comparar com os clássicos","Meu próximo save"},
        {"Diversão em sessões curtas","Só mais uma partida","No ritmo do controle"},
        {"Todos os cantos do mapa","Lugares que ainda não vi","Viagens memoráveis"}
    };
    @Transactional
    public int personalize(String titles) {
        if(!Boolean.TRUE.equals(db.queryForObject("SELECT completed FROM demo_seed_runs WHERE name='community-v1' FOR UPDATE",Boolean.class)))return 0;
        if(Boolean.TRUE.equals(db.queryForObject("SELECT completed FROM demo_seed_runs WHERE name='community-collections-v3' FOR UPDATE",Boolean.class)))return 0;
        var catalog=games.findAll().stream().sorted(Comparator.comparing(Game::getId)).toList();
        var requested=new HashSet<String>();
        if(titles!=null&&!titles.isBlank())for(String title:titles.split(",",-1)) {
            if(title.isBlank())throw new IllegalStateException("DEMO_GAME_TITLES contém um título vazio.");
            requested.add(title.strip().toLowerCase(Locale.ROOT));
        }
        for(String title:requested)if(catalog.stream().noneMatch(g->key(g).equals(title)))
            throw new IllegalStateException("Jogo da demonstração não encontrado no catálogo: "+title);
        var pool=catalog.stream().filter(g->requested.isEmpty()||requested.contains(key(g))).toList();
        if(pool.isEmpty())throw new IllegalStateException("Não há jogos da seleção para diversificar a demonstração.");
        var baseline=pool.stream().filter(g->!requested.isEmpty()||DemoDataService.originalTitle(g)).toList();
        int changed=0;
        for(int person=0;person<14;person++) {
            var user=users.findByUsername(DemoDataService.demoNames().get(person)).orElse(null);
            if(user==null)continue;
            db.queryForList("SELECT id FROM diary_entries WHERE user_id=? ORDER BY id FOR UPDATE",user.getId());
            var entries=diary.list(user.getId());
            int size=Math.min(8,baseline.size());
            var oldGames=new ArrayList<Game>();
            for(int slot=0;slot<size;slot++)oldGames.add(baseline.get((person+slot)%baseline.size()));
            boolean original=entries.size()==size&&size>0;
            for(int slot=0;original&&slot<size;slot++) {
                var game=oldGames.get(slot);
                var entry=entries.stream().filter(e->e.getGameId().equals(game.getId())).findFirst().orElse(null);
                original=entry!=null&&DemoDataService.originalContent(person,slot,game,entry,
                    db.queryForObject("SELECT row_version FROM diary_entries WHERE id=?",Long.class,entry.getId()));
            }
            // A custom review, changed flag/date/status, or extra/removed game protects the entire library.
            if(!original)continue;
            var keep=entries.stream().filter(e->!e.getReview().isBlank()).sorted(Comparator.comparing(DiaryEntry::getGameId)).toList();
            var ids=new HashSet<Long>();keep.forEach(e->ids.add(e.getGameId()));
            var chosen=new ArrayList<Game>();
            for(var entry:keep)chosen.add(catalog.stream().filter(g->g.getId().equals(entry.getGameId())).findFirst().orElseThrow());
            int target=Math.min(pool.size(),7+person*3%5);
            for(var game:rank(pool,person,31))if(chosen.size()<target&&ids.add(game.getId()))chosen.add(game);
            for(var entry:entries)if(entry.getReview().isBlank())db.update("DELETE FROM diary_entries WHERE id=?",entry.getId());
            int p=person;
            var favorites=chosen.stream().filter(g->keep.stream().anyMatch(e->e.getGameId().equals(g.getId()))&&DemoOpinions.rating(p,g)>=3)
                .sorted(Comparator.<Game>comparingDouble(g->DemoOpinions.rating(p,g)).reversed().thenComparing(g->score(g,p,17)))
                .limit(1+person%3).map(Game::getId).toList();
            var additions=chosen.stream().filter(g->keep.stream().noneMatch(e->e.getGameId().equals(g.getId()))).toList();
            var wishes=rank(additions.isEmpty()?chosen:additions,person,53).stream().limit(1+person%3).map(Game::getId).toList();
            int index=0;
            for(var game:chosen) {
                var existing=keep.stream().filter(e->e.getGameId().equals(game.getId())).findFirst().orElse(null);
                boolean wishlist=wishes.contains(game.getId());
                PlayStatus status=existing==null?(wishlist?PlayStatus.WANT_TO_PLAY:(index+person)%3==0?PlayStatus.DROPPED:PlayStatus.PLAYING):
                    (index+person)%7==5?PlayStatus.PLAYING:(index+person)%7==6?PlayStatus.DROPPED:PlayStatus.COMPLETED;
                LocalDate start=existing==null?(status==PlayStatus.WANT_TO_PLAY?null:LocalDate.now().minusDays(8+person+index)):existing.getStartDate();
                LocalDate finish=status==PlayStatus.COMPLETED?existing.getFinishDate():null;
                if(existing!=null)db.update("UPDATE diary_entries SET status=?,favorite=?,wishlist=?,finish_date=?,row_version=row_version+1 WHERE id=?",
                    status.name(),favorites.contains(game.getId()),wishlist,finish,existing.getId());
                else diary.save(user.getId(),game.getId(),new DiaryRequest(status,status==PlayStatus.WANT_TO_PLAY?null:DemoOpinions.rating(person,game),"",
                    favorites.contains(game.getId()),wishlist,start,finish));
                index++;
            }
            // Replace only the untouched generic list; custom lists and edits are left intact.
            var expectedItems=new HashSet<Long>();for(int slot=0;slot<size;slot+=2)expectedItems.add(oldGames.get(slot).getId());
            var oldLists=db.queryForList("SELECT id,name,description FROM game_lists WHERE user_id=? ORDER BY id FOR UPDATE",user.getId());
            for(var old:oldLists) {
                long listId=((Number)old.get("id")).longValue();
                var items=new HashSet<>(db.queryForList("SELECT game_id FROM game_list_items WHERE list_id=?",Long.class,listId));
                if(!Objects.equals(old.get("name"),person%2==0?"Para revisitar [Demo]":"Próximas aventuras [Demo]")||
                    !Objects.equals(old.get("description"),DESCRIPTION)||!items.equals(expectedItems))continue;
                for(int theme=0;theme<1+person%3;theme++) {
                    String description="Seleção fictícia de "+user.getDisplayName()+" para demonstrar gostos e planos pessoais.";
                    long id;
                    if(theme==0) {
                        db.update("UPDATE game_lists SET name=?,description=? WHERE id=?",THEMES[person][theme],description,listId);
                        db.update("DELETE FROM game_list_items WHERE list_id=?",listId);id=listId;
                    } else id=((Number)lists.create(user.getId(),new ListRequest(THEMES[person][theme],description)).get("id")).longValue();
                    var candidates=theme==0?chosen:theme==1?rank(pool,person,97):rank(pool,person,157);
                    int count=Math.min(candidates.size(),2+(person+theme)%4);
                    for(var game:rank(candidates,person,71+theme*37).stream().limit(count).toList())
                        db.update("INSERT INTO game_list_items(list_id,game_id,added_at) VALUES (?,?,?)",id,game.getId(),java.sql.Timestamp.from(Instant.now()));
                }
                break;
            }
            var oldFeatured=db.queryForList("SELECT game_id FROM profile_featured_games WHERE user_id=? ORDER BY position",Long.class,user.getId());
            if(oldFeatured.equals(oldGames.stream().limit(4).map(Game::getId).toList())) {
                db.update("DELETE FROM profile_featured_games WHERE user_id=?",user.getId());
                var featured=new ArrayList<Long>();featured.addAll(favorites);
                for(var game:rank(chosen,person,211))if(!featured.contains(game.getId()))featured.add(game.getId());
                int position=0;for(long game:featured.stream().limit(2+person%3).toList())
                    db.update("INSERT INTO profile_featured_games(user_id,game_id,position) VALUES (?,?,?)",user.getId(),game,++position);
            }
            changed++;
        }
        db.update("UPDATE demo_seed_runs SET completed=TRUE WHERE name='community-collections-v3'");
        return changed;
    }
    private static List<Game> rank(List<Game> games,int person,int salt) {
        return games.stream().sorted(Comparator.<Game>comparingLong(g->score(g,person,salt)).thenComparing(Game::getId)).toList();
    }
    private static long score(Game game,int person,int salt) {
        // Seeded shuffle, different for each person and each collection; independent of DB IDs.
        return UUID.nameUUIDFromBytes((person+"|"+salt+"|"+key(game)).getBytes(java.nio.charset.StandardCharsets.UTF_8)).getMostSignificantBits();
    }
    private static String key(Game game){return game.getTitle().strip().toLowerCase(Locale.ROOT);}
}

package br.com.critplay.demo;

import br.com.critplay.catalog.*;
import br.com.critplay.collections.*;
import br.com.critplay.diary.*;
import br.com.critplay.profile.*;
import br.com.critplay.social.*;
import br.com.critplay.users.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;

/** Opt-in, atomic fixture. Never adopts or overwrites an existing account. */
@Service
public class DemoDataService {
    private final UserRepository users;
    private final GameRepository games;
    private final DiaryService diary;
    private final ListService lists;
    private final ProfileService profiles;
    private final SocialService social;
    private final CommunityService community;
    private final PasswordEncoder encoder;
    private final JdbcTemplate db;
    public DemoDataService(UserRepository users, GameRepository games, DiaryService diary, ListService lists,
            ProfileService profiles, SocialService social, CommunityService community, PasswordEncoder encoder, JdbcTemplate db) {
        this.users=users;this.games=games;this.diary=diary;this.lists=lists;this.profiles=profiles;
        this.social=social;this.community=community;this.encoder=encoder;this.db=db;
    }
    private record Persona(String username, String name, String bio) {}
    private static final List<Persona> PEOPLE=List.of(
        new Persona("demo_luna", "Luna Pixel", "Explorando mundos e colecionando histórias."),
        new Persona("demo_rafa", "Rafa Respawn", "Sempre volto para mais uma tentativa."),
        new Persona("demo_bia", "Bia Quest", "Missões secundárias são minha missão principal."),
        new Persona("demo_caio", "Caio Combo", "Gosto de ação, chefes e desafios."),
        new Persona("demo_nina", "Nina Cozy", "Um jogo tranquilo e um café, por favor."),
        new Persona("demo_theo", "Theo RPG", "Montando personagens e escolhendo diálogos."),
        new Persona("demo_maya", "Maya Indie", "Pequenos estúdios, grandes ideias."),
        new Persona("demo_leo", "Leo Platinum", "Só mais uma conquista antes de dormir."),
        new Persona("demo_iris", "Iris Lore", "Eu leio até a descrição dos itens."),
        new Persona("demo_davi", "Davi Speed", "Aprendendo caminhos e tentando melhorar meu tempo."),
        new Persona("demo_yuki", "Yuki Save", "Meu backlog já virou uma aventura própria."),
        new Persona("demo_alan", "Alan Retro", "Curioso por jogos de todas as gerações."),
        new Persona("demo_zoe", "Zoe Arcade", "Partidas curtas, diversão longa."),
        new Persona("demo_otto", "Otto Explorer", "Se tem um canto no mapa, eu vou olhar."),
        new Persona("demo_sol", "Sol New Game", "Começando minha biblioteca por aqui."),
        new Persona("demo_noah", "Noah Offline", "Meu cantinho reservado para jogar.")
    );
    // Game-specific observations; lookup uses the title, never assumes a particular database ID.
    private static final Map<String,String> OBSERVATIONS=Map.ofEntries(
        Map.entry("elden ring", "A exploração recompensa a curiosidade e os chefes exigem aprender com cada tentativa."),
        Map.entry("red dead redemption 2", "O mundo e os personagens impressionam, mas o ritmo pede tempo e paciência."),
        Map.entry("cyberpunk 2077", "Night City tem muita personalidade e gostei de experimentar diferentes estilos de combate."),
        Map.entry("hollow knight", "O mapa cheio de conexões e a atmosfera de Hallownest tornam cada descoberta especial."),
        Map.entry("hades", "As armas e os diálogos fazem cada tentativa de fuga ter algo novo para descobrir."),
        Map.entry("stardew valley", "Cuidar da fazenda e conhecer os moradores é uma ótima forma de jogar sem pressa."),
        Map.entry("baldur's gate 3", "As escolhas nos diálogos e as possibilidades do combate por turnos são o grande destaque."),
        Map.entry("the witcher 3: wild hunt", "As missões secundárias e as escolhas de Geralt me prenderam tanto quanto a história principal."),
        Map.entry("god of war", "A relação entre Kratos e Atreus dá peso à jornada, com um combate de impacto."),
        Map.entry("marvel's spider-man remastered", "Passear pelas teias em Nova York é divertido mesmo sem seguir nenhuma missão."),
        Map.entry("celeste", "Cada sala ensina um detalhe novo e a precisão dos saltos combina muito bem com a história."),
        Map.entry("the last of us part i", "A relação entre Joel e Ellie sustenta uma viagem tensa, com encontros que exigem atenção.")
    );
    private static final String[] OPINIONS={
        "Gostei, mas não entrou entre meus favoritos. Vale experimentar com calma.",
        "Tem boas ideias, embora algumas partes tenham me cansado. Fiquei com uma impressão positiva.",
        "Uma experiência muito boa. Pretendo voltar para descobrir o que deixei passar.",
        "Me prendeu do começo ao fim. Recomendo para quem gosta dessa proposta.",
        "Virou um dos meus favoritos. Terminei com vontade de conversar sobre ele."
    };

    @Transactional
    public boolean seed(String selectedTitles) {
        // Row lock also serializes two application instances sharing the same database.
        if(Boolean.TRUE.equals(db.queryForObject("SELECT completed FROM demo_seed_runs WHERE name='community-v1' FOR UPDATE",Boolean.class)))
            return false;
        var catalog=games.findAll().stream().sorted(Comparator.comparing(Game::getId)).toList();
        Set<String> requested=new LinkedHashSet<>();
        if(selectedTitles!=null&&!selectedTitles.isBlank()) {
            for(String title:selectedTitles.split(",",-1)) {
                if(title.isBlank())throw new IllegalStateException("DEMO_GAME_TITLES contém um título vazio.");
                requested.add(title.strip().toLowerCase(Locale.ROOT));
            }
            for(String title:requested)if(catalog.stream().noneMatch(g->key(g).equals(title)))
                throw new IllegalStateException("Jogo da demonstração não encontrado no catálogo: "+title);
        }
        var selected=catalog.stream().filter(g->requested.isEmpty()?OBSERVATIONS.containsKey(key(g)):requested.contains(key(g))).toList();
        if(selected.isEmpty())throw new IllegalStateException("Nenhum jogo disponível para a demonstração. Cadastre os jogos e defina DEMO_GAME_TITLES.");
        for(var person:PEOPLE)if(users.existsByUsername(person.username()))
            throw new IllegalStateException("Carga cancelada sem alterações: já existe a conta "+person.username()+". Não será sobrescrita.");
        var accounts=new ArrayList<AppUser>();
        for(var person:PEOPLE) {
            // No shared password or publicly usable fixture credentials; accounts stay visible to the community.
            var account=new AppUser(person.username(),person.name()+" [Demo]",encoder.encode(UUID.randomUUID().toString()),Role.USER);
            account.updateProfile(account.getDisplayName(),"Perfil fictício de demonstração. "+person.bio(),null,null);
            if(accounts.size()==15)account.updatePrivacy(ProfileVisibility.PRIVATE,true);
            accounts.add(users.saveAndFlush(account));
        }
        var reviews=new ArrayList<DiaryEntry>();
        for(int i=0;i<14;i++) {
            var account=accounts.get(i);
            int size=Math.min(8,selected.size());
            var featured=new ArrayList<Long>();
            var list=lists.create(account.getId(),new ListRequest(i%2==0?"Para revisitar [Demo]":"Próximas aventuras [Demo]","Seleção fictícia para demonstrar listas no CritPlay."));
            long listId=((Number)list.get("id")).longValue();
            for(int j=0;j<size;j++) {
                var game=selected.get((i+j)%selected.size());
                PlayStatus status=switch(j%8) {
                    case 5 -> PlayStatus.PLAYING;
                    case 6 -> PlayStatus.DROPPED;
                    case 7 -> PlayStatus.WANT_TO_PLAY;
                    default -> PlayStatus.COMPLETED;
                };
                Double rating=status==PlayStatus.WANT_TO_PLAY?null:DemoOpinions.rating(i,game);
                String review=j<5?DemoOpinions.review(i,game):"";
                LocalDate start=status==PlayStatus.WANT_TO_PLAY?null:LocalDate.now().minusDays(40+i+j);
                LocalDate finish=status==PlayStatus.COMPLETED?start.plusDays(10+j):null;
                var entry=diary.save(account.getId(),game.getId(),new DiaryRequest(status,rating,review,
                    rating!=null&&rating>=4.5,status==PlayStatus.WANT_TO_PLAY,start,finish));
                if(!review.isBlank())reviews.add(entry);
                if(j<4)featured.add(game.getId());
                if(j%2==0)lists.add(account.getId(),listId,game.getId());
            }
            profiles.updateGamer(account.getUsername(),new GamerProfileRequest(null,featured));
        }
        // Public demonstration accounts interact only with each other; existing users remain untouched.
        for(int i=0;i<14;i++) {
            for(int offset=1;offset<=3;offset++)social.follow(accounts.get(i).getUsername(),accounts.get((i+offset)%14).getUsername(),true);
            if(i%2==0) {
                social.friendship(accounts.get(i).getUsername(),accounts.get(i+1).getUsername(),"REQUEST");
                social.friendship(accounts.get(i+1).getUsername(),accounts.get(i).getUsername(),"ACCEPT");
            }
        }
        for(int i=0;i<reviews.size();i++)for(int offset=1;offset<=1+i%3;offset++) {
            var entry=reviews.get(i);
            int owner=0;while(!accounts.get(owner).getId().equals(entry.getUserId()))owner++;
            community.like(accounts.get((owner+offset)%14).getUsername(),entry.getId(),true);
        }
        db.update("UPDATE demo_seed_runs SET completed=TRUE WHERE name IN ('community-v1','community-variety-v2')");
        return true;
    }
    /** Upgrade only exact legacy fixture reviews; leave customized reviews/ratings and real users alone. */
    @Transactional
    public int diversifyLegacyReviews() {
        boolean seeded=Boolean.TRUE.equals(db.queryForObject("SELECT completed FROM demo_seed_runs WHERE name='community-v1' FOR UPDATE",Boolean.class));
        boolean done=Boolean.TRUE.equals(db.queryForObject("SELECT completed FROM demo_seed_runs WHERE name='community-variety-v2' FOR UPDATE",Boolean.class));
        if(!seeded||done)return 0;
        int changed=0;
        for(int i=0;i<14;i++) {
            var account=users.findByUsername(PEOPLE.get(i).username()).orElse(null);
            if(account==null)continue;
            for(var entry:diary.list(account.getId())) {
                if(entry.getRating()==null)continue;
                var game=games.findById(entry.getGameId()).orElse(null);
                if(game==null)continue;
                if(entry.getReview().isBlank()) {
                    int originalSlot=entry.getStatus()==PlayStatus.PLAYING?5:entry.getStatus()==PlayStatus.DROPPED?6:-1;
                    if(originalSlot>=0&&entry.getRating()==3.0+((i+originalSlot)%5)*0.5)
                        changed+=replaceLegacy(entry,DemoOpinions.rating(i,game),"");
                    continue;
                }
                for(int opinion=0;opinion<OPINIONS.length;opinion++) {
                    String legacy="[Resenha fictícia de demonstração] "+OBSERVATIONS.getOrDefault(key(game),
                        "Em "+game.getTitle()+", gostei de conhecer a proposta e explorar as possibilidades do jogo.")+" "+OPINIONS[opinion];
                    if(entry.getRating()==3.0+opinion*0.5&&entry.getReview().equals(legacy)) {
                        changed+=replaceLegacy(entry,DemoOpinions.rating(i,game),DemoOpinions.review(i,game));
                        break;
                    }
                }
            }
        }
        db.update("UPDATE demo_seed_runs SET completed=TRUE WHERE name='community-variety-v2'");
        return changed;
    }
    private int replaceLegacy(DiaryEntry entry,double rating,String review) {
        if(Objects.equals(entry.getRating(),rating)&&entry.getReview().equals(review))return 0;
        // Preserve all other fields and the historical timestamp; stale form submissions cannot overwrite this change.
        return db.update("UPDATE diary_entries SET rating=?,review=?,row_version=row_version+1 WHERE id=? AND rating=? AND review=? AND updated_at=?",
            rating,review,entry.getId(),entry.getRating(),entry.getReview(),java.sql.Timestamp.from(entry.getUpdatedAt()));
    }
    static List<String> demoNames(){return PEOPLE.stream().limit(14).map(Persona::username).toList();}
    static boolean originalTitle(Game game){return OBSERVATIONS.containsKey(key(game));}
    static boolean originalContent(int person,int slot,Game game,DiaryEntry entry,long version) {
        PlayStatus status=slot==5?PlayStatus.PLAYING:slot==6?PlayStatus.DROPPED:slot==7?PlayStatus.WANT_TO_PLAY:PlayStatus.COMPLETED;
        if(version>1||entry.getStatus()!=status||entry.isWishlist()!=(slot==7))return false;
        if(slot==7)return entry.getRating()==null&&entry.getReview().isBlank()&&!entry.isFavorite()&&entry.getStartDate()==null&&entry.getFinishDate()==null;
        if(entry.getStartDate()==null)return false;
        var expected=entry.getUpdatedAt().atZone(java.time.ZoneOffset.UTC).toLocalDate().minusDays(40+person+slot);
        if(Math.abs(java.time.temporal.ChronoUnit.DAYS.between(expected,entry.getStartDate()))>1)return false;
        if(!Objects.equals(entry.getFinishDate(),slot<5?entry.getStartDate().plusDays(10+slot):null))return false;
        double oldRating=3.0+((person+slot)%5)*0.5;
        boolean oldFavorite=oldRating>=4.5;
        double newRating=DemoOpinions.rating(person,game);
        boolean modern=Objects.equals(entry.getRating(),newRating)&&entry.getReview().equals(slot<5?DemoOpinions.review(person,game):"");
        if(modern&&entry.isFavorite()==(version==0?newRating>=4.5:oldFavorite))return true;
        String oldReview=slot<5?"[Resenha fictícia de demonstração] "+OBSERVATIONS.getOrDefault(key(game),
            "Em "+game.getTitle()+", gostei de conhecer a proposta e explorar as possibilidades do jogo.")+" "+OPINIONS[(person+slot)%5]:"";
        return version==0&&Objects.equals(entry.getRating(),oldRating)&&entry.getReview().equals(oldReview)&&entry.isFavorite()==oldFavorite;
    }
    private static String key(Game game){return game.getTitle().strip().toLowerCase(Locale.ROOT);}
}

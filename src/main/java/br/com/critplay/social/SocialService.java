package br.com.critplay.social;

import br.com.critplay.users.*;
import br.com.critplay.profile.ProfileService;
import br.com.critplay.shared.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class SocialService {
    private final br.com.critplay.collections.ListService lists;
    private final br.com.critplay.diary.DiaryService diary;
    private final br.com.critplay.catalog.CatalogService catalog;
    private final BlockService blocks;
    private final UserService users; private final UserRepository repository;
    private final JdbcTemplate db; private final ProfileService profiles;
    public SocialService(UserService users, UserRepository repository, JdbcTemplate db, ProfileService profiles,BlockService blocks,br.com.critplay.collections.ListService lists,br.com.critplay.diary.DiaryService diary,br.com.critplay.catalog.CatalogService catalog) {
        this.lists=lists;this.diary=diary;this.catalog=catalog;this.users=users;this.repository=repository;this.db=db;this.profiles=profiles;this.blocks=blocks;
    }
    public Map<String,Object> identity(AppUser user) {
        var value=new LinkedHashMap<String,Object>();
        value.put("id",user.getId());value.put("username",user.getUsername());value.put("displayName",user.getDisplayName());
        value.put("avatarUrl",user.getAvatarUrl());value.put("visibility",user.getVisibility());return value;
    }
    public List<Map<String,Object>> search(String viewer, String query) {
        var viewerAccount=users.require(viewer);String q=query.strip().toLowerCase(Locale.ROOT);
        if(q.length()<2)return List.of();
        if(q.length()>80)throw new BadRequestException("Busque por até 80 caracteres.");
        // Escapa os curingas para a pesquisa representar o texto digitado.
        String term="%"+q.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        return db.query("SELECT id FROM app_users WHERE enabled=TRUE AND discoverable=TRUE AND (LOWER(username) LIKE ? ESCAPE '!' OR LOWER(display_name) LIKE ? ESCAPE '!') ORDER BY username LIMIT 30",
            (rs,row)->identity(repository.findById(rs.getLong(1)).orElseThrow()),term,term).stream().filter(card->!blocks.blocked(viewerAccount.getId(),((Number)card.get("id")).longValue())).toList();
    }
    public Map<String,Object> profile(String viewer, String targetName) {
        var access=access(viewer,targetName);var target=access.target();var actor=access.actor();boolean own=access.own(),visible=access.visible();
        var result=new LinkedHashMap<String,Object>();result.put("user",identity(target));result.put("visible",visible);
        result.put("relationship",actor==null?Map.of("following",false,"friendship","NONE"):relationship(actor.getId(),target.getId()));
        if(visible) {
            var publicUser=new LinkedHashMap<String,Object>(identity(target));publicUser.put("bio",target.getBio());publicUser.put("bannerUrl",target.getBannerUrl());publicUser.put("featuredGames",profiles.featuredGames(target.getId()));result.put("user",publicUser);
            var details=profiles.get(target.getUsername());result.put("stats",details.get("stats"));
            // Explicit allowlist: never serialize diary entities into public collections.
            var privacy=target.collectionPrivacy();var collections=new LinkedHashMap<String,Object>();
            var entries=diary.list(target.getId());
            for(String section:List.of("library","favorites","wishlist")) {
                if(own||privacy.get(section))collections.put(section,entries.stream()
                    .filter(e->section.equals("library")||section.equals("favorites")&&e.isFavorite()||section.equals("wishlist")&&e.isWishlist())
                    .map(e->{var item=new LinkedHashMap<String,Object>();item.put("game",catalog.find(e.getGameId()));
                        if(section.equals("library")){item.put("status",e.getStatus());if(!e.isReviewHidden())item.put("rating",e.getRating());}
                        return item;}).toList());
            }
            if(own||privacy.get("lists"))collections.put("lists",lists.all(target.getId()));
            result.put("collections",collections);
            // Reviews follow profile visibility and moderation independently of collections.
            var reviews=new ArrayList<Map<String,Object>>();
            for(var raw:(List<?>)details.get("reviews")) {
                var row=(Map<?,?>)raw;var entry=(br.com.critplay.diary.DiaryEntry)row.get("entry");if(entry.isReviewHidden())continue;
                var review=new LinkedHashMap<String,Object>();review.put("game",row.get("game"));review.put("review",entry.getReview());
                review.put("entryId",entry.getId());review.put("rating",entry.getRating());review.put("updatedAt",entry.getUpdatedAt());
                review.put("likeCount",db.queryForObject("SELECT COUNT(*) FROM review_likes l JOIN app_users u ON u.id=l.user_id WHERE l.entry_id=? AND u.enabled=TRUE",Long.class,entry.getId()));
                review.put("likedByMe",actor!=null&&db.queryForObject("SELECT COUNT(*) FROM review_likes WHERE entry_id=? AND user_id=?",Long.class,entry.getId(),actor.getId())>0);reviews.add(review);
            }
            result.put("reviews",reviews);var sharedStats=new LinkedHashMap<>((Map<String,Object>)details.get("stats"));sharedStats.put("reviews",reviews.size());
            if(!own){if(!privacy.get("library")){sharedStats.remove("library");sharedStats.remove("completed");}
                if(!privacy.get("favorites"))sharedStats.remove("favorites");if(!privacy.get("wishlist"))sharedStats.remove("wishlist");}
            result.put("stats",sharedStats);
            result.put("social",socialCounts(actor==null?-1:actor.getId(),target.getId()));
        }
        return result;
    }
    private record Access(AppUser target,AppUser actor,boolean own,boolean visible) {}
    private Access access(String viewer,String targetName) {
        var target=users.require(targetName);var actor=viewer==null?null:users.require(viewer);
        if(actor!=null&&blocks.blocked(actor.getId(),target.getId()))throw new NotFoundException("Perfil não encontrado.");
        if(!target.isEnabled())throw new NotFoundException("Perfil não encontrado.");
        boolean own=actor!=null&&actor.getId().equals(target.getId());
        boolean friend=actor!=null&&friends(actor.getId(),target.getId());
        if(!own&&!target.isDiscoverable()&&!friend)throw new NotFoundException("Perfil não encontrado.");
        boolean visible=own||target.getVisibility()==ProfileVisibility.PUBLIC||target.getVisibility()==ProfileVisibility.FRIENDS&&friend;
        return new Access(target,actor,own,visible);
    }
    private static final String MEMBER = """
        u.enabled=TRUE AND NOT EXISTS (SELECT 1 FROM user_blocks b
          WHERE (b.blocker_id=? AND b.blocked_id=u.id) OR (b.blocked_id=? AND b.blocker_id=u.id))
        AND (?=? OR u.discoverable=TRUE OR u.id=? OR EXISTS (SELECT 1 FROM friendships friend
          WHERE friend.status='ACCEPTED' AND ((friend.first_id=? AND friend.second_id=u.id) OR (friend.second_id=? AND friend.first_id=u.id))))
        """;
    private String followQuery(boolean followers) {
        return " FROM user_follows f JOIN app_users u ON u.id=f."+(followers?"follower_id":"followed_id")+
            " WHERE f."+(followers?"followed_id":"follower_id")+"=? AND "+MEMBER;
    }
    private Object[] followArgs(long viewer,long owner) {return new Object[]{owner,viewer,viewer,viewer,owner,viewer,viewer,viewer};}
    private long followCount(long viewer,long owner,boolean followers) {return count("SELECT COUNT(*)"+followQuery(followers),followArgs(viewer,owner));}
    private Map<String,Object> socialCounts(long viewer,long owner) {
        return Map.of("followers",followCount(viewer,owner,true),"following",followCount(viewer,owner,false),
            "friends",count("SELECT COUNT(*) FROM friendships WHERE status='ACCEPTED' AND (first_id=? OR second_id=?)",owner,owner));
    }
    public Map<String,Object> ownSocial(String viewer) {long id=users.require(viewer).getId();return socialCounts(id,id);}
    public Map<String,Object> followPage(String viewer,String targetName,boolean followers,long after) {
        if(after<0)throw new BadRequestException("Página inválida.");var access=access(viewer,targetName);
        if(!access.visible())throw new NotFoundException("Lista não disponível.");
        long actor=access.actor()==null?-1:access.actor().getId(),owner=access.target().getId();
        var args=new ArrayList<Object>(Arrays.asList(followArgs(actor,owner)));args.add(after);
        var rows=db.query("SELECT u.id,u.username,u.display_name,u.avatar_url,u.visibility"+followQuery(followers)+" AND u.id>? ORDER BY u.id LIMIT 31",(rs,row)->{
            var person=new LinkedHashMap<String,Object>();person.put("id",rs.getLong("id"));person.put("username",rs.getString("username"));person.put("displayName",rs.getString("display_name"));
            person.put("avatarUrl",rs.getString("avatar_url"));person.put("visibility",rs.getString("visibility"));return person;
        },args.toArray());
        boolean more=rows.size()>30;var items=rows.subList(0,Math.min(30,rows.size()));
        var page=new LinkedHashMap<String,Object>();page.put("items",items);page.put("total",followCount(actor,owner,followers));
        page.put("nextCursor",more?items.get(items.size()-1).get("id"):null);return page;
    }
    private long count(String sql,Object... args){return db.queryForObject(sql,Long.class,args);}
    public boolean friends(Long a,Long b) {return count("SELECT COUNT(*) FROM friendships WHERE first_id=? AND second_id=? AND status='ACCEPTED'",Math.min(a,b),Math.max(a,b))>0;}
    private Map<String,Object> relationship(Long a,Long b) {
        var rows=db.queryForList("SELECT requester_id,status FROM friendships WHERE first_id=? AND second_id=?",Math.min(a,b),Math.max(a,b));
        String friendship="NONE";
        if(!rows.isEmpty()){var row=rows.get(0);friendship="ACCEPTED".equals(row.get("status"))?"FRIENDS":((Number)row.get("requester_id")).longValue()==a?"OUTGOING":"INCOMING";}
        return Map.of("following",count("SELECT COUNT(*) FROM user_follows WHERE follower_id=? AND followed_id=?",a,b)>0,"friendship",friendship);
    }
    public Map<String,Object> connections(String viewer) {
        var user=users.require(viewer);Long id=user.getId();
        var following=db.query("SELECT followed_id FROM user_follows WHERE follower_id=? ORDER BY followed_id",(rs,row)->identity(repository.findById(rs.getLong(1)).orElseThrow()),id);
        var followers=db.query("SELECT follower_id FROM user_follows WHERE followed_id=? ORDER BY follower_id",(rs,row)->identity(repository.findById(rs.getLong(1)).orElseThrow()),id);
        var friendships=db.queryForList("SELECT first_id,second_id,requester_id,status FROM friendships WHERE first_id=? OR second_id=? ORDER BY first_id,second_id",id,id);
        var friends=new ArrayList<Map<String,Object>>();var incoming=new ArrayList<Map<String,Object>>();var outgoing=new ArrayList<Map<String,Object>>();
        for(var row:friendships){long first=((Number)row.get("first_id")).longValue(),second=((Number)row.get("second_id")).longValue();
            var card=identity(repository.findById(first==id?second:first).orElseThrow());
            if("ACCEPTED".equals(row.get("status")))friends.add(card);
            else if(((Number)row.get("requester_id")).longValue()==id)outgoing.add(card);else incoming.add(card);
        }
        return Map.of("following",following,"followers",followers,"friends",friends,"incoming",incoming,"outgoing",outgoing);
    }
    @Transactional public Map<String,Object> privacy(String viewer,ProfileVisibility visibility,boolean discoverable) {
        return privacy(viewer,visibility,discoverable,null,null,null,null);
    }
    @Transactional public Map<String,Object> privacy(String viewer,ProfileVisibility visibility,boolean discoverable,
        Boolean library,Boolean favorites,Boolean wishlist,Boolean lists) {
        var user=users.require(viewer);user.updatePrivacy(visibility,discoverable);user.updateCollections(library,favorites,wishlist,lists);repository.save(user);
        return Map.of("visibility",user.getVisibility(),"discoverable",user.isDiscoverable(),"collectionPrivacy",user.collectionPrivacy());
    }
    // Trava o par em ordem consistente: evita duplicidade e deadlock entre pedidos recíprocos.
    private long[] pair(String viewer,String targetName) {
        var actor=users.require(viewer);var target=users.require(targetName);
        if(actor.getId().equals(target.getId()))throw new BadRequestException("Escolha outra pessoa.");
        long a=actor.getId(),b=target.getId();
        db.queryForList("SELECT id FROM app_users WHERE id IN (?,?) ORDER BY id FOR UPDATE",Math.min(a,b),Math.max(a,b));
        if(blocks.blocked(actor.getId(),target.getId()))throw new NotFoundException("Perfil não encontrado.");
        if(!target.isDiscoverable()&&!friends(a,b)&&count("SELECT COUNT(*) FROM user_follows WHERE (follower_id=? AND followed_id=?) OR (follower_id=? AND followed_id=?)",a,b,b,a)==0&&count("SELECT COUNT(*) FROM friendships WHERE first_id=? AND second_id=?",Math.min(a,b),Math.max(a,b))==0)
            throw new NotFoundException("Perfil não encontrado.");
        return new long[]{a,b};
    }
    @Transactional public Map<String,Object> follow(String viewer,String targetName,boolean following) {
        var pair=pair(viewer,targetName);long a=pair[0],b=pair[1];
        if(following&&!db.queryForObject("SELECT enabled FROM app_users WHERE id=?",Boolean.class,b))throw new NotFoundException("Perfil não encontrado.");
        if(following&&count("SELECT COUNT(*) FROM user_follows WHERE follower_id=? AND followed_id=?",a,b)==0)
            db.update("INSERT INTO user_follows(follower_id,followed_id) VALUES (?,?)",a,b);
        if(!following)db.update("DELETE FROM user_follows WHERE follower_id=? AND followed_id=?",a,b);
        return relationship(a,b);
    }
    @Transactional public Map<String,Object> friendship(String viewer,String targetName,String action) {
        var pair=pair(viewer,targetName);long a=pair[0],b=pair[1],first=Math.min(a,b),second=Math.max(a,b);
        if((action.equals("REQUEST")||action.equals("ACCEPT"))&&!db.queryForObject("SELECT enabled FROM app_users WHERE id=?",Boolean.class,b))throw new NotFoundException("Perfil não encontrado.");
        String current=(String)relationship(a,b).get("friendship");
        switch(action) {
            case "REQUEST" -> {if(current.equals("NONE"))db.update("INSERT INTO friendships(first_id,second_id,requester_id,status) VALUES (?,?,?,'PENDING')",first,second,a);
                else if(current.equals("INCOMING"))throw new BadRequestException("Você já recebeu um pedido desta pessoa. Aceite ou recuse.");}
            case "ACCEPT" -> {if(!current.equals("INCOMING"))throw new BadRequestException("Você não tem esse pedido para aceitar.");
                db.update("UPDATE friendships SET status='ACCEPTED' WHERE first_id=? AND second_id=?",first,second);}
            case "REJECT" -> {if(!current.equals("INCOMING"))throw new BadRequestException("Você não tem esse pedido para recusar.");db.update("DELETE FROM friendships WHERE first_id=? AND second_id=?",first,second);}
            case "CANCEL" -> {if(!current.equals("OUTGOING"))throw new BadRequestException("Você não tem esse pedido para cancelar.");db.update("DELETE FROM friendships WHERE first_id=? AND second_id=?",first,second);}
            case "REMOVE" -> {if(!current.equals("FRIENDS"))throw new BadRequestException("Essa amizade não existe.");db.update("DELETE FROM friendships WHERE first_id=? AND second_id=?",first,second);}
            default -> throw new BadRequestException("Ação de amizade inválida.");
        }
        return relationship(a,b);
    }
}

package br.com.critplay.collections;
import br.com.critplay.catalog.CatalogService;
import br.com.critplay.shared.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class ListService {
    private final GameListRepository lists;private final GameListItemRepository items;private final CatalogService catalog;
    public ListService(GameListRepository lists,GameListItemRepository items,CatalogService catalog) {this.lists=lists;this.items=items;this.catalog=catalog;}
    public List<Map<String,Object>> all(Long owner) {return lists.findByUserIdOrderByCreatedAtDesc(owner).stream().map(this::view).toList();}
    public Map<String,Object> get(Long owner,Long id) {return view(require(owner,id));}
    private GameList require(Long owner,Long id) {return lists.findByIdAndUserId(id,owner).orElseThrow(()->new NotFoundException("Lista não encontrada."));}
    private Map<String,Object> view(GameList list) {return Map.of("id",list.getId(),"name",list.getName(),"description",list.getDescription(),
        "createdAt",list.getCreatedAt(),"games",items.findByListIdOrderByAddedAtAsc(list.getId()).stream().map(i->catalog.find(i.getGameId())).toList());}
    @Transactional public Map<String,Object> create(Long owner,ListRequest body) {return view(lists.save(new GameList(owner,body.name(),body.description())));}
    @Transactional public Map<String,Object> update(Long owner,Long id,ListRequest body) {var list=require(owner,id);list.update(body.name(),body.description());return view(lists.save(list));}
    @Transactional public void delete(Long owner,Long id) {var list=require(owner,id);items.deleteByListId(id);lists.delete(list);}
    @Transactional public Map<String,Object> add(Long owner,Long id,Long gameId) {
        var list=require(owner,id);catalog.find(gameId);
        if(!items.existsByListIdAndGameId(id,gameId)) items.save(new GameListItem(id,gameId));
        return view(list);
    }
    @Transactional public void remove(Long owner,Long id,Long gameId) {require(owner,id);items.deleteByListIdAndGameId(id,gameId);}
}

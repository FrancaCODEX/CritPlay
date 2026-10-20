package br.com.critplay.profile;

import br.com.critplay.catalog.CatalogService;
import br.com.critplay.catalog.Game;
import br.com.critplay.collections.ListService;
import br.com.critplay.diary.DiaryEntry;
import br.com.critplay.diary.DiaryService;
import br.com.critplay.social.PersonIdentity;
import br.com.critplay.social.ReviewLikesRepository;
import br.com.critplay.social.SocialAccessService;
import br.com.critplay.social.SocialGraphRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Explicit public projection: owner-only profile data never enters this response. */
@Service
public class PublicProfileService {
    private final SocialAccessService access;
    private final SocialGraphRepository graph;
    private final ProfileService profiles;
    private final DiaryService diary;
    private final CatalogService catalog;
    private final ListService lists;
    private final ReviewLikesRepository likes;

    public PublicProfileService(SocialAccessService access, SocialGraphRepository graph, ProfileService profiles,
                                DiaryService diary, CatalogService catalog, ListService lists, ReviewLikesRepository likes) {
        this.access = access;
        this.graph = graph;
        this.profiles = profiles;
        this.diary = diary;
        this.catalog = catalog;
        this.lists = lists;
        this.likes = likes;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(String viewer, String targetName) {
        var permission = access.profile(viewer, targetName);
        var target = permission.target();
        var user = PersonIdentity.from(target).toMap();
        var result = new LinkedHashMap<String, Object>();
        result.put("user", user);
        result.put("visible", permission.visible());
        result.put("relationship", permission.actor() == null ? Map.of("following", false, "friendship", "NONE")
            : graph.relationship(permission.viewerId(), target.getId()).toMap());
        if (!permission.visible()) {
            return result;
        }

        user.put("bio", target.getBio());
        user.put("bannerUrl", target.getBannerUrl());
        user.put("featuredGames", profiles.featuredGames(target.getId()));
        var entries = diary.list(target.getId());
        var games = new HashMap<Long, Game>();
        Function<Long, Game> findGame = id -> games.computeIfAbsent(id, catalog::find);
        var privacy = target.collectionPrivacy();
        result.put("collections", collections(target.getId(), entries, privacy, permission.own(), findGame));
        var reviews = reviews(entries, permission.actor() == null ? null : permission.viewerId(), findGame);
        result.put("reviews", reviews);
        result.put("stats", sharedStats(entries, reviews.size(), privacy, permission.own()));
        result.put("social", graph.counts(permission.viewerId(), target.getId()));
        return result;
    }

    private Map<String, Object> collections(long owner, List<DiaryEntry> entries, Map<String, Boolean> privacy,
                                             boolean own, Function<Long, Game> game) {
        var result = new LinkedHashMap<String, Object>();
        if (own || privacy.get("library")) {
            result.put("library", entries.stream().map(entry -> libraryItem(entry, game)).toList());
        }
        if (own || privacy.get("favorites")) {
            result.put("favorites", entries.stream().filter(DiaryEntry::isFavorite)
                .map(entry -> Map.of("game", game.apply(entry.getGameId()))).toList());
        }
        if (own || privacy.get("wishlist")) {
            result.put("wishlist", entries.stream().filter(DiaryEntry::isWishlist)
                .map(entry -> Map.of("game", game.apply(entry.getGameId()))).toList());
        }
        if (own || privacy.get("lists")) {
            result.put("lists", lists.all(owner));
        }
        return result;
    }

    private Map<String, Object> libraryItem(DiaryEntry entry, Function<Long, Game> game) {
        var item = new LinkedHashMap<String, Object>();
        item.put("game", game.apply(entry.getGameId()));
        item.put("status", entry.getStatus());
        if (!entry.isReviewHidden()) {
            item.put("rating", entry.getRating());
        }
        return item;
    }

    private List<Map<String, Object>> reviews(List<DiaryEntry> entries, Long viewer, Function<Long, Game> game) {
        var visible = entries.stream().filter(ProfileStats::hasReview).filter(entry -> !entry.isReviewHidden())
            .sorted(Comparator.comparing(DiaryEntry::getUpdatedAt).reversed()).toList();
        var summaries = likes.summaries(visible.stream().map(DiaryEntry::getId).toList(), viewer);
        return visible.stream().<Map<String, Object>>map(entry -> {
            var summary = summaries.getOrDefault(entry.getId(), ReviewLikesRepository.NONE);
            var review = new LinkedHashMap<String, Object>();
            review.put("game", game.apply(entry.getGameId()));
            review.put("review", entry.getReview());
            review.put("entryId", entry.getId());
            review.put("rating", entry.getRating());
            review.put("updatedAt", entry.getUpdatedAt());
            review.put("likeCount", summary.count());
            review.put("likedByMe", summary.likedByMe());
            return review;
        }).toList();
    }

    private Map<String, Object> sharedStats(List<DiaryEntry> entries, int reviewCount,
                                            Map<String, Boolean> privacy, boolean own) {
        var stats = ProfileStats.from(entries).toMap();
        stats.put("reviews", reviewCount);
        if (!own) {
            if (!privacy.get("library")) {
                stats.remove("library");
                stats.remove("completed");
            }
            if (!privacy.get("favorites")) stats.remove("favorites");
            if (!privacy.get("wishlist")) stats.remove("wishlist");
        }
        return stats;
    }
}

package br.com.critplay.profile;

import br.com.critplay.diary.DiaryEntry;
import br.com.critplay.diary.PlayStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared statistics for the owner's profile and its public projection. */
public record ProfileStats(int library, long completed, long favorites, long wishlist, int reviews) {
    public static ProfileStats from(List<DiaryEntry> entries) {
        long completed = 0;
        long favorites = 0;
        long wishlist = 0;
        int reviews = 0;
        for (var entry : entries) {
            if (entry.getStatus() == PlayStatus.COMPLETED) completed++;
            if (entry.isFavorite()) favorites++;
            if (entry.isWishlist()) wishlist++;
            if (hasReview(entry)) reviews++;
        }
        return new ProfileStats(entries.size(), completed, favorites, wishlist, reviews);
    }

    public Map<String, Object> toMap() {
        var stats = new LinkedHashMap<String, Object>();
        stats.put("library", library);
        stats.put("completed", completed);
        stats.put("favorites", favorites);
        stats.put("wishlist", wishlist);
        stats.put("reviews", reviews);
        return stats;
    }

    static boolean hasReview(DiaryEntry entry) {
        return entry.getReview() != null && !entry.getReview().isBlank();
    }
}

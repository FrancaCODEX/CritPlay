package br.com.critplay.profile;

import br.com.critplay.catalog.CatalogService;
import br.com.critplay.catalog.Game;
import br.com.critplay.diary.DiaryEntry;
import br.com.critplay.diary.DiaryService;
import br.com.critplay.media.ImageService;
import br.com.critplay.shared.BadRequestException;
import br.com.critplay.users.AppUser;
import br.com.critplay.users.PasswordResetRepository;
import br.com.critplay.users.UserRepository;
import br.com.critplay.users.UserService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class ProfileService {
    private final UserService users;
    private final UserRepository repository;
    private final PasswordEncoder encoder;
    private final DiaryService diary;
    private final CatalogService catalog;
    private final ImageService images;
    private final PasswordResetRepository resets;
    private final JdbcTemplate db;

    public ProfileService(UserService users, UserRepository repository, PasswordEncoder encoder, DiaryService diary,
                          CatalogService catalog, ImageService images, PasswordResetRepository resets, JdbcTemplate db) {
        this.users = users;
        this.repository = repository;
        this.encoder = encoder;
        this.diary = diary;
        this.catalog = catalog;
        this.images = images;
        this.resets = resets;
        this.db = db;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(String username) {
        var user = users.require(username);
        var entries = diary.list(user.getId());
        var reviews = entries.stream().filter(ProfileStats::hasReview)
            .sorted(Comparator.comparing(DiaryEntry::getUpdatedAt).reversed())
            .map(entry -> Map.of("game", catalog.find(entry.getGameId()), "entry", entry)).toList();
        return Map.of("user", ownerIdentity(user), "reviews", reviews, "stats", ProfileStats.from(entries).toMap());
    }

    private Map<String, Object> ownerIdentity(AppUser user) {
        var profile = new LinkedHashMap<String, Object>();
        profile.put("id", user.getId());
        profile.put("username", user.getUsername());
        profile.put("displayName", user.getDisplayName());
        profile.put("bannerUrl", user.getBannerUrl());
        profile.put("featuredGames", featuredGames(user.getId()));
        profile.put("bio", user.getBio());
        profile.put("email", user.getEmail());
        profile.put("avatarUrl", user.getAvatarUrl());
        profile.put("collectionPrivacy", user.collectionPrivacy());
        profile.put("visibility", user.getVisibility());
        profile.put("discoverable", user.isDiscoverable());
        profile.put("role", user.getRole());
        profile.put("createdAt", user.getCreatedAt());
        return profile;
    }

    @Transactional(readOnly = true)
    public List<Game> featuredGames(Long userId) {
        return db.query("SELECT game_id FROM profile_featured_games WHERE user_id=? ORDER BY position",
            (row, index) -> catalog.find(row.getLong(1)), userId);
    }

    @Transactional
    public void updateGamer(String username, GamerProfileRequest request) {
        images.lockReferences();
        var user = users.require(username);
        if (new HashSet<>(request.featuredGameIds()).size() != request.featuredGameIds().size()) {
            throw new BadRequestException("Escolha jogos diferentes para os destaques.");
        }
        for (long game : request.featuredGameIds()) catalog.find(game);
        String banner = Objects.equals(request.bannerUrl(), user.getBannerUrl())
            ? user.getBannerUrl() : images.ownedUrl(user.getId(), request.bannerUrl());
        user.updateBanner(banner);
        repository.saveAndFlush(user);
        db.update("DELETE FROM profile_featured_games WHERE user_id=?", user.getId());
        int position = 0;
        for (long game : request.featuredGameIds()) {
            db.update("INSERT INTO profile_featured_games(user_id,game_id,position) VALUES (?,?,?)",
                user.getId(), game, ++position);
        }
    }

    @Transactional
    public void update(String username, ProfileUpdateRequest request) {
        images.lockReferences();
        var user = users.require(username);
        String email = normalizeEmail(request.email());
        if (!Objects.equals(email, user.getEmail())) {
            checkPassword(user, request.currentPassword());
            resets.revokeUserTokens(user.getId());
        }
        String avatar = Objects.equals(request.avatarUrl(), user.getAvatarUrl())
            ? user.getAvatarUrl() : images.ownedUrl(user.getId(), request.avatarUrl());
        user.updateProfile(request.displayName().strip(), request.bio() == null ? "" : request.bio().strip(), email, avatar);
        repository.save(user);
    }

    @Transactional
    public void changePassword(String username, PasswordChangeRequest request) {
        var user = users.require(username);
        checkPassword(user, request.currentPassword());
        UserService.validatePassword(request.newPassword());
        user.changePassword(encoder.encode(request.newPassword()));
        repository.save(user);
        resets.revokeUserTokens(user.getId());
    }

    private void checkPassword(AppUser user, String password) {
        if (password == null || password.getBytes(StandardCharsets.UTF_8).length > 72
            || !encoder.matches(password, user.getPasswordHash())) {
            throw new BadRequestException("Senha atual incorreta.");
        }
    }

    public static String normalizeEmail(String value) {
        return value == null || value.isBlank() ? null : value.strip().toLowerCase(Locale.ROOT);
    }
}

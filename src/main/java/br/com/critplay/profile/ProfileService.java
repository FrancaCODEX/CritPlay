package br.com.critplay.profile;

import br.com.critplay.users.*;
import br.com.critplay.diary.*;
import br.com.critplay.catalog.CatalogService;
import br.com.critplay.media.ImageService;
import br.com.critplay.shared.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class ProfileService {
    private final UserService users;private final UserRepository repository;private final PasswordEncoder encoder;
    private final DiaryService diary;private final CatalogService catalog;private final ImageService images;
    private final PasswordResetRepository resets;
    public ProfileService(UserService users,UserRepository repository,PasswordEncoder encoder,DiaryService diary,
        CatalogService catalog,ImageService images,PasswordResetRepository resets) {
        this.users=users;this.repository=repository;this.encoder=encoder;this.diary=diary;this.catalog=catalog;this.images=images;this.resets=resets;
    }
    public Map<String,Object> get(String username) {
        var user=users.require(username);var entries=diary.list(user.getId());
        var profile=new LinkedHashMap<String,Object>();
        profile.put("id",user.getId());profile.put("username",user.getUsername());profile.put("displayName",user.getDisplayName());
        profile.put("bio",user.getBio());profile.put("email",user.getEmail());profile.put("avatarUrl",user.getAvatarUrl());
        profile.put("visibility",user.getVisibility());profile.put("discoverable",user.isDiscoverable());
        profile.put("role",user.getRole());profile.put("createdAt",user.getCreatedAt());
        var reviews=entries.stream().filter(e->e.getReview()!=null&&!e.getReview().isBlank())
            .sorted(Comparator.comparing(DiaryEntry::getUpdatedAt).reversed())
            .map(e->Map.of("game",catalog.find(e.getGameId()),"entry",e)).toList();
        return Map.of("user",profile,"reviews",reviews,"stats",Map.of("library",entries.size(),
            "completed",entries.stream().filter(e->e.getStatus()==PlayStatus.COMPLETED).count(),
            "favorites",entries.stream().filter(DiaryEntry::isFavorite).count(),
            "wishlist",entries.stream().filter(DiaryEntry::isWishlist).count(),"reviews",reviews.size()));
    }
    @Transactional public void update(String username,ProfileUpdateRequest request) {
        images.lockReferences();
        var user=users.require(username);
        String email=normalizeEmail(request.email());
        if(!Objects.equals(email,user.getEmail())) {
            checkPassword(user,request.currentPassword());resets.revokeUserTokens(user.getId());
        }
        String avatar=Objects.equals(request.avatarUrl(),user.getAvatarUrl())?user.getAvatarUrl():images.ownedUrl(user.getId(),request.avatarUrl());
        user.updateProfile(request.displayName().strip(),request.bio()==null?"":request.bio().strip(),email,avatar);
        repository.save(user);
    }
    @Transactional public void changePassword(String username,PasswordChangeRequest request) {
        var user=users.require(username);checkPassword(user,request.currentPassword());UserService.validatePassword(request.newPassword());
        user.changePassword(encoder.encode(request.newPassword()));repository.save(user);resets.revokeUserTokens(user.getId());
    }
    private void checkPassword(AppUser user,String password) {
        if(password==null||password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72||!encoder.matches(password,user.getPasswordHash()))
            throw new BadRequestException("Senha atual incorreta.");
    }
    public static String normalizeEmail(String value) { return value==null||value.isBlank()?null:value.strip().toLowerCase(Locale.ROOT); }
}

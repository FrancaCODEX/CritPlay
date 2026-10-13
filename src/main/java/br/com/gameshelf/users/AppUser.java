package br.com.gameshelf.users;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "app_users")
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 30) private String username;
    @Column(nullable = false, length = 80) private String displayName;
    @Column(nullable = false) private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Role role;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false, length = 500) private String bio = "";
    @Column(unique = true, length = 254) private String email;
    private String avatarUrl;
    @Column(nullable = false) private int sessionVersion;
    protected AppUser() {}
    public AppUser(String username, String displayName, String passwordHash, Role role) {
        this.username = username; this.displayName = displayName;
        this.passwordHash = passwordHash; this.role = role; this.createdAt = Instant.now();
    }
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public String getBio() { return bio; }
    public String getEmail() { return email; }
    public String getAvatarUrl() { return avatarUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public int getSessionVersion() { return sessionVersion; }
    public void updateProfile(String displayName, String bio, String email, String avatarUrl) {
        this.displayName = displayName; this.bio = bio; this.email = email; this.avatarUrl = avatarUrl;
    }
    public void changePassword(String hash) { this.passwordHash = hash; this.sessionVersion++; }
}

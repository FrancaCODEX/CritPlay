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
}

package br.com.gameshelf.security;

import br.com.gameshelf.users.AppUser;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import java.util.List;

public class AccountPrincipal extends User {
    private final Long accountId;
    private final int sessionVersion;
    public AccountPrincipal(AppUser user) {
        super(user.getUsername(), user.getPasswordHash(), List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
        this.accountId = user.getId(); this.sessionVersion = user.getSessionVersion();
    }
    public Long getAccountId() { return accountId; }
    public int getSessionVersion() { return sessionVersion; }
}

package br.com.critplay.users;

import br.com.critplay.diary.DiaryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(20)
public class AdminBootstrap implements CommandLineRunner {
    private final UserRepository users;
    private final DiaryRepository diary;
    private final PasswordEncoder encoder;
    private final String username, password, displayName;
    public AdminBootstrap(UserRepository users, DiaryRepository diary, PasswordEncoder encoder,
        @Value("${app.admin.username}") String username, @Value("${app.admin.password}") String password,
        @Value("${app.admin.display-name}") String displayName) {
        this.users = users; this.diary = diary; this.encoder = encoder;
        this.username = username; this.password = password; this.displayName = displayName;
    }
    @Override @Transactional public void run(String... args) {
        if (username.isBlank() && password.isBlank()) return;
        if (!username.matches("[a-zA-Z0-9_.-]{3,30}") || password.isBlank())
            throw new IllegalStateException("Defina usuário e senha válidos nas variáveis do administrador.");
        UserService.validatePassword(password);
        if (displayName.isBlank() || displayName.length() > 80)
            throw new IllegalStateException("Nome de administrador inválido.");
        String normalized = UserService.normalize(username);
        var user = users.findByUsername(normalized).orElse(null);
        if (user != null && user.getRole() != Role.ADMIN)
            return; // Uma conta existente nunca é promovida pelo ambiente, inclusive depois de um rebaixamento.
        if (user == null) user = users.save(new AppUser(normalized, displayName.strip(), encoder.encode(password), Role.ADMIN));
        // Preservar o diário da v0.1 e atribuí-lo apenas ao administrador configurado.
        for (var legacy : diary.findByUserIdIsNull()) legacy.assignOwner(user.getId());
        // Alterar a variável depois não troca a senha de uma conta já criada.
    }
}

package br.com.critplay.users;

import br.com.critplay.shared.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class UserService {
    private final UserRepository repository;
    private final PasswordEncoder encoder;
    public UserService(UserRepository repository, PasswordEncoder encoder) {
        this.repository = repository; this.encoder = encoder;
    }
    public UserView register(RegisterRequest request) {
        String username = normalize(request.username());
        validatePassword(request.password());
        if (repository.existsByUsername(username)) throw new ConflictException("Este nome de usuário já está em uso.");
        // A API pública nunca recebe nem decide um papel de administrador.
        var user = new AppUser(username, request.displayName().strip(), encoder.encode(request.password()), Role.USER);
        return UserView.from(repository.save(user));
    }
    public AppUser require(String username) {
        return repository.findByUsername(normalize(username))
            .orElseThrow(() -> new NotFoundException("Usuário não encontrado."));
    }
    public static String normalize(String username) { return username.strip().toLowerCase(Locale.ROOT); }
    public static void validatePassword(String password) {
        if (password == null || password.length() < 10 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new BadRequestException("A senha deve ter ao menos 10 caracteres e no máximo 72 bytes UTF-8.");
    }
}

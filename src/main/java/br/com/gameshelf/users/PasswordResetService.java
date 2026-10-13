package br.com.gameshelf.users;

import br.com.gameshelf.profile.ProfileService;
import br.com.gameshelf.shared.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.security.*;
import java.util.*;

@Service
public class PasswordResetService {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(PasswordResetService.class);
    private final UserRepository users;private final PasswordResetRepository tokens;private final PasswordEncoder encoder;
    private final ResetMailer mail;private final String baseUrl;private final SecureRandom random=new SecureRandom();
    public PasswordResetService(UserRepository users,PasswordResetRepository tokens,PasswordEncoder encoder,ResetMailer mail,
        @Value("${app.recovery.base-url}") String baseUrl) {
        this.users=users;this.tokens=tokens;this.encoder=encoder;this.mail=mail;this.baseUrl=baseUrl.replaceAll("/$","");
        var uri=java.net.URI.create(this.baseUrl);
        if(uri.getHost()==null||(!"http".equals(uri.getScheme())&&!"https".equals(uri.getScheme()))||uri.getRawQuery()!=null||uri.getRawFragment()!=null)
            throw new IllegalArgumentException("APP_BASE_URL inválida.");
    }
    @Transactional public void request(String username,String email) {
        if(!mail.enabled()) throw new ServiceUnavailableException("Recuperação por e-mail ainda não foi configurada. Você pode trocar a senha no seu perfil.");
        var user=users.findByUsername(UserService.normalize(username)).orElse(null);
        if(user==null||user.getEmail()==null||!user.getEmail().equals(ProfileService.normalizeEmail(email))) return;
        var previous=tokens.findTopByUserIdOrderByIdDesc(user.getId()).orElse(null);
        if(previous!=null&&previous.getExpiresAt().minus(Duration.ofMinutes(13)).isAfter(Instant.now())) return;
        byte[] bytes=new byte[32];random.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.revokeUserTokens(user.getId());
        tokens.save(new PasswordResetToken(user.getId(),hash(token),Instant.now().plus(Duration.ofMinutes(15))));
        // Token nunca é devolvido pela API nem registrado em logs.
        try {mail.send(user.getEmail(),baseUrl+"/#reset="+token);}
        catch(ServiceUnavailableException ex) {
            tokens.revokeUserTokens(user.getId());
            // Mesmo erro de transporte mantém a resposta genérica e não expõe a existência da conta.
            LOG.warn("Falha no envio de recuperação de senha. Confira a configuração SMTP.");
        }
    }
    @Transactional public void reset(String token,String password) {
        UserService.validatePassword(password);
        var saved=tokens.findLocked(hash(token)).orElseThrow(()->new BadRequestException("Link inválido ou expirado."));
        if(saved.isUsed()||!saved.getExpiresAt().isAfter(Instant.now())) throw new BadRequestException("Link inválido ou expirado.");
        var user=users.findById(saved.getUserId()).orElseThrow(()->new BadRequestException("Link inválido ou expirado."));
        user.changePassword(encoder.encode(password));users.save(user);saved.consume();tokens.save(saved);tokens.revokeUserTokens(user.getId());
    }
    public static String hash(String token) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException ex) {throw new IllegalStateException(ex);}
    }
}

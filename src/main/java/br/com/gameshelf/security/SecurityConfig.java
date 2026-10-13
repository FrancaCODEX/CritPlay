package br.com.gameshelf.security;

import br.com.gameshelf.users.*;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CsrfFilter;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean UserDetailsService userDetailsService(UserRepository repository) {
        return username -> {
            var user = repository.findByUsername(UserService.normalize(username))
                .orElseThrow(() -> new UsernameNotFoundException("Credenciais inválidas."));
            return new AccountPrincipal(user);
        };
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, UserRepository users,
        @Value("${app.security.rate-limit-enabled}") boolean rateLimit) throws Exception {
        // CSRF permanece ligado. O frontend obtém um token de sessão antes das escritas.
        http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/", "/index.html", "/styles.css", "/app.js", "/workspace.js", "/error", "/favicon.ico").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/games", "/api/games/*", "/api/auth/csrf", "/api/auth/me").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/auth/recovery-status", "/media/*").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/forgot-password", "/api/auth/reset-password").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/games", "/api/games/import").hasRole("ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/games/*").hasRole("ADMIN")
            .requestMatchers("/api/diary/**", "/api/games/*/prices", "/api/profile/**", "/api/profile", "/api/lists/**", "/api/lists", "/api/images").authenticated()
            .anyRequest().denyAll())
            .requestCache(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(form -> form.loginProcessingUrl("/api/auth/login")
                .successHandler((request,response,authentication) -> json(response,200,"Login realizado."))
                .failureHandler((request,response,exception) -> json(response,401,"Usuário ou senha inválidos.")))
            .logout(logout -> logout.logoutUrl("/api/auth/logout")
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request,response,authentication) -> json(response,200,"Sessão encerrada.")))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request,response,exception) -> json(response,401,"Entre na sua conta para continuar."))
                .accessDeniedHandler((request,response,exception) -> json(response,403,"Acesso negado ou token de segurança inválido.")));
        http.addFilterAfter(new AccountSessionFilter(users),SecurityContextHolderFilter.class);
        http.addFilterBefore(new AuthRateLimitFilter(rateLimit),CsrfFilter.class);
        return http.build();
    }
    private static void json(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}

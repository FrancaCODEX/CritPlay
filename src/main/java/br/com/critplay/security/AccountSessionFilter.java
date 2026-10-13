package br.com.critplay.security;

import br.com.critplay.users.UserRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

// Uma troca/reset de senha revoga também sessões abertas em outros navegadores.
public class AccountSessionFilter extends OncePerRequestFilter {
    private final UserRepository users;
    public AccountSessionFilter(UserRepository users) { this.users = users; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AccountPrincipal principal) {
            var account = users.findById(principal.getAccountId()).orElse(null);
            if (account == null || !account.isEnabled() || account.getSessionVersion() != principal.getSessionVersion()) {
                SecurityContextHolder.clearContext();
                var session = request.getSession(false); if (session != null) session.invalidate();
            }
        }
        chain.doFilter(request,response);
    }
}

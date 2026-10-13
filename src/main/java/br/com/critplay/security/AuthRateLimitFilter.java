package br.com.critplay.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.*;

public class AuthRateLimitFilter extends OncePerRequestFilter {
    private record Window(long start, int requests) {}
    private final Map<String,Window> windows = new HashMap<>();
    private final boolean enabled;
    private final Map<String,Integer> limits = Map.of("/api/auth/login",40,"/api/auth/register",20,"/api/auth/forgot-password",5);
    public AuthRateLimitFilter(boolean enabled) { this.enabled = enabled; }
    synchronized boolean accept(String key, int limit, long now) {
        long duration = 600_000;
        windows.entrySet().removeIf(e -> now - e.getValue().start() >= duration);
        if (!windows.containsKey(key) && windows.size() >= 10_000) return false;
        var window = windows.getOrDefault(key,new Window(now,0));
        if (window.requests() >= limit) return false;
        windows.put(key,new Window(window.start(),window.requests()+1)); return true;
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
        throws ServletException,IOException {
        var limit=limits.get(request.getRequestURI());
        if(enabled && "POST".equals(request.getMethod()) && limit!=null
            && !accept(request.getRemoteAddr()+":"+request.getRequestURI(),limit,System.currentTimeMillis())) {
            response.setStatus(429); response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Retry-After","600");
            response.getWriter().write("{\"message\":\"Muitas tentativas. Aguarde alguns minutos e tente novamente.\"}");return;
        }
        chain.doFilter(request,response);
    }
}

package br.com.critplay.config;

import org.springframework.context.annotation.*;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration(proxyBeanMethods=false)
public class SessionConfig {
    @Bean DefaultCookieSerializer cookieSerializer(
            @org.springframework.beans.factory.annotation.Value("${server.servlet.session.cookie.secure:false}") boolean secure) {
        var cookie=new DefaultCookieSerializer();
        cookie.setCookieName("JSESSIONID");
        cookie.setCookiePath("/");
        cookie.setUseHttpOnlyCookie(true);
        cookie.setUseSecureCookie(secure);
        cookie.setSameSite("Lax");
        return cookie;
    }
}

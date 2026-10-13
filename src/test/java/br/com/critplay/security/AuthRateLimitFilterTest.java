package br.com.critplay.security;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuthRateLimitFilterTest {
    @Test void blocksExcessAndExpiresWindowWithoutTrustingForwardedHeaders() {
        var filter=new AuthRateLimitFilter(true);
        assertTrue(filter.accept("127.0.0.1:login",2,1));
        assertTrue(filter.accept("127.0.0.1:login",2,2));
        assertFalse(filter.accept("127.0.0.1:login",2,3));
        assertTrue(filter.accept("other:login",2,3));
        assertTrue(filter.accept("127.0.0.1:login",2,600_002));
    }
}

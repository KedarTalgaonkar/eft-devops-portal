package com.euronet.eft.portal.security;

import jakarta.servlet.http.Cookie;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Double-submit CSRF exactly as the Angular app does it: the same random value in the XSRF-TOKEN cookie and the
 * X-XSRF-TOKEN header. (Spring's csrf() test helper keeps its token in the container session, which Spring Session
 * replaces, so it cannot be used here.)
 */
public final class Xsrf {

    private Xsrf() {
    }

    public static RequestPostProcessor xsrf() {
        String token = UUID.randomUUID().toString();
        return request -> {
            List<Cookie> cookies = new ArrayList<>(request.getCookies() == null ? List.of() : List.of(request.getCookies()));
            cookies.add(new Cookie("XSRF-TOKEN", token));
            request.setCookies(cookies.toArray(Cookie[]::new));
            request.addHeader("X-XSRF-TOKEN", token);
            return request;
        };
    }
}

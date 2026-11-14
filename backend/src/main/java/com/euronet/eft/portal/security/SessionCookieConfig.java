package com.euronet.eft.portal.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * The session cookie, set explicitly (Spring Session does not take server.servlet.session.cookie.* on its own):
 * name EFTSESSION, HttpOnly (no script access), SameSite=Lax (not sent on cross-site sub-requests, still sent on the
 * top-level redirect back from the identity provider), Secure whenever the portal runs on HTTPS, path "/".
 */
@Configuration(proxyBeanMethods = false)
public class SessionCookieConfig {

    static final String COOKIE_NAME = "EFTSESSION";

    @Bean
    CookieSerializer cookieSerializer(SecurityProperties props) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(COOKIE_NAME);
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax");
        serializer.setUseSecureCookie(props.cookieSecure());
        return serializer;
    }
}

package com.euronet.eft.portal.security;

import com.euronet.eft.portal.security.audit.AuditService;
import com.euronet.eft.portal.security.local.LocalUserService;
import com.euronet.eft.portal.security.oidc.PortalOidcUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authorization.AuthorizationEventPublisher;
import org.springframework.security.authorization.SpringAuthorizationEventPublisher;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Backend-for-frontend security: the browser never sees an OAuth token. Sign-in is OIDC (Microsoft Entra ID, PKCE,
 * signed and issuer-checked ID token) or a local account; both end in an HttpOnly, SameSite session cookie whose
 * session lives in PostgreSQL. Changes need the CSRF header. Access is role based and denied by default.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    static final String RETURN_TO = "PORTAL_RETURN_TO";

    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("""
                ROLE_ADMIN > ROLE_EDITOR
                ROLE_EDITOR > ROLE_VIEWER
                """);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        // {bcrypt} prefix keeps hashes upgradeable to a stronger algorithm later
        return new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(12)));
    }

    @Bean
    AuthorizationEventPublisher authorizationEventPublisher(ApplicationEventPublisher publisher) {
        return new SpringAuthorizationEventPublisher(publisher);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository(SecurityProperties props) {
        CookieCsrfTokenRepository repo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repo.setCookieCustomizer(c -> c.secure(props.cookieSecure()).sameSite("Strict").path("/"));
        return repo;
    }

    /** Used by the local login endpoint: new session id (fixation protection) and a fresh CSRF token. */
    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrf) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrf)));
    }

    /** Local accounts only: BCrypt check, disabled / locked accounts refused, unknown users timed like known ones. */
    @Bean
    AuthenticationManager localAuthenticationManager(LocalUserService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        ProviderManager manager = new ProviderManager(provider);
        manager.setEraseCredentialsAfterAuthentication(true);
        return manager;
    }

    /** ID-token validation: signature, expiry, audience, nonce and issuer (explicit when discovery is not used). */
    @Bean
    JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory(SecurityProperties props) {
        OidcIdTokenDecoderFactory factory = new OidcIdTokenDecoderFactory();
        factory.setJwtValidatorFactory(registration -> {
            List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
            validators.add(new OidcIdTokenValidator(registration));
            String discovered = registration.getProviderDetails().getIssuerUri();
            String expected = discovered != null ? discovered : props.oidc().expectedIssuer();
            if (expected == null || expected.isBlank()) {
                throw new IllegalStateException("OIDC provider '" + registration.getRegistrationId()
                        + "' has no issuer: set issuer-uri or portal.security.oidc.expected-issuer");
            }
            validators.add(new JwtIssuerValidator(expected));
            return new DelegatingOAuth2TokenValidator<>(validators);
        });
        return factory;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProperties props, CsrfTokenRepository csrf,
                                            SecurityContextRepository contextRepository, AuditService audit,
                                            ObjectProvider<ClientRegistrationRepository> registrations) throws Exception {
        http
                .securityContext(c -> c.securityContextRepository(contextRepository))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED).sessionFixation(f -> f.changeSessionId()))
                .csrf(c -> c.csrfTokenRepository(csrf).csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .cors(c -> c.disable())
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .headers(h -> h
                        .frameOptions(f -> f.deny())
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000)))
                .authorizeHttpRequests(a -> a
                        // public: health checks, the session probe, sign-in entry points
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/session", "/api/auth/sso").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/oauth2/authorization/**", "/login/oauth2/code/**").permitAll()
                        // any signed-in user
                        .requestMatchers("/api/auth/**").authenticated()
                        // administrators
                        .requestMatchers("/api/admin/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/environments/**").hasRole("ADMIN")
                        // editors change data, viewers read
                        .requestMatchers(HttpMethod.POST, "/api/**").hasRole("EDITOR")
                        .requestMatchers(HttpMethod.PUT, "/api/**").hasRole("EDITOR")
                        .requestMatchers(HttpMethod.PATCH, "/api/**").hasRole("EDITOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasRole("EDITOR")
                        .requestMatchers(HttpMethod.GET, "/api/**").hasRole("VIEWER")
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> JsonErrors.write(res, 401, "Unauthorized", "AUTHENTICATION_REQUIRED",
                                "Sign in to continue.", req.getRequestURI()))
                        .accessDeniedHandler((req, res, ex) -> JsonErrors.write(res, 403, "Forbidden", "ACCESS_DENIED",
                                "You do not have permission for this action.", req.getRequestURI())))
                .addFilterAfter(new SessionPolicyFilter(props.absoluteSessionTimeout()), ExceptionTranslationFilter.class);

        ClientRegistrationRepository repository = registrations.getIfAvailable();
        if (repository != null) {
            DefaultOAuth2AuthorizationRequestResolver resolver =
                    new DefaultOAuth2AuthorizationRequestResolver(repository, "/oauth2/authorization");
            resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
            http.oauth2Login(o -> o
                    .loginPage("/login")
                    .authorizationEndpoint(ae -> ae.authorizationRequestResolver(resolver))
                    .userInfoEndpoint(u -> u.oidcUserService(new PortalOidcUserService(props)))
                    .successHandler((req, res, auth) -> onSsoSuccess(req, res, auth, audit))
                    .failureHandler((req, res, ex) -> {
                        audit.record("unknown", "SSO", "LOGIN", props.registrationId(), "FAILURE", ex.getMessage());
                        res.sendRedirect("/login?error=sso");
                    }));
        }
        return http.build();
    }

    private static void onSsoSuccess(HttpServletRequest request, HttpServletResponse response, Authentication auth,
                                     AuditService audit) throws IOException {
        HttpSession session = request.getSession();
        session.setAttribute(SessionPolicyFilter.AUTHENTICATED_AT, Instant.now());
        Object returnTo = session.getAttribute(RETURN_TO);
        session.removeAttribute(RETURN_TO);
        CurrentUser user = CurrentUser.from(auth);
        audit.record(user.username(), "SSO", "LOGIN", user.username(), "SUCCESS", "roles " + user.roleNames());
        response.sendRedirect(SafeRedirects.relativeOrRoot(returnTo == null ? null : returnTo.toString()));
    }
}

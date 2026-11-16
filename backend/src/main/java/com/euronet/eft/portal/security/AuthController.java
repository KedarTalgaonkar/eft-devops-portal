package com.euronet.eft.portal.security;

import com.euronet.eft.portal.common.NotFoundException;
import com.euronet.eft.portal.security.audit.AuditService;
import com.euronet.eft.portal.security.local.LocalUserDetails;
import com.euronet.eft.portal.security.local.LocalUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Sign-in, sign-out and session state for the SPA.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Single sign-on, local accounts and the current session")
public class AuthController {

    private static final String INVALID = "Invalid username or password.";
    private final SecurityProperties props;
    private final AuthenticationManager localAuth;
    private final LocalUserService localUsers;
    private final SessionAuthenticationStrategy sessionStrategy;
    private final SecurityContextRepository contextRepository;
    private final CsrfTokenRepository csrfRepository;
    private final AuditService audit;
    private final ObjectProvider<ClientRegistrationRepository> registrations;
    public AuthController(SecurityProperties props, @Qualifier("localAuthenticationManager") AuthenticationManager localAuth,
                          LocalUserService localUsers, SessionAuthenticationStrategy sessionStrategy,
                          SecurityContextRepository contextRepository, CsrfTokenRepository csrfRepository, AuditService audit,
                          ObjectProvider<ClientRegistrationRepository> registrations) {
        this.props = props;
        this.localAuth = localAuth;
        this.localUsers = localUsers;
        this.sessionStrategy = sessionStrategy;
        this.contextRepository = contextRepository;
        this.csrfRepository = csrfRepository;
        this.audit = audit;
        this.registrations = registrations;
    }

    @GetMapping("/session")
    @Operation(summary = "Who is signed in, which sign-in methods exist; also issues the XSRF-TOKEN cookie")
    public SessionView session(CsrfToken csrf) {
        csrf.getToken(); // make sure the XSRF-TOKEN cookie is sent before the first change request
        CurrentUser user = CurrentUser.current();
        Providers providers = new Providers(ssoRegistration() != null, props.ssoLabel(), props.localLoginEnabled());
        return new SessionView(user != null, user == null ? null : UserView.of(user), providers);
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in with a local account (CSRF header required)")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        if (!props.localLoginEnabled()) {
            throw new NotFoundException("Local sign-in", "disabled");
        }
        String username = body.username().strip();
        Authentication auth;
        try {
            auth = localAuth.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(username, body.password()));
        } catch (AuthenticationException e) {
            localUsers.recordFailure(username);
            audit.record(username, "LOCAL", "LOGIN", username, "FAILURE", e.getClass().getSimpleName());
            // one generic message for unknown user, wrong password, disabled or locked account: no user enumeration
            return ResponseEntity.status(401).body(java.util.Map.of("status", 401, "code", "INVALID_CREDENTIALS", "message", INVALID));
        }
        sessionStrategy.onAuthentication(auth, request, response);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        request.getSession().setAttribute(SessionPolicyFilter.AUTHENTICATED_AT, Instant.now());
        localUsers.recordSuccess(username);
        CurrentUser user = CurrentUser.from(auth);
        audit.record(user.username(), "LOCAL", "LOGIN", user.username(), "SUCCESS", "roles " + user.roleNames());
        return ResponseEntity.ok(UserView.of(user));
    }

    @GetMapping("/sso")
    @Operation(summary = "Start single sign-on; returnTo must be a relative portal path")
    public void sso(@RequestParam(required = false) String returnTo, HttpServletRequest request, HttpServletResponse response)
        throws IOException {
        ClientRegistration registration = ssoRegistration();
        if (registration == null) {
            response.sendRedirect("/login?error=sso-disabled");
            return;
        }
        request.getSession().setAttribute(SecurityConfig.RETURN_TO, SafeRedirects.relativeOrRoot(returnTo));
        response.sendRedirect("/oauth2/authorization/" + registration.getRegistrationId());
    }

    @PostMapping("/logout")
    @Operation(summary = "End the session; returns where the browser goes next (the IdP sign-out for SSO users)")
    public LogoutView logout(HttpServletRequest request, HttpServletResponse response, Authentication auth) {
        CurrentUser user = CurrentUser.from(auth);
        String base = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        String next = base + "/login?signedOut";
        if (auth != null && auth.getPrincipal() instanceof OidcUser oidc) {
            String endSession = endSessionEndpoint();
            if (endSession != null) {
                next = UriComponentsBuilder.fromUriString(endSession)
                    .queryParam("id_token_hint", oidc.getIdToken().getTokenValue())
                    .queryParam("post_logout_redirect_uri", next)
                    .encode().build().toUriString();
            }
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        csrfRepository.saveToken(null, request, response);
        if (user != null) {
            audit.record(user.username(), user.source().name(), "LOGOUT", user.username(), "SUCCESS", null);
        }
        return new LogoutView(next);
    }

    @PostMapping("/password")
    @Operation(summary = "Change the password of the signed-in local account")
    public UserView changePassword(@Valid @RequestBody ChangePasswordRequest body, HttpServletRequest request,
                                   HttpServletResponse response, Authentication auth) {
        if (!(auth.getPrincipal() instanceof LocalUserDetails local)) {
            throw new IllegalArgumentException("Passwords of single sign-on accounts are managed by Microsoft Entra ID");
        }
        LocalUserDetails refreshed = localUsers.changePassword(local.getUsername(), body.currentPassword(), body.newPassword());
        refreshed.eraseCredentials();
        Authentication updated = UsernamePasswordAuthenticationToken.authenticated(refreshed, null, refreshed.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(updated);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        return UserView.of(CurrentUser.from(updated));
    }

    private ClientRegistration ssoRegistration() {
        ClientRegistrationRepository repo = registrations.getIfAvailable();
        return repo == null ? null : repo.findByRegistrationId(props.registrationId());
    }

    private String endSessionEndpoint() {
        if (props.oidc().endSessionUri() != null && !props.oidc().endSessionUri().isBlank()) {
            return props.oidc().endSessionUri();
        }
        ClientRegistration registration = ssoRegistration();
        Object discovered = registration == null ? null
            : registration.getProviderDetails().getConfigurationMetadata().get("end_session_endpoint");
        return discovered == null ? null : discovered.toString();
    }

    public record SessionView(boolean authenticated, UserView user, Providers providers) {
    }

    public record UserView(String username, String displayName, String email, List<String> roles, String source,
                           boolean mustChangePassword) {
        static UserView of(CurrentUser u) {
            return new UserView(u.username(), u.displayName(), u.email(), u.roleNames(), u.source().name(), u.mustChangePassword());
        }
    }

    public record Providers(boolean ssoEnabled, String ssoLabel, boolean localEnabled) {
    }

    public record LoginRequest(@NotBlank @Size(max = 100) String username, @NotBlank @Size(max = 200) String password) {
    }

    public record ChangePasswordRequest(@NotBlank @Size(max = 200) String currentPassword,
                                        @NotBlank @Size(max = 200) String newPassword) {
    }

    public record LogoutView(String logoutUrl) {
    }
}

package com.euronet.eft.portal.security.local;

import com.euronet.eft.portal.common.NotFoundException;
import com.euronet.eft.portal.security.CurrentUser;
import com.euronet.eft.portal.security.PortalRole;
import com.euronet.eft.portal.security.SecurityProperties;
import com.euronet.eft.portal.security.audit.AuditService;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Local accounts: lookup for sign-in, lockout after repeated failures, password policy, administration and the
 * one-time bootstrap of the first administrator. Disabling an account, resetting or changing its password ends
 * all of that user's sessions.
 */
@Service
@Transactional
public class LocalUserService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(LocalUserService.class);
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9._@-]{3,100}$");

    public record UserView(Long id, String username, String displayName, String email, List<String> roles, boolean enabled,
                           boolean mustChangePassword, boolean locked, Instant lockedUntil, int failedAttempts,
                           Instant lastLoginAt, Instant passwordChangedAt, Instant createdAt, String createdBy) {
    }

    private final LocalUserRepository users;
    private final PasswordEncoder encoder;
    private final SecurityProperties props;
    private final AuditService audit;
    private final ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> sessions;

    public LocalUserService(LocalUserRepository users, PasswordEncoder encoder, SecurityProperties props, AuditService audit,
                            ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> sessions) {
        this.users = users;
        this.encoder = encoder;
        this.props = props;
        this.audit = audit;
        this.sessions = sessions;
    }

    // ---------------- sign-in ----------------

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        LocalUser u = users.findByUsernameIgnoreCase(username == null ? "" : username.strip())
                .orElseThrow(() -> new UsernameNotFoundException("not found"));
        return toDetails(u);
    }

    public void recordSuccess(String username) {
        users.findByUsernameIgnoreCase(username).ifPresent(u -> {
            u.setFailedAttempts(0);
            u.setLockedUntil(null);
            u.setLastLoginAt(Instant.now());
        });
    }

    /** Counts a failed sign-in; locks the account after the configured number of consecutive failures. */
    public void recordFailure(String username) {
        users.findByUsernameIgnoreCase(username == null ? "" : username.strip()).ifPresent(u -> {
            int attempts = u.getFailedAttempts() + 1;
            u.setFailedAttempts(attempts);
            if (attempts >= props.lockout().maxAttempts() && !u.isLocked(Instant.now())) {
                u.setLockedUntil(Instant.now().plus(props.lockout().duration()));
                audit.record(u.getUsername(), "LOCAL", "ACCOUNT_LOCKED", u.getUsername(), "SUCCESS",
                        attempts + " failed sign-ins; locked for " + props.lockout().duration().toMinutes() + " min");
            }
        });
    }

    // ---------------- self service ----------------

    public LocalUserDetails changePassword(String username, String currentPassword, String newPassword) {
        LocalUser u = users.findByUsernameIgnoreCase(username).orElseThrow(() -> new NotFoundException("User", username));
        if (currentPassword == null || !encoder.matches(currentPassword, u.getPasswordHash())) {
            throw new IllegalArgumentException("The current password is not correct");
        }
        if (encoder.matches(newPassword, u.getPasswordHash())) {
            throw new IllegalArgumentException("The new password must be different from the current one");
        }
        PasswordPolicy.require(newPassword, u.getUsername());
        u.setPasswordHash(encoder.encode(newPassword));
        u.setMustChangePassword(false);
        audit.record(u.getUsername(), "LOCAL", "PASSWORD_CHANGED", u.getUsername(), "SUCCESS", null);
        endSessionsExceptCurrent(u.getUsername());
        return toDetails(u);
    }

    // ---------------- administration ----------------

    @Transactional(readOnly = true)
    public List<UserView> list() {
        Instant now = Instant.now();
        return users.findAllByOrderByUsernameAsc().stream().map(u -> view(u, now)).toList();
    }

    public UserView create(String username, String displayName, String email, Set<PortalRole> roles, String temporaryPassword) {
        String name = username == null ? "" : username.strip();
        if (!USERNAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Username: 3-100 characters, letters, digits and . _ @ - only");
        }
        if (users.findByUsernameIgnoreCase(name).isPresent()) {
            throw new IllegalArgumentException("A user with this username already exists");
        }
        PasswordPolicy.require(temporaryPassword, name);
        LocalUser u = new LocalUser(name, blankTo(displayName, name), blankTo(email, null),
                encoder.encode(temporaryPassword), roles(roles), CurrentUser.nameOrSystem());
        users.save(u);
        audit.record("USER_CREATED", name, "SUCCESS", "roles " + u.roleSet());
        return view(u, Instant.now());
    }

    public UserView update(long id, String displayName, String email, Set<PortalRole> roles, Boolean enabled) {
        LocalUser u = users.findById(id).orElseThrow(() -> new NotFoundException("User", id));
        boolean self = u.getUsername().equalsIgnoreCase(CurrentUser.nameOrSystem());
        if (self && Boolean.FALSE.equals(enabled)) {
            throw new IllegalArgumentException("You cannot disable your own account");
        }
        if (self && roles != null && !roles.contains(PortalRole.ADMIN)) {
            throw new IllegalArgumentException("You cannot remove your own administrator role");
        }
        if (displayName != null && !displayName.isBlank()) {
            u.setDisplayName(displayName.strip());
        }
        if (email != null) {
            u.setEmail(blankTo(email, null));
        }
        if (roles != null) {
            u.setRoles(roles(roles));
        }
        if (enabled != null && enabled != u.isEnabled()) {
            u.setEnabled(enabled);
            if (!enabled) {
                endAllSessions(u.getUsername());
            }
        }
        audit.record("USER_UPDATED", u.getUsername(), "SUCCESS", "roles " + u.roleSet() + ", enabled " + u.isEnabled());
        return view(u, Instant.now());
    }

    public UserView resetPassword(long id, String temporaryPassword) {
        LocalUser u = users.findById(id).orElseThrow(() -> new NotFoundException("User", id));
        if (u.getUsername().equalsIgnoreCase(CurrentUser.nameOrSystem())) {
            // a reset ends every session of the user, including this one, so the temporary password would be lost
            throw new IllegalArgumentException("You cannot reset your own password here; use Change password instead");
        }
        PasswordPolicy.require(temporaryPassword, u.getUsername());
        u.setPasswordHash(encoder.encode(temporaryPassword));
        u.setMustChangePassword(true);
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        endAllSessions(u.getUsername());
        audit.record("USER_PASSWORD_RESET", u.getUsername(), "SUCCESS", "must change at next sign-in");
        return view(u, Instant.now());
    }

    public UserView unlock(long id) {
        LocalUser u = users.findById(id).orElseThrow(() -> new NotFoundException("User", id));
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        audit.record("USER_UNLOCKED", u.getUsername(), "SUCCESS", null);
        return view(u, Instant.now());
    }

    /** Creates the first administrator from configuration, once, when no local account exists. */
    public void bootstrapAdminIfNeeded() {
        if (!props.localLoginEnabled() || users.count() > 0) {
            return;
        }
        String password = props.bootstrapAdmin().password();
        if (password == null || password.isBlank()) {
            log.warn("Local sign-in is enabled but there are no local accounts. Set PORTAL_BOOTSTRAP_ADMIN_PASSWORD "
                    + "(min {} chars, 3 character classes) to create the first administrator, or use SSO.", PasswordPolicy.MIN_LENGTH);
            return;
        }
        String username = props.bootstrapAdmin().username();
        PasswordPolicy.require(password, username);
        LocalUser admin = new LocalUser(username, "Portal administrator", null, encoder.encode(password),
                EnumSet.of(PortalRole.ADMIN), "bootstrap");
        users.save(admin);
        audit.record("system", "SYSTEM", "USER_CREATED", username, "SUCCESS", "bootstrap administrator; password change required");
        log.info("Created bootstrap administrator '{}' (password change required at first sign-in)", username);
    }

    // ---------------- helpers ----------------

    private LocalUserDetails toDetails(LocalUser u) {
        List<GrantedAuthority> authorities = u.roleSet().stream()
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority(r.authority()))
                .toList();
        return new LocalUserDetails(u.getUsername(), u.getPasswordHash(), u.getDisplayName(), u.getEmail(), authorities,
                u.isEnabled(), !u.isLocked(Instant.now()), u.isMustChangePassword());
    }

    private static Set<PortalRole> roles(Set<PortalRole> roles) {
        return roles == null || roles.isEmpty() ? EnumSet.of(PortalRole.VIEWER) : EnumSet.copyOf(roles);
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private UserView view(LocalUser u, Instant now) {
        return new UserView(u.getId(), u.getUsername(), u.getDisplayName(), u.getEmail(),
                u.roleSet().stream().map(Enum::name).toList(), u.isEnabled(), u.isMustChangePassword(), u.isLocked(now),
                u.getLockedUntil(), u.getFailedAttempts(), u.getLastLoginAt(), u.getPasswordChangedAt(), u.getCreatedAt(),
                u.getCreatedBy());
    }

    private void endAllSessions(String username) {
        FindByIndexNameSessionRepository<? extends Session> repo = sessions.getIfAvailable();
        if (repo != null) {
            repo.findByPrincipalName(username).keySet().forEach(repo::deleteById);
        }
    }

    private void endSessionsExceptCurrent(String username) {
        FindByIndexNameSessionRepository<? extends Session> repo = sessions.getIfAvailable();
        if (repo == null) {
            return;
        }
        String current = org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes().getSessionId();
        repo.findByPrincipalName(username).keySet().stream().filter(id -> !id.equals(current)).forEach(repo::deleteById);
    }
}

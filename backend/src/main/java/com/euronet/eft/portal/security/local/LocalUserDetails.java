package com.euronet.eft.portal.security.local;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Principal of a local-account session. Serializable because sessions live in PostgreSQL;
 * the password hash is erased after authentication so it never reaches the session store.
 */
public final class LocalUserDetails implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String username;
    private String passwordHash;
    private final String displayName;
    private final String email;
    private final List<GrantedAuthority> authorities;
    private final boolean enabled;
    private final boolean accountNonLocked;
    private final boolean mustChangePassword;

    public LocalUserDetails(String username, String passwordHash, String displayName, String email,
                            List<GrantedAuthority> authorities, boolean enabled, boolean accountNonLocked,
                            boolean mustChangePassword) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.email = email;
        this.authorities = List.copyOf(authorities);
        this.enabled = enabled;
        this.accountNonLocked = accountNonLocked;
        this.mustChangePassword = mustChangePassword;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }

    public String displayName() {
        return displayName;
    }

    public String email() {
        return email;
    }

    public boolean mustChangePassword() {
        return mustChangePassword;
    }
}

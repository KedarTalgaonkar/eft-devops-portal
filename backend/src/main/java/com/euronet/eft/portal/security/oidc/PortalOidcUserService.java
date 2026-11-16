package com.euronet.eft.portal.security.oidc;

import com.euronet.eft.portal.security.PortalRole;
import com.euronet.eft.portal.security.SecurityProperties;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Turns the signed ID token into a portal user. Roles come from the token's "roles" claim (Entra ID app roles,
 * or Keycloak realm roles) and optionally "groups" (Entra group object ids), mapped through
 * {@code portal.security.role-mapping}. Users without any mapped role get {@code default-role}, or are refused
 * when it is empty. Only claims of the signature-checked ID token are used (no user-info call).
 */
public class PortalOidcUserService extends OidcUserService {

    private final SecurityProperties props;

    public PortalOidcUserService(SecurityProperties props) {
        this.props = props;
        setRetrieveUserInfo(request -> false);
    }

    /**
     * Maps "roles" and "groups" claim values to portal roles; ADMIN implies the lower roles via the role hierarchy.
     */
    public static Set<PortalRole> mapRoles(Map<String, Object> claims, Map<String, String> mapping, String defaultRole) {
        Map<String, String> lower = mapping.entrySet().stream()
            .collect(Collectors.toMap(e -> e.getKey().toLowerCase(Locale.ROOT), Map.Entry::getValue, (a, b) -> a));
        Set<PortalRole> roles = EnumSet.noneOf(PortalRole.class);
        for (String claim : List.of("roles", "groups")) {
            if (claims.get(claim) instanceof Collection<?> values) {
                for (Object v : values) {
                    String target = lower.get(String.valueOf(v).toLowerCase(Locale.ROOT));
                    if (target != null) {
                        PortalRole.parse(target).ifPresent(roles::add);
                    }
                }
            }
        }
        if (roles.isEmpty()) {
            PortalRole.parse(defaultRole).ifPresent(roles::add);
        }
        return roles;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) {
        OidcUser user = super.loadUser(request);
        Set<PortalRole> roles = mapRoles(user.getClaims(), props.roleMapping(), props.defaultRole());
        if (roles.isEmpty()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("access_denied",
                "No EFT DevOps Portal role is assigned to this account", null));
        }
        List<GrantedAuthority> authorities = roles.stream()
            .map(r -> (GrantedAuthority) new SimpleGrantedAuthority(r.authority()))
            .toList();
        String nameAttribute = request.getClientRegistration().getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();
        if (nameAttribute == null || nameAttribute.isBlank() || !user.getClaims().containsKey(nameAttribute)) {
            nameAttribute = user.getClaims().containsKey("preferred_username") ? "preferred_username" : "sub";
        }
        return new DefaultOidcUser(authorities, user.getIdToken(), user.getUserInfo(), nameAttribute);
    }
}

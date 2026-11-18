package com.euronet.eft.portal.security.access;

import com.euronet.eft.portal.common.NotFoundException;
import com.euronet.eft.portal.security.CurrentUser;
import com.euronet.eft.portal.security.PortalRole;
import com.euronet.eft.portal.security.audit.AuditService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Group-based access to modules. A user's level for a module is the best level granted by any group they belong to
 * (the built-in "Everyone" group, groups whose SSO group appears in their ID token's "groups" claim, groups that list
 * their username), capped by their role: VIEWER at most VIEW, EDITOR at most EDIT. ADMIN always has EDIT everywhere,
 * so administrators can never lock themselves out. The configuration is cached for 30 seconds and reloaded at once
 * after a change made through this service.
 */
@Service
public class ModuleAccessService {

    public static final String EVERYONE = "Everyone";
    private static final Duration TTL = Duration.ofSeconds(30);

    private final JdbcClient jdbc;
    private final AuditService audit;
    private volatile Snapshot snapshot;

    public ModuleAccessService(JdbcClient jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    // ---------------- effective access ----------------

    /** Effective access of the signed-in user (all NONE when nobody is signed in). */
    public Map<PortalModule, AccessLevel> current() {
        return effective(SecurityContextHolder.getContext().getAuthentication());
    }

    public Map<PortalModule, AccessLevel> effective(Authentication auth) {
        CurrentUser user = CurrentUser.from(auth);
        return user == null ? none() : effective(user.username(), user.roles(), ssoGroups(auth));
    }

    public Map<PortalModule, AccessLevel> effective(String username, Set<PortalRole> roles, Collection<String> ssoGroups) {
        return resolve(username, roles, ssoGroups).levels();
    }

    /** Effective access plus the groups that granted it (for the administrators' preview). */
    public Resolution resolve(String username, Set<PortalRole> roles, Collection<String> ssoGroups) {
        Map<PortalModule, AccessLevel> levels = none();
        List<String> matched = new ArrayList<>();
        if (roles.contains(PortalRole.ADMIN)) {
            levels.replaceAll((m, l) -> AccessLevel.EDIT);
            return new Resolution(levels, List.of("Administrator (full access)"));
        }
        AccessLevel cap = roles.contains(PortalRole.EDITOR) ? AccessLevel.EDIT
            : roles.contains(PortalRole.VIEWER) ? AccessLevel.VIEW : AccessLevel.NONE;
        String user = lower(username);
        Set<String> tokenGroups = ssoGroups.stream().map(ModuleAccessService::lower).collect(Collectors.toSet());
        for (Group g : snapshot().groups()) {
            boolean applies = g.builtin()
                || (user != null && g.members().contains(user))
                || g.ssoGroups().stream().anyMatch(tokenGroups::contains);
            if (applies) {
                matched.add(g.name());
                g.modules().forEach((m, l) -> levels.merge(m, l, AccessLevel::max));
            }
        }
        levels.replaceAll((m, l) -> AccessLevel.min(l, cap));
        return new Resolution(levels, matched);
    }

    public boolean can(PortalModule module, AccessLevel needed) {
        return current().get(module).atLeast(needed);
    }

    /** Values of the ID token's "groups" claim for SSO users; empty for local accounts. */
    public static Set<String> ssoGroups(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof OidcUser oidc && oidc.getClaims().get("groups") instanceof Collection<?> values) {
            return values.stream().map(String::valueOf).collect(Collectors.toCollection(LinkedHashSet::new));
        }
        return Set.of();
    }

    /** Request rule: reading (GET/HEAD) needs VIEW on the module, changing needs EDIT. */
    public AuthorizationManager<RequestAuthorizationContext> module(PortalModule module) {
        return (authentication, ctx) -> {
            String method = ctx.getRequest().getMethod();
            AccessLevel needed = HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method) ? AccessLevel.VIEW : AccessLevel.EDIT;
            return new AuthorizationDecision(effective(authentication.get()).get(module).atLeast(needed));
        };
    }

    /** Request rule with a fixed level, e.g. asking for an AI build explanation only needs VIEW on Pipelines. */
    public AuthorizationManager<RequestAuthorizationContext> module(PortalModule module, AccessLevel needed) {
        return (authentication, ctx) -> new AuthorizationDecision(effective(authentication.get()).get(module).atLeast(needed));
    }

    // ---------------- administration ----------------

    public List<GroupView> list() {
        return snapshot(true).groups().stream().map(ModuleAccessService::view).toList();
    }

    @Transactional
    public GroupView create(GroupRequest r) {
        String name = r.name().strip();
        requireUniqueName(name, null);
        long id = jdbc.sql("insert into access_group (name, description, updated_by) values (?, ?, ?) returning id")
            .params(name, blankToNull(r.description()), CurrentUser.nameOrSystem())
            .query(Long.class).single();
        writeChildren(id, r, false);
        invalidate();
        audit.record("ACCESS_GROUP_CREATED", name, "SUCCESS", summary(r));
        return get(id);
    }

    @Transactional
    public GroupView update(long id, GroupRequest r) {
        Group existing = find(id);
        String name = existing.builtin() ? existing.name() : r.name().strip();
        requireUniqueName(name, id);
        jdbc.sql("update access_group set name = ?, description = ?, updated_at = now(), updated_by = ? where id = ?")
            .params(name, blankToNull(r.description()), CurrentUser.nameOrSystem(), id).update();
        writeChildren(id, r, existing.builtin());
        invalidate();
        audit.record("ACCESS_GROUP_UPDATED", name, "SUCCESS", summary(r));
        return get(id);
    }

    @Transactional
    public void delete(long id) {
        Group existing = find(id);
        if (existing.builtin()) {
            throw new IllegalArgumentException("The built-in group '" + existing.name() + "' cannot be deleted");
        }
        jdbc.sql("delete from access_group where id = ?").param(id).update();
        invalidate();
        audit.record("ACCESS_GROUP_DELETED", existing.name(), "SUCCESS", null);
    }

    public void invalidate() {
        snapshot = null;
    }

    // ---------------- internals ----------------

    private void writeChildren(long id, GroupRequest r, boolean builtin) {
        jdbc.sql("delete from access_group_module where group_id = ?").param(id).update();
        jdbc.sql("delete from access_group_sso where group_id = ?").param(id).update();
        jdbc.sql("delete from access_group_member where group_id = ?").param(id).update();
        r.modules().forEach((m, l) -> {
            if (m != null && l != null && l != AccessLevel.NONE) {
                jdbc.sql("insert into access_group_module (group_id, module, level) values (?, ?, ?)").params(id, m.name(), l.name()).update();
            }
        });
        if (builtin) {
            return; // Everyone applies to all users: no explicit members
        }
        for (String s : clean(r.ssoGroups())) {
            jdbc.sql("insert into access_group_sso (group_id, sso_group) values (?, ?)").params(id, s).update();
        }
        for (String u : clean(r.members())) {
            jdbc.sql("insert into access_group_member (group_id, username) values (?, ?)").params(id, u).update();
        }
    }

    private void requireUniqueName(String name, Long self) {
        boolean clash = jdbc.sql("select count(*) from access_group where lower(name) = lower(?) and id <> ?")
            .params(name, self == null ? -1L : self).query(Long.class).single() > 0;
        if (clash) {
            throw new IllegalArgumentException("An access group named '" + name + "' already exists");
        }
    }

    private GroupView get(long id) {
        return view(find(id));
    }

    private Group find(long id) {
        return snapshot(true).groups().stream().filter(g -> g.id() == id).findFirst()
            .orElseThrow(() -> new NotFoundException("Access group", id));
    }

    private Snapshot snapshot() {
        return snapshot(false);
    }

    private Snapshot snapshot(boolean fresh) {
        Snapshot s = snapshot;
        if (fresh || s == null || s.loadedAt().plus(TTL).isBefore(Instant.now())) {
            s = load();
            snapshot = s;
        }
        return s;
    }

    private Snapshot load() {
        Map<Long, Map<PortalModule, AccessLevel>> modules = new LinkedHashMap<>();
        jdbc.sql("select group_id, module, level from access_group_module").query((rs, n) -> {
            try {
                modules.computeIfAbsent(rs.getLong(1), k -> new EnumMap<>(PortalModule.class))
                    .put(PortalModule.valueOf(rs.getString(2)), AccessLevel.valueOf(rs.getString(3)));
            } catch (IllegalArgumentException ignored) {
                // a module removed from the code: ignore its old rows
            }
            return null;
        }).list();
        Map<Long, List<String>> sso = new LinkedHashMap<>();
        jdbc.sql("select group_id, sso_group from access_group_sso order by sso_group")
            .query((rs, n) -> sso.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getString(2))).list();
        Map<Long, List<String>> members = new LinkedHashMap<>();
        jdbc.sql("select group_id, username from access_group_member order by username")
            .query((rs, n) -> members.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getString(2))).list();
        List<Group> groups = jdbc.sql("select id, name, description, builtin, updated_at, updated_by from access_group order by builtin desc, lower(name)")
            .query((rs, n) -> {
                long id = rs.getLong(1);
                return new Group(id, rs.getString(2), rs.getString(3), rs.getBoolean(4),
                    modules.getOrDefault(id, new EnumMap<>(PortalModule.class)),
                    sso.getOrDefault(id, List.of()), members.getOrDefault(id, List.of()),
                    rs.getTimestamp(5).toInstant(), rs.getString(6));
            }).list();
        return new Snapshot(groups, Instant.now());
    }

    private static GroupView view(Group g) {
        Map<String, String> modules = new LinkedHashMap<>();
        for (PortalModule m : PortalModule.values()) {
            modules.put(m.name(), g.modules().getOrDefault(m, AccessLevel.NONE).name());
        }
        return new GroupView(g.id(), g.name(), g.description(), g.builtin(), modules, g.ssoGroupsAsTyped(),
            g.membersAsTyped(), g.updatedAt(), g.updatedBy());
    }

    private static Map<PortalModule, AccessLevel> none() {
        Map<PortalModule, AccessLevel> m = new EnumMap<>(PortalModule.class);
        for (PortalModule p : PortalModule.values()) {
            m.put(p, AccessLevel.NONE);
        }
        return m;
    }

    private static List<String> clean(List<String> values) {
        if (values == null) {
            return List.of();
        }
        Map<String, String> unique = new LinkedHashMap<>();
        values.stream().filter(Objects::nonNull).map(String::strip).filter(s -> !s.isEmpty())
            .forEach(s -> unique.putIfAbsent(s.toLowerCase(Locale.ROOT), s));
        return List.copyOf(unique.values());
    }

    private static String summary(GroupRequest r) {
        String mods = r.modules().entrySet().stream()
            .filter(e -> e.getValue() != null && e.getValue() != AccessLevel.NONE)
            .map(e -> e.getKey().name() + "=" + e.getValue().name()).collect(Collectors.joining(", "));
        return "modules [" + mods + "], sso " + clean(r.ssoGroups()) + ", members " + clean(r.members());
    }

    private static String lower(String s) {
        return s == null ? null : s.strip().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    // ---------------- types ----------------

    private record Group(long id, String name, String description, boolean builtin, Map<PortalModule, AccessLevel> modules,
                         List<String> ssoGroupsAsTyped, List<String> membersAsTyped, Instant updatedAt, String updatedBy) {
        Set<String> ssoGroups() {
            return ssoGroupsAsTyped.stream().map(ModuleAccessService::lower).collect(Collectors.toSet());
        }

        Set<String> members() {
            return membersAsTyped.stream().map(ModuleAccessService::lower).collect(Collectors.toSet());
        }
    }

    private record Snapshot(List<Group> groups, Instant loadedAt) {
    }

    public record Resolution(Map<PortalModule, AccessLevel> levels, List<String> groups) {
    }

    public record GroupView(long id, String name, String description, boolean builtin, Map<String, String> modules,
                            List<String> ssoGroups, List<String> members, Instant updatedAt, String updatedBy) {
    }

    public record GroupRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 300) String description,
        @NotNull Map<PortalModule, AccessLevel> modules,
        @Size(max = 50) List<@Size(max = 200) String> ssoGroups,
        @Size(max = 500) List<@Size(max = 200) String> members) {
    }
}

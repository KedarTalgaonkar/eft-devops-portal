package com.euronet.eft.portal.security.local;

import com.euronet.eft.portal.security.PortalRole;
import com.euronet.eft.portal.security.local.LocalUserService.UserView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Local account administration; administrators only (SecurityConfig: /api/admin/**). Every call is audited.
 */
@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "Administration")
public class UserAdminController {

    private final LocalUserService users;

    public UserAdminController(LocalUserService users) {
        this.users = users;
    }

    private static Set<PortalRole> roles(List<String> names) {
        Set<PortalRole> roles = EnumSet.noneOf(PortalRole.class);
        if (names != null) {
            for (String n : names) {
                roles.add(PortalRole.parse(n).orElseThrow(() -> new IllegalArgumentException("Unknown role: " + n)));
            }
        }
        return roles;
    }

    @GetMapping
    @Operation(summary = "All local accounts")
    public List<UserView> list() {
        return users.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a local account with a temporary password (must be changed at first sign-in)")
    public UserView create(@Valid @RequestBody CreateUserRequest r) {
        return users.create(r.username(), r.displayName(), r.email(), roles(r.roles()), r.temporaryPassword());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Change name, e-mail, roles or enable / disable (disabling ends the user's sessions)")
    public UserView update(@PathVariable long id, @Valid @RequestBody UpdateUserRequest r) {
        return users.update(id, r.displayName(), r.email(), r.roles() == null ? null : roles(r.roles()), r.enabled());
    }

    @PostMapping("/{id}/reset-password")
    @Operation(summary = "Set a temporary password, unlock, and end the user's sessions")
    public UserView resetPassword(@PathVariable long id, @Valid @RequestBody ResetPasswordRequest r) {
        return users.resetPassword(id, r.temporaryPassword());
    }

    @PostMapping("/{id}/unlock")
    @Operation(summary = "Clear a lockout after failed sign-ins")
    public UserView unlock(@PathVariable long id) {
        return users.unlock(id);
    }

    public record CreateUserRequest(@NotBlank @Size(max = 100) String username, @Size(max = 200) String displayName,
                                    @Size(max = 200) String email, List<String> roles,
                                    @NotBlank @Size(max = 200) String temporaryPassword) {
    }

    public record UpdateUserRequest(@Size(max = 200) String displayName, @Size(max = 200) String email,
                                    List<String> roles,
                                    Boolean enabled) {
    }

    public record ResetPasswordRequest(@NotBlank @Size(max = 200) String temporaryPassword) {
    }
}

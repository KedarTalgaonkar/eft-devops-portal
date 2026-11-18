package com.euronet.eft.portal.security.access;

import com.euronet.eft.portal.security.PortalRole;
import com.euronet.eft.portal.security.access.ModuleAccessService.GroupRequest;
import com.euronet.eft.portal.security.access.ModuleAccessService.GroupView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Administration of access groups (ADMIN only, through the /api/admin/** rule).
 */
@RestController
@RequestMapping("/api/admin/access-groups")
@Tag(name = "Access groups", description = "Which groups see or edit which modules")
public class AccessGroupController {

    private final ModuleAccessService access;

    public AccessGroupController(ModuleAccessService access) {
        this.access = access;
    }

    @GetMapping
    @Operation(summary = "All access groups with their module levels, SSO groups and members")
    public List<GroupView> list() {
        return access.list();
    }

    @GetMapping("/modules")
    @Operation(summary = "The modules access can be granted to")
    public List<ModuleView> modules() {
        return Arrays.stream(PortalModule.values()).map(m -> new ModuleView(m.name(), m.label(), m.description())).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an access group")
    public GroupView create(@Valid @RequestBody GroupRequest request) {
        return access.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Change an access group (the built-in Everyone group keeps its name and has no members)")
    public GroupView update(@PathVariable long id, @Valid @RequestBody GroupRequest request) {
        return access.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an access group (not the built-in Everyone group)")
    public void delete(@PathVariable long id) {
        access.delete(id);
    }

    @PostMapping("/preview")
    @Operation(summary = "What a user would get: effective level per module and the groups that apply")
    public PreviewView preview(@Valid @RequestBody PreviewRequest request) {
        EnumSet<PortalRole> roles = EnumSet.of(request.role());
        ModuleAccessService.Resolution r = access.resolve(request.username(), roles,
            request.ssoGroups() == null ? List.of() : request.ssoGroups());
        Map<String, String> levels = new LinkedHashMap<>();
        r.levels().forEach((m, l) -> levels.put(m.name(), l.name()));
        return new PreviewView(levels, r.groups());
    }

    public record ModuleView(String key, String label, String description) {
    }

    public record PreviewRequest(@Size(max = 200) String username, @NotNull PortalRole role,
                                 @Size(max = 50) List<@Size(max = 200) String> ssoGroups) {
    }

    public record PreviewView(Map<String, String> modules, List<String> groups) {
    }
}

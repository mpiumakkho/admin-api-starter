package com.adminstarter.accesscontrol.authorization.service;

import com.adminstarter.accesscontrol.authorization.dto.RoleResponse;
import com.adminstarter.accesscontrol.authorization.entity.Permission;
import com.adminstarter.accesscontrol.authorization.entity.Role;
import com.adminstarter.accesscontrol.authorization.repository.PermissionRepository;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Roles as the dashboard sees them, permissions in catalog order */
@Component
public class RoleMapper {

    /** RoleService.permissionsOf; evicted when a role changes or is deleted */
    static final String PERMISSIONS_CACHE = "role-permissions";

    private final PermissionRepository permissions;

    public RoleMapper(PermissionRepository permissions) {
        this.permissions = permissions;
    }

    public RoleResponse toResponse(Role role) {
        return toResponse(role, order());
    }

    public List<RoleResponse> toResponses(List<Role> roles) {
        var order = order();
        return roles.stream().map(role -> toResponse(role, order)).toList();
    }

    /** Catalog position of every permission code, deprecated ones included */
    public Map<String, Integer> order() {
        return permissions.findAll().stream().collect(Collectors.toMap(Permission::getCode, Permission::getPosition));
    }

    public static List<String> ordered(Collection<String> codes, Map<String, Integer> order) {
        return codes.stream()
                .distinct()
                .sorted(Comparator.comparing((String code) -> order.getOrDefault(code, Integer.MAX_VALUE))
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    private static RoleResponse toResponse(Role role, Map<String, Integer> order) {
        return new RoleResponse(role.getId(), role.getName(), role.getDescription(), role.getTone(),
                ordered(role.getPermissions(), order),
                new RoleResponse.Assignment(role.getAssignmentMaxDays(), role.isAssignmentReasonRequired()), role.isSystem());
    }
}

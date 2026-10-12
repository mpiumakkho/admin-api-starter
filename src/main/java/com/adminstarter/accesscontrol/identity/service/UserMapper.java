package com.adminstarter.accesscontrol.identity.service;

import com.adminstarter.accesscontrol.identity.dto.AccountSummary;
import com.adminstarter.accesscontrol.identity.dto.UserResponse;
import com.adminstarter.accesscontrol.identity.entity.RoleAssignment;
import com.adminstarter.accesscontrol.identity.entity.User;
import com.adminstarter.accesscontrol.identity.entity.UserCredential;
import com.adminstarter.accesscontrol.identity.repository.UserCredentialRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Users as the dashboard sees them, with their role assignments (P-019) */
@Component
public class UserMapper {

    private final RoleAssignmentResolver assignments;
    private final UserCredentialRepository credentials;

    public UserMapper(RoleAssignmentResolver assignments, UserCredentialRepository credentials) {
        this.assignments = assignments;
        this.credentials = credentials;
    }

    /** With whether they use two-step sign-in, looked up */
    public UserResponse toResponse(User user) {
        return toResponse(user, twoFactor(user.getId()));
    }

    public UserResponse toResponse(User user, boolean twoFactor) {
        return toResponse(user, twoFactor, assignments.of(user.getId()));
    }

    /** With assignments already loaded, as for the whole list */
    public UserResponse toResponse(User user, boolean twoFactor, List<RoleAssignment> roles) {
        return new UserResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(), user.getPhone(),
                user.getDepartment(), user.getBranchId(), roles.stream().map(RoleAssignmentResolver::toResponse).toList(),
                user.getStatus().value(), user.getAvatar(), user.getCreatedAt(), user.getLastActiveAt(), twoFactor,
                user.getLanguage());
    }

    public AccountSummary toAccount(User user) {
        var twoFactor = twoFactor(user.getId());
        return new AccountSummary(user.getId(), user.getEmail(), user.getFirstName(), user.getFullName(),
                user.getStatus().value(), assignments.of(user.getId()).stream().map(RoleAssignmentResolver::toGrant).toList(),
                twoFactor);
    }

    private boolean twoFactor(UUID userId) {
        return credentials.findById(userId).map(UserCredential::isTwoFactorOn).orElse(false);
    }
}

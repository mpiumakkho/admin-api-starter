package com.adminstarter.accesscontrol.identity.service;

import com.adminstarter.accesscontrol.identity.dto.AccountSummary;
import com.adminstarter.accesscontrol.identity.dto.UserResponse;
import com.adminstarter.accesscontrol.identity.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toResponse(User user, boolean twoFactor) {
        return new UserResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(), user.getPhone(),
                user.getDepartment(), user.getRoleId(), user.getStatus().value(), user.getAvatar(), user.getCreatedAt(),
                user.getLastActiveAt(), twoFactor);
    }

    public AccountSummary toAccount(User user, boolean twoFactor) {
        return new AccountSummary(user.getId(), user.getEmail(), user.getFirstName(), user.getFullName(),
                user.getStatus().value(), user.getRoleId(), twoFactor);
    }
}

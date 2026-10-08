package com.adminstarter.accesscontrol.authorization;

import com.adminstarter.accesscontrol.authorization.spi.RoleDefinition;
import com.adminstarter.accesscontrol.authorization.spi.RoleTemplate;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** The one role every installation needs: the first administrator gets it. Other roles come from AppRoles. */
@Component
public class CoreRoles implements RoleTemplate {

    public static final String ADMINISTRATOR = "ADMINISTRATOR";

    @Override
    public List<RoleDefinition> roles() {
        return List.of(new RoleDefinition(ADMINISTRATOR, "Administrator", "Full access to every part of the system",
                "primary", true, Set.of(RoleDefinition.ALL_PERMISSIONS)));
    }
}

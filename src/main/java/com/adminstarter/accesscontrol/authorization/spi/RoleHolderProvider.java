package com.adminstarter.accesscontrol.authorization.spi;

import java.util.UUID;

/**
 * Whether anyone holds a role, now or later, so a role in use is not deleted (role_in_use). Identity, which keeps the
 * role assignments (P-019), answers it; authorization depends only on this interface, not on identity.
 */
public interface RoleHolderProvider {

    boolean heldNowOrLater(UUID roleId);
}

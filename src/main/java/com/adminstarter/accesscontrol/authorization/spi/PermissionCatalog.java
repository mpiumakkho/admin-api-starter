package com.adminstarter.accesscontrol.authorization.spi;

import java.util.List;

/**
 * Permissions declared in code. Every bean of this type is read at startup by PermissionCatalogSynchronizer: new
 * codes are added, changed descriptions updated, and codes no catalog declares any more marked deprecated (never
 * deleted, since roles may still hold them). Nobody creates permissions from the dashboard.
 */
public interface PermissionCatalog {

    /** In the order the dashboard should list them */
    List<PermissionDefinition> permissions();
}

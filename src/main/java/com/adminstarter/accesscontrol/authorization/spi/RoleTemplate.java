package com.adminstarter.accesscontrol.authorization.spi;

import java.util.List;

/**
 * Roles to create on the first start. A role whose code already exists is left alone: once created, it belongs to
 * the people managing roles on the dashboard.
 */
public interface RoleTemplate {

    List<RoleDefinition> roles();
}

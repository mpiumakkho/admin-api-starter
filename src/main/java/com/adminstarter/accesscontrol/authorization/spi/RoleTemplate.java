package com.adminstarter.accesscontrol.authorization.spi;

import java.util.List;

/**
 * Roles to create on the first start. A role whose code already exists is left alone, as it belongs to the people
 * managing roles on the dashboard, unless it is built in: those take the template's name, description and permissions
 * on every start (P-019).
 */
public interface RoleTemplate {

    List<RoleDefinition> roles();
}

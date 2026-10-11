package com.adminstarter.accesscontrol.authorization.service;

import com.adminstarter.accesscontrol.authorization.entity.Permission;
import com.adminstarter.accesscontrol.authorization.entity.Role;
import com.adminstarter.accesscontrol.authorization.repository.PermissionRepository;
import com.adminstarter.accesscontrol.authorization.repository.RoleRepository;
import com.adminstarter.accesscontrol.authorization.spi.PermissionCatalog;
import com.adminstarter.accesscontrol.authorization.spi.PermissionDefinition;
import com.adminstarter.accesscontrol.authorization.spi.RoleDefinition;
import com.adminstarter.accesscontrol.authorization.spi.RoleTemplate;
import java.time.Clock;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * On startup, brings the permissions table in line with every PermissionCatalog, then creates the roles of every
 * RoleTemplate that do not exist yet. Built-in ones that exist take their name, description and permissions from the
 * template again, as Microsoft Entra's built-in roles are the same everywhere (P-019); the others, once created,
 * belong to the people managing roles. Runs first: the settings and the first administrator need the roles.
 */
@Service
@Order(10)
public class PermissionCatalogSynchronizer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PermissionCatalogSynchronizer.class);

    private final List<PermissionCatalog> catalogs;
    private final List<RoleTemplate> templates;
    private final PermissionRepository permissions;
    private final RoleRepository roles;
    private final Clock clock;

    public PermissionCatalogSynchronizer(List<PermissionCatalog> catalogs, List<RoleTemplate> templates,
            PermissionRepository permissions, RoleRepository roles, Clock clock) {
        this.catalogs = catalogs;
        this.templates = templates;
        this.permissions = permissions;
        this.roles = roles;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var declared = catalogs.stream().flatMap(catalog -> catalog.permissions().stream()).toList();
        var codes = declared.stream().map(PermissionDefinition::code).toList();
        if (new HashSet<>(codes).size() != codes.size()) {
            throw new IllegalStateException("A permission code is declared twice: " + codes);
        }
        syncPermissions(declared);
        var definitions = templates.stream().flatMap(template -> template.roles().stream()).toList();
        var created = definitions.stream()
                .filter(definition -> roles.findByCode(definition.code()).isEmpty())
                .map(definition -> roles.save(toRole(definition, codes)))
                .map(Role::getName)
                .toList();
        definitions.stream().filter(RoleDefinition::system).forEach(definition -> roles.findByCode(definition.code())
                .ifPresent(role -> role.redefine(definition.name(), definition.description(), granted(definition, codes),
                        clock.instant())));
        log.info("Permission catalog: {} permissions; roles created: {}", codes.size(), created.isEmpty() ? "none" : created);
    }

    private void syncPermissions(List<PermissionDefinition> declared) {
        var existing = permissions.findAll().stream()
                .collect(Collectors.toMap(Permission::getCode, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        var position = 0;
        for (var definition : declared) {
            var permission = existing.remove(definition.code());
            if (permission == null) {
                permissions.save(new Permission(definition.code(), definition.resource(), definition.action(),
                        definition.description(), position));
            } else {
                permission.declare(definition.description(), position);
            }
            position++;
        }
        // Roles may still hold a code no catalog declares: keep it, marked, rather than break them
        existing.values().forEach(Permission::deprecate);
    }

    private Role toRole(RoleDefinition definition, List<String> catalog) {
        return new Role(definition.code(), definition.name(), definition.description(), definition.tone(),
                definition.system(), granted(definition, catalog), clock.instant());
    }

    private static Set<String> granted(RoleDefinition definition, List<String> catalog) {
        var granted = definition.grantsAll() ? new HashSet<>(catalog) : new HashSet<>(definition.permissions());
        if (!catalog.containsAll(granted)) {
            throw new IllegalStateException("Role " + definition.code() + " grants a permission no catalog declares");
        }
        return granted;
    }
}

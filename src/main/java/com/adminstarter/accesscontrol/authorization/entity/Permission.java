package com.adminstarter.accesscontrol.authorization.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** A permission from a PermissionCatalog; only PermissionCatalogSynchronizer writes these */
@Entity
@Table(name = "permissions")
public class Permission {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private String code;
    private String resource;
    private String action;
    private String description;
    /** Where the catalogs list it; roles list their permissions in this order */
    private int position;
    /** No catalog declares it any more */
    private boolean deprecated;

    protected Permission() {}

    public Permission(String code, String resource, String action, String description, int position) {
        this.code = code;
        this.resource = resource;
        this.action = action;
        this.description = description;
        this.position = position;
    }

    public void declare(String description, int position) {
        this.description = description;
        this.position = position;
        this.deprecated = false;
    }

    public void deprecate() {
        this.deprecated = true;
    }

    public String getCode() {
        return code;
    }

    public int getPosition() {
        return position;
    }

    public boolean isDeprecated() {
        return deprecated;
    }
}

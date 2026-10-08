package com.adminstarter.accesscontrol.settings.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** How user management behaves, changed on the settings page. There is one row. */
@Entity
@Table(name = "admin_settings")
public class AdminSettings {

    public static final short ID = 1;

    @Id
    private short id;

    private UUID defaultRoleId;

    /** Lower-cased, without duplicates; empty allows any domain */
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> allowedDomains;

    private int inviteExpiryDays;
    private int sessionTimeoutMinutes;
    private Instant updatedAt;

    protected AdminSettings() {}

    public AdminSettings(UUID defaultRoleId, List<String> allowedDomains, int inviteExpiryDays,
            int sessionTimeoutMinutes, Instant now) {
        this.id = ID;
        update(defaultRoleId, allowedDomains, inviteExpiryDays, sessionTimeoutMinutes, now);
    }

    public final void update(UUID defaultRoleId, List<String> allowedDomains, int inviteExpiryDays,
            int sessionTimeoutMinutes, Instant now) {
        this.defaultRoleId = defaultRoleId;
        this.allowedDomains = List.copyOf(allowedDomains);
        this.inviteExpiryDays = inviteExpiryDays;
        this.sessionTimeoutMinutes = sessionTimeoutMinutes;
        this.updatedAt = now;
    }

    public UUID getDefaultRoleId() {
        return defaultRoleId;
    }

    public List<String> getAllowedDomains() {
        return List.copyOf(allowedDomains);
    }

    public int getInviteExpiryDays() {
        return inviteExpiryDays;
    }

    public int getSessionTimeoutMinutes() {
        return sessionTimeoutMinutes;
    }
}

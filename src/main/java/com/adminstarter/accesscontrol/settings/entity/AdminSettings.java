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

    /** Roles whose holders must turn on two-step sign-in before anything else (P-016); without duplicates */
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<UUID> twoFactorRoleIds;

    /** The shortest password someone can set (P-017) */
    private int passwordMinLength;
    /** How many of someone's most recent passwords, the current one included, they cannot choose again; 0 allows any */
    private int passwordHistory;
    /** Activity older than this is deleted every night (P-018) */
    private int activityRetentionDays;

    private Instant updatedAt;

    protected AdminSettings() {}

    public AdminSettings(UUID defaultRoleId, List<String> allowedDomains, int inviteExpiryDays,
            int sessionTimeoutMinutes, List<UUID> twoFactorRoleIds, int passwordMinLength, int passwordHistory,
            int activityRetentionDays, Instant now) {
        this.id = ID;
        update(defaultRoleId, allowedDomains, inviteExpiryDays, sessionTimeoutMinutes, now);
        updateAccountRules(twoFactorRoleIds, passwordMinLength, passwordHistory, activityRetentionDays, now);
    }

    public final void update(UUID defaultRoleId, List<String> allowedDomains, int inviteExpiryDays,
            int sessionTimeoutMinutes, Instant now) {
        this.defaultRoleId = defaultRoleId;
        this.allowedDomains = List.copyOf(allowedDomains);
        this.inviteExpiryDays = inviteExpiryDays;
        this.sessionTimeoutMinutes = sessionTimeoutMinutes;
        this.updatedAt = now;
    }

    /** The account rules: two-step sign-in by role, the password rules and how long activity is kept */
    public final void updateAccountRules(List<UUID> twoFactorRoleIds, int passwordMinLength, int passwordHistory,
            int activityRetentionDays, Instant now) {
        this.twoFactorRoleIds = List.copyOf(twoFactorRoleIds);
        this.passwordMinLength = passwordMinLength;
        this.passwordHistory = passwordHistory;
        this.activityRetentionDays = activityRetentionDays;
        this.updatedAt = now;
    }

    /** A deleted role no longer requires anything; whether it was on the list */
    public boolean removeTwoFactorRole(UUID roleId, Instant now) {
        if (!twoFactorRoleIds.contains(roleId)) {
            return false;
        }
        this.twoFactorRoleIds = twoFactorRoleIds.stream().filter(id -> !id.equals(roleId)).toList();
        this.updatedAt = now;
        return true;
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

    public List<UUID> getTwoFactorRoleIds() {
        return List.copyOf(twoFactorRoleIds);
    }

    public int getPasswordMinLength() {
        return passwordMinLength;
    }

    public int getPasswordHistory() {
        return passwordHistory;
    }

    public int getActivityRetentionDays() {
        return activityRetentionDays;
    }
}

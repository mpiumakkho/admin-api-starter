package com.adminstarter.accesscontrol.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One field an action changed, as the activity log keeps it (ActivityChange in admin-ng-starter's models.ts).
 * Values are raw (a role id, a status); the app formats them. Absent parts are left out of the JSON, because the
 * app tells "no before value" from an empty one.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldChange(
        String field,
        @Nullable String from,
        @Nullable String to,
        @Nullable List<String> added,
        @Nullable List<String> removed) {

    public static FieldChange of(String field, @Nullable String from, @Nullable String to) {
        return new FieldChange(field, from, to, null, null);
    }

    /** A value that was set where there was none, as on a new user */
    public static FieldChange set(String field, String to) {
        return new FieldChange(field, null, to, null, null);
    }

    /** A value that is gone, as on a deleted user */
    public static FieldChange was(String field, String from) {
        return new FieldChange(field, from, null, null, null);
    }

    /** A change too big to keep, such as a photo: only that it changed */
    public static FieldChange changed(String field) {
        return new FieldChange(field, null, null, null, null);
    }

    /** Items added to and removed from a list, such as a role's permissions */
    public static FieldChange list(String field, List<String> added, List<String> removed) {
        return new FieldChange(field, null, null, List.copyOf(added), List.copyOf(removed));
    }
}

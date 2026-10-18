package com.adminstarter.accesscontrol.identity.dto;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * UserDraft: everything the server does not assign. Phone, photo, branch and language may be left out; without roles, the
 * new user gets the settings' default role for good, and without a language, their emails are in the settings' one.
 */
public record CreateUserRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotNull @Size(max = 254) @Pattern(regexp = EMAIL) String email,
        @Nullable @Size(max = 50) String phone,
        @NotBlank @Size(max = 100) String department,
        @Nullable UUID branchId,
        @Nullable @Size(max = 50) List<@NotNull @Valid RoleGrantRequest> roles,
        @NotNull @Pattern(regexp = STATUSES) String status,
        @Nullable @Avatar String avatar,
        @Nullable @Pattern(regexp = LANGUAGES) String language) {

    /** The check admin-ng-starter makes: something@something.something, surrounding spaces allowed */
    public static final String EMAIL = "^\\s*[^\\s@]+@[^\\s@]+\\.[^\\s@]+\\s*$";
    public static final String STATUSES = "active|invited|suspended";
    /** The languages emails are written in (P-029) */
    public static final String LANGUAGES = "th|en";
    /** Some text other than spaces, for a field that may be left out but not emptied (UpdateUserRequest) */
    public static final String NOT_BLANK = "(?s).*\\S.*";

    /**
     * A profile picture: empty for none, or a JPEG, PNG or WebP image in a base64 data URL of at most
     * {@link #MAX_LENGTH} characters, whose bytes are that kind of image (P-039). Never a link, which the browser of
     * everyone who sees the person would fetch from wherever it points, nor an SVG, which can carry script. The apps
     * send a 160-pixel JPEG of a few tens of kilobytes.
     */
    @Documented
    @Constraint(validatedBy = Avatar.Check.class)
    @Target({METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE})
    @Retention(RUNTIME)
    public @interface Avatar {

        /** About 75 KB of image */
        int MAX_LENGTH = 100_000;

        String message() default "not a JPEG, PNG or WebP image in a data URL";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};

        final class Check implements ConstraintValidator<Avatar, String> {

            private static final java.util.regex.Pattern DATA_URL =
                    java.util.regex.Pattern.compile("data:image/(jpeg|png|webp);base64,([A-Za-z0-9+/]++={0,2})");

            @Override
            public boolean isValid(@Nullable String value, ConstraintValidatorContext context) {
                if (value == null || value.isEmpty()) {
                    return true;
                }
                if (value.length() > MAX_LENGTH) {
                    return false;
                }
                var match = DATA_URL.matcher(value);
                if (!match.matches()) {
                    return false;
                }
                byte[] image;
                try {
                    image = Base64.getDecoder().decode(match.group(2));
                } catch (IllegalArgumentException notBase64) {
                    return false;
                }
                return switch (match.group(1)) {
                    case "jpeg" -> startsWith(image, 0, 0xFF, 0xD8, 0xFF);
                    case "png" -> startsWith(image, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A);
                    default -> startsWith(image, 0, 'R', 'I', 'F', 'F') && startsWith(image, 8, 'W', 'E', 'B', 'P');
                };
            }

            private static boolean startsWith(byte[] bytes, int offset, int... expected) {
                if (bytes.length < offset + expected.length) {
                    return false;
                }
                for (var index = 0; index < expected.length; index++) {
                    if ((bytes[offset + index] & 0xFF) != expected[index]) {
                        return false;
                    }
                }
                return true;
            }
        }
    }
}

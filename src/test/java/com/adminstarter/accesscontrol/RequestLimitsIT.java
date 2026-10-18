package com.adminstarter.accesscontrol;

import static org.assertj.core.api.Assertions.assertThat;

import com.adminstarter.support.ApiIntegrationTest;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/** How much a request may bring, and what a profile picture may be (API.md, "Users"; P-039) */
class RequestLimitsIT extends ApiIntegrationTest {

    /** The eight bytes every PNG starts with, and a little more */
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};

    @Test
    void a_body_larger_than_a_megabyte_is_refused_before_it_is_read_even_without_signing_in() {
        var body = "{\"email\":\"someone@example.com\",\"password\":\"" + "x".repeat(1_100_000) + "\",\"remember\":false}";
        assertRefused(mvc.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body).exchange(),
                HttpStatus.BAD_REQUEST, "invalid_request");
    }

    @Test
    void importing_people_may_bring_more_up_to_five_megabytes() {
        var admin = admin();
        var viewer = roleId(admin, "Viewer");
        var rows = json.writeValueAsString(Map.of("users", List.of(draft(uniqueEmail(), viewer, "invited"))));
        // The same rows, padded with spaces past a megabyte
        var padded = rows.substring(0, rows.length() - 1) + " ".repeat(2_000_000) + "}";
        assertThat(send(HttpMethod.POST, "/api/users/import", admin, padded)).hasStatusOk();
        assertRefused(send(HttpMethod.POST, "/api/users", admin, padded), HttpStatus.BAD_REQUEST, "invalid_request");
        var tooLarge = rows.substring(0, rows.length() - 1) + " ".repeat(5_300_000) + "}";
        assertRefused(send(HttpMethod.POST, "/api/users/import", admin, tooLarge), HttpStatus.BAD_REQUEST, "invalid_request");
    }

    @Test
    void a_profile_picture_is_a_jpeg_png_or_webp_image_in_a_data_url() {
        var admin = admin();
        var viewer = roleId(admin, "Viewer");
        for (var avatar : List.of(
                // A link, which every browser showing the person would fetch
                "https://tracker.example/pixel.png",
                // An SVG, which can carry script
                "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(
                        "<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>".getBytes()),
                // Called a PNG, but a JPEG inside; and not base64
                "data:image/png;base64," + Base64.getEncoder().encodeToString(JPEG),
                "data:image/png;base64,not base64!",
                // Too large
                "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(JPEG) + "A".repeat(100_000))) {
            var draft = new HashMap<>(draft(uniqueEmail(), viewer, "invited"));
            draft.put("avatar", avatar);
            assertRefused(send(HttpMethod.POST, "/api/users", admin, draft), HttpStatus.BAD_REQUEST, "invalid_request");
        }

        var draft = new HashMap<>(draft(uniqueEmail(), viewer, "invited"));
        draft.put("avatar", "data:image/png;base64," + Base64.getEncoder().encodeToString(PNG));
        var id = body(send(HttpMethod.POST, "/api/users", admin, draft)).get("id").asString();
        var jpeg = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(JPEG);
        assertThat(send(HttpMethod.PATCH, "/api/users/" + id, admin, Map.of("avatar", jpeg))).hasStatusOk()
                .bodyJson().extractingPath("$.avatar").isEqualTo(jpeg);
        assertRefused(send(HttpMethod.PATCH, "/api/users/" + id, admin, Map.of("avatar", "https://tracker.example/pixel.png")),
                HttpStatus.BAD_REQUEST, "invalid_request");
        // Empty takes it away
        assertThat(send(HttpMethod.PATCH, "/api/users/" + id, admin, Map.of("avatar", ""))).hasStatusOk()
                .bodyJson().extractingPath("$.avatar").isEqualTo("");
    }

    @Test
    void a_name_or_department_may_be_left_out_of_a_change_but_not_emptied() {
        var admin = admin();
        var id = body(send(HttpMethod.POST, "/api/users", admin, draft(uniqueEmail(), roleId(admin, "Viewer"), "invited")))
                .get("id").asString();
        for (var field : List.of("firstName", "lastName", "department")) {
            assertRefused(send(HttpMethod.PATCH, "/api/users/" + id, admin, Map.of(field, "  ")), HttpStatus.BAD_REQUEST,
                    "invalid_request");
        }
        assertThat(send(HttpMethod.PATCH, "/api/users/" + id, admin, Map.of("phone", "0812345678"))).hasStatusOk()
                .bodyJson().extractingPath("$.firstName").isEqualTo("Test");
    }
}

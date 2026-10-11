package com.adminstarter.accesscontrol.common.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FieldChangeTest {

    @Test
    void a_value_that_changed_is_kept_as_text() {
        assertThat(FieldChange.diff("passwordMinLength", 8, 12)).contains(FieldChange.of("passwordMinLength", "8", "12"));
        assertThat(FieldChange.diff("active", true, false)).contains(FieldChange.of("active", "true", "false"));
        assertThat(FieldChange.diff("name", "Phuket", "Phuket")).isEmpty();
    }

    @Test
    void a_list_keeps_what_it_gained_and_lost_in_order() {
        var change = FieldChange.listDiff("allowedDomains", List.of("a.example", "b.example"), List.of("b.example", "d.example", "c.example"));

        assertThat(change).contains(FieldChange.list("allowedDomains", List.of("d.example", "c.example"), List.of("a.example")));
        // Order alone is no change
        assertThat(FieldChange.listDiff("allowedDomains", List.of("a", "b"), List.of("b", "a"))).isEmpty();
    }
}

package com.adminstarter.accesscontrol.settings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adminstarter.accesscontrol.AccessControlProperties;
import com.adminstarter.accesscontrol.authorization.service.RoleService;
import com.adminstarter.accesscontrol.settings.entity.AdminSettings;
import com.adminstarter.accesscontrol.settings.repository.AdminSettingsRepository;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** The settings of a new installation (DEFAULT_SETTINGS in the apps' catalog.ts) */
class SettingsInitializerTest {

    private final UUID viewer = UUID.randomUUID();
    private final UUID administrator = UUID.randomUUID();

    @Test
    void a_new_installation_requires_two_step_sign_in_of_administrators_and_signs_out_after_half_an_hour_idle() {
        var saved = initialize(Map.of());
        assertThat(saved.getTwoFactorRoleIds()).containsExactly(administrator);
        assertThat(saved.getSessionTimeoutMinutes()).isEqualTo(30);
        assertThat(saved.getDefaultRoleId()).isEqualTo(viewer);
    }

    @Test
    void the_roles_that_must_use_it_can_be_none_from_the_start() {
        assertThat(initialize(Map.of("access-control.bootstrap.two-factor-roles", "")).getTwoFactorRoleIds()).isEmpty();
    }

    private AdminSettings initialize(Map<String, String> configuration) {
        var properties = new Binder(new MapConfigurationPropertySource(configuration))
                .bindOrCreate("access-control", Bindable.of(AccessControlProperties.class));
        var repository = mock(AdminSettingsRepository.class);
        var roles = mock(RoleService.class);
        when(roles.findIdByCode("VIEWER")).thenReturn(Optional.of(viewer));
        when(roles.findIdByCode("ADMINISTRATOR")).thenReturn(Optional.of(administrator));
        new SettingsInitializer(repository, roles, properties, Clock.systemUTC()).run(null);
        var settings = ArgumentCaptor.forClass(AdminSettings.class);
        verify(repository).save(settings.capture());
        return settings.getValue();
    }
}

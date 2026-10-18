package com.adminstarter.accesscontrol.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/** What application-prod.yml must keep, for the protections that depend on it */
class ProductionConfigTest {

    private PropertySource<?> production;

    @BeforeEach
    void load() throws IOException {
        production = new YamlPropertySourceLoader().load("prod", new ClassPathResource("application-prod.yml")).getFirst();
    }

    @Test
    void only_the_proxies_named_may_say_who_the_client_is() {
        // Behind the proxy, the address and scheme come from X-Forwarded-*, believed from AC_TRUSTED_PROXIES alone,
        // which has no default: without it the server does not start (P-040)
        assertThat(production.getProperty("server.forward-headers-strategy")).isEqualTo("native");
        assertThat(production.getProperty("server.tomcat.remoteip.internal-proxies")).hasToString("${AC_TRUSTED_PROXIES}");
    }
}

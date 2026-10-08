package com.adminstarter.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** A throwaway PostgreSQL 17 for integration tests; Spring Boot points the datasource at it */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerSupport {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:17");
    }
}

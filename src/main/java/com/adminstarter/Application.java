package com.adminstarter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Sits in the root package, so Spring Boot finds the beans, entities and repositories of both trees below it: the
 * access-control foundation (com.adminstarter.accesscontrol) and this project's modules (com.adminstarter.app).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

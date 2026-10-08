package com.adminstarter.accesscontrol.config;

import com.adminstarter.accesscontrol.AccessControlProperties;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Caffeine caches for what every request looks up: an account's status and role ("accounts") and a role's
 * permissions ("role-permissions"). Evictions wait for the transaction to commit, so a request in between cannot
 * cache the old value again; the TTL bounds how long another server instance may lag.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    CacheManager cacheManager(AccessControlProperties properties) {
        var caffeine = new CaffeineCacheManager();
        caffeine.setCaffeine(Caffeine.newBuilder().expireAfterWrite(properties.permissionCacheTtl()).maximumSize(10_000));
        caffeine.setAllowNullValues(false);
        return new TransactionAwareCacheManagerProxy(caffeine);
    }
}

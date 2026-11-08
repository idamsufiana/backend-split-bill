package dev.settleup.service;

import dev.settleup.config.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Put on every service method that changes a group's ledger. Evicts that group's cached
 * settlement and category summary. The annotated method must have a parameter named
 * {@code groupId}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Caching(evict = {
        @CacheEvict(cacheNames = CacheConfig.SETTLEMENTS, key = "#groupId"),
        @CacheEvict(cacheNames = CacheConfig.CATEGORY_SUMMARIES, key = "#groupId")
})
public @interface EvictGroupCaches {
}

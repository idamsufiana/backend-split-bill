package dev.settleup.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.settleup.domain.dto.Responses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;

/**
 * Redis cache for the two expensive read models: the settlement plan and the category summary.
 *
 * <ul>
 *   <li><b>What is cached:</b> derived results only, keyed by group id. The database stays the
 *       source of truth; every write to a group evicts that group's entries
 *       ({@link dev.settleup.service.EvictGroupCaches}).</li>
 *   <li><b>Transaction-aware:</b> evictions run <em>after</em> the write commits. Evicting before
 *       commit would let a concurrent reader re-cache the old numbers in the gap.</li>
 *   <li><b>Typed JSON:</b> each cache has a serializer for its exact record type, so values are
 *       readable in {@code redis-cli} and nothing depends on Java serialization or embedded
 *       class names.</li>
 *   <li><b>Redis is optional:</b> cache errors are logged and the request falls through to the
 *       database, so a Redis outage slows responses down instead of breaking them.</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String SETTLEMENTS = "settlements";
    public static final String CATEGORY_SUMMARIES = "category-summaries";

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheCustomizer(ObjectMapper objectMapper,
                                                                   @Value("${app.cache-ttl:PT10M}") Duration ttl) {
        ObjectMapper mapper = objectMapper.copy();
        return builder -> builder
                .transactionAware()
                .withCacheConfiguration(SETTLEMENTS, typed(mapper, Responses.Settlement.class, ttl))
                .withCacheConfiguration(CATEGORY_SUMMARIES, typed(mapper, Responses.CategorySummary.class, ttl));
    }

    private static <T> RedisCacheConfiguration typed(ObjectMapper mapper, Class<T> type, Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .prefixCacheNameWith("settleup:")
                .serializeValuesWith(SerializationPair.fromSerializer(new Jackson2JsonRedisSerializer<>(mapper, type)));
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }

    /**
     * The database is in-memory and starts empty, but Redis outlives restarts. Without this,
     * a fresh "group 1" could be served the cached settlement of the previous run's "group 1".
     * With a persistent database this listener should be removed.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void clearCachesOnStartup(ApplicationReadyEvent event) {
        CacheManager cacheManager = event.getApplicationContext().getBean(CacheManager.class);
        for (String name : cacheManager.getCacheNames()) {
            Cache cache = cacheManager.getCache(name);
            try {
                if (cache != null) {
                    cache.clear();
                }
            } catch (RuntimeException e) {
                log.warn("Could not clear cache '{}' on startup (is Redis running?): {}", name, e.getMessage());
            }
        }
    }
}

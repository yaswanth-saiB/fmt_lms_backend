package com.fmt.fmt_backend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    /**
     * Caffeine cache manager.
     *
     * activeReviews — public homepage reviews, cached for 1 hour.
     * Evicted on any admin create / update / delete / reorder.
     * Max 1 entry (it's always a single list response).
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("activeReviews");
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(12, TimeUnit.HOURS)
                .maximumSize(1));
        return manager;
    }
}

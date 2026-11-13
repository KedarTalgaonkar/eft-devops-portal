package com.euronet.eft.portal.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Kept out of the main class so test slices (@WebMvcTest, @DataJpaTest) don't need a CacheManager or scheduler.
 * Cache names and spec: spring.cache.* in application.yml.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
@EnableScheduling
public class SchedulingAndCachingConfig {
}

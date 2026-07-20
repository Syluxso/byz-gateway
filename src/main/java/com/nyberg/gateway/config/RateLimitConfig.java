package com.nyberg.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {

    /**
     * Prefer Authorization token fingerprint when present, else client IP.
     */
    @Bean
    @Primary
    public KeyResolver byzKeyResolver() {
        return exchange -> {
            String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
            if (auth != null && !auth.isBlank()) {
                String token = auth.startsWith("Bearer ") ? auth.substring(7) : auth;
                int hash = token.hashCode();
                return Mono.just("token:" + Integer.toHexString(hash));
            }
            var remote = exchange.getRequest().getRemoteAddress();
            String ip = remote != null && remote.getAddress() != null
                    ? remote.getAddress().getHostAddress()
                    : "unknown";
            return Mono.just("ip:" + ip);
        };
    }

    @Bean
    public RedisRateLimiter redisRateLimiter(
            @Value("${byz.gateway.rate-limit.replenish-rate:40}") int replenishRate,
            @Value("${byz.gateway.rate-limit.burst-capacity:80}") int burstCapacity
    ) {
        return new RedisRateLimiter(replenishRate, burstCapacity);
    }
}

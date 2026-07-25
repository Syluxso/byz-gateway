package com.nyberg.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyberg.gateway.auth.AccessJwtClaims;
import com.nyberg.gateway.auth.ApiKeyJwtClaims;
import com.nyberg.gateway.events.ApiUsageEvent;
import com.nyberg.gateway.events.ApiUsageKafkaPublisher;
import com.nyberg.gateway.events.GatewayAccessEvent;
import com.nyberg.gateway.events.GatewayAccessKafkaPublisher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Emits best-effort Kafka facts after each proxied request completes.
 * Skips actuator to avoid health-check noise.
 */
@Component
public class AccessLogGlobalFilter implements GlobalFilter, Ordered {

    private final ObjectProvider<GatewayAccessKafkaPublisher> accessPublisher;
    private final ObjectProvider<ApiUsageKafkaPublisher> usagePublisher;
    private final ObjectMapper objectMapper;

    public AccessLogGlobalFilter(
            ObjectProvider<GatewayAccessKafkaPublisher> accessPublisher,
            ObjectProvider<ApiUsageKafkaPublisher> usagePublisher,
            ObjectMapper objectMapper
    ) {
        this.accessPublisher = accessPublisher;
        this.usagePublisher = usagePublisher;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getRawPath();
        if (path != null && path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }

        long startedNanos = System.nanoTime();
        return chain.filter(exchange).doFinally(signal -> emit(exchange, startedNanos));
    }

    private void emit(ServerWebExchange exchange, long startedNanos) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getRawPath();
        String requestId = exchange.getResponse().getHeaders().getFirst(RequestIdGlobalFilter.HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = request.getHeaders().getFirst(RequestIdGlobalFilter.HEADER);
        }

        Integer statusCode = exchange.getResponse().getStatusCode() != null
                ? exchange.getResponse().getStatusCode().value()
                : 0;

        String routeId = null;
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route != null) {
            routeId = route.getId();
        }

        String clientIp = null;
        if (request.getRemoteAddress() != null && request.getRemoteAddress().getAddress() != null) {
            clientIp = request.getRemoteAddress().getAddress().getHostAddress();
        }

        long durationMs = (System.nanoTime() - startedNanos) / 1_000_000L;
        String method = request.getMethod() != null ? request.getMethod().name() : null;
        String occurredAt = Instant.now().toString();

        String authorization = request.getHeaders().getFirst("Authorization");
        AccessJwtClaims.Claims accessClaims = AccessJwtClaims.parse(objectMapper, authorization);
        String organizationId = accessClaims != null ? accessClaims.organizationId() : null;
        String clientId = accessClaims != null ? accessClaims.clientId() : null;

        GatewayAccessKafkaPublisher accessKafka = accessPublisher.getIfAvailable();
        if (accessKafka != null) {
            GatewayAccessEvent access = new GatewayAccessEvent(
                    UUID.randomUUID().toString(),
                    GatewayAccessEvent.TYPE,
                    occurredAt,
                    requestId,
                    method,
                    path,
                    statusCode,
                    durationMs,
                    clientIp,
                    routeId,
                    organizationId,
                    clientId
            );
            accessKafka.publishAsync(access);
        }

        ApiUsageKafkaPublisher usageKafka = usagePublisher.getIfAvailable();
        if (usageKafka == null) {
            return;
        }

        ApiKeyJwtClaims.ApiKeyClaims apiKey = ApiKeyJwtClaims.parseIfApiKey(objectMapper, authorization);
        if (apiKey == null) {
            return;
        }

        ApiUsageEvent usage = new ApiUsageEvent(
                UUID.randomUUID().toString(),
                ApiUsageEvent.TYPE,
                occurredAt,
                apiKey.organizationId(),
                apiKey.tokenId(),
                apiKey.appId(),
                apiKey.grantType(),
                apiKey.userId(),
                apiKey.tenantId(),
                method,
                path,
                statusCode,
                durationMs
        );
        usageKafka.publishAsync(usage);
    }

    @Override
    public int getOrder() {
        // After RequestIdGlobalFilter so X-Request-Id is already set.
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}

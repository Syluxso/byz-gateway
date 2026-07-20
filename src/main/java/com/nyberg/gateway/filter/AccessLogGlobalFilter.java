package com.nyberg.gateway.filter;

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
 * Emits a best-effort access event to Kafka after each proxied request completes.
 * Skips actuator to avoid health-check noise.
 */
@Component
public class AccessLogGlobalFilter implements GlobalFilter, Ordered {

    private final ObjectProvider<GatewayAccessKafkaPublisher> publisher;

    public AccessLogGlobalFilter(ObjectProvider<GatewayAccessKafkaPublisher> publisher) {
        this.publisher = publisher;
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
        GatewayAccessKafkaPublisher kafka = publisher.getIfAvailable();
        if (kafka == null) {
            return;
        }

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

        GatewayAccessEvent event = new GatewayAccessEvent(
                UUID.randomUUID().toString(),
                GatewayAccessEvent.TYPE,
                Instant.now().toString(),
                requestId,
                request.getMethod() != null ? request.getMethod().name() : null,
                path,
                statusCode,
                durationMs,
                clientIp,
                routeId
        );
        kafka.publishAsync(event);
    }

    @Override
    public int getOrder() {
        // After RequestIdGlobalFilter so X-Request-Id is already set.
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}

package com.nyberg.gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Gateway-wide CORS for browsers (Admin health probes, CCC, etc.).
 *
 * <p>Do not use {@code spring.cloud.gateway.globalcors} — its CorsWebFilter can
 * 403 OPTIONS before route filters run.
 *
 * <p>OPTIONS: answered here (no proxy / rate limit).
 * <p>Actuator: served by the gateway process itself — {@code GlobalFilter}s do
 * not run, so CORS must be applied here via {@code beforeCommit}. Using
 * {@code doOnSuccess} is too late (headers already sent) and leaves Admin
 * System Health showing "API Gateway unreachable".
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OptionsCorsWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            GatewayCorsSupport.apply(exchange);
            exchange.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
            return exchange.getResponse().setComplete();
        }

        if (pathStartsWithActuator(exchange)) {
            exchange.getResponse().beforeCommit(() -> {
                GatewayCorsSupport.apply(exchange);
                return Mono.empty();
            });
        }

        return chain.filter(exchange);
    }

    private static boolean pathStartsWithActuator(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getRawPath();
        return path != null && path.startsWith("/actuator");
    }
}

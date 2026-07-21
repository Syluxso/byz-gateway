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
 * Other methods: ensure a single ACAO on the response, including local
 * {@code /actuator/**} (GlobalFilters do not run for Actuator).
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

        // CORS for proxied responses is applied in DedupeCorsGlobalFilter.beforeCommit
        // (strip upstream + single ACAO). Actuator bypasses GlobalFilters, so apply here.
        if (pathStartsWithActuator(exchange)) {
            return chain.filter(exchange)
                    .doOnSuccess(v -> GatewayCorsSupport.apply(exchange))
                    .doOnError(e -> GatewayCorsSupport.apply(exchange));
        }
        return chain.filter(exchange);
    }

    private static boolean pathStartsWithActuator(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getRawPath();
        return path != null && path.startsWith("/actuator");
    }
}

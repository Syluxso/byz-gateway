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
 * Handle CORS preflight before Spring Cloud Gateway {@code CorsWebFilter}.
 * That filter uses {@code Ordered.HIGHEST_PRECEDENCE} and was returning 403 on
 * OPTIONS when {@code globalcors} rejected the request — GlobalFilters never ran.
 *
 * We answer OPTIONS here and never proxy preflight to backends or Redis rate limit.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OptionsCorsWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (exchange.getRequest().getMethod() != HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }
        GatewayCorsSupport.apply(exchange);
        exchange.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
        return exchange.getResponse().setComplete();
    }
}

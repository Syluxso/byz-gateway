package com.nyberg.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Answer CORS preflight at the gateway and do not proxy OPTIONS to backends
 * (backends often 403) or run it through Redis rate limiting.
 */
@Component
public class OptionsCorsGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (exchange.getRequest().getMethod() != HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        GatewayCorsSupport.apply(exchange);
        exchange.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        // Before rate limiter / routing
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

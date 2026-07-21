package com.nyberg.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Upstream services also set CORS. Strip theirs and apply one gateway set so the
 * browser never sees duplicate {@code Access-Control-Allow-Origin}.
 *
 * <p>Uses {@code beforeCommit} instead of a {@code ServerHttpResponseDecorator}
 * that wraps {@code writeWith} — decorator wrapping has caused truncated /
 * closed responses (curl 18) for some proxied bodies (notably 201 JSON).
 */
@Component
public class DedupeCorsGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        exchange.getResponse().beforeCommit(() -> {
            GatewayCorsSupport.stripUpstream(exchange.getResponse().getHeaders());
            GatewayCorsSupport.apply(exchange);
            return Mono.empty();
        });
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        // Early so beforeCommit is registered before the response is written.
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}

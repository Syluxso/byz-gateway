package com.nyberg.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Upstream services also set CORS. Strip theirs and apply one gateway set so the
 * browser never sees duplicate {@code Access-Control-Allow-Origin}.
 */
@Component
public class DedupeCorsGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponseDecorator decorated = new ServerHttpResponseDecorator(exchange.getResponse()) {
            @Override
            public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends org.springframework.core.io.buffer.DataBuffer> body) {
                normalize();
                return super.writeWith(body);
            }

            @Override
            public Mono<Void> writeAndFlushWith(
                    org.reactivestreams.Publisher<? extends org.reactivestreams.Publisher<? extends org.springframework.core.io.buffer.DataBuffer>> body) {
                normalize();
                return super.writeAndFlushWith(body);
            }

            @Override
            public Mono<Void> setComplete() {
                normalize();
                return super.setComplete();
            }

            private void normalize() {
                GatewayCorsSupport.stripUpstream(getDelegate().getHeaders());
                GatewayCorsSupport.apply(exchange);
            }
        };

        return chain.filter(exchange.mutate().response(decorated).build());
    }

    @Override
    public int getOrder() {
        // Before NettyWriteResponseFilter (-1)
        return -2;
    }
}

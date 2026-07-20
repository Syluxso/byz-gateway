package com.nyberg.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Proxied backends often add CORS headers; gateway {@code globalcors} adds another set.
 * Browsers reject responses with duplicate {@code Access-Control-Allow-Origin}.
 * Keep a single value for each CORS header (first wins).
 */
@Component
public class DedupeCorsGlobalFilter implements GlobalFilter, Ordered {

    private static final List<String> CORS_HEADERS = List.of(
            HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
            HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
            HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
            HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
            HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
            HttpHeaders.ACCESS_CONTROL_MAX_AGE
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).doOnSuccess(v -> dedupeCors(exchange.getResponse().getHeaders()));
    }

    static void dedupeCors(HttpHeaders headers) {
        for (String name : CORS_HEADERS) {
            List<String> values = headers.get(name);
            if (values != null && values.size() > 1) {
                headers.put(name, List.of(values.get(0)));
            }
        }
    }

    @Override
    public int getOrder() {
        // Just before NettyWriteResponseFilter (-1)
        return -2;
    }
}

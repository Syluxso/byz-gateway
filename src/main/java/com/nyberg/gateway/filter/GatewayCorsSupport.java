package com.nyberg.gateway.filter;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;

/**
 * Single place for gateway CORS headers. Backends must not win (duplicate ACAO).
 */
final class GatewayCorsSupport {

    /** Explicit list — browsers reject {@code *} with {@code Allow-Credentials: true}. */
    private static final String ALLOW_HEADERS =
            "Authorization, Content-Type, Accept, Origin, X-Requested-With, X-Request-Id";

    private GatewayCorsSupport() {}

    static void apply(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        HttpHeaders headers = response.getHeaders();

        String origin = request.getHeaders().getOrigin();
        if (origin == null || origin.isBlank()) {
            // Non-browser / same-origin probes — no CORS headers needed.
            return;
        }

        stripUpstream(headers);

        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        headers.set(HttpHeaders.VARY, HttpHeaders.ORIGIN);
        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,PUT,PATCH,DELETE,OPTIONS,HEAD");

        String reqHeaders = request.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS);
        if (reqHeaders != null && !reqHeaders.isBlank()) {
            headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, reqHeaders);
        } else {
            headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, ALLOW_HEADERS);
        }
        headers.set(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "X-Request-Id");
        headers.set(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600");
    }

    /** Remove CORS headers contributed by upstream services. */
    static void stripUpstream(HttpHeaders headers) {
        headers.remove(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
        headers.remove(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS);
        headers.remove(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS);
        headers.remove(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS);
        headers.remove(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS);
        headers.remove(HttpHeaders.ACCESS_CONTROL_MAX_AGE);
    }
}

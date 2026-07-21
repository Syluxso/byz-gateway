package com.nyberg.gateway.filter;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;

/**
 * Single place for gateway CORS headers. Backends must not win (duplicate ACAO).
 */
final class GatewayCorsSupport {

    private GatewayCorsSupport() {}

    static void apply(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        HttpHeaders headers = response.getHeaders();

        String origin = request.getHeaders().getOrigin();
        if (origin == null || origin.isBlank()) {
            origin = "*";
        }

        // Clear any values already present (e.g. from a backend or globalcors).
        headers.remove(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
        headers.remove(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS);
        headers.remove(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS);
        headers.remove(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS);
        headers.remove(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS);
        headers.remove(HttpHeaders.ACCESS_CONTROL_MAX_AGE);
        headers.remove(HttpHeaders.VARY);

        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        if (!"*".equals(origin)) {
            headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
            headers.set(HttpHeaders.VARY, HttpHeaders.ORIGIN);
        }
        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,PUT,PATCH,DELETE,OPTIONS,HEAD");

        String reqHeaders = request.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS);
        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                (reqHeaders != null && !reqHeaders.isBlank()) ? reqHeaders : "*");
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

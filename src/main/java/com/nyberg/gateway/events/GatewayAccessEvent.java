package com.nyberg.gateway.events;

/**
 * Access log fact emitted on {@code byz.gateway.access}.
 * No Authorization, cookies, query string, or body.
 */
public record GatewayAccessEvent(
        String eventId,
        String type,
        String occurredAt,
        String requestId,
        String method,
        String path,
        int status,
        long durationMs,
        String clientIp,
        String routeId
) {
    public static final String TYPE = "gateway.request.completed";
}

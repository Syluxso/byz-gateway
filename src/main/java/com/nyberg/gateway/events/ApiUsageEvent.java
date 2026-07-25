package com.nyberg.gateway.events;

/**
 * Metering fact emitted on {@code byz.api.usage} for API-key JWTs only.
 * No Authorization, cookies, query string, or body.
 */
public record ApiUsageEvent(
        String eventId,
        String type,
        String occurredAt,
        String organizationId,
        String tokenId,
        String appId,
        String grantType,
        String userId,
        String tenantId,
        String method,
        String path,
        int status,
        long durationMs
) {
    public static final String TYPE = "api.request";
}

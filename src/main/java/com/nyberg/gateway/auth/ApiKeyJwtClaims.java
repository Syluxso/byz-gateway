package com.nyberg.gateway.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

/**
 * Unverified JWT payload peek for metering. Gateway does not validate signatures;
 * backends still enforce auth. Only API-key grant types are returned.
 */
public final class ApiKeyJwtClaims {

    public static final String GRANT_USER = "user_api_key";
    public static final String GRANT_TENANT = "tenant_api_key";

    private static final Set<String> API_KEY_GRANTS = Set.of(GRANT_USER, GRANT_TENANT);
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private ApiKeyJwtClaims() {}

    /**
     * @return claims if Bearer is a JWT with API-key grant and required metering fields; else null
     */
    public static ApiKeyClaims parseIfApiKey(ObjectMapper mapper, String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }
        String token = authorizationHeader;
        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = token.substring(7).trim();
        }
        if (token.isEmpty() || token.startsWith("byz_sk_") || token.chars().filter(c -> c == '.').count() < 2) {
            return null;
        }

        int first = token.indexOf('.');
        int second = token.indexOf('.', first + 1);
        if (first < 0 || second < 0) {
            return null;
        }

        try {
            String payloadB64 = token.substring(first + 1, second);
            byte[] json = Base64.getUrlDecoder().decode(payloadB64);
            Map<String, Object> claims = mapper.readValue(new String(json, StandardCharsets.UTF_8), MAP);

            String grantType = asString(claims.get("grant_type"));
            if (grantType == null || !API_KEY_GRANTS.contains(grantType)) {
                return null;
            }

            String tokenId = asString(claims.get("token_id"));
            String appId = asString(claims.get("app_id"));
            String organizationId = asString(claims.get("organization_id"));
            if (tokenId == null || appId == null || organizationId == null) {
                return null;
            }

            return new ApiKeyClaims(
                    organizationId,
                    tokenId,
                    appId,
                    grantType,
                    asString(claims.get("user_id")),
                    asString(claims.get("tenant_id"))
            );
        } catch (Exception e) {
            return null;
        }
    }

    private static String asString(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() || "null".equals(s) ? null : s;
    }

    public record ApiKeyClaims(
            String organizationId,
            String tokenId,
            String appId,
            String grantType,
            String userId,
            String tenantId
    ) {}
}

# byz-gateway

Lightweight edge for Byzantine: **path routing**, **X-Request-Id**, **Redis rate limit**, **Kafka access events**.
JWT validation stays on each backend service.

| Setting | Local |
|---------|--------|
| HTTP | `8096` |
| Redis | `127.0.0.1:6379` |
| Kafka | `localhost:9092` |

## Public path map

| Gateway path | Backend |
|--------------|---------|
| `/iam/**` | IAM (`8082`) |
| `/notifications/**` | Notifications (`8081`) |
| `/directory/**` | Directory (`8086`) |
| `/events/**` | Events (`8088`) |
| `/files/**` | Files (`8089`) |

Example: `GET http://localhost:8096/iam/actuator/health` → IAM.

## Run locally

```bash
# from projects/db
docker compose up -d byz-redis byz-kafka

cd ../byz-gateway
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## Rate limit

Default: **40 req/s replenish**, burst **80**, keyed by Bearer token hash or client IP.
Env: `RATE_LIMIT_REPLENISH`, `RATE_LIMIT_BURST`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`.

## Request id

If the client omits `X-Request-Id`, the gateway generates a UUID, forwards it upstream, and returns it on the response.

## CORS

Browsers call `api.byzantineapp.dev/{service}/…` with `Authorization`. That requires a working preflight.

**Do not use** `spring.cloud.gateway.globalcors` — its `CorsWebFilter` runs at `HIGHEST_PRECEDENCE` and returns **403** on rejected OPTIONS before route filters run (empty body, `Vary: Origin,…`).

Instead:

- `OptionsCorsWebFilter` — answers OPTIONS; sets ACAO on local `/actuator/**` via `beforeCommit` (needed for Admin System Health — `doOnSuccess` is too late)
- `DedupeCorsGlobalFilter` — strips upstream `Access-Control-*` on proxied routes and re-applies one gateway ACAO via `beforeCommit`
- YAML `DedupeResponseHeader` + `RemoveRequestHeader=Expect` kept as safety nets

After deploy, verify:

```bash
# Admin System Health — GET must include ACAO (not just OPTIONS):
curl -i 'https://api.byzantineapp.dev/actuator/health' \
  -H 'Origin: https://admin.byzantineapp.dev'
# Expect: 200 + Access-Control-Allow-Origin: https://admin.byzantineapp.dev

# In-app create via gateway (should return 201, not curl 18):
# curl -i -X POST 'https://api.byzantineapp.dev/notifications/api/v1/notifications/in-app' \
#   -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
#   -d '{"userId":"...","title":"t","message":"m"}'

curl -i -X OPTIONS 'https://api.byzantineapp.dev/notifications/api/v1/notifications' \
  -H 'Origin: https://claritasclassicalcommunity.org' \
  -H 'Access-Control-Request-Method: GET' \
  -H 'Access-Control-Request-Headers: authorization'
```

Expect **204** and a **single** `Access-Control-Allow-Origin` matching the Origin.

## Kafka access events

After each proxied request (not `/actuator/**`), the gateway best-effort publishes `gateway.request.completed` to topic **`byz.gateway.access`** (key = `X-Request-Id`). Failures are logged at debug and never fail the HTTP response.

Env: `KAFKA_BOOTSTRAP`, `BYZ_KAFKA_ENABLED` (set `false` if the broker is down). Create the topic via events-service bootstrap (`POST /api/v1/topics/bootstrap`) — do not rely on auto-create in prod.

Contract: `projects/events-service/docs/EVENTS.md`.

## Prod env (sketch)

Redis on this host (already provisioned):

- bind `127.0.0.1` only (not public)
- `redis://127.0.0.1:6379`
- maxmemory ~128MB, `allkeys-lru`
- no password required for local socket/loopback use

```bash
PORT=8096
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
# leave REDIS_PASSWORD unset
KAFKA_BOOTSTRAP=localhost:9092
BYZ_KAFKA_ENABLED=true
BYZ_IAM_URI=http://127.0.0.1:8082
BYZ_NOTIFICATIONS_URI=http://127.0.0.1:8081
BYZ_DIRECTORY_URI=http://127.0.0.1:8086
BYZ_EVENTS_URI=http://127.0.0.1:8088
BYZ_FILES_URI=http://127.0.0.1:8089
```

Put nginx/TLS in front of `:8096` (e.g. `api.byzantineapp.dev`). Gateway and Redis must stay on the same box (or private network) since Redis is loopback-only.

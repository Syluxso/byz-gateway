# byz-gateway

Lightweight edge for Byzantine: **path routing**, **X-Request-Id**, **Redis rate limit**.
JWT validation stays on each backend service.

| Setting | Local |
|---------|--------|
| HTTP | `8096` |
| Redis | `127.0.0.1:6379` |

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
docker compose up -d byz-redis

cd ../byz-gateway
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## Rate limit

Default: **40 req/s replenish**, burst **80**, keyed by Bearer token hash or client IP.
Env: `RATE_LIMIT_REPLENISH`, `RATE_LIMIT_BURST`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`.

## Request id

If the client omits `X-Request-Id`, the gateway generates a UUID, forwards it upstream, and returns it on the response.

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
BYZ_IAM_URI=http://127.0.0.1:8082
BYZ_NOTIFICATIONS_URI=http://127.0.0.1:8081
BYZ_DIRECTORY_URI=http://127.0.0.1:8086
BYZ_EVENTS_URI=http://127.0.0.1:8088
BYZ_FILES_URI=http://127.0.0.1:8089
```

Put nginx/TLS in front of `:8096` (e.g. `api.byzantineapp.dev`). Gateway and Redis must stay on the same box (or private network) since Redis is loopback-only.

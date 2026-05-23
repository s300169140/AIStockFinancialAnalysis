# Server — AIStockFinancialAnalysis

Fastify + Prisma + Postgres. Proxies DeepSeek with streaming SSE; gates on a 3-call device trial or active Play Billing subscription.

## Endpoints

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/health` | – | Liveness |
| GET | `/tickers` | – | Allowlist (SPY 500 ∪ NDX 100) |
| GET | `/status` | device, optional bearer | Trial counter + subscription state |
| POST | `/auth/google` | device | Exchange Google ID token → session JWT |
| POST | `/analyze` | device, optional bearer | **SSE** stream of analysis |
| POST | `/billing/verify` | bearer | Verify Play purchase token, save sub |
| GET | `/billing/status` | bearer | Read current sub |
| POST | `/billing/rtdn` | shared-secret query param | Pub/Sub push for Play RTDN |

All non-RTDN requests need `X-Device-Id: <8-128 char id>`. Authenticated requests also send `Authorization: Bearer <session-jwt>`.

## Local dev

```bash
cp .env.example .env       # fill in secrets
npm install
npx prisma migrate dev --name init
npm run dev
```

## Production deploy

```bash
npm ci --omit=dev
npm run build
npx prisma migrate deploy
NODE_ENV=production node dist/server.js
```

Run behind nginx with TLS; point your `API_BASE_URL` (Android) at it.

## SSE stream format

```
event: meta
data: {"ticker":"AAPL","gate":{"mode":"trial","remaining":2}}

event: delta
data: {"text":"## 1. Wall Street Analysis\n\n..."}

event: delta
data: {"text":" Apple's business model..."}

event: done
data: {"ok":true}
```

Error events look like `event: error\ndata: {"code":"DEEPSEEK_ERROR"}`. The trial slot is refunded automatically on error.

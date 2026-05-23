# AIStockFinancialAnalysis

AI-powered stock analysis for SPY 500 and top NASDAQ-100 tickers. Enter a symbol, get a 10-section Wall Street-style report streamed back from DeepSeek.

- **Android app** — Kotlin + Jetpack Compose, Google Sign-In, Play Billing v7
- **Server** — Node + Fastify + Prisma + Postgres, DeepSeek streaming proxy
- **Trial** — 3 free analyses per device, then $9.99/mo PRO subscription

## Layout

```
android/    Android app (publish to Google Play)
server/     Backend API (deploy to a VPS)
.github/    CI workflows
```

## Quickstart

See [DEPLOY.md](./DEPLOY.md) for full release setup. TL;DR:

1. **Server:** `cd server && npm install && npx prisma migrate dev && npm run dev`
2. **Android:** Open `android/` in Android Studio → set `API_BASE_URL` in `local.properties` → Run

## Architecture

```
Android app ──HTTPS──▶ Fastify ──▶ DeepSeek API
                          │
                          ├──▶ Postgres (users, devices, trials, subs)
                          ├──▶ Google Sign-In token verify
                          └──▶ Google Play Developer API (verify purchase tokens)
                                   ▲
                                   │ RTDN
                          Google Pub/Sub
```

The DeepSeek API key and analysis prompt template never leave the server.

## License

MIT

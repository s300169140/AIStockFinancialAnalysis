# DEPLOY — AIStockFinancialAnalysis

End-to-end checklist to ship to Google Play.

## 0. Prereqs

- Google Play Console account (one-time $25 fee)
- Google Cloud project (free tier is fine)
- A VPS to host the server (or Render/Fly/Railway)
- A domain pointed at your VPS (e.g. `api.your-domain.com`)
- A DeepSeek API key from <https://platform.deepseek.com>

---

## 1. Google Cloud setup (OAuth + service account)

### a. Create project and enable APIs
1. Go to <https://console.cloud.google.com> → create a project (e.g. "AIStockAnalysis").
2. Enable **Google Play Android Developer API**:
   `APIs & Services → Library → search "Google Play Android Developer" → Enable`

### b. Create OAuth 2.0 Client IDs
You need TWO client IDs in the same project:

**Web client ID** (server-side, also passed to Android SDK as `serverClientId`)
1. `APIs & Services → Credentials → Create Credentials → OAuth client ID → Web application`
2. Name: `AIStockAnalysis Web`
3. No redirect URIs needed.
4. Copy the client ID — this is `GOOGLE_WEB_CLIENT_ID`.

**Android client ID** (tells Google which app may sign in)
1. `Create Credentials → OAuth client ID → Android`
2. Package name: `com.aistock.analysis`
3. SHA-1 fingerprint:
   - For local debug: `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`
   - For Play release: from **Play Console → Setup → App integrity → App signing key certificate → SHA-1**
4. Repeat to add both fingerprints if testing local debug builds too.

> Configure the OAuth consent screen first (`APIs & Services → OAuth consent screen`). External, name "AI Stock Analysis", support email = your email. Add scopes: `openid`, `profile`, `email`. Add yourself as a test user while in testing mode.

### c. Service account for Play Developer API
1. `IAM & Admin → Service Accounts → Create service account`
2. Name: `play-billing-verifier`
3. Skip roles in IAM step.
4. Open the service account → **Keys → Add Key → JSON** → download. Save as `service-account.json` on your server.
5. In Play Console: `Setup → API access → Link your Google Cloud project → ensure the service account appears → grant: View financial data + Manage orders and subscriptions`.

---

## 2. Google Play Console setup

### a. Create the app
1. `All apps → Create app`. Name "AI Stock Analysis", default language, App type = App, paid? Free (with in-app subscription).
2. Walk through "Set up your app" tasks (privacy policy, target audience, ads = no, data safety, content rating).

### b. Create the subscription
1. `Monetize → Products → Subscriptions → Create subscription`
2. Product ID: **`pro_monthly`** (must match `PRO_PRODUCT_ID` in server `.env` and `PRO_PRODUCT_ID` in `local.properties`)
3. Name: "PRO"
4. Add a **base plan**: `monthly`, auto-renewing, billing period 1 month, price $9.99.

### c. Real-Time Developer Notifications (RTDN)
1. `Monetize → Monetization setup → Cloud Pub/Sub topic`
2. Create a topic in your Google Cloud project: e.g. `play-billing-rtdn`.
3. Grant `gcp-sa-androidpublisher@system.gserviceaccount.com` the **Pub/Sub Publisher** role on this topic.
4. Create a **push subscription** with endpoint URL:
   `https://api.your-domain.com/billing/rtdn?key=<RTDN_SHARED_SECRET>`
5. Paste the topic name into Play Console.

### d. Internal testing track
1. `Testing → Internal testing → Create new release`.
2. Upload your AAB once built (see §4).
3. Add yourself + a tester email under "Testers".

---

## 3. Deploy the server

### a. Provision
- Any VPS with Node 20+. Postgres can be on the same box (`apt install postgresql`).
- Open ports 80/443 in your firewall.

### b. Configure
```bash
git clone https://github.com/s300169140/AIStockFinancialAnalysis.git
cd AIStockFinancialAnalysis/server
cp .env.example .env
# Fill in: DATABASE_URL, JWT_SECRET, DEEPSEEK_API_KEY, GOOGLE_WEB_CLIENT_ID,
# GOOGLE_SERVICE_ACCOUNT_JSON (path), ANDROID_PACKAGE_NAME, RTDN_SHARED_SECRET
```

Generate `JWT_SECRET`:
```bash
openssl rand -hex 64
```

### c. Install + migrate
```bash
npm ci
npx prisma migrate deploy
npm run build
```

### d. Run under PM2 (or systemd)
```bash
npm i -g pm2
pm2 start dist/server.js --name aistock-api -i 2
pm2 save && pm2 startup
```

### e. nginx in front
```nginx
server {
  server_name api.your-domain.com;
  listen 443 ssl http2;

  # TLS via certbot — separate setup
  ssl_certificate /etc/letsencrypt/live/api.your-domain.com/fullchain.pem;
  ssl_certificate_key /etc/letsencrypt/live/api.your-domain.com/privkey.pem;

  location / {
    proxy_pass http://127.0.0.1:3001;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;

    # SSE-friendly
    proxy_buffering off;
    proxy_cache off;
    proxy_read_timeout 5m;
    chunked_transfer_encoding on;
  }
}
```

Test with:
```bash
curl https://api.your-domain.com/health
curl -H "X-Device-Id: test_device_abcdefgh" https://api.your-domain.com/tickers | jq '.data | length'
```

---

## 4. Build the signed Android release

### a. Generate a release keystore (one time, KEEP SAFE — losing it locks you out of Play updates)
```bash
keytool -genkey -v \
  -keystore release.keystore \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias release
```

### b. Configure local.properties (DO NOT COMMIT)
```properties
sdk.dir=/Users/you/Library/Android/sdk
API_BASE_URL=https://api.your-domain.com
GOOGLE_WEB_CLIENT_ID=xxx.apps.googleusercontent.com
PRO_PRODUCT_ID=pro_monthly
```

### c. Configure keystore.properties (DO NOT COMMIT)
```properties
storeFile=/absolute/path/to/release.keystore
storePassword=...
keyAlias=release
keyPassword=...
```

### d. Build
```bash
cd android
./gradlew :app:bundleRelease
# Output: app/build/outputs/bundle/release/app-release.aab
```

Or trigger CI by tagging:
```bash
git tag android-v1.0.0
git push origin android-v1.0.0
```
Then download the AAB from the Actions artifact.

### Required GitHub secrets (for CI builds)
- `RELEASE_KEYSTORE_BASE64` — `base64 -i release.keystore | pbcopy`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`
- `API_BASE_URL`
- `GOOGLE_WEB_CLIENT_ID`
- `PRO_PRODUCT_ID` (optional, defaults to `pro_monthly`)

---

## 5. Upload to Play Console

1. `Testing → Internal testing → Create new release → Upload` the AAB.
2. Save → Review → Roll out to Internal testing.
3. Install on your test device via the **opt-in URL** Play shows you (must sign in with a tester email).
4. Verify trial works (3 free calls) → Verify paywall triggers → Verify Play subscription completes → Verify analyses become unlimited.
5. When stable: promote Internal → Closed → Open → Production.

---

## 6. Privacy / data-safety claims you must declare

You collect:
- **Email** + **Name** + **Profile photo URL** (Google Sign-In, for PRO users) — purpose: Account management
- **Device or other IDs** (your generated device UUID) — purpose: App functionality (trial gate)
- **Purchases** (via Google Play) — purpose: App functionality
- **App activity** (which tickers were analyzed) — purpose: Analytics

All data is encrypted in transit. Be honest in Play Console's Data Safety form — Google audits this.

---

## 7. Things to do before going public

- Replace placeholder app icon with a real one (`app/src/main/res/drawable/ic_launcher_*.xml`).
- Add screenshots (phone + 7" + 10" tablets) to the Play Console listing.
- Write a privacy policy (sample: <https://app-privacy-policy-generator.firebaseapp.com/>) and host it.
- Decide a **support email** and add it to the Play listing.
- Add prominent **"Not financial advice"** disclaimer (already in the app — keep it there).
- Set `versionCode` and `versionName` in `app/build.gradle.kts` for each release. Play rejects re-uploads of the same `versionCode`.

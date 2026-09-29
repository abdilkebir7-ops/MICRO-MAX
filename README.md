# MICRO-MAX V3.0.1 — MikroTik Manager + Smart Cards (admin-only)

نسخة مفتوحة من MICRO-MAX تركز على MikroTik والكروت والمتجر، بدون اشتراك إجباري أو بوابة دفع مطلوبة.

## V3.0.1 — Release hardening and live dashboard
- GitHub Actions now accepts either the full source tree or the complete source ZIP uploaded at repository root.
- Source layout is verified before Java/Gradle setup, preventing the previous `cache-dependency-path` failure.
- Release artifacts include the signed AAB and R8/ProGuard mapping file for safer troubleshooting.
- Dashboard auto-refreshes every 30 seconds while the main page is open.
- Android versionCode/versionName are now `301` / `3.0.1`.


## الجديد في V2.6
- Card Studio: تحديد رابط HotSpot الحقيقي الذي يدخل داخل QR.
- QR وBarcode والطباعة يعملان بدون Binance أو أي اشتراك.
- طباعة مجموعة حتى 24 كرتًا في ورقة A4 (6 كروت/صفحة).
- طباعة بطاقة منفردة مع QR وCode 128.
- المتجر مفتوح، وتفعيل العناصر مجاني داخل الحساب.
- الدفع الإلكتروني مفصول ويمكن إضافته لاحقًا.
- تمت إضافة ملفات Gradle كاملة للمشروع حتى يصبح بناء Android واضحًا عبر CI أو Gradle 8.11.1.

## Backend
```bash
cd backend
cp .env.example .env
npm install
npm start
```

## Android
المشروع يستخدم JDK 17 وAndroid Gradle Plugin 8.7.3 وKotlin 2.0.21.

عنوان Backend الافتراضي للمحاكي:
`http://10.0.2.2:8080`

يمكن تغييره في `app/build.gradle.kts` عبر `MICROMAX_API_BASE_URL`.

## الدفع
Binance Pay/NITA غير مطلوبين لتشغيل المميزات الأساسية. مفاتيح الدفع لا توضع داخل Android.

## V2.7 — HotSpot Profiles
- إدارة HotSpot User Profiles لكل MikroTik.
- إنشاء/تحديث Profile من التطبيق مع السعر والمدة وRate Limit وSession/Idle Timeout وShared Users.
- التحقق من وجود الـ Profile على MikroTik قبل إنشاء الكروت.
- حفظ سعر ومدة الباقة محليًا لربطهما بالكروت والمبيعات.
- اختيار Profile مباشرة من شاشة توليد الكروت.

## V2.9 Smart Pricing
- MikroTik HotSpot Profile controls network behavior only.
- MICRO-MAX Plan controls product name, selling price, currency, and active state.
- Card stores a price snapshot and plan_id at generation time.
- Price history is stored in `plan_price_history`.
- Changing a plan price does not rewrite old cards or past sales.
- Online payments remain optional and separate.

## V2.14 — Smart Card Preflight
- Added `POST /api/routers/:id/cards/preflight` for a read-only bulk generation check.
- Validates router access, MikroTik HotSpot Profile, username format, and estimated available username space before generation.
- Preflight never creates, deletes, sells, or changes cards.
- Existing pricing, Plan/Profile separation, Username controls, QR/Barcode, Store, and optional payments remain unchanged.

## V2.15 — Real MikroTik HotSpot QR
- QR content is generated from the actual HotSpot portal URL supplied for the router/card batch.
- Each card stores its generated `qr_content` so printed cards keep the exact QR payload used at creation time.
- Added `/api/routers/:id/qr-preflight` to validate the portal URL, reachability, login form, and RouterOS HotSpot login methods without changing MikroTik settings.
- The app exposes a QR compatibility check before bulk generation.
- Automatic login is only reported when the actual login page already contains compatible form auto-submit handling; otherwise the QR opens the real HotSpot login page with the card credentials prefilled.
- No MikroTik authentication settings are modified automatically.

## V2.16 — MikroTik login.html Editor
- Edit `hotspot/login.html` from MICRO-MAX.
- Validate required MikroTik HotSpot login fields before publish.
- Preview source before publishing.
- Automatic backup before replacing the live file.
- List and restore MICRO-MAX backups.
- Publishing/restore are admin-only and do not change MikroTik HotSpot authentication settings automatically.

## V2.16.0 — Branding, release readiness (this pass)
Online payments (Binance Pay / NITA / Visa) are still intentionally left **disabled by
default** — nothing below touches that.

- **App icon & splash screen**: added an adaptive launcher icon (`mipmap-anydpi-v26`) and a
  Core SplashScreen theme, both built from the same brand gradient/monogram used by the
  in-app `BrandMark` composable. See `app/src/main/res/drawable/ic_launcher_*.xml` and
  `splash_icon.xml`.
- **Unified palette**: added `res/values/colors.xml` as the single source for the brand
  colors used by the icon/splash/status bar (must stay in sync with the `Blue`/`Orange`/etc.
  constants in `MainActivity.kt`).
- **Localization groundwork**: started extracting hardcoded Arabic strings into
  `res/values/strings.xml` (app name, nav labels, Login screen, Settings screen, shared
  action words). This is a **first pass**, not a full extraction — the Cards, Sales, Store,
  Reports, Security and HotspotLoginEditor screens still have inline literals. Add a French
  `values-fr/strings.xml` once ready to expand beyond Arabic.
- **Release signing**: `app/build.gradle.kts` now reads a release signing config from
  `keystore.properties` (gitignored, see `keystore.properties.example`) or from
  `MICROMAX_KEYSTORE_*` environment variables in CI. Without either, `assembleRelease` fails
  loudly instead of silently shipping a debug-signed / unminified APK.
- **Minify + shrink resources** enabled for release builds.
- **Version bump**: `versionCode`/`versionName` now match the feature level (`216` /
  `2.16.0`) instead of the stale `2.7.0`. Bump both on every future release.
- **Theme persistence**: the Black Glass / White Glass toggle in Settings now survives app
  restarts (stored in local `SharedPreferences`, loaded before the first frame).

### Still required before a fully store-ready release
- Full string extraction + a second language (French, given the FCFA/XOF market)
- Unit/UI tests (none exist yet, backend or Android)
- A real signing keystore generated and stored securely (template only is provided)
- TLS/reverse-proxy in front of the backend's port 8080 in `docker-compose.yml`
- Privacy policy / terms of service (required for Play Store listing)
- Configure a real signing keystore, production HTTPS, privacy policy, and physical-device QA


## V2.16.1 — MICRO-MAX Manager UI expansion
- Multi-router management with connection test and API/API-SSL.
- Dashboard: CPU, RAM, storage, uptime, users, active users and interface traffic.
- HotSpot Profiles + MICRO-MAX Plans with XOF pricing.
- Card Studio wired to Smart Preflight and Real HotSpot QR generation.
- QR + Code 128 Barcode preview and Android printing.
- Batch Center summary, Store activation, Sales and Reports.
- Users/Roles and Audit Log screens.
- Network center: Interfaces, DHCP leases, ARP, DNS, IP addresses, Routes, Firewall, Queues and Logs.
- HotSpot `login.html` editor with validation, publish, automatic backup and restore.
- **MICRO-MAX Studio**: dedicated internal design surface with card templates, live card preview, HEX brand color, store-ready visual identity, and router-scoped HotSpot theme saving.
- **Printer Center**: A4 / 80mm Thermal / Custom paper presets, columns, rows, copies, sheet preview, and Android Print integration.
- **HotSpot Page Studio**: design and validate both `login.html` and `status.html`; admin-only publish creates an automatic MikroTik backup.
- Dark/Light glass interface.
- Online payments remain optional; card generation and QR/Barcode do not require a subscription.


## MICRO-MAX authentication
- Google Sign-In via Google ID token verification on the backend.
- Email/password registration and login remain available.
- Configure `GOOGLE_CLIENT_ID` on the backend and the GitHub Actions secret `MICROMAX_GOOGLE_WEB_CLIENT_ID` for Android builds.
- Never place Google client secrets or RouterOS credentials in the Android source.

## Additional network tools
- RouterOS Terminal (admin only) with audit logging.
- HotSpot IP Bindings in Network tools.
- Feature discovery endpoint exposes authentication, card, network, security, backup, store and payment capabilities.

## Production / Google Play readiness (V2.16.1 hardened)

- Android release uses HTTPS by default; cleartext is enabled only for the debug emulator build.
- User session and theme preference are stored locally and cleared on logout.
- Backend uses Helmet, explicit production CORS allowlists, API rate limiting, strict production secrets, and verified RouterOS TLS by default.
- Docker now uses a reproducible npm lockfile and runs the API as a non-root user.
- Backup export is scoped to the authenticated account and omits encrypted RouterOS credentials.
- A Gradle Wrapper is included. Use JDK 17 and `./gradlew :app:assembleDebug`.
- Release builds require a real signing keystore; do not commit it. Configure `MICROMAX_KEYSTORE_*` in CI.

### Required before Play Store submission

1. Replace `https://api.example.com` with the real HTTPS API URL using `MICROMAX_API_BASE_URL`.
2. Configure Google OAuth Web Client ID and Android package/signing certificate fingerprints.
3. Configure a production reverse proxy with TLS, HSTS, backups, monitoring, and database migrations.
4. Publish a real privacy policy and terms of service, then complete the Play Console Data Safety form.
5. Test on physical Android devices, Android 13–15, tablets, RTL layouts, offline mode, printing, and multiple screen sizes.
6. Generate a unique release keystore and keep it in a secure secret manager. Play App Signing is recommended.
7. Review the account-scoped `/api/backup/restore` workflow in staging; router credentials and payment secrets are intentionally never imported.

## Payments / NITA status

NITA and all online payment providers remain **disabled** until merchant API credentials, webhook signing, and a production callback URL are available. Cash/manual sales, card generation, QR, Barcode, Store, and printing work independently and do not require a payment API.

## Play Store Release Signing — V2.16.1

The project now supports a real upload keystore through either a local `keystore.properties` file or CI environment variables. The keystore file, passwords, and `keystore.properties` are gitignored. Release tasks are guarded: `assembleRelease` and `bundleRelease` stop with an explicit error when signing is missing, so an unsigned artifact cannot be mistaken for a Play release.

### Local signed AAB

1. Create a dedicated upload key outside the repository; use Play App Signing in Google Play Console.
2. Copy `keystore.properties.example` to `keystore.properties` and set `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`.
3. Configure the production API URL and Google Web Client ID with `MICROMAX_API_BASE_URL` and `MICROMAX_GOOGLE_WEB_CLIENT_ID`.
4. Run `./gradlew :app:signingReport` to record the SHA-1/SHA-256 fingerprints, then run `./gradlew :app:bundleRelease`.
5. Upload `app/build/outputs/bundle/release/app-release.aab` to Play Console. Do not upload the debug APK.

### GitHub Actions signed release

Run the workflow manually with the `release` input enabled. Add these repository or environment secrets: `MICROMAX_KEYSTORE_BASE64`, `MICROMAX_KEYSTORE_PASSWORD`, `MICROMAX_KEY_ALIAS`, `MICROMAX_KEY_PASSWORD`, `MICROMAX_API_BASE_URL`, and `MICROMAX_GOOGLE_WEB_CLIENT_ID`. The workflow decodes the keystore only into the ephemeral runner, builds `bundleRelease`, and uploads the AAB as the `MICRO-MAX-release-aab` artifact. A normal push still builds only the debug APK and never requires signing secrets.

### Important Play Console checks

Use the same application ID `com.micromax.app`, increment `versionCode` for every upload, complete Data Safety, add a privacy policy URL, configure the production HTTPS API, and register the release/upload certificate fingerprints in Google OAuth. Keep the upload key separate from the Play App Signing key and store recovery material securely.


## V3.0.0 — Admin-only, MikroTik-generated cards, signed QR, smart detection
- **Admin only**: `/api/auth/register` (and first Google sign-in) works exactly once, for the first account, which becomes admin. Afterwards it returns `REGISTRATION_CLOSED`. Extra staff can only be created by the admin via `/api/users`.
- **Cards are generated by MikroTik itself**: the backend runs a temporary RouterOS script (`:rndstr`) that creates the hotspot users on the router, then reads them back. Nothing is generated in Node.
- **No duplicates**: the router checks its own users before each add; after read-back the backend checks the database, removes clashing users from the router and regenerates until the exact count is met (`USERNAME_POOL_EXHAUSTED` if impossible). Username alphabet excludes ambiguous characters; pool size math uses the real 24-letter alphabet.
- **Modes**: `passwordMode: "userpass"` (default) or `"pin"` (password = username); `passwordLength` 4-16.
- **Passwords stored encrypted** (AES-256-GCM, `enc:` prefix); legacy plain rows still read fine. QR is rebuilt on read from `portal_url`, never stored in clear.
- **Real QR bound to the card**: `?username=&password=&mmc=<cardId>&mms=<HMAC>&auto=1`. The HotSpot `login.html` templates prefill and auto-submit once per session.
- **Smart detection**:
  - `POST /api/routers/:id/cards/audit` `{batchId?, apply?}` compares DB with router: missing on router, orphan MICRO-MAX users, password mismatch, status drift. `apply:true` only syncs availability status; it never deletes.
  - `POST /api/cards/scan` `{qr | username, routerId?}` returns a verdict: `VALID_UNUSED`, `SOLD_UNUSED`, `IN_USE`, `USED`, `EXPIRED`, `DISABLED`, `MISSING_ON_ROUTER`, `QR_TAMPERED`, `PASSWORD_MISMATCH`, `NOT_FOUND`, `AMBIGUOUS_ROUTER`, plus `sellable`.
- **Router requirements**: the RouterOS API user needs policies `read,write,test,api` and `sensitive` (to read back passwords). Login method `http-pap` (or https) must be enabled for QR auto-login.
- Security/ops: production mode is forced by compose, secrets are mandatory, `TRUST_PROXY` for correct rate limiting, fixed broken quoting in the generated HotSpot login/status HTML, CI no longer depends on a missing archive.
- Tests: `npm test` runs smoke checks plus 9 unit tests (script generation, duplicate resolution against a fake router, QR signing/tamper detection, state detection, audit, scan).
- **Two QR codes per card** when an SSID is given: (1) Wi-Fi join QR (`WIFI:T:nopass;S:<ssid>;;`) so phones connect without typing, (2) signed login QR that auto-fills and submits the card. Both are drawn on the preview and on the printed card from the same values stored in the database, which were read back from the router.
- **Two card methods** (chosen before generation): (1) username + password + QR, (2) **code only + QR** (`passwordMode:"pin"`). Method 2 uses `hotspot-templates/login-code.html` (single field; the code is sent as username and password) and needs at least 100M combinations (8 digits) or `PIN_MODE_CODE_TOO_SHORT`. Upload that file as the router's `hotspot/login.html` (method 1 uses `login.html`). Set `shared-users=1` and a `limit-uptime` on the profile for code-only cards.

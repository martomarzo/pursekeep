# PurseKeep Android companion — design (2026-09-22)

Status: **approved 2026-09-22; implementation plan at `docs/superpowers/plans/2026-09-22-pursekeep-android-companion.md`.** Deviation: the §9 route-level test is replaced by unit tests of the pure pieces (`parseClientVersion`, contract fixtures) because no local Postgres/Docker exists on the dev machine. Supersedes the phone-automation
approach in `docs/wallet-android-setup.md` (MacroDroid) and the "Phase 1.7
superseded" note in plan.md §6. Server-side wallet capture
(`src/lib/wallet/`, `POST /api/wallet/capture`, migration 0003) stays as is
and becomes the primary capture path.

## 1. Why

Automatic capture of card payments needs the phone's notifications
(Google Wallet, bank apps). No web API exposes other apps' notifications on
Android or iOS, so the PWA can never do it. Android offers exactly one
supported mechanism, `NotificationListenerService`, which requires a native
app. The user rejected third-party automation apps (MacroDroid never
delivered, and it is one more thing to configure).

Longer term the user wants a full app with biometric lock and a possible
Play Store listing. Rewriting every web screen natively would double the
maintenance surface forever. The chosen path is **hybrid**: a native shell
that owns what the web cannot do (notification capture, biometric lock,
later share sheet and widgets) and embeds the existing web app for
everything else. The companion built now is **step 1 of that app, not a
throwaway** — package name, signing key and module boundaries are chosen
for the final app from day one.

### Decisions (2026-09-22)

| Decision | Value |
|---|---|
| Product name | **PurseKeep** (renames "Money Maker" everywhere user-facing) |
| Domain | `pursekeep.app` (bought 2026-09-22); `.com` also free at that date |
| Android application id | `app.pursekeep` — permanent once installed or published |
| Signing | one release keystore, generated now, shared by local and CI builds |
| Repo | same repo, `android/` folder |
| Pairing | QR code shown on Settings › Devices, scanned by the app; manual entry as fallback |
| First source | Google Wallet; curated list of known payment apps, user-addable by package name |
| Min Android | 13 (API 33); target/compile SDK 35 |
| Kept unchanged | GitHub repo name `money-maker`, tailnet host `money-maker.peacock-snapper.ts.net`, DB/table names, code identifiers |

## 2. Roadmap (what "full app" means)

1. **Companion (this spec, §3–§10).** Listener, outbox, QR pairing, status
   screen, CI-built signed APK on GitHub Releases, in-app update check.
2. **Full app.** Add `androidx.biometric` lock on launch and on every
   resume, and a `WebView` home screen showing the web app (session cookie
   persists; file chooser wired for `/import`; pull-to-refresh; deep links
   for `https://pursekeep.app/*`). The companion's screens move under a
   native Settings entry. This is the Play Store candidate: needs a privacy
   policy URL (`pursekeep.app/privacy`), the Data safety form (notification
   content is read and sent to the user's own server), and — for a personal
   developer account created after Nov 2023 — a closed test with 12 testers
   for 14 days before production.
3. **Native screens, only if ever needed.** Replace individual web screens
   with Compose behind a token-authenticated REST API, one at a time.

Play policy constraints already respected in step 1: no
`QUERY_ALL_PACKAGES` (curated list + manual package entry instead of
scanning installed apps); notification access is requested with an in-app
explanation before opening the system page.

## 3. Product rename (done before the first APK)

User-facing strings only: `src/app/manifest.ts` (`name: "PurseKeep"`,
`short_name: "PurseKeep"`, description "Personal and shared finances"),
`src/app/layout.tsx` metadata title, `src/components/app-shell.tsx` brand
label, `src/app/(auth)/layout.tsx`, `src/app/join/[code]/page.tsx`, README
title, CHANGELOG title. Icons keep their current artwork (a later visual
pass may replace them). Nothing in code identifiers, env vars, compose
project name, Docker image names or the DB changes.

## 4. Android project

```
android/
  settings.gradle.kts, build.gradle.kts, gradle.properties, gradlew*, gradle/wrapper/
  version.properties            # versionName=0.1.0  versionCode=1  (CI asserts tag == versionName)
  keystore.properties.example   # storeFile/storePassword/keyAlias/keyPassword (real file is gitignored)
  contract/fixtures/*.json      # shared with the server tests, see §7
  README.md                     # local toolchain, build, install, release steps
  app/
    build.gradle.kts            # applicationId app.pursekeep, minSdk 33, target/compile 35
    src/main/AndroidManifest.xml
    src/main/kotlin/app/pursekeep/
      PurseKeepApp.kt           # Application: WorkManager init, notification channel
      capture/NotificationCatcher.kt      # NotificationListenerService
      capture/CaptureExtractor.kt         # pure: StatusBarNotification-ish input -> CapturePayload?
      capture/KnownApps.kt                # curated package list
      outbox/OutboxDb.kt, OutboxEntry.kt, OutboxDao.kt, EventEntry.kt   # Room
      outbox/OutboxFlushWorker.kt         # WorkManager, unique work "flush-outbox"
      net/CaptureApi.kt                   # OkHttp POST, response classification
      net/UpdateChecker.kt                # GitHub releases/latest, once per 24h
      settings/Settings.kt                # DataStore: serverUrl, token, deviceName, enabledPackages, customPackages, lastUpdateCheck
      pairing/PairingPayload.kt           # parse/validate QR JSON
      ui/HomeScreen.kt, ui/PairingScreen.kt, ui/AppsScreen.kt, ui/theme.kt   # Compose
      MainActivity.kt
    src/test/kotlin/app/pursekeep/        # JVM unit tests (§9)
```

Toolchain: JDK 17 (Temurin) and Android SDK command-line tools at
`~/.local/android-toolchain/{jdk17,sdk}` locally (installed 2026-09-22);
`android/local.properties` (gitignored) points `sdk.dir` there. Gradle
wrapper 8.x, Android Gradle Plugin 8.x, Kotlin 2.x, Compose BOM, Room with
KSP, WorkManager, DataStore Preferences, OkHttp, kotlinx.serialization,
`play-services-code-scanner`, `androidx.browser` (Custom Tabs). Dependency
versions are pinned in `gradle/libs.versions.toml`.

## 5. App behaviour

### 5.1 Catcher (`NotificationCatcher`)

- Bound by the system once the user grants Notification access; the app
  never needs to keep a foreground service alive. The home screen offers
  the battery-optimisation exemption as a belt-and-braces measure.
- `onNotificationPosted` → `CaptureExtractor.extract(input)` where `input`
  is a plain data class (package, postTime, flags, title, text, bigText,
  isGroupSummary, isOngoing) so the extractor is unit-testable.
- Extractor rules: skip if package not enabled; skip group summaries and
  ongoing notifications; `text` = bigText when present else text; skip if
  title and text are both blank; result is the wire payload
  `{kind:"android_notification", app, title, text, postedAt}` with
  `postedAt` = ISO-8601 with the device's UTC offset.
- Local dedupe: sha256(package | postTime | title | text) stored on the
  outbox row with a unique index; a re-posted identical notification is
  ignored. (The server additionally dedupes by its own `capture_hash`.)
- Every accepted notification becomes an outbox row with status `pending`
  and an event log line, then `OutboxFlushWorker` is enqueued.

### 5.2 Outbox and sender

- Room entity `outbox(id, payloadJson, hash UNIQUE, createdAt, attempts,
  status pending|sent|failed, lastError, serverStatus)`; entity
  `events(id, at, kind, message)` capped to the newest 50 rows.
- `OutboxFlushWorker`: unique work, `KEEP` policy, network-connected
  constraint, exponential backoff starting at 30 s. Sends pending rows
  oldest-first through `CaptureApi`.
- Response classification:
  - `201` or `200 {duplicate:true}` → `sent`, store `status` from the body
    (`booked`, `needs_account`, `unparsed`, `duplicate`) for the log.
  - `401` → stop flushing, mark pairing invalid in Settings, post a system
    notification "PurseKeep is no longer paired" that opens the app.
  - `400` → `failed` with the server's error text; never retried.
  - `5xx`, timeouts, connection errors → stay `pending`, worker returns
    `retry`. Rows older than 30 days are marked `failed` ("expired").
- Headers on every request: `Authorization: Bearer <token>`,
  `Content-Type: application/json`, `X-PurseKeep-Client:
  android/<versionName>+<versionCode>`, `User-Agent:
  PurseKeep-Android/<versionName>`.

### 5.3 Pairing

- QR content (rendered by the server, §6): `{"v":1,"app":"pursekeep",
  "url":"https://money-maker.peacock-snapper.ts.net","token":"<token>"}`.
  The app rejects `v != 1`, `app != "pursekeep"`, non-https URLs, or an
  empty token, with a readable error.
- Scanner: Google code scanner (`play-services-code-scanner`), no camera
  permission, no bundled decoder. Fallback screen with two text fields for
  URL and token.
- On success the app immediately sends a **test capture** (§5.4) to prove
  the pairing and shows the server's reply. Token, URL and device name live
  in DataStore in app-private storage; the token is device-scoped and
  revocable from the web, which is why an encrypted store is not required.

### 5.4 Test capture

"Send test" builds a Wallet-shaped notification (`app` = Google Wallet
package, title `"€0.01 with Visa ••0000"`, text `"PurseKeep test"`) and
pushes it through the normal outbox. The exact title/text are also the
`android-wallet-test.json` fixture, and the server contract test asserts
the engine parses it to amount 1 minor unit, EUR, card `0000`, merchant
"PurseKeep test" — so the format can never drift away from the parser. On the server it parses, has no card
mapping for `0000`, and lands in `/wallet` as *Needs account*, where it can
be dismissed. This exercises listener-independent parts end to end
without a real payment.

### 5.5 Screens

- **Home**: pairing state (server host, device name, last successful send)
  · Notification access status with a button to the system page, preceded
  by a one-paragraph explanation · battery exemption status/button ·
  pending count · last 50 events · "Send test" · "Open PurseKeep" (Custom
  Tab to the server URL) · "Unpair" · update banner when a newer
  `android-v*` release exists.
- **Apps**: curated list with toggles — Google Wallet
  (`com.google.android.apps.walletnfcrel`, on by default), Revolut
  (`com.revolut.revolut`), Wise (`com.transferwise.android`) — each showing
  "installed / not installed" via `<queries>` entries for exactly those
  packages, plus "Add package name" for anything else.
- **Pairing**: scan button, manual entry, result.

### 5.6 Update check

`GET https://api.github.com/repos/martomarzo/money-maker/releases/latest`
(public repo, unauthenticated), at most once per 24 h, on app open. If the
tag `android-vX.Y.Z` is newer than `versionName`, Home shows a banner
linking to the release page. No auto-download.

## 6. Server changes

- **Migration 0005**: `wallet_devices.client_version text` (nullable).
  `POST /api/wallet/capture` sets `last_seen_at = now()` and
  `client_version` from `X-PurseKeep-Client` on every authenticated
  request (best-effort, outside the capture transaction).
- **Settings › Devices** (`src/components/wallet-devices-panel.tsx`,
  `src/lib/actions/wallet.ts`): after "Create device" show a QR (SVG
  generated server-side with the `qrcode` package) encoding the §5.3 JSON,
  next to the plaintext token and a "copy" button; the QR is shown once,
  like the token. Device list shows client version and last seen. Explain
  where to download the app (link to the latest GitHub release).
- Remove the "Experimental" banners on `/wallet` and `/settings/devices`;
  link `/wallet` ("Captured payments") from Settings and from the Devices
  page; the `/wallet` inbox itself is unchanged.
- Docs: replace `docs/wallet-android-setup.md` with the companion install
  guide; keep `docs/wallet-ios-setup.md` (Shortcuts "Wallet"/"Transaction"
  automation remains the only iOS option).
- `.github/workflows/ci.yaml` and `deploy.yaml` get
  `paths-ignore: ["android/**"]` so app-only pushes do not rebuild or
  redeploy the server.

## 7. Wire contract shared by both apps

`docs/wallet-capture-contract.md` (contract **v1**) documents endpoint,
headers, the two payload kinds, responses, error codes, and the QR payload.
Fixtures in `android/contract/fixtures/`:

- `android-wallet.json` — a Google Wallet notification payload
- `android-wallet-test.json` — the §5.4 test capture
- `android-bank.json` — a bank-app payload with a double quote in the text
- `ios-transaction.json` — the Shortcuts payload
- `qr-pairing.json` — the pairing QR content

Tests on both sides read the same files:

- `tests/wallet-contract.test.ts` (vitest): each fixture parses with
  `capturePayloadSchema` / the pairing schema, and `captureHash` of the
  wallet fixture equals a recorded constant (so the dedupe hash never
  silently changes).
- `android/app/src/test/.../ContractTest.kt`: building the Kotlin models
  and serialising them yields JSON equal (as parsed objects) to each
  fixture; the pairing parser accepts `qr-pairing.json`.

Evolution rules: adding optional fields is backwards compatible and does
not bump `v`; renaming or removing a field, or changing a response shape,
bumps `v`, and the server accepts both versions for at least one app
release. The app version and the server deploy are otherwise independent.

## 8. Build, signing, releases

- **Keystore**: `keytool -genkeypair -v -keystore ~/.pursekeep/release.jks
  -alias pursekeep -keyalg RSA -keysize 4096 -validity 36500`, generated
  once on the user's machine, never committed, backed up by the user
  (losing it means a new package identity for Play and a reinstall for
  every phone). `android/keystore.properties` (gitignored) points at it for
  local release builds.
- **CI secrets** (set by the user with `gh secret set`):
  `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`,
  `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.
- **Signing config** in `app/build.gradle.kts`: use `keystore.properties`
  if present, else the four env vars, else fall back to the debug key with
  a loud warning (so `assembleRelease` never fails for lack of a key, but a
  debug-signed APK cannot upgrade a release-signed install).
- **Workflow `.github/workflows/android.yaml`**: on `push`/`pull_request`
  with `paths: ["android/**", ".github/workflows/android.yaml"]` and on
  tags `android-v*`. Runs on `ubuntu-latest` (never the self-hosted deploy
  runner): checkout, `actions/setup-java` 17 Temurin, `gradle/actions/
  setup-gradle`, `./gradlew --no-daemon :app:testDebugUnitTest
  :app:assembleRelease`, upload `pursekeep-<sha7>.apk` as an artifact. On a
  tag: assert `tag == "android-v" + versionName`, then create a GitHub
  Release with `pursekeep-<versionName>.apk` attached and the CHANGELOG
  "Android app" lines as the body.
- **Versioning**: `android/version.properties` is the single source;
  bump `versionCode` by 1 and `versionName` by semver on every release.

## 9. Testing

Unit (JVM, run in CI): `CaptureExtractorTest` (enabled/disabled package,
group summary, ongoing, bigText preference, blank skip, ISO offset
formatting, hash stability), `PairingPayloadTest` (valid, wrong app, wrong
version, http URL, empty token), `ResponseClassificationTest` (201, 200
duplicate, 401, 400, 500, IOException), `OutboxDaoTest` with Room
in-memory on Robolectric (unique hash, oldest-first, event cap at 50),
`ContractTest` (§7). Server: `tests/wallet-contract.test.ts`, plus the
first route-level test for `/api/wallet/capture` (401 without token, 201
with a fixture, 200 duplicate on resend, `client_version` stored).

Manual acceptance on the user's phone (Android 14+), in this order:
1. Install the APK from the CI artifact (`adb install` or file download).
2. Pair by QR from Settings › Devices; the automatic test capture appears
   in `/wallet` as *Needs account*; the device row shows the client version.
3. Grant Notification access from Home; send another test; dismiss both.
4. Real Google Wallet tap → transaction appears (card mapped) or *Needs
   account* (first time; map the card there).
5. Airplane mode, tap, back online → capture arrives, pending count drops.
6. Revoke the device on the web → next send flips Home to "unpaired" with
   a system notification; re-pair works.

## 10. Build order (for the implementation plan)

1. Product rename (§3) + memory/CLAUDE.md name notes.
2. Keystore + `android/` skeleton that builds a signed `assembleRelease`
   APK locally with an empty Home screen (proves toolchain, signing,
   application id).
3. Extractor + Room outbox + worker + `CaptureApi` with manual pairing and
   "Send test" → first installable, testable APK.
4. Server: migration 0005, client version, QR on Devices page, banners
   off, Settings links.
5. QR scanning in the app, Apps screen, update checker, unpair, 401 flow.
6. Contract doc + fixtures + tests on both sides; route-level test.
7. `android.yaml` workflow, `paths-ignore` on ci/deploy, secrets handed to
   the user, first tagged release `android-v0.1.0`.
8. Docs (`android/README.md`, replace `docs/wallet-android-setup.md`),
   CHANGELOG, plan.md §6, CLAUDE.md status + "keeping both apps in step"
   rule.

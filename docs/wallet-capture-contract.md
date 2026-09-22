# Wallet capture wire contract — v1

Shared by the web server (`src/lib/wallet/types.ts`) and the Android app
(`android/app/src/main/kotlin/app/pursekeep/capture/AndroidCapture.kt`).
Fixtures: `android/contract/fixtures/`. Tests: `tests/wallet-contract.test.ts`
(server) and `android/app/src/test/kotlin/app/pursekeep/ContractTest.kt` (app).

## Endpoint
`POST {origin}/api/wallet/capture`

Headers: `Authorization: Bearer <device token>` · `Content-Type: application/json; charset=utf-8` ·
`X-PurseKeep-Client: android/<versionName>+<versionCode>` (stored on the device row) ·
`User-Agent: PurseKeep-Android/<versionName>`.

## Payloads
- `{"kind":"android_notification","app":"<package>","title":"…","text":"…","postedAt":"<ISO-8601>"}`
  — `app` 1–200 chars, `title` ≤ 2000, `text` ≤ 4000, `postedAt` `YYYY-MM-DDTHH:MM…` with offset.
- `{"kind":"ios_transaction","merchant":"…","amount":"…","currency":"EUR"?,"cardName":"…","postedAt":"…"}`.
- A body that is not valid JSON is stored raw as an *unparsed* capture (never 4xx).

## Responses
- `201 {"id":"<uuid>","status":"booked"|"needs_account"|"unparsed"}`
- `200 {"duplicate":true}` — same device already sent this payload
- `400 {"error":"empty body"}` — permanent, do not retry
- `401 {"error":"unauthorized"}` — token missing/revoked: the app marks itself unpaired
- `5xx` / network errors — retry with backoff; the app gives up after 30 days

## Pairing QR
`{"v":1,"app":"pursekeep","url":"https://<origin>","token":"<device token>"}` — the app rejects
`v≠1`, `app≠"pursekeep"`, non-https URLs and blank tokens.

## Evolution
Adding optional fields: no version bump. Renaming/removing fields or changing a response shape:
bump `v` and the `kind` schemas' version, keep accepting the previous version for at least one
app release, update fixtures + both tests + `CHANGELOG.md` ("Android app" lines).

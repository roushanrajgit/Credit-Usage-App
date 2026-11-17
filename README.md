# Usage Widget — Android preview 0.2

Standard Android SDK project: API 26 minimum, API 36 target. No phone-specific APIs or companion server. This revision compiled successfully against Android API 36 and produced a signed APK. Live-account testing remains pending.

## Evidence and likely cause

On the phone, login and Settings → Usage work inside WebView while the native request receives HTTP 403. The original code copies WebView cookies into a separate HttpsURLConnection and sends Accept, a hard-coded User-Agent, and Cookie. It discards failure headers/body, so the exact refusing layer cannot be established from source or screenshot.

Browser protection/client-fingerprint differences are a plausible leading explanation. Missing browser headers, incorrect cookie transfer, User-Agent-bound clearance, or application-level access rules remain possible. No live device/traffic is available here, so these hypotheses are not proven. Seeing Settings → Usage does not prove the two fetch endpoints will work. This revision uses the working browser context instead of guessing headers or bypassing protection.

## Changes

- Claude now uses same-origin fetch inside the visible authenticated WebView, with credentials included and redirects rejected. Its real WebView User-Agent replaces the old forced UA for Claude only.
- An explicit button reads organization inventory and then usage per distinct organization. Only expected workspace/plan, session/weekly, extra budget and timestamp fields reach the cache. No cookies, tokens, prompts or full account payloads are exported.
- Java and JavaScript check the exact HTTPS Claude origin. No addJavascriptInterface bridge. Navigation interrupts sync. Native polling has a 45-second deadline; browser requests abort after 40 seconds. A challenge, redirect, non-JSON response or access error is reported, not bypassed.
- Successful organization readings replace their cache. Failed organizations retain previous values/timestamp with an error. Whole-request failures retain all previous Claude rows. Empty successful inventory clears obsolete Claude rows. Missing usage or plan fields stay unavailable.
- Failed/older-than-30-minute Claude readings show Stale since the last successful timestamp. This is the reading timestamp, not a claim about when the network failed.
- Claude is marked connected after organization discovery, rather than before any usage access test. The old native Claude request is removed from normal refresh.
- Codex's fetch implementation and shared HTTP helper are byte-for-byte unchanged. Shared cache handling now preserves Claude during Codex updates and avoids holding its lock across network calls.

Organization membership does not guarantee a paid subscription. Discovery covers identities/workspaces accessible to the current login, not unrelated email addresses. Extra usage budget is distinguished from prepaid credit.

## Refresh behavior and limitation

1. Open Claude connection (app button, Refresh all, or widget refresh). It opens https://claude.ai/settings/usage directly; signed-out users are sent to Claude's own sign-in.
2. Once a signed-in Claude page has loaded (and 1.5 s has passed without further navigation), usage syncs automatically, once per screen. Sign-in, verification and onboarding pages never auto-sync. "Refresh Claude usage now" remains as a manual retry.
3. If the automatic attempt is interrupted by a redirect, or the page reports HTTP 401 (signed out), nothing is recorded; it re-arms and runs on the next page load. Manual taps and other failures are recorded as before.
4. On success the screen closes and the widget redraws. Organizations whose `capabilities` list lacks `chat` (API/Console-only) are skipped; organizations without that list are kept.
5. Labels: a workspace with an earlier successful reading shows "Stale · reason" and keeps its values; one without shows "Unavailable · reason".
6. Reset times accept ISO timestamps with `Z` or a numeric offset (`+00:00`), via OffsetDateTime; `Instant.parse` rejected offsets on Android 13 and older.

Claude background refresh every 30 minutes is NOT implemented. This approach requires a visible authenticated page; it does not create hidden background WebViews or assume unattended challenges can be solved. The toggle says Codex background refresh (~30 minutes). Android may delay jobs. Scheduled jobs redraw cached widget readings so stale labels can appear, but do not fetch Claude. During a few-hour test, Claude's timestamp should stay unchanged and become stale; Codex updates when Android runs its jobs.

## Debug-only diagnostic

A debuggable Gradle build exposes Debug: probe original native request in the Claude connection screen. This opt-in GET repeats the original /api/organizations native request. Local logcat tag: ClaudeUsageDiagnostic.

It records status, cookie presence only, and whether native/WebView User-Agents match. Allowlisted headers: Server, Content-Type, CF-Mitigated, CF-Ray, Via, WWW-Authenticate. Request IDs and authentication challenges are presence-only. The first 500 error-body characters pass through a strict error-vocabulary allowlist: unknown words/numbers are removed/redacted. Raw body, cookie values/names and tokens are never logged. Aggressive redaction can hide useful evidence; privacy takes priority. No file/upload/analytics is produced, and the action/logging is disabled in non-debuggable builds.

Collect locally with `adb logcat -s ClaudeUsageDiagnostic`. Compare the native diagnostic to browser sync. Native 403 plus browser success and challenge headers supports a protection/client difference but does not isolate TLS versus headers/cookies. Cookie presence does not establish correct session cookies. Additional device inspection may be necessary; do not share credentials.

## Build and install

Install Android Studio/JDK 17+, SDK platform 36 and build-tools 36.0.0:

```
./gradlew assembleDebug
```

Output: app/build/outputs/apk/debug/app-debug.apk, with debug diagnostics.

Alternatively, with JDK, Android SDK, Python and zip:

```
export ANDROID_HOME=/path/to/Android/sdk
./build-apk.sh
```

Output: build/UsageWidget-preview.apk. The script creates a non-debuggable APK without diagnostics and explicitly packages the JavaScript asset. Both routes use versionCode 2, versionName 0.2.0-preview.

Signing environment variables: USAGE_WIDGET_KEYSTORE (absolute private local path), USAGE_WIDGET_STORE_PASSWORD, USAGE_WIDGET_KEY_PASSWORD, USAGE_WIDGET_KEY_ALIAS. The script requires all four. Gradle uses that configuration when all four are set; otherwise its normal development debug key is used, which will not update the existing preview installation.

Signing keys are excluded from this repository. Keep your existing preview signing key privately to build an update over the installed preview without losing its stored login. Configure your own local signing setup before building. A build signed with a different key requires uninstalling the previous app, which clears its login and cached readings.

## Verification of this revision

- Twelve Node mock-fetch scenarios pass (adds: API-only organization skipped, offset reset timestamp, signed-out 401). Original nine: multiple/deduplicated organizations and partial 403, full 403, HTML challenge, sanitized network error, empty inventory, invalid organization path, unexpected usage shape, out-of-range utilization, foreign origin. Assertions cover credentials, same-origin mode, redirect refusal and no Authorization header.
- All 11 Java files parsed with the JDK compiler parser: syntax only, NOT Android type checking/compilation.
- build-apk.sh shell syntax passed.
- Codex fetch/native HTTP helper compared byte-for-byte with the original.
- Existing APK certificate matched the included preview keystore.
- Android SDK installed; Java compiled against API 36, resources and JavaScript asset packaged, DEX generated, and APK aligned/signed. APK v2/v3 signature verification passed; application ID com.usagewidget, versionCode 2, minimum API 26, target API 36.
- No live account/device/emulator/background test ran. Existing JVM formatting tests were not rerun (no runnable JVM org.json dependency).

Run JS tests from this directory:

```
node tests/claude-browser.test.js
```

## On-device checklist (still required)

- Login: existing Claude session works.
- Discovery: all expected personal/work organizations appear; unavailable usage is labeled.
- Values: compare session/weekly remaining, local reset times and extra budget with Settings → Usage.
- Manual refresh: app and widget actions open Claude; browser refresh updates timestamps and keeps Codex rows.
- Airplane mode: failed refresh preserves old values/timestamp with stale/error status; reconnect and retry.
- Lifecycle: navigate during fetch and leave/reopen the screen; no false success/crash.
- Background over a few hours: Claude remains cached and becomes stale; Codex scheduling remains approximate.
- Codex: login, workspace discovery, manual/scheduled refresh and cached error handling still work.
- Install: use matching included key to update without losing login. Disconnect/delete removes both connections, cookies, web storage and cached usage.

Internal provider endpoints may change. Mocked tests and syntax checks do not establish a working live connection.

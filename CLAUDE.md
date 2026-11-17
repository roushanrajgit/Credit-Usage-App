# Usage Widget handoff

## Goal
Android home-screen widget showing Claude and Codex usage across accessible workspaces, including session/weekly remaining, reset times and extra usage where available. Standard Android APIs, dark appearance, local credentials only.

## Current baseline
Version 0.2, application ID com.usagewidget, API 26 minimum / API 36 target. Signed APK built successfully; v2/v3 verification passed. Nine mocked JS scenarios passed. Live connection behavior has not been verified on the phone since these changes.

## Claude issue and changes
The user can sign in and view Settings → Usage inside LoginActivity's WebView, but the original CookieManager-to-HttpsURLConnection request returns 403. The precise reason is unproven; do not assert Cloudflare/TLS/header causes without diagnostics.

ClaudeBrowser.java now executes assets/claude-usage.js on the visible HTTPS claude.ai page through evaluateJavascript. Same-origin fetch includes credentials, rejects redirects, discovers organizations and projects usage fields. No native JS bridge or cookie/token export. ClaudeSnapshots preserves successful readings on failure. ClaudeDiagnostics provides opt-in debug-only redacted native 403 evidence.

Claude is foreground/manual only in this baseline. Do not claim a 30-minute Claude background refresh. Codex keeps scheduled native refresh. Widget/app refresh opens the Claude connection for an explicit browser refresh. Validate values against the provider usage page and test stale values after airplane mode.

## Collaboration
Make provider-specific changes in branches/PRs. Preserve Codex login/fetch behavior when changing Claude. Respect authentication, security checks and provider restrictions; never bypass challenges, log secrets, or send prompts to obtain usage. Do not claim a live fix from a build alone. See README for on-device checklist, diagnostics and build commands.

## Build/test
Run `node tests/claude-browser.test.js`. Build with JDK 17+, SDK platform 36/build-tools 36.0.0 using `./gradlew assembleDebug` or `ANDROID_HOME=/path/to/sdk ./build-apk.sh`.

Signing keys and credentials are excluded from git. Preserve the existing preview key privately to update the installed app without losing its login. Use local signing configuration; do not commit keystores or credentials. A different signing key requires uninstalling the old app, which clears login/cache.

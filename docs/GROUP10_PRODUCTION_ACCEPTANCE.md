# Group 10 — Production acceptance and signed release preparation

**Status: pre-production.** These automated gates do not mean a real production upload key was used or a Play Store release was approved.

## Five CI gates

| Job | Scope |
| --- | --- |
| android | Debug build, JVM tests, lint, debug APK (manual install only) |
| android-ui (35) | API 35 Compose and SQLite emulator instrumentation |
| android-ui (36) | Android 16 edge-to-edge and the full emulator instrumentation suite |
| release-audit | Unsigned, R8-minified APK/AAB, release unit tests/lint, permission/SDK/package checks, hash report, offline policy tests |
| signing-smoke | Signed APK/AAB made with a newly generated **disposable CI-only key**, verified, then deleted; no signed artifact or key is uploaded |

Target SDK 36 enforces edge-to-edge on Android 16. MainActivity uses enableEdgeToEdge, the custom Compose top bar handles status bar insets and Back has an accessible name. Official references: https://developer.android.com/about/versions/16/behavior-changes-16 and https://developer.android.com/develop/ui/views/layout/edge-to-edge

## Manually sign an approved candidate on Windows

Keep your actual upload keystore and four BT_RELEASE_* environment variables off GitHub. Follow the Group 9 signing instructions, then run:

    .\gradlew.bat --no-daemon :app:assembleRelease :app:bundleRelease :app:lintRelease :app:testReleaseUnitTest
    python scripts/verify_signed_candidate.py --report local-signed-release-audit.json

This verifies APK v2/v3 signature, AAB JAR signature, matching upload-key SHA-256 certificate, package, version, SDK and artifact hashes. It never sends the key or signs a release without user-supplied key credentials. The throwaway CI key is not a release key.

## Safely assess installation-over-existing-version

First, without modifying the device:

    python scripts/verify_upgrade_pair.py --old "D:\releases\previous.apk" --new "app\build\outputs\apk\release\app-release.apk"

Preflight refuses package mismatch, certificate mismatch and non-increasing version code. Only after an independent secure backup and selecting the intended device:

    python scripts/verify_upgrade_pair.py --old "D:\releases\previous.apk" --new "app\build\outputs\apk\release\app-release.apk" --install --serial "YOUR_ADB_SERIAL" --confirm-device-replacement

That opt-in uses adb install -r (never adb uninstall or pm clear). Confirm existing budgets, linked payments, MP2/government contributions, and backup recovery manually. The simulated SQLite v1 to v2 upgrade instrumentation test is separate from an actual signed APK upgrade.

## Human go/no-go requirements (all currently unverified)

- [ ] Release owner authorized distribution and control of the upload signing identity; same key used for prior signed versions.
- [ ] Actual signed artifact hashes, certificate SHA-256 and versionCode recorded and verified.
- [ ] Signed in-place upgrade retains user records on physical devices; backups verified.
- [ ] Physical Android 16 and older-device accessibility, large fonts, RTL, rotation/process death and offline/resume tested.
- [ ] Live *test* Next.js backend deployed with Prisma migration; authentication, token revocation, HTTP 409 conflict and account isolation verified.
- [ ] Privacy policy, Data safety form, account deletion terms, financial/non-affiliation disclosures and companion artwork licensing approved.
- [ ] Authorized person signs release notes, test evidence, Play track and rollback plan.

Use RELEASE_APPROVAL_TEMPLATE.md to document evidence. Never commit keystores, passwords, personal records or real tokens.

## Incomplete before production

Live backend and physical-device end-to-end acceptance cannot be verified using GitHub CI alone. Local SQLite and manually exported JSON remain unencrypted beyond Android application sandbox/file handling. OS background reminders, automatic local/cloud merge and original companion illustrations remain out of scope. No production signing, deployment, PR or Play publication occurs in Group 10.


## Group 10 continuation — transport and staging acceptance

The automated CI suite now adds **in-process mobile API wire tests** for the existing Next.js mobile contract:

- HTTPS endpoint construction, POST login body, no cookie/query credentials and bearer-token header isolation.
- GET workspace projection, revisioned PUT preserving other modules, and exact revision increment.
- HTTP 401/403/409/500 and 302 redirects, malformed HTTP 200 JSON, oversized responses and oversized workspace saves.
- Reject invalid tokens and over-limit JSON **before opening a network connection**.
- Once a remote write is attempted, any unconfirmed save response (including malformed successful HTTP 200 responses or a validation error) requires an explicit authoritative refresh before further edits. Only an expired 401 session clears credentials and requests sign-in. This prevents accidental duplicate writes after a server commit with a lost reply.
- Real-device encrypted session storage: confirm AES-GCM ciphertext is neither the token nor plaintext origin, reject tampering and erase invalid stored sessions.

**Staging probe:** run the optional **read-only** probe yourself after deploying an isolated staging backend. It sends GET only: no registration, PUT writes, logout or token revocation, and never prints email/financial data.

\`\`\`powershell
python scripts/staging_mobile_readonly_probe.py --origin "https://YOUR-STAGING-HOST" --staging-confirmed
$env:BT_STAGING_MOBILE_TOKEN = "<test mobile token from your staging environment>"
python scripts/staging_mobile_readonly_probe.py --origin "https://YOUR-STAGING-HOST" --staging-confirmed
Remove-Item Env:BT_STAGING_MOBILE_TOKEN
\`\`\`

The first command checks that unauthenticated GET is rejected (HTTP 401 or 403). With an authorized staging test token, the second verifies a valid user/workspace schema and revision **without changing it**. It refuses HTTP, embedded credentials, query parameters, redirects, unexpected public access, invalid tokens, oversized responses and missing core modules. The script has an offline mocked test suite in CI; it **does not claim a live staging test has been performed**.

**Still manual:** token expiry/revocation tests, concurrent edits returning revision 409, remote-account isolation, production-device network interruption, Play Console review, real signing, and physical-device go/no-go.

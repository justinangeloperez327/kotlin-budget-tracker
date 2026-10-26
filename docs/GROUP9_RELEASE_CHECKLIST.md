# Group 9 — Android Release Candidate and Security Checklist

Status: **release-candidate build pipeline**, not production approval or Play Store publication.

## Build contract

- Package name: `dev.budgettracker.android` (unchanged)
- Version: `0.9.0-rc.1`; integer Android `versionCode = 9`. Increment the versionCode for each subsequent uploaded release; never use a lower code.
- SDK: compile/target 36, min 26; Kotlin 2.2.21, AGP 8.13.2, Gradle 8.13, JDK 17.
- Gradle Wrapper: official Gradle 8.13 launcher/JAR and pinned binary ZIP SHA-256. Use `./gradlew` (macOS/Linux) or `gradlew.bat` (Windows).
- Release: R8 minification and resource shrinking **enabled**. CI builds release APK and AAB without a signing key. It checks the APK's actual package, version and SDK metadata with `aapt`, source manifest backup/security restrictions and release artifact checksums.
- Debug: development APK still available from CI; it is not a signed production candidate.

### CI separation

| Job | When | Output | Publication |
| --- | --- | --- | --- |
| `android` | Push/PR | Debug APK, unit tests and lint | None |
| `android-ui` | Push/PR | API 35 emulator UI/SQLite tests | None |
| `release-audit` | Push/PR | Minified unsigned release APK/AAB internally and **only audit.json** artifact | None |
| **Manual Android Release Candidate** | **Manually dispatch** | Unsigned APK/AAB plus SHA-256 audit.json | No Play Store/Release upload |

An unsigned APK cannot be installed or published as a production release. AAB signing and Play upload require a legitimate upload key and separate manual approvals.

## Manual signing (Windows PowerShell)

Keep the upload keystore OUTSIDE this repository and CI. Do not share its password in chats, source code, artifacts or build logs. The four variables must be set together:

```powershell
$env:BT_RELEASE_STORE_FILE = "D:\\private\\upload-keystore.jks"
$env:BT_RELEASE_STORE_PASSWORD = "<from your secure password manager>"
$env:BT_RELEASE_KEY_ALIAS = "<your upload key alias>"
$env:BT_RELEASE_KEY_PASSWORD = "<from your secure password manager>"

.\gradlew.bat --no-daemon :app:assembleRelease :app:bundleRelease :app:lintRelease :app:testReleaseUnitTest
```

This example is **for commands on your own machine**, not for CI. The values are placeholders only. All four absent gives an unsigned release build; only some variables present makes configuration fail. No keystore files or passwords are committed.

After manual signing, check:

```powershell
$env:ANDROID_HOME + "\\build-tools\\35.0.0\\apksigner.bat"
& "$env:ANDROID_HOME\build-tools\35.0.0\apksigner.bat" verify --verbose --print-certs app\build\outputs\apk\release\app-release.apk
jarsigner -verify -verbose -certs app\build\outputs\bundle\release\app-release.aab
```

Store the upload certificate fingerprint in a secure release log. Android/Google Play App Signing should use the designated **upload key** (not a production app-signing private key) if Google Play App Signing is enabled.

Official guidance: https://developer.android.com/studio/publish/app-signing

## Financial-data privacy

- Manifest disallows cleartext HTTP (`usesCleartextTraffic=false`), Android automatic backup (`allowBackup=false`) and declares only `android.permission.INTERNET`.
- `res/xml/backup_rules.xml` excludes finance databases/preferences for Android 11 and older; `res/xml/data_extraction_rules.xml` excludes cloud AND device-to-device transfers for Android 12+. OEM behavior may vary; verify on target devices. Source: https://developer.android.com/identity/data/autobackup
- Android Keystore secures mobile bearer tokens at rest. No backend database credentials or signing keys exist in APK sources.
- Group 8 **manual device-only backup** is a plaintext, checksum-validated JSON export and is independent of Android auto-backup; users must protect the file. Export does not merge with the remote workspace.
- A successful integrity audit does **not** verify government contributions, bank balances, remittance delivery or security of a user's export location.

## Manual production acceptance checklist

- [ ] Pass all three GitHub Actions CI jobs on the intended release commit.
- [ ] Validate live login, logout, revocation, session expiry and 409 revision conflict with a **manually deployed** test instance of the Next.js backend after its Prisma migration.
- [ ] Exercise local backup/restore and recover original expense links on at least two real devices (API 26 and API 36 where available).
- [ ] Exercise Android 16 edge-to-edge, permissions, large font, TalkBack, keyboard, RTL, background/resume and process death with sensitive screens.
- [ ] Check network and database behavior without connectivity and after an interrupted remote save; no local transaction may be uploaded without explicit authorization.
- [ ] Test a signed candidate installed over a previously installed signed version; confirm non-destructive SQLite v1→v2 migration. Never ship a different signing certificate with the same application ID.
- [ ] Review Google Play privacy policy, Data safety form, government/non-affiliation statements, account deletion process (if necessary), financial advice disclaimers, graphic assets and content rights.
- [ ] Obtain ownership approval to distribute and sign. Do not publish automatically.
- [ ] Archive the build commit, SHA-256 checksums, signing certificate fingerprint, Play track and rollback procedure.

## Known incomplete features

- Source character artwork is not fully ported; palette selector works.
- Reminders are in-app only; no OS background notification scheduler.
- No automatic device-only→web account import/merge, no offline write queue and no live backend E2E test in CI.
- Local SQLite data is not encrypted at rest beyond Android sandbox/filesystem protection; the current manual JSON export is plaintext.
- No claim of full production parity or Play Store readiness should be made until the manual checks pass.

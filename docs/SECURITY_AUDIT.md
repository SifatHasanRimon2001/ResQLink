# ResQLink error and security audit — 2026-09-05

> **Addendum, 2026-10-02.** The findings and verification results below record the state audited on 2026-09-05 and have not been re-run. After that audit, the automatic SOS message was changed to include the saved emergency profile, the primary call contact's number, and the last known location. This is a material change to what leaves the device and is summarised in [Addendum: message composition](#addendum-message-composition). Run the audit again before release.
>
> **Outstanding remediation.** The finding below recorded as "Schema snapshots were ignored → Track Room schema version 1" is not actually closed. `exportSchema` is configured and `app/schemas/com.resqlink.data.local.ResQLinkDatabase/1.json` is generated on build, but the directory is untracked by git and not covered by `.gitignore`, so the snapshot would be lost by a clean checkout and could not validate a future migration. Commit the file to close the finding.

## Addendum: message composition

The payload change reverses a previous design decision. The 2026-09-05 audit recorded that emergency profile fields and captured coordinates were never added to the automatic SMS payload; they now are, by requirement.

Security properties of the new payload:

- Composed at send time from an in-memory input object. The finished message is never persisted, so it adds no new stored plaintext to the encrypted database or preferences.
- Every value is sanitised before sending. Control and Unicode format characters are stripped so a profile field cannot spoof layout or forge a section header; whitespace is collapsed; each free-text field is bounded so an oversized note cannot displace the location or call target.
- Coordinates are rejected unless finite and within range, so an unusable fix yields an explicit availability note instead of a misleading map link.
- No new permission, network capability, or exported component was introduced. The app still has no `INTERNET` permission.
- The map link is a plain `https://maps.google.com/?q=` URL using locale-independent decimal formatting, so it cannot be corrupted by a comma decimal separator.
- Notifications still contain no profile, contact, message, or location details.
- Profile and Settings screens now display the exact composed body before an emergency occurs.

Residual risk accepted: profile and location data are transmitted to every enabled trusted contact through the carrier, under the carrier's and recipient's own privacy terms. This is a deliberate product decision and must be reflected in the store privacy disclosure and Play permissions declaration. The Profile and Settings copy, [PRIVACY.md](../PRIVACY.md), [SECURITY.md](../SECURITY.md), and the SOS confirmation disclosure were all updated to state it.

Not covered by the original audit and still outstanding: physical-device delivery of the enlarged multipart message, multi-SIM behaviour, and carrier cost implications of the larger payload. On an emulator the message is composed and submitted but never delivered.

## Scope

Reviewed the Android module's application code, Room repositories and schema, DataStore settings, platform adapters, permissions, lifecycle behavior, build configuration, and tests. Checked debug/release builds, Android lint, dependency advisories, credential patterns, the merged release manifest, and emulator behavior.

This is an engineering audit and automated regression suite, not a certification that every possible vulnerability is absent. No real SMS or calls were sent during the automated tests.

Existing user edits to AGP 9.4.0, Gradle 9.6.0, parallel sync, and the Java 21 daemon configuration were preserved. Security pins were added where required.

## Fixes

| Finding | Result |
| --- | --- |
| Phone validation silently stripped arbitrary input into another number | Reject dial codes, extensions, URI payloads, letters, invisible characters, and non-ASCII digits; normalize presentation separators only; validate again at persistence and dispatch |
| Duplicate checks and primary selection could race | Wrap contact operations in Room transactions; keep exactly one enabled primary; ignore invalid/disabled primary selections |
| Editing overwrote contact creation timestamps | Preserve the original createdAt; reject updates of deleted contacts |
| Repeated activation could create another recipient draft for a recovered event | Only the transaction that prepares and activates an event returns a dispatch draft |
| Dispatch deduplication lived in an Activity instance | Claim prepared attempts persistently before external actions; use UNKNOWN after interruption; idempotent outcome updates |
| Pending permission flow was lost during activity recreation | Save Activity and ViewModel pending state; buffer activation requests; recheck durable readiness after permissions |
| An error inside activation success handling could escape | Handle the complete activation operation; preserve structured coroutine cancellation |
| Bulk deletion could remove an active SOS and race late GPS results | Guard active events in the repository; transact database deletion; cancel location capture on stop; reject late inserts into inactive/deleted events |
| Event completion could be rewritten | Conditional terminal transitions; cancel unsubmitted attempts; preserve end time |
| SmsManager service lookup was unsuitable on older Android versions | Use getDefault below API 31 and the service API on API 31+ |
| Permission revocation, unavailable providers, or notification service errors could interrupt SOS | Degrade location safely and isolate notification failures |
| Database writes could crash contact/profile/settings actions | Catch operational failures and show actionable UI feedback |
| Sensitive UI lacked screen/overlay protection | FLAG_SECURE, obscured-touch rejection, and Android 12+ overlay hiding |
| Unused network permission and missing explicit cleartext policy | Remove INTERNET and disallow cleartext |
| Wrapper distribution had no checksum pin | Pin official Gradle 9.6.0 distribution SHA-256; verify the wrapper JAR hash |
| Schema snapshots were ignored | Track Room schema version 1 for future migration validation |
| Debug builds shared release data | Use com.resqlink.debug; preserve com.resqlink for release |
| Sensitive local files lacked application encryption | Encrypt Room with SQLCipher and the custom message with Keystore AES-GCM; atomically migrate existing files |
| Database corruption handling could erase saved data | Replace SQLCipher default deletion with non-destructive failure; preserve files on missing/tampered keys |
| Failed observable storage reads could crash or hang startup | Show a retry screen without substituting empty data; keep per-source failures until successful reads |
| Setup/history write exceptions escaped their ViewModels | Catch failures and show retryable UI feedback |

## Dependency findings

The first OSV pass examined 290 unique resolved Maven package/version pairs across runtime, test, and build plugin dependencies. It returned six advisory matches, all traced to build tooling.

| Package | Before | Patched version | Advisory |
| --- | --- | --- | --- |
| Apache Commons Lang | 3.16.0 | 3.18.0 | [CVE-2025-48924](https://osv.dev/vulnerability/GHSA-j288-q9x7-2f5v) |
| jose4j | 0.9.5 | 0.9.6 | [CVE-2024-29371](https://osv.dev/vulnerability/GHSA-3677-xxcr-wjqv) |
| Bouncy Castle PKIX | 1.80.2 | 1.84 | [CVE-2026-5588](https://osv.dev/vulnerability/GHSA-wg6q-6289-32hp) |
| Bouncy Castle provider | 1.80.2 | 1.84 | [CVE-2026-0636](https://osv.dev/vulnerability/GHSA-c3fc-8qff-9hwx) |
| JDOM | 2.0.6 | 2.0.6.1 | [CVE-2021-33813](https://osv.dev/vulnerability/GHSA-2363-cqg2-863c) |
| Kotlin Gradle plugin | 2.4.10 | 2.4.20-RC3 | [CVE-2026-53914](https://osv.dev/vulnerability/GHSA-r937-wjx7-w2jp) |

The Bouncy Castle utility module is aligned to 1.84. Kotlin's compiler plugin is aligned with its Gradle plugin. [Kotlin 2.4.20-RC3](https://kotlinlang.org/docs/whatsnew-eap.html) is a release candidate, published September 2, 2026; the stable patched release was not yet published at audit time. This choice fixes the advisory and requires continued release qualification.

No dependency exclusions or advisory suppressions were used to hide these matches. The audit sends Maven coordinates, versions, and public native component commit IDs to the OSV API, never application source code or user data. It obtains SQLCipher's public native submodule references from GitHub. Its failure status remains explicit if inventory or network queries fail.

## Verification results

- **16 JVM tests passed**, zero failures.
- **38 Android instrumentation tests passed** on Pixel 8 / API 37, zero failures or skips. Includes 15 repository security/concurrency tests, 11 encrypted-storage/migration/write-failure tests, 5 Android security tests, 2 database close/reopen tests, the retry-screen test, the emergency integration test, onboarding, and 2 theme tests.
- **Debug and optimized unsigned release APK builds passed.**
- **Debug and release lint passed with zero errors.** Three informational upgrade warnings remain: Gradle 9.7.1, Navigation 2.10.0, and coroutines 1.11.0 are newer than the retained versions.
- **Source/configuration, credential-pattern, wrapper-integrity, and merged release manifest checks passed.**
- **Final OSV audit passed: 332 resolved Maven package/version pairs and 2 native release submodule commits checked, zero advisory matches.** Includes runtime, test, build plugins, compiler, and annotation-processor classpaths. All 15 source/configuration/manifest/dependency audit checks passed.
- SQLCipher ARM64 and x86_64 native load segments use 16 KB alignment. Release APK zip alignment passed with 16 KB pages; the test emulator also reports a 16 KB page size.
- The release artifact is unsigned and requires a release signing key before distribution.
- No FATAL EXCEPTION appeared in the passing instrumentation run's application logs.

Reports generated locally:

- app/build/reports/tests/testDebugUnitTest/index.html
- app/build/reports/androidTests/connected/debug/index.html
- app/build/reports/lint-results-debug.html
- app/build/reports/lint-results-release.html
- app/build/reports/security/audit.json
- app/build/reports/security/dependencies.tsv
- app/build/reports/security/dependency-scopes.tsv
- app/build/reports/security/history-secrets.json
- app/build/reports/security/native-components.json
- app/build/reports/security/native-osv.json
- app/build/reports/security/native-alignment.json
- app/build/reports/security/apk-hashes.json
- app/build/reports/security/logs/storage-final.log
- app/build/reports/security/logs/release-alignment.log

Credential pattern checks over both repository commits found no matches. These bounded patterns are not exhaustive secret detection.

The first emulator run encountered a pre-existing database at schema 2 while this checkout defines schema 1. No destructive downgrade was added. Debug package isolation allows testing this checkout without reusing release data. Never install an older release schema over a newer release database without a deliberate compatible migration.

## Reproduce

Use Java 21, the installed Android SDK 37, and an emulator (the audit uses Pixel 8 / API 37).

~~~powershell
.\gradlew.bat testDebugUnitTest lintDebug lintRelease assembleDebug assembleDebugAndroidTest assembleRelease securityDependencyInventory
.\gradlew.bat connectedDebugAndroidTest
.\scripts\security-audit.ps1
~~~

Offline source/configuration checks can be run with -Offline; this explicitly skips online advisories. Release auditing requires a freshly built merged release manifest.

## Remaining release work

- Physical-device Keystore behavior and sudden-power-loss migration testing. Emulator tests cover interrupted temporary exports and failed keys, but flash-level forensic erasure is not guaranteed.
- Physical-device SMS submission, multipart handling, permission revocation, multi-SIM selection, carrier failures, calls, GPS, and OEM restrictions.
- Full process-kill fault injection at every communication boundary. Database close/reopen tests cover durable claims, but cannot prove an SMS was delivered.
- Independent security assessment, accessibility/large-text review, Play permission review, and release signing.
- Adopt the stable patched Kotlin release and retest when it is published.

## Storage protection

SQLCipher for Android 4.18.0 encrypts the Room database. An Android Keystore AES-256-GCM key wraps a random 32-byte database password; only the authenticated envelope is saved under noBackupFilesDir. The configured emergency message uses separate purpose-bound AES-GCM encryption with a new random IV on each write. Generic preference flags remain plaintext.

Migration preserves schema 1 and every application table. It verifies the encrypted export, syncs the file and rename, then requires Room validation before removing the old plaintext database. Corrupt databases and missing or tampered keys fail without automatic replacement. A retry screen preserves saved files and directs urgent users to their phone dialer. Hardware-backed key storage varies by device; no biometric prompt gates emergency operations.

SQLCipher's release source references native SQLCipher commit e2a6040f2ae5cfff2b3e08eb3320007d93cdf3fc and LibTomCrypt commit 476a9579ae94f32b9ea9e2747bfb04b302370259. OSV commit queries returned no matches for either; this has limited coverage for vendored native code and does not establish absence of native vulnerabilities. Evidence: native-components.json and native-osv.json under the security reports directory.

## Primary references

- [SQLCipher Android 4.18.0 release](https://github.com/sqlcipher/sqlcipher-android/releases/tag/v4.18.0)
- [SQLCipher export API](https://www.zetetic.net/sqlcipher/sqlcipher-api/#sqlcipher_export)
- [Android Keystore](https://developer.android.com/privacy-and-security/keystore)
- [Android SmsManager API](https://developer.android.com/reference/android/telephony/SmsManager)
- [Android tapjacking guidance](https://developer.android.com/privacy-and-security/risks/tapjacking)
- [Official Gradle checksums](https://gradle.org/release-checksums/)
- [OSV batch query API](https://google.github.io/osv.dev/post-v1-querybatch/)

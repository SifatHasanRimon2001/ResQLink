# Testing

## Reproduce the automated verification

Requirements: Java 21, Android SDK 37, and an Android emulator for connected tests.

~~~powershell
.\gradlew.bat testDebugUnitTest lintDebug lintRelease assembleDebug assembleDebugAndroidTest assembleRelease securityDependencyInventory
.\gradlew.bat connectedDebugAndroidTest
.\scripts\security-audit.ps1
~~~

The security script requires a freshly generated dependency inventory and release manifest. It queries the public OSV API using package names/versions and public native submodule commits; it also reads SQLCipher's public release tree from GitHub. Use -Offline for local source/configuration checks without the vulnerability query; an offline pass is not a completed dependency audit.

Debug builds use **com.resqlink.debug**; release builds use **com.resqlink**. Test data and old release schemas therefore remain separate. Do not solve an incompatible downgrade by enabling destructive Room migrations.

## Verified on 2026-09-05

- 16 JVM tests passed.
- 38 connected Android tests passed on Pixel 8 / API 37.
- Debug and unsigned optimized release builds passed.
- Debug and release lint passed with no errors; three newer-version warnings remain.
- The security audit and its dependency results are recorded in [docs/SECURITY_AUDIT.md](docs/SECURITY_AUDIT.md).

## Verified on 2026-10-02

Re-verified after the SOS message-composition change. The 2026-09-05 dependency audit was not re-run; see the addendum in [docs/SECURITY_AUDIT.md](docs/SECURITY_AUDIT.md).

- 23 JVM tests passed (7 new, covering composed message content).
- 38 connected Android tests passed on Pixel 8 / API 37, zero failures or skips.
- Debug and unsigned optimized release builds passed from a clean build.
- Debug and release lint passed with zero errors.
- A full manual pass on a clean install covered onboarding, contact creation, profile entry, SOS activation, automatic primary-contact call, location capture, emergency stop, and history recording, with no FATAL EXCEPTION in application logs.

Composition coverage asserts every available field appears, that blank fields and unusable coordinates are omitted without stray labels or misleading map links, that the message still sends when nothing is saved, that layout-spoofing control and bidi characters are neutralised, and that oversized fields stay bounded so the location and call target survive.

No real SMS or call was placed. The emulator has no telephony radio, so the composed body was verified through the preview and the alert-attempt record, not through carrier delivery.

Repository tests use synthetic contacts and separate in-memory or uniquely named test databases. Device security tests exercise the real debug Activity without sending SMS or placing calls.

## What the automated suite enforces

Security and privacy properties are asserted against the installed package rather than only described:

- `AndroidSecurityTest` checks the package is denied `INTERNET` at runtime, that `FLAG_ALLOW_BACKUP` is unset, that cleartext traffic is refused, that no unprotected component is exported beyond the launcher, that `FLAG_SECURE` and obscured-touch rejection survive activity recreation, and that a denied location permission returns an explicit result without touching the platform.
- `DatabaseRecoveryTest` proves a dispatch claim survives closing and reopening the database, and that a deleted event cannot receive a late location write afterwards.
- `RepositorySecurityTest` and `EncryptedStorageTest` cover transactional contact/alert behaviour and encrypted-database, key-loss, and migration handling.

These tests send no SMS and place no calls. They exercise the real debug Activity, so device-security behaviour is genuinely checked rather than mocked.

## Report locations

- app/build/reports/tests/testDebugUnitTest/index.html
- app/build/reports/androidTests/connected/debug/index.html
- app/build/reports/lint-results-debug.html
- app/build/reports/lint-results-release.html
- app/build/reports/security/audit.json

## Manual verification on a device or emulator

`FLAG_SECURE` blanks `adb screencap` output for this app, so verify UI state with `adb shell uiautomator dump` instead of screenshots.

To exercise the composed message end to end, uninstall `com.resqlink.debug` first so onboarding, contacts, and profile start empty, then complete setup and activate SOS. To give the preview a real map link, seed a fix with `adb emu geo fix <lon> <lat>`; without one the preview honestly reads `LOCATION: unavailable at the time of sending.` Dangerous permissions can be pre-granted with `adb shell pm grant com.resqlink.debug android.permission.SEND_SMS` and the equivalent for `CALL_PHONE`, `ACCESS_FINE_LOCATION`, and `POST_NOTIFICATIONS` to skip the consent dialogs during scripted runs.

## Physical-device release checklist

Use dedicated consenting test recipients and a test SIM. Verify single- and multi-SIM SMS submission, multipart messages, carrier failures/charges, permissions denied/revoked, primary-contact calls, process-kill boundaries, GPS, notifications, background restrictions, TalkBack, large fonts, and OEM-specific behavior.

A passing emulator test cannot prove carrier delivery or call connection. Independent security assessment, sudden-power-loss testing during migration, and key behavior across physical-device lock/OEM configurations remain release checks.

# ResQLink

ResQLink is an Android-first personal emergency communication app built around one principle: critical local functionality must remain useful when connectivity is unreliable.

This repository contains a working offline-first implementation based on the supplied product requirements. SOS becomes available after at least one enabled trusted contact and a completed emergency profile have been saved.

## What works

- Three-step first-run onboarding
- Local trusted-contact add, edit, enable/disable, duplicate detection, and deletion
- Primary call-contact selection with automatic fallback when the primary contact is removed or disabled
- Required emergency profile setup using full name and emergency notes, with optional home address and important information
- Large, accessible SOS action with optional confirmation
- Contextual Android permission requests for SMS, phone, location, and notifications
- Automatic dispatch of the emergency message to every enabled trusted contact
- Automatic call initiation to the primary trusted contact
- Per-contact SMS dispatch outcome persistence without claiming carrier delivery
- Automatic SOS message composed from the configured text, saved profile, primary call number, and a clickable last-known-location link
- In-app preview of the exact message recipients receive, on the profile and settings screens
- Permission-aware local location capture with timeout and safe degradation
- Haptic confirmation and a private ongoing notification on activation
- Active emergency timer, device/network/battery status, notification, and stop flow
- Local incident history with per-event detail, diagnostics screen, data deletion, and system/light/dark themes
- Non-destructive storage-failure screen that preserves saved files and offers retry
- Encrypted database and custom message storage, validated legacy-data migration, and retryable storage errors

## Screenshots

The screens below were captured from the Pixel 8 API 37 emulator build after completing the required safety setup and running the SOS flow.

> These predate the message-composition change, so the home and emergency-screen copy differs slightly from the current build. `MainActivity` sets `FLAG_SECURE`, so `adb screencap` renders this app black; regenerate these through the UI test harness or by temporarily disabling that flag in a local build rather than in a committed change.

| Ready — light | Ready — dark |
| --- | --- |
| ![ResQLink ready home screen in light mode](docs/screenshots/resqlink-home-light.png) | ![ResQLink ready home screen in dark mode](docs/screenshots/resqlink-home-dark.png) |

| Emergency active — light | Emergency active — dark |
| --- | --- |
| ![ResQLink active emergency screen in light mode](docs/screenshots/resqlink-sos-light.png) | ![ResQLink active emergency screen in dark mode](docs/screenshots/resqlink-sos-dark.png) |

## Emergency message

One message is composed and sent to every enabled trusted contact when SOS is activated. Blocks are ordered by what a responder needs first:

```text
RESQLINK EMERGENCY — <profile full name>

LAST KNOWN LOCATION:
https://maps.google.com/?q=<lat>,<lon>
GPS coordinates: <lat>, <lon> (±<accuracy> m)

Call for assistance: <primary contact name> (<phone>)

<message configured in Settings>

EMERGENCY NOTES:
<emergency notes>

HOME ADDRESS:
<home address>

IMPORTANT INFORMATION:
<important information>
```

Behaviour worth knowing:

- Blank fields are omitted entirely, so no empty heading is ever sent. When nothing at all is saved, the Settings message and the location availability note are still sent.
- If no usable fix exists — permission denied, no provider, or an out-of-range or null-island coordinate — the message says so rather than sending a misleading map link.
- The location line carries a tappable link and plain decimal coordinates, so a recipient can still act in an SMS app that does not render links.
- Every value is stripped of control and bidi characters so profile text cannot forge a section heading, and each free-text field is bounded so one long note cannot push the location or call target out of the message.
- Because the payload carries profile and location details, **every** enabled contact receives that information. Choose recipients accordingly, and see [PRIVACY.md](PRIVACY.md).

The Profile and Settings screens render this exact body before any emergency, using the same builder, so nothing about the outgoing text is hidden.

## Privacy posture

The app declares **no `INTERNET` permission**. It cannot reach a network even if a future dependency tried to; everything it does is local, with SMS and calls handed to the platform and carrier. It also sets `allowBackup="false"`, `usesCleartextTraffic="false"`, and full backup-content exclusions.

`android.hardware.telephony` is declared **optional**, so the app installs on tablets and other non-telephony devices and degrades to showing the dialer and local records rather than failing to install.

Note that `ACCESS_NETWORK_STATE` and `VIBRATE` are requested: the first drives the online/offline indicator, the second confirms an activation by feel.

## Build

Requirements:

- Android Studio Quail 3 (2026.1.3) or compatible
- JDK 21 (matching the checked-in Gradle daemon toolchain)
- Android SDK 37

The app targets SDK 37 and supports Android 6.0 (API 23) and newer.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug lintRelease assembleDebug assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat assembleRelease
```

Debug builds use `com.resqlink.debug` so their data is separate from release installations. Release builds retain `com.resqlink`.

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. The optimized release build is generated unsigned at `app/build/outputs/apk/release/app-release-unsigned.apk` and must be signed before distribution.

### Verification status

Last verified on 2026-10-02 after the message-composition change:

| Check | Result |
| --- | --- |
| JVM unit tests | 23 passed |
| Connected Android tests | 38 passed on Pixel 8 / API 37 |
| Lint (debug + release) | 0 errors |
| Debug and unsigned release builds | Pass from a clean build |
| Manual clean-install pass | Onboarding, contact, profile, SOS, call, location, stop, history |

The 2026-09-05 dependency audit was not re-run after this change; see the addendum in [docs/SECURITY_AUDIT.md](docs/SECURITY_AUDIT.md). No real SMS or call is placed by the automated suite, and an emulator has no telephony radio, so carrier delivery remains unverified and requires a physical device with a test SIM.

## SOS behavior

After explicit SOS confirmation, Android requests any missing dangerous permissions. The app atomically creates or recovers the local event, freezes one alert attempt per enabled contact, marks the incident active, then submits one composed message to every enabled phone number through `SmsManager` and starts an `ACTION_CALL` intent for the primary contact. Location capture runs independently so a slow or unavailable GPS fix cannot delay SMS or calling.

The message is composed from the profile, the primary contact, and the location as described in [Emergency message](#emergency-message). A cached fix is used so coordinates are usually present in the first message, and an accurate fix upgrades the message if it arrives before sending.

SMS delivery, call connection, location accuracy, connectivity, and emergency response cannot be guaranteed; carrier charges may apply.

Google Play restricts `SEND_SMS`. A Play release must qualify for the physical-safety/emergency-alert exception and complete the relevant Permissions Declaration review. Because the message now carries profile and location details, the Play Data Safety form must also be updated to declare what is sent.

## Technology

| Component | Version |
| --- | --- |
| Kotlin | 2.4.20-RC3 (patched build-cache advisory; still a release candidate — see the audit) |
| Android Gradle Plugin | 9.4.0 |
| Gradle | 9.6.0 (distribution SHA-256 pinned) |
| KSP | 2.3.6 |
| Jetpack Compose BOM | 2026.08.00, Material 3 |
| Room | 2.8.4 |
| SQLCipher for Android | 4.18.0 |
| Hilt | 2.60.1 |
| Navigation Compose | 2.9.8 |
| Lifecycle | 2.11.0 |
| DataStore | 1.2.1 |
| Coroutines | 1.10.2 |
| compileSdk / targetSdk / minSdk | 37 / 37 / 23 |

Architecture is a single application module with Clean Architecture-oriented packages: Compose screens over Hilt ViewModels over domain use cases and repository interfaces over Room, DataStore, and platform adapters. See [ARCHITECTURE.md](ARCHITECTURE.md).

### Known limitation

The emergency profile stores a `Preferred language`, but the composed message text is currently English-only. The field is persisted and surfaced in the UI; it is not yet used to localise outgoing text.

See [ARCHITECTURE.md](ARCHITECTURE.md), [SECURITY.md](SECURITY.md), [PRIVACY.md](PRIVACY.md), [TESTING.md](TESTING.md), and the [2026-09-05 security audit](docs/SECURITY_AUDIT.md).

## Test suite

23 JVM unit tests and 38 connected Android tests:

| JVM | | Connected | |
| --- | --- | --- | --- |
| SecurityValidationTest | 8 | RepositorySecurityTest | 15 |
| EmergencyMessageTest | 7 | EncryptedStorageTest | 11 |
| StorageHealthTest | 3 | AndroidSecurityTest | 5 |
| ContactValidationTest | 2 | DatabaseRecoveryTest | 2 |
| EmergencyReadinessTest | 2 | ThemeRenderingTest | 2 |
| BatteryBandTest | 1 | EmergencyFlowIntegrationTest | 1 |
| | | OnboardingScreenTest | 1 |
| | | StorageUnavailableScreenTest | 1 |

## Intentionally deferred

Cloud synchronization, foreground continuous location tracking, BLE communication, a caregiver client, and server-backed delivery acknowledgements remain later-phase capabilities. They are not included in this Android repository and are not dependencies of its local SMS-and-call emergency path.

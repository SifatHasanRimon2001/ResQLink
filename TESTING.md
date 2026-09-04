# Testing

## Automated checks

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat assembleRelease
```

The suite currently contains five JVM tests and four connected-device tests:

- Contact normalization and validation
- Battery thresholds
- Required profile readiness and primary-contact selection
- First-run onboarding rendering
- Light- and dark-theme surface rendering
- Transactional emergency activation, repeated-trigger deduplication, enabled-recipient fan-out, per-recipient outcome persistence, non-blocking location capture, and cancellation history

## Emulator validation

The debug build was installed and cold-launched on a Pixel 8 emulator running API 37. The manual walkthrough validated:

- All three onboarding pages and transition to Home
- SOS refusal before both an enabled contact and the required profile exist, without premature permission requests
- Contact creation and automatic primary-contact assignment
- Profile persistence and the resulting `ResQLink is ready` state
- Explicit system, light, and dark theme selection
- Contextual SOS confirmation
- Automatic SMS submission through Android's `SmsManager`
- Automatic primary-contact call launch into Android's in-call activity using a reserved 555 test number
- Location capture from an emulator-provided fix without delaying communication
- Active-emergency rendering, timer, notification, and stop action
- A single persisted `Cancelled` history item containing one alert attempt and captured-location status
- No fatal Android runtime exception during the walkthrough

The emulator confirms that Android accepted the SMS request and launched the call flow. It cannot prove carrier delivery, connection to a real cellular recipient, or behavior on every OEM device.

## Physical-device release checklist

Use dedicated consenting test recipients and a test SIM before release. Verify single- and multi-SIM SMS submission, multipart messages, carrier failures and charges, permission denial, primary-contact calling, no duplicate dispatch after recreation, GPS behavior, notifications, background restrictions, process recreation, TalkBack, large fonts, and OEM-specific behavior.

# Security

## Implemented controls

- Room uses SQLCipher with a random 32-byte database password wrapped by an Android Keystore AES-256-GCM key (`AES/GCM/NoPadding`, 256-bit, randomised IV required). The wrapped password is stored atomically under noBackupFilesDir with a versioned envelope, and password buffers are zeroed after use. A missing key is never silently created for an existing database.
- The configured emergency message is separately encrypted with AES-GCM and purpose-bound authenticated data (`<package>:<purpose>:1`), so database-password and message ciphertext cannot be substituted for one another. Envelope and ciphertext sizes are bounded before decryption. Theme, onboarding, and confirmation flags remain ordinary preferences.
- Plaintext schema-1 databases are exported to a separate encrypted file, checked for integrity and row counts, synced, and validated by Room before plaintext removal. Corruption and missing keys never trigger database replacement.
- Storage read failures show a retry screen and preserve saved files; setup, history, profile, contact, and settings write failures surface feedback.
- Offline Android application: no Internet permission, remote endpoint, API key, or authentication token in app source. `AndroidSecurityTest` asserts the installed package is denied `INTERNET` at runtime, so a future dependency cannot silently introduce network access.
- Backup and device-transfer exclusions protect current app storage; legacy backups and cleartext traffic are explicitly disabled. `allowBackup` is false, `fullBackupContent` is false, and data-extraction rules exclude every domain from both cloud backup and device transfer.
- MainActivity blocks obscured touches, hides overlays on Android 12+, and uses FLAG_SECURE to protect screenshots, recent-task previews, and non-secure displays. `FLAG_SECURE` is re-asserted across activity recreation.
- Only the launcher is publicly exported in release. AndroidX's exported profile installer requires the platform signature-level DUMP permission. `AndroidSecurityTest` asserts every exported activity, service, provider, and receiver against this rule.
- The emergency notification is `VISIBILITY_PRIVATE`, ongoing, and carries only a fixed title and body. It contains no profile, contact, message, or location details. Notification and vibration failures are isolated so they cannot block SOS.
- Release builds are minified and resource-shrunk. Keep rules are limited to `Signature` and `*Annotation*` attributes, which Room and Hilt require; no class is broadly kept and no obfuscation mapping is published.
- Phone validation permits ordinary telephone numbers (7–15 ASCII digits, optional leading plus) and presentation separators. Dial codes, extensions, URI input, Unicode digit lookalikes, and invisible characters are rejected. Repositories revalidate and normalize input.
- Contact saves, duplicate checks, primary selection, and fallback selection are transactional.
- An explicit SOS action precedes dangerous permission requests. Pending permission state survives activity recreation, and persisted readiness is checked afterward.
- Recipient preparation and activation are transactional. Dispatch is durably claimed before SMS/call actions. Recovered incidents cannot automatically create another dispatch draft.
- Dispatch outcomes are idempotent. UNKNOWN means the process may have stopped after claiming or submitting communication; the app does not automatically retry.
- Active emergencies cannot be deleted through history or bulk data deletion. Late location results cannot write into stopped or deleted events.
- The automatic SMS payload deliberately includes responder-critical context: the saved emergency profile (name, notes, home address, important information), the primary call contact's number, and the last known location as a map link plus plain coordinates. This means profile and location data now leaves the device by design; see [PRIVACY.md](PRIVACY.md). The payload is assembled at send time, never persisted as a composed message, and is not written to logs.
- Outgoing text is sanitized before sending: control and Unicode format characters are stripped so a profile value cannot spoof message layout or forge a section header, whitespace is collapsed, and each free-text field is bounded so one oversized note cannot displace the location or call target. Coordinates are rejected unless finite and in range, so an unusable fix yields an explicit availability note instead of a misleading map link. The configured message remains normalized and limited to 500 characters.
- The Profile and Settings screens render the exact composed body before an emergency occurs, so the person supplying the data can see what recipients receive.
- Source logs contain no sensitive values. Debug builds use a separate application ID.
- Gradle distribution and wrapper integrity checks, resolved dependency auditing, and Android/JVM regression tests are repeatable.

## Audit and release limitations

See [the dated audit](docs/SECURITY_AUDIT.md) for findings, evidence, dependency fixes, and commands.

Application encryption supplements Android's sandbox and device encryption. Keystore hardware backing depends on the device; biometric authentication is not required for emergency operation. Lost or invalidated keys can make saved data unrecoverable. The app preserves unreadable files and never silently resets them. Encryption cannot protect plaintext while the app is running on a compromised device, and removing a legacy file cannot guarantee forensic erasure from flash storage.

A durable dispatch claim prevents automatic duplicate attempts, but cannot make SQLite and the carrier one atomic system. Interruption between a claim and submission can leave communication unsent or its outcome unknown. DISPATCHED only means the platform accepted the request; carrier delivery, calls connecting, GPS accuracy, and emergency response are not guaranteed.

Physical-device and multi-SIM carrier tests, OEM background behavior, accessibility review, and an independent security assessment remain release checks. Test real communication only with dedicated consenting recipients and a test SIM.

The build uses Kotlin 2.4.20-RC3 because the fixed 2.4.20 line is currently a release candidate. Replace it with the stable patched release when available and rerun all checks. Build dependencies are overridden to patched versions in the root build script.

Google Play release still requires eligibility and review for the physical-safety SEND_SMS exception and accurate privacy disclosures.

# Architecture

ResQLink uses a single Android application module with Clean Architecture-oriented package boundaries.

```text
Compose screens
    ↓ immutable UI state / actions
Hilt ViewModels
    ↓
Domain use cases and repository interfaces
    ↓
Room / DataStore / Android platform adapters
```

## Source of truth

Room is the source of truth for contacts, the emergency profile, emergency events, alert attempts, and location points. DataStore owns onboarding, theme, confirmation, and configurable-message preferences. Screens collect lifecycle-aware `StateFlow`s.

An emergency event is inserted before location or communication work begins. `createOrGetActive` performs the active check and insert in one Room transaction, while prepared alert insertion and the transition to active happen together in a second transaction. Only its winning caller creates a communication draft. The Home ViewModel derives its active state from the Room query, allowing recovery after recreation without automatically repeating an SMS or call.

## Emergency activation order

1. Verify at least one enabled contact and a complete profile.
2. Show the optional confirmation disclosure.
3. Request missing SMS, call, location, and notification permissions in context.
4. Atomically create or recover the active local event.
5. Read enabled contacts in priority order.
6. Transactionally persist exactly one prepared SMS attempt per enabled recipient and mark the event active.
7. Atomically claim prepared attempts as UNKNOWN, then compose and submit one message body to the frozen contacts. An incident cannot be automatically claimed again after recreation.
8. Start a phone call to the primary contact.
9. Independently attempt and locally persist the current location when permission exists; GPS never blocks steps 7–8.
10. Persist each immediate SMS submission result as `DISPATCHED` or `FAILED`.

`DISPATCHED` means Android accepted the send request; it does not mean the carrier delivered the message. Outcome writes only transition UNKNOWN attempts, so repeating a callback cannot increment attempt counts or overwrite an already recorded result. A process interruption can leave UNKNOWN attempts; the app does not automatically retry them.

## Message composition

`buildEmergencyMessage` in the domain model is a pure function: it takes an `EmergencyMessageContent` of configured text, profile, primary contact, and an optional `LocationSnapshot`, and returns the body. It has no Android dependency, so the exact outgoing text is unit-testable without a device.

Ordering is by responder priority — identity, location, call target, then the configured message, then free-text profile blocks. A blank or whitespace-only value contributes no block at all, so no stray label is ever sent. Missing or unusable coordinates produce an explicit availability note instead of an invented position.

The draft created at activation carries composition *inputs*, not a finished string. `MainActivity` calls the builder at send time, so the freshest profile and location are used. Two location paths feed it:

- `LocationRepository.lastKnownLocation()` returns a cached fix across enabled providers without waiting for a GPS acquisition, so coordinates are usually present in the first message.
- `CaptureEmergencyLocation` still runs the accurate current fix in parallel and returns it, upgrading a draft that has not yet been claimed.

The Preview and Settings screens reuse the same builder, so what a person previews is byte-for-byte what a recipient receives.

Stopping an emergency cancels pending location capture and unclaimed alert attempts. Conditional database operations prevent reactivation, repeated completion, active-event deletion, and late location writes after stop/delete. Bulk deletion refuses an active event. Contact writes and primary-contact updates are transactional.

Room remains at schema version 1. Schema export is configured (`exportSchema = true`, writing to `app/schemas/`), but the generated `1.json` is currently present only on disk and is **not yet committed** — it should be tracked so a future version bump can be validated against it. No destructive migration or downgrade is configured.

## Encrypted storage and recovery

Database construction is lazy. On Room's first IO access, SecureStorage unwraps a random 32-byte SQLCipher password using Android Keystore AES-256-GCM. Key creation is allowed only for a new database. The authenticated envelope lives in an AtomicFile in noBackupFilesDir. Decryption never creates a missing Keystore key.

EncryptedDatabaseFactory opens resqlink-encrypted.db. When only the old resqlink.db exists, it exports schema 1 to a temporary encrypted database, compares all five application tables' row counts inside a transaction, checks integrity, syncs the file, renames it, and fsyncs the parent directory so the rename survives power loss before any removal of the plaintext source. Room must successfully open and validate the destination before the plaintext file is removed. Unsupported schemas, corruption, key failures, and failed exports preserve original saved files. SQLCipher's destructive default corruption handler is replaced with one that rethrows, and password buffers are zeroed on close and on failure.

The migration opens the legacy source without `CREATE` so attached files inherit that mode, preventing a partially written destination from silently gaining tables.

DataStore atomically migrates emergency_message to an AES-GCM encrypted preference with purpose-specific authenticated data. Non-sensitive preference flags remain plaintext. StorageHealth catches failed observable reads and retains the failed source name until an explicit retry, gated on a retry-generation counter so a retry never substitutes empty user data. AppRoot displays the storage error independently of whether the settings flow has emitted yet.

Presentation uses a fixed Mint-on-ink Material 3 palette with explicit light and dark schemes selected by the theme preference or the system setting; there is no dynamic colour extraction, so contrast is identical across devices.

Cloud, BLE, continuous background tracking, and server delivery receipts are not dependencies of this flow.

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

An emergency event is inserted before location or communication work begins. `createOrGetActive` performs the active check and insert in one Room transaction, while prepared alert insertion is also transactional and idempotent. The Home ViewModel derives its active state from the Room query, allowing recovery after recreation without automatically repeating an SMS or call.

## Emergency activation order

1. Verify at least one enabled contact and a complete profile.
2. Show the optional confirmation disclosure.
3. Request missing SMS, call, location, and notification permissions in context.
4. Atomically create or recover the active local event.
5. Read enabled contacts in priority order.
6. Transactionally persist exactly one prepared SMS attempt per enabled recipient and mark the event active.
7. Submit the configured message to every enabled contact.
8. Start a phone call to the primary contact.
9. Independently attempt and locally persist the current location when permission exists; GPS never blocks steps 7–8.
10. Persist each immediate SMS submission result as `DISPATCHED` or `FAILED`.

`DISPATCHED` means Android accepted the send request; it does not mean the carrier delivered the message.

Cloud, BLE, continuous background tracking, and server delivery receipts are not dependencies of this flow.

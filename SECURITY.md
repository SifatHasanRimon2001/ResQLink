# Security

## Implemented controls

- No API keys, production credentials, authentication tokens, or remote endpoints exist in the app.
- Android backups are disabled because the database contains private user-owned data.
- Notification copy contains no profile, contact, message, or location details.
- Exact coordinates, phone numbers, addresses, message contents, and profile details are never logged.
- Dangerous SMS, phone, location, and notification permissions are requested only after an explicit SOS action and disclosure.
- The automatic SMS payload is limited to the message the user configured in Settings.
- Emergency-profile fields and captured coordinates are not automatically transmitted.
- Each active event is deduplicated and each in-process event is dispatched at most once.
- Dispatch failures degrade safely and are persisted without claiming carrier delivery.

## Production hardening still required

Before a distributed release, add encrypted-at-rest storage backed by Android Keystore, complete migration and process-death tests, physical-device carrier testing, rate-limit and abuse analysis, and an external security review. Google Play distribution requires review for the physical-safety `SEND_SMS` exception and accurate permission disclosures.

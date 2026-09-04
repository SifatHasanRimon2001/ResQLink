# ResQLink — Ultra-Advanced Product Requirements Document

**Document:** `prd.md`  
**Version:** 1.0  
**Status:** Final / Build Specification  
**Product:** ResQLink  
**Platform:** Android  
**Primary Language:** Kotlin  
**UI:** Jetpack Compose  
**Architecture:** Clean Architecture + MVVM  
**Target:** Production-quality portfolio project / potential real-world product  
**AI Dependency:** None

---

# 1. Product Vision

ResQLink is an Android-first personal emergency communication application designed to help users rapidly alert trusted contacts, communicate essential emergency information, and maintain access to critical safety data during situations where connectivity may be limited.

The product must prioritize:

1. Reliability
2. Speed
3. Offline availability
4. Privacy
5. Battery efficiency
6. Clear UX under stress
7. Safe failure behavior
8. Maintainable architecture
9. Testability
10. Accessibility

ResQLink is **not** an emergency-services replacement and must never claim guaranteed delivery, guaranteed location accuracy, guaranteed connectivity, or guaranteed emergency response.

---

# 2. Core Product Principle

The application must remain useful when network connectivity is unreliable.

The fundamental architecture is:

```text
                 ┌─────────────────────┐
                 │     Compose UI      │
                 └──────────┬──────────┘
                            │
                    ViewModel / State
                            │
                 ┌──────────▼──────────┐
                 │     Use Cases       │
                 └──────────┬──────────┘
                            │
                 ┌──────────▼──────────┐
                 │    Repositories     │
                 └───────┬───────┬─────┘
                         │       │
                 ┌───────▼───┐ ┌─▼──────────┐
                 │ Room DB   │ │ Remote APIs│
                 └───────────┘ └────────────┘

                    Offline-first
```

The local database is the application's source of truth for user-owned data.

Remote services are synchronization and enhancement layers, not prerequisites for displaying essential local information.

---

# 3. Target Users

## Primary User

A smartphone user who wants:

- One-tap access to an SOS workflow
- Trusted emergency contacts
- Emergency information available offline
- Location sharing when possible
- Emergency event history
- Nearby-device communication where supported

## Secondary User

A trusted contact who receives:

- SOS notification
- Emergency message
- User identity information
- Location information when available

---

# 4. Product Goals

## P0 Goals

The MVP must allow a user to:

- Complete onboarding
- Configure emergency contacts
- Configure emergency profile
- Trigger an SOS workflow
- Cancel an accidental SOS
- Obtain current location when permission is available
- Send an emergency alert through supported channels
- Enter emergency mode
- Access emergency information offline
- Record SOS events locally
- View emergency history
- Receive clear success/failure feedback

## P1 Goals

The application should additionally support:

- Live/periodic location updates during emergency mode
- Background execution where permitted by Android
- Battery-aware emergency mode
- Cloud synchronization
- Encrypted local sensitive data
- BLE nearby-device communication
- Delivery state tracking
- Robust retry mechanisms
- Comprehensive automated testing

## P2 Goals

Future versions may support:

- Multi-device synchronization
- Trusted-contact companion experience
- Organization/group safety
- Family safety circles
- Wear OS companion
- Advanced BLE relay/mesh experimentation
- Incident analytics

These features are explicitly outside the initial MVP.

---

# 5. Non-Goals

ResQLink must NOT:

- Replace government emergency services
- Guarantee emergency message delivery
- Guarantee GPS availability
- Guarantee BLE communication
- Automatically contact police/fire/ambulance without explicit user configuration and legally appropriate infrastructure
- Collect unnecessary personal data
- Continuously track users by default
- secretly record audio/video
- bypass Android permission systems
- circumvent device security
- silently activate emergency functionality

---

# 6. UX Requirements

Emergency UX must follow these principles:

### Minimal cognitive load

During an emergency, the user should not need to navigate through multiple screens.

### High visibility

SOS must be visually obvious.

### Confirmation against accidental activation

A single accidental tap must not silently trigger a potentially disruptive emergency workflow.

### Fast activation

The path from Home → SOS → confirmation should require minimal interaction.

### Transparent state

The user must always know:

- Whether SOS is active
- Whether location is available
- Whether an alert was attempted
- Whether an alert succeeded
- Whether synchronization is pending
- Whether battery is low

---

# 7. Application Navigation

```text
Splash
  ↓
Onboarding
  ↓
Home
 ├── SOS
 ├── Emergency Contacts
 ├── Emergency Profile
 ├── Emergency History
 ├── Nearby Devices
 └── Settings
```

Bottom navigation should NOT be used if it creates unnecessary complexity.

Preferred primary navigation:

```text
Home
Contacts
History
Settings
```

SOS remains a persistent primary action.

---

# 8. Screen Specifications

# 8.1 Splash Screen

Responsibilities:

- Initialize application
- Load local configuration
- Initialize dependency graph
- Verify database availability
- Restore active emergency state if applicable

Requirements:

- Must not perform unnecessary network requests.
- Must transition quickly.
- Must recover gracefully from database initialization failure.

---

# 8.2 Onboarding

Screens:

1. Welcome
2. Product explanation
3. Emergency contacts
4. Permissions explanation
5. Emergency profile
6. Completion

Requirements:

- Explain why each sensitive permission is needed.
- Never request permissions without contextual explanation.
- Allow onboarding to be completed with optional information skipped.
- User can return to configuration later.

---

# 8.3 Home Screen

Required elements:

```text
RESQLINK

Current status
────────────────────

Location
● Available

Network
● Connected

Battery
78%

        [ SOS ]

Emergency Contacts
2 configured

Emergency Profile
Configured

Recent Emergency
None
```

Home screen must prioritize emergency activation.

The SOS action must remain visually dominant.

---

# 8.4 SOS Confirmation

When user taps SOS:

```text
Are you sure?

ResQLink will:
• Alert your trusted contacts
• Attempt to share your location
• Start emergency mode

[ CANCEL ]

[ ACTIVATE SOS ]
```

The confirmation must be accessible and usable with screen readers.

---

# 8.5 Emergency Mode

Emergency Mode becomes the primary application state.

Example:

```text
🚨 EMERGENCY ACTIVE

Alert status
✓ Contact 1 notified
✓ Contact 2 notified

Location
● Updating

Battery
18%

Emergency duration
04:32

Nearby communication
Searching...

[ STOP EMERGENCY ]
```

Requirements:

- Persistent notification when required by Android.
- Clear indication that emergency mode is active.
- Emergency mode must survive configuration changes.
- State must be persisted.
- If the process is killed, the app must recover the persisted emergency state where technically possible.

---

# 8.6 Contacts

Capabilities:

- Add contact
- Edit contact
- Delete contact
- Mark trusted contact
- Reorder priority
- Test contact configuration

Data:

```text
Contact
- id
- name
- phoneNumber
- email (optional)
- priority
- enabled
- createdAt
- updatedAt
```

Validation:

- Name cannot be empty.
- Phone number must be normalized.
- Duplicate contacts should be detected.
- At least one contact should be recommended before SOS configuration is considered complete.

---

# 8.7 Emergency Profile

The profile contains user-controlled information.

Possible fields:

```text
Name
Emergency notes
Home address
Important personal information
Preferred language
```

The app must clearly indicate which fields are optional.

Sensitive fields must not be logged.

---

# 8.8 History

Display:

```text
August 29
SOS activated
Duration: 8 min
Alert attempts: 2
Location: Available

August 17
SOS activated
Duration: 2 min
Cancelled by user
```

Each incident has a detail screen.

---

# 8.9 Settings

Sections:

### Emergency

- Default emergency message
- SOS confirmation
- Emergency mode preferences

### Location

- Location behavior
- Accuracy preference

### Contacts

- Manage trusted contacts

### Privacy

- Data controls
- Clear local data
- Export data

### Appearance

- Theme
- Dynamic color where supported

### Diagnostics

- App version
- Permission status
- Database status
- Last synchronization

---

# 9. SOS State Machine

The emergency system must be modeled as an explicit state machine.

```text
IDLE
 ↓
CONFIRMING
 ↓
ACTIVATING
 ↓
ACTIVE
 ├── ALERTING
 ├── LOCATION_UPDATING
 ├── BLE_DISCOVERY
 ├── SYNC_PENDING
 └── COMPLETING
 ↓
COMPLETED
```

Failure states:

```text
ACTIVATION_FAILED
ALERT_FAILED
LOCATION_UNAVAILABLE
SYNC_FAILED
BLE_UNAVAILABLE
```

The UI must derive state from domain state rather than manually coordinating independent booleans.

Avoid:

```kotlin
isLoading
isEmergency
isSending
isLocationLoading
isSyncing
```

when a sealed state model can represent the same workflow more safely.

Preferred conceptual model:

```kotlin
sealed interface EmergencyState
```

with explicit state objects.

---

# 10. Emergency Alert Workflow

When SOS is activated:

## Step 1

Persist emergency event locally immediately.

## Step 2

Acquire location if permission exists.

## Step 3

Construct emergency payload.

Example:

```text
Emergency Alert

User: <name>

Status:
SOS activated

Time:
<timestamp>

Location:
<coordinates if available>

Accuracy:
<accuracy if available>

Battery:
<percentage>

Message:
<configured message>
```

## Step 4

Attempt configured alert channels.

Possible channels:

- SMS
- Internet backend
- Other supported mechanisms

## Step 5

Persist result of each attempt.

## Step 6

Enter emergency mode.

---

# 11. Delivery Tracking

Every outbound alert must have a unique identifier.

Example:

```text
alertId
emergencyEventId
recipientId
channel
createdAt
attemptCount
status
lastAttemptAt
errorCode
```

Possible status:

```text
PENDING
ATTEMPTING
SENT
DELIVERED
FAILED
CANCELLED
UNKNOWN
```

Do not claim `DELIVERED` unless the underlying transport provides reliable delivery confirmation.

---

# 12. Offline-First Requirements

The application must support:

- Reading contacts offline
- Reading emergency profile offline
- Reading emergency history offline
- Creating emergency events offline
- Queueing synchronization work
- Recovering from temporary connectivity loss

The application must never block the Home screen simply because the backend is unavailable.

Example:

```text
Internet unavailable.

Local data:
✓ Available

Emergency contacts:
✓ Available

Emergency profile:
✓ Available

Cloud sync:
⏳ Waiting for connection
```

---

# 13. Local Database

Use Room.

Suggested entities:

```text
EmergencyContactEntity
EmergencyProfileEntity
EmergencyEventEntity
AlertAttemptEntity
LocationPointEntity
SyncOperationEntity
AppSettingsEntity
```

Relationships:

```text
EmergencyEvent
    │
    ├── AlertAttempt[]
    │
    └── LocationPoint[]
```

Database migrations must be explicit.

Destructive migrations must not be used in production.

---

# 14. Repository Layer

Repositories must abstract data sources.

Examples:

```kotlin
EmergencyRepository
ContactRepository
ProfileRepository
LocationRepository
AlertRepository
SyncRepository
```

Repositories must not expose Android framework details to domain use cases unless unavoidable.

---

# 15. Domain Use Cases

Suggested use cases:

```text
ActivateEmergency
CancelEmergency
CompleteEmergency
GetEmergencyStatus
AddEmergencyContact
RemoveEmergencyContact
UpdateEmergencyProfile
GetCurrentLocation
StartEmergencyLocationTracking
StopEmergencyLocationTracking
SendEmergencyAlert
RetryFailedAlert
SyncPendingOperations
GetEmergencyHistory
GetEmergencyDetails
DiscoverNearbyDevices
SendNearbyEmergencyMessage
```

Each use case should have one clear responsibility.

---

# 16. Location Requirements

Location must be:

- Permission-aware
- Battery-conscious
- Explicitly controlled
- Error tolerant

Possible states:

```text
LocationPermissionGranted
LocationPermissionDenied
LocationUnavailable
LocationAcquiring
LocationAvailable
LocationStale
```

Location metadata should include:

```text
latitude
longitude
accuracyMeters
timestamp
provider
```

Do not assume location is always available.

The app must gracefully handle:

- GPS disabled
- Permission denied
- Approximate location
- Poor accuracy
- Indoor environments
- Timeout
- Provider failure

---

# 17. Background Execution

The implementation must comply with current Android platform restrictions.

Use the appropriate Android mechanism for the task:

- Foreground service for user-visible ongoing emergency location tracking where required
- WorkManager for deferrable synchronization
- Broadcast receivers only where appropriate and permitted

Never use:

- Infinite background loops
- aggressive polling
- hidden services
- undocumented platform behavior
- permission bypasses

The emergency foreground notification must be clear and persistent while required.

---

# 18. Battery Strategy

Emergency mode must expose battery information.

Battery levels:

```text
> 50%    Normal
20–50%   Moderate
10–20%   Low
< 10%    Critical
```

Behavior should become increasingly conservative as battery falls.

The application should reduce unnecessary work.

Location update frequency must be configurable within safe platform constraints.

Never claim that the app can guarantee a specific battery runtime.

---

# 19. SMS Strategy

SMS functionality must respect Android permissions and platform behavior.

Where direct SMS sending is unavailable or inappropriate:

- provide a supported fallback
- explain the limitation
- never falsely report successful delivery

The system must record:

```text
SMS attempted
SMS API result
timestamp
recipient
error
```

Sensitive message contents should not be written to logs.

---

# 20. BLE Nearby Communication

BLE is an advanced feature.

## Objective

Allow nearby ResQLink devices to discover one another and exchange a compact emergency payload.

## MVP BLE behavior

```text
Device A activates SOS
       ↓
BLE discovery
       ↓
Device B discovered
       ↓
Secure handshake
       ↓
Emergency payload exchange
       ↓
Device B acknowledges
```

Do not implement a complex mesh network in the first release.

## BLE message

Use a compact protocol:

```text
messageId
senderDeviceId
emergencyEventId
messageType
timestamp
payload
signature
```

Supported message types:

```text
SOS
LOCATION
ACK
CANCEL
```

---

# 21. BLE Security

BLE communication must not blindly trust any nearby device.

The implementation should provide:

- Device/session identifiers
- Message integrity
- Replay protection
- Timestamp validation
- Reasonable message size limits
- Session expiration

Never expose unnecessary personal data through BLE advertisements.

Prefer exchanging only minimal identifiers during discovery.

---

# 22. Cloud Backend

The backend is optional for basic offline functionality.

Possible responsibilities:

- Account authentication
- Cloud synchronization
- Emergency event backup
- Alert routing
- Device synchronization

The backend must not become a single point of failure for local emergency functionality.

Suggested API structure:

```text
POST   /v1/emergency-events
POST   /v1/emergency-events/{id}/alerts
POST   /v1/emergency-events/{id}/locations
GET    /v1/emergency-events
GET    /v1/emergency-events/{id}
POST   /v1/sync
```

All APIs must be authenticated.

---

# 23. Sync Engine

Synchronization must be resilient.

Requirements:

- Idempotent operations
- Retry with exponential backoff
- Network-awareness
- Conflict handling
- Persistent queue
- Duplicate prevention

Example:

```text
Local operation
      ↓
Sync queue
      ↓
Network available?
   ├── NO → wait
   └── YES
        ↓
Attempt
        ↓
Success → mark complete
Failure → retry
```

Every sync operation requires an idempotency key.

---

# 24. Conflict Resolution

For user-owned configuration:

Prefer deterministic conflict resolution.

Example:

```text
updatedAt
```

can be used for simple last-write-wins behavior where appropriate.

For emergency events:

**Never silently overwrite immutable event records.**

Emergency events should be append-oriented.

---

# 25. Security

Security is a first-class requirement.

## Data classification

### Public

- App version
- Generic UI content

### Private

- Emergency history
- Contacts

### Sensitive

- Emergency profile
- Location history
- Emergency messages

Sensitive data must:

- Avoid logs
- Avoid analytics unless explicitly consented
- Use secure storage where appropriate
- Be deleted when the user requests deletion

---

# 26. Logging

Logging must be structured and privacy-safe.

Never log:

- Phone numbers
- Exact GPS coordinates
- Emergency message content
- Personal addresses
- Sensitive profile fields
- Authentication tokens

Allowed:

```text
Emergency activation started
Location acquisition failed: TIMEOUT
Alert attempt failed: NETWORK_UNAVAILABLE
Sync operation completed
```

Production builds should disable verbose diagnostic logging.

---

# 27. Authentication

If cloud synchronization exists:

Support:

- Secure authentication
- Session expiration
- Sign out
- Account deletion

The application must remain useful locally without requiring cloud login unless a specific feature requires it.

---

# 28. Accessibility

Required:

- Screen-reader labels
- Sufficient touch target sizes
- Content descriptions
- Logical focus order
- Dynamic font scaling
- Reduced reliance on color
- High contrast
- Clear error states

The SOS action must remain understandable without color perception.

---

# 29. Internationalization

All user-facing strings must be externalized.

Do not hardcode UI strings.

Prepare architecture for:

- English
- Bangla
- Additional languages later

Date/time formatting must respect device locale.

Never manually construct localized dates.

---

# 30. Theme

Support:

- Light theme
- Dark theme
- System default

Use Material 3 principles.

Emergency mode should be visually distinct while remaining accessible.

Avoid excessive animations.

Animations must never delay emergency actions.

---

# 31. Notifications

Notification channels:

```text
Emergency
Emergency Updates
Synchronization
General
```

Emergency notifications must be high priority where platform rules allow.

Notification actions:

```text
View Emergency
Stop Emergency
```

Notification content must not expose excessive sensitive information on lock screens by default.

---

# 32. Permissions

Request permissions only when necessary.

Potential permissions include:

- Location
- Bluetooth-related permissions
- Notifications
- SMS where applicable

Permission flow:

```text
Explain why
    ↓
User chooses
    ↓
Request permission
    ↓
Handle result
```

Never repeatedly request denied permissions without user action.

The app must function in a degraded mode when optional permissions are denied.

---

# 33. Error Handling

All errors must be converted into domain-level meaningful states.

Avoid exposing raw exceptions to the user.

Examples:

```text
LOCATION_PERMISSION_DENIED
LOCATION_TIMEOUT
NETWORK_UNAVAILABLE
SMS_UNAVAILABLE
BLE_UNAVAILABLE
DATABASE_ERROR
AUTHENTICATION_EXPIRED
SYNC_FAILED
```

UI message:

> We couldn't get your current location. You can continue the emergency process without location.

Not:

> `SecurityException: location provider disabled`

---

# 34. Observability

The app should expose an internal diagnostic screen in debug builds.

Include:

```text
Database status
Location permission
Notification permission
Bluetooth availability
Network status
Battery level
Active emergency state
Pending sync operations
Last sync timestamp
```

Do not expose sensitive content.

---

# 35. Testing Strategy

Testing is mandatory.

## Unit tests

Minimum targets:

- SOS state machine
- Emergency activation
- Contact validation
- Location state mapping
- Alert retry logic
- Sync queue
- Conflict resolution
- Battery-state mapping

## Repository tests

Test:

- Local persistence
- Remote failure
- Offline behavior
- Sync recovery
- Duplicate prevention

## Compose UI tests

Test:

- Home screen
- SOS confirmation
- Emergency mode
- Contacts
- History
- Settings
- Permission-degraded states

## Integration tests

Test:

```text
Activate SOS
→ persist event
→ acquire location
→ create alert
→ record result
→ enter emergency mode
```

---

# 36. Failure Scenario Testing

The following scenarios are mandatory:

### Scenario 1

Internet disappears immediately after SOS.

Expected:

- Event remains persisted.
- App does not crash.
- Local emergency mode remains active.
- Pending network operation is queued.

### Scenario 2

Location permission denied.

Expected:

- SOS can continue.
- Alert indicates location unavailable.
- User receives clear explanation.

### Scenario 3

Battery drops below critical level.

Expected:

- UI indicates critical battery.
- Nonessential operations are reduced.

### Scenario 4

Application process is killed during emergency mode.

Expected:

- Persisted state is recoverable where Android permits.
- App does not create duplicate emergency events.

### Scenario 5

Duplicate SOS request.

Expected:

- No accidental duplicate emergency event.
- Existing active emergency is surfaced.

### Scenario 6

BLE device disconnects.

Expected:

- Session marked unavailable.
- Emergency mode remains functional.
- Retry/discovery can occur according to policy.

### Scenario 7

Cloud backend unavailable for several hours.

Expected:

- Local functionality remains operational.
- Sync queue persists.
- Operations synchronize later.

---

# 37. Performance Requirements

Target:

- Cold start: reasonable on mid-range devices
- Smooth 60fps UI where device supports it
- No blocking database calls on main thread
- No network calls on main thread
- Lazy loading for large history
- Efficient location processing
- Minimal battery drain outside emergency mode

Avoid premature optimization, but profile real performance before release.

---

# 38. Offline Database as Source of Truth

UI should generally observe Room/Flow.

Preferred conceptual flow:

```text
Room
 ↓
Flow
 ↓
Repository
 ↓
ViewModel
 ↓
Compose
```

Network synchronization updates Room.

The UI should not maintain a second competing copy of server state.

---

# 39. Dependency Injection

Use Hilt or another established dependency injection framework.

Dependency graph should support:

```text
Database
DAO
Repository
UseCase
ViewModel
```

Tests must be able to replace production dependencies with fakes.

---

# 40. Recommended Project Structure

```text
com.resqlink
│
├── app
│
├── core
│   ├── common
│   ├── database
│   ├── network
│   ├── security
│   ├── logging
│   ├── permissions
│   └── ui
│
├── domain
│   ├── model
│   ├── repository
│   └── usecase
│
├── data
│   ├── local
│   ├── remote
│   ├── mapper
│   └── repository
│
├── feature
│   ├── onboarding
│   ├── home
│   ├── emergency
│   ├── contacts
│   ├── profile
│   ├── history
│   ├── settings
│   └── diagnostics
│
├── location
├── bluetooth
├── notification
└── sync
```

---

# 41. Code Quality Rules

The AI coding agent MUST:

- Use Kotlin idioms
- Prefer immutable data
- Avoid unnecessary mutable global state
- Avoid God classes
- Avoid giant ViewModels
- Avoid business logic inside Composables
- Avoid database access from UI
- Avoid networking from UI
- Avoid hardcoded strings
- Avoid magic numbers
- Document non-obvious platform workarounds
- Keep functions focused
- Prefer composition over inheritance

---

# 42. Coroutine Rules

Use structured concurrency.

Avoid:

```kotlin
GlobalScope.launch
```

Never launch unmanaged application work.

Use appropriate scopes:

- `viewModelScope`
- lifecycle-aware scopes
- service scope
- application scope only when genuinely required

Cancellation must be handled correctly.

---

# 43. Flow Rules

Use:

- `StateFlow` for UI state
- `SharedFlow` or equivalent for one-shot events where appropriate
- Room Flow for reactive persistence

Avoid converting every event into persistent UI state.

---

# 44. Compose Rules

Composables should be:

- Stateless where practical
- Previewable
- Reusable
- Testable

Preferred:

```text
Screen
 ↓
State
 ↓
UI components
```

Avoid business logic inside Composables.

---

# 45. Data Models

Domain models must be independent of Room/API DTOs.

Example:

```text
EmergencyEvent
AlertAttempt
EmergencyContact
EmergencyProfile
LocationPoint
SyncOperation
```

Create explicit mapping:

```text
Entity ↔ Domain
DTO ↔ Domain
```

Do not leak Retrofit DTOs throughout the application.

---

# 46. API Security

If a backend is used:

- HTTPS only
- Secure token storage
- Authentication expiration
- Request validation
- Server-side authorization
- Rate limiting
- Idempotency
- Audit-safe logging

Never place API secrets inside the Android application.

---

# 47. Privacy UX

The user must be able to understand:

- What data is stored
- Why location is used
- When location tracking occurs
- Which contacts receive alerts
- Whether cloud synchronization is enabled
- How to delete data

Privacy information must be understandable without reading a legal document.

---

# 48. Emergency Message Customization

Allow a default message:

> I may need assistance. ResQLink has been activated. My current location is included when available.

User may customize the message.

Limit excessive message length.

Validate unsafe/invalid values.

---

# 49. Emergency Lifecycle

Emergency event lifecycle:

```text
CREATED
 ↓
ACTIVATING
 ↓
ACTIVE
 ↓
STOPPING
 ↓
COMPLETED
```

Cancellation should preserve the historical record.

Do not delete an emergency record simply because the user stopped emergency mode.

---

# 50. Data Retention

Default local retention should be configurable.

User must be able to:

- Delete individual emergency records
- Clear history
- Delete local account data
- Delete cloud data where applicable

Deletion must cascade appropriately.

---

# 51. Analytics

Analytics are optional and privacy-sensitive.

Do NOT collect:

- GPS coordinates
- Contact details
- Emergency message content

If analytics are implemented, events should be generic:

```text
app_opened
onboarding_completed
sos_started
sos_completed
permission_denied
```

Emergency analytics must never interfere with the emergency workflow.

---

# 52. Release Requirements

Before release:

- Debug logging disabled
- No test accounts
- No fake emergency alerts
- No hardcoded secrets
- No development endpoints
- Database migrations verified
- Permission flows verified
- Offline behavior verified
- Crash handling verified
- Accessibility pass completed
- ProGuard/R8 configuration reviewed
- Release build tested on physical devices

---

# 53. Device Testing Matrix

Minimum:

### Android versions

Test across supported modern Android versions, including at least:

- One older supported version
- Current stable version
- One representative recent device

### Device conditions

Test:

- Wi-Fi
- Mobile data
- No network
- Airplane mode
- Low battery
- Location disabled
- Bluetooth disabled
- Permission denied
- Dark mode
- Large font
- Screen rotation
- App backgrounded
- Process recreation

Physical-device testing is mandatory for:

- Location
- Bluetooth
- Notifications
- Background behavior
- Battery behavior

---

# 54. Accessibility Acceptance Criteria

A release is not complete unless:

- All primary actions have accessible labels.
- SOS can be understood using TalkBack.
- Dynamic font scaling does not make SOS unusable.
- Color is not the only indication of status.
- Touch targets are appropriately sized.
- Focus order is logical.
- Error messages are accessible.

---

# 55. Security Acceptance Criteria

A release is not complete unless:

- No sensitive data appears in logs.
- Authentication tokens are not stored insecurely.
- Sensitive local data uses appropriate protection.
- API communication uses HTTPS.
- No secrets are embedded in the APK.
- BLE messages cannot be trivially replayed.
- User deletion requests remove applicable stored data.

---

# 56. MVP Definition of Done

MVP is complete when:

### Onboarding

- [ ] User can complete onboarding
- [ ] Permissions are explained
- [ ] Optional information can be skipped

### Contacts

- [ ] Add contact
- [ ] Edit contact
- [ ] Delete contact
- [ ] Validate contact
- [ ] Persist locally

### Profile

- [ ] Create profile
- [ ] Edit profile
- [ ] Persist locally
- [ ] Available offline

### SOS

- [ ] Confirmation
- [ ] Emergency event persistence
- [ ] Location attempt
- [ ] Alert attempt
- [ ] Result persistence
- [ ] Emergency mode
- [ ] Stop emergency mode

### History

- [ ] List events
- [ ] View event details
- [ ] Delete event

### Reliability

- [ ] No-network behavior
- [ ] Permission-denied behavior
- [ ] Location failure behavior
- [ ] App restart recovery

### Testing

- [ ] Unit tests
- [ ] Repository tests
- [ ] Compose tests
- [ ] Physical-device tests

---

# 57. P1 Definition of Done

P1 additionally requires:

- [ ] Cloud synchronization
- [ ] Persistent sync queue
- [ ] Retry/backoff
- [ ] Background synchronization
- [ ] Battery-aware emergency mode
- [ ] Secure local storage
- [ ] BLE discovery
- [ ] BLE emergency message
- [ ] BLE acknowledgement
- [ ] Delivery-state tracking
- [ ] Diagnostics screen

---

# 58. AI Coding Agent Operating Rules

The AI coding agent must behave as a senior Android engineer.

## Before coding

1. Inspect the repository.
2. Identify existing architecture.
3. Do not overwrite working code blindly.
4. Create an implementation plan.
5. Identify platform-sensitive APIs.
6. Identify security-sensitive areas.
7. Identify required permissions.
8. Identify test requirements.

## During implementation

Implement vertically.

Preferred order:

```text
Database
 ↓
Domain
 ↓
Repository
 ↓
Use Case
 ↓
ViewModel
 ↓
UI
 ↓
Tests
```

Do not build every UI screen before implementing the underlying architecture.

---

# 59. Agent Rules for Uncertainty

When requirements are ambiguous:

1. Prefer the safest behavior.
2. Prefer Android platform-supported behavior.
3. Avoid assumptions about unavailable APIs.
4. Do not invent SDK capabilities.
5. Do not silently weaken security.
6. Document assumptions.
7. Keep implementation replaceable.

For emergency functionality, reliability is more important than feature count.

---

# 60. Agent Rules for External APIs

Before using an external API:

- Verify current API documentation.
- Confirm Android version compatibility.
- Confirm permission requirements.
- Confirm background restrictions.
- Confirm rate limits.
- Confirm failure behavior.

Do not implement undocumented behavior.

---

# 61. Agent Rules for Dependencies

Minimize dependencies.

Every dependency must have a reason.

Prefer official Android/Jetpack libraries where appropriate.

Do not introduce a library merely to avoid writing a small amount of straightforward code.

Record major dependencies in project documentation.

---

# 62. Git Strategy

Commits should be small and meaningful.

Recommended sequence:

```text
feat: initialize Android project
feat: add Room database
feat: add emergency contact management
feat: add emergency profile
feat: implement SOS state machine
feat: add location acquisition
feat: implement alert pipeline
feat: add emergency mode
feat: add emergency history
test: add emergency domain tests
test: add repository tests
test: add compose UI tests
feat: add offline sync
feat: add BLE communication
fix: improve emergency recovery
docs: add architecture documentation
```

Do not make one enormous commit containing the entire application.

---

# 63. Documentation Requirements

Repository must include:

```text
README.md
ARCHITECTURE.md
SECURITY.md
PRIVACY.md
TESTING.md
CONTRIBUTING.md
```

README must contain:

- Product overview
- Screenshots
- Architecture diagram
- Feature list
- Tech stack
- Setup instructions
- Testing instructions
- Known limitations
- Roadmap

---

# 64. Architecture Diagram

README should include a diagram similar to:

```text
                 ┌──────────────┐
                 │ Jetpack      │
                 │ Compose      │
                 └──────┬───────┘
                        │
                 ┌──────▼───────┐
                 │ ViewModels   │
                 └──────┬───────┘
                        │
                 ┌──────▼───────┐
                 │ Domain       │
                 │ Use Cases    │
                 └──────┬───────┘
                        │
                ┌───────▼────────┐
                │ Repositories   │
                └───┬────────┬───┘
                    │        │
              ┌─────▼───┐ ┌──▼─────────┐
              │ Room DB │ │ Remote API │
              └─────────┘ └────────────┘
                    │
              ┌─────▼─────────┐
              │ Sync Engine   │
              └───────────────┘

        Platform Services
        ├── Location
        ├── Bluetooth
        ├── Notifications
        └── SMS
```

---

# 65. Portfolio Requirements

The finished project should demonstrate:

### Android

- Kotlin
- Compose
- Navigation
- ViewModel
- StateFlow
- Room
- Coroutines
- WorkManager
- Location APIs
- BLE
- Notifications

### Architecture

- Clean Architecture
- MVVM
- Repository pattern
- Dependency injection
- Reactive data flow

### Engineering

- Offline-first
- Error handling
- Retry logic
- Background processing
- Security
- Testing
- Accessibility

### Product thinking

- Real user problem
- Failure-state design
- Privacy
- Reliability
- Clear UX

---

# 66. Final Product Quality Bar

The project must NOT feel like a tutorial application.

It must have:

- Consistent UI
- Real architecture
- Meaningful error states
- Loading states
- Empty states
- Offline states
- Permission states
- Recovery flows
- Automated tests
- Documentation
- Security considerations
- Physical-device validation

A recruiter should be able to clone the repository and understand the project without asking the author how it works.

---

# 67. Critical Safety Constraint

ResQLink must always communicate uncertainty honestly.

Examples:

Bad:

> “Your emergency alert was delivered.”

when only an attempt was made.

Good:

> “Alert sent to the messaging system. Delivery confirmation is unavailable.”

Bad:

> “Your location is being shared.”

when GPS has failed.

Good:

> “Location unavailable. ResQLink will continue trying while permitted.”

Bad:

> “Emergency services have been contacted.”

unless that action genuinely occurred through an authorized supported integration.

Good:

> “Your trusted contacts were alerted.”

Only claim what the application can verify.

---

# 68. Final Acceptance Test

A fresh installation on a physical Android device must be able to perform this complete scenario:

```text
Install
 ↓
Launch
 ↓
Complete onboarding
 ↓
Add two trusted contacts
 ↓
Configure emergency profile
 ↓
Return Home
 ↓
Activate SOS
 ↓
Confirm
 ↓
Create emergency event
 ↓
Attempt location
 ↓
Attempt alert
 ↓
Enter emergency mode
 ↓
Display persistent emergency state
 ↓
Simulate network loss
 ↓
Continue functioning locally
 ↓
Restore network
 ↓
Process pending synchronization
 ↓
Stop emergency
 ↓
Persist completed event
 ↓
Open history
 ↓
Inspect event
```

The test must pass without crashes.

---

# 69. Definition of Success

ResQLink succeeds if it demonstrates that a junior Android developer can build a system that is:

**Reliable → Offline-first → Secure → Testable → Accessible → Maintainable → Production-minded.**

The objective is not maximum feature count.

The objective is **engineering quality**.

---

# 70. Implementation Priority

The AI coding agent must prioritize in this exact order:

```text
P0 — Reliability
P0 — SOS state machine
P0 — Local persistence
P0 — Emergency contacts
P0 — Emergency profile
P0 — Location
P0 — Alert workflow
P0 — Emergency mode

P1 — Offline synchronization
P1 — Background processing
P1 — Battery optimization
P1 — Security hardening
P1 — BLE communication

P2 — Cloud enhancements
P2 — Advanced device communication
P2 — Wear OS
P2 — Organization features
```

**Never sacrifice P0 reliability to add P1/P2 features.**

---

# FINAL INSTRUCTION TO THE AI CODING AGENT

Build ResQLink as if it were going through a professional Android engineering review.

Do not optimize for the number of files, number of features, or visual complexity.

Optimize for:

> **Correctness → Reliability → Safety → Privacy → Testability → Maintainability → UX → Performance.**

When a feature cannot be implemented reliably under Android platform constraints, implement the safest supported fallback and clearly communicate the limitation to the user.

Every important feature must have:

```text
Requirement
→ Domain model
→ Implementation
→ Error handling
→ Persistence
→ Tests
→ Documentation
```

Do not consider the project complete merely because the application compiles.

The project is complete only when the defined acceptance criteria pass on a physical Android device and the resulting codebase is understandable, testable, secure, and production-minded.

Absolutely. Since your **Pixel 8 / API 37 emulator is working**, here's the bootstrap package I'd give an AI coding agent.

The important idea: **don't let the agent freestyle the architecture or dependencies.** Give it the PRD + `AGENTS.md` + one bootstrap prompt, then make it prove each phase builds and tests.

# 1. Create the project folder

In PowerShell:

```powershell
cd "$HOME\OneDrive\Desktop"
mkdir ResQLink
cd ResQLink
git init
```

Then create:

```text
ResQLink/
├── AGENTS.md
├── prd.md
└── README.md
```

Put the ultra-advanced PRD we created earlier into `prd.md`.

---

# 2. `AGENTS.md`

Give your coding agent this exact file:

```markdown
# ResQLink — AI Coding Agent Instructions

## 1. Mission

You are the primary senior Android engineer responsible for building ResQLink.

ResQLink is a production-minded personal emergency communication Android application.

The complete product requirements are defined in:

`prd.md`

You MUST treat `prd.md` as the product source of truth.

Do not invent major product requirements that conflict with `prd.md`.

---

# 2. Primary Engineering Objective

Build a reliable, privacy-conscious, offline-first Android application.

Priority order:

1. Correctness
2. Safety
3. Reliability
4. Privacy
5. Testability
6. Maintainability
7. Accessibility
8. Performance
9. UX polish
10. Feature breadth

Never sacrifice reliability to add features.

---

# 3. Technology Direction

Use:

- Kotlin
- Jetpack Compose
- Material 3
- AndroidX
- MVVM
- Clean Architecture
- Room
- Kotlin Coroutines
- Kotlin Flow
- Hilt
- WorkManager
- Android Location APIs
- Android Bluetooth/BLE APIs where required
- Gradle Version Catalog
- Kotlin DSL

Use stable, mutually compatible versions.

Do NOT blindly copy dependency versions from tutorials.

Before selecting versions, inspect the installed/current Android Studio and use a coherent modern Android toolchain.

Avoid unnecessary dependencies.

---

# 4. Architecture

Use:

Presentation
    ↓
ViewModel
    ↓
Use Cases
    ↓
Repository interfaces
    ↓
Repository implementations
    ↓
Local / Remote data sources

The UI must not directly access:

- Room
- Retrofit
- BLE
- LocationManager
- SharedPreferences
- network APIs

---

# 5. Project Structure

Prefer:

app/

core/
    common/
    database/
    network/
    security/
    permissions/
    ui/
    logging/

domain/
    model/
    repository/
    usecase/

data/
    local/
    remote/
    mapper/
    repository/

feature/
    onboarding/
    home/
    emergency/
    contacts/
    profile/
    history/
    settings/
    diagnostics/

location/
bluetooth/
notification/
sync/

---

# 6. Domain Rules

Business logic belongs in domain/use-case layers.

Do not put emergency business logic inside Composables.

Do not put business logic inside Room DAOs.

Do not put business logic inside Retrofit services.

---

# 7. Emergency State

Represent emergency workflow as an explicit state machine.

Do NOT create a collection of unrelated Boolean flags such as:

isEmergency
isLoading
isSending
isSyncing
isTracking

Prefer a sealed state model.

The emergency lifecycle must be recoverable from persisted state.

---

# 8. Offline First

Room is the local source of truth for user-owned application data.

The UI should observe local data.

Network synchronization updates local state.

Do not make the UI dependent on network availability.

The application must continue functioning in degraded/offline mode.

---

# 9. Database

Use Room.

Entities should remain separate from domain models.

Use:

Entity ↔ Mapper ↔ Domain

Do not expose Room entities throughout the application.

Database migrations must be explicit.

Never use destructive migrations in production.

---

# 10. Coroutines

Use structured concurrency.

Never use:

GlobalScope.launch

Use appropriate lifecycle/application scopes.

Never perform blocking database or network operations on the main thread.

---

# 11. Compose

Prefer stateless/reusable Composables.

Example:

Screen(
    state = state,
    onAction = viewModel::handleAction
)

Business logic belongs outside Composables.

Use previews for important UI components.

Use accessibility semantics.

---

# 12. State Management

Use StateFlow for persistent UI state.

Use SharedFlow or appropriate event mechanisms for one-shot events.

Avoid unnecessary duplicated state.

Prefer:

Room
 ↓
Flow
 ↓
Repository
 ↓
ViewModel
 ↓
Compose

---

# 13. Error Handling

Never expose raw exceptions to users.

Convert technical failures into domain-level errors.

Examples:

LOCATION_PERMISSION_DENIED
LOCATION_TIMEOUT
LOCATION_UNAVAILABLE
NETWORK_UNAVAILABLE
ALERT_FAILED
BLE_UNAVAILABLE
DATABASE_ERROR
SYNC_FAILED

Every important failure must have:

1. Logging
2. User-safe message
3. Recoverable behavior where possible
4. Test coverage

---

# 14. Privacy

NEVER log:

- Phone numbers
- GPS coordinates
- Emergency messages
- Addresses
- Authentication tokens
- Personal emergency information

Production logs must be privacy-safe.

Do not collect unnecessary analytics.

---

# 15. Permissions

Request permissions contextually.

Never request all permissions immediately.

Explain:

Why the permission is needed
What feature uses it
What happens if the user denies it

The application must support degraded functionality when optional permissions are denied.

---

# 16. Location

Location must be treated as unreliable.

Handle:

- Permission denied
- GPS disabled
- Timeout
- Poor accuracy
- Approximate location
- Provider failure
- Stale location

Never assume a valid location exists.

Never claim location is available unless it actually is.

---

# 17. Emergency Alerts

Never falsely report delivery.

Distinguish:

ATTEMPTED
SENT
DELIVERED
FAILED
UNKNOWN

Only report DELIVERY when the underlying mechanism provides delivery confirmation.

---

# 18. Background Work

Respect Android background execution restrictions.

Use:

- Foreground service when appropriate for active emergency location tracking
- WorkManager for deferrable synchronization
- Notifications for user-visible ongoing work

Do not use:

- Infinite background loops
- hidden services
- aggressive polling
- undocumented Android APIs

---

# 19. BLE

BLE is an advanced feature.

Implement incrementally.

First:

1. Device discovery
2. Connection
3. Minimal message exchange
4. Acknowledgement
5. Timeout handling
6. Disconnect recovery

Do not build a complicated mesh network unless explicitly requested.

Never put sensitive personal information in BLE advertisements.

---

# 20. Security

Use secure storage for sensitive local information where appropriate.

Never hardcode:

- API secrets
- private keys
- production credentials

Backend secrets must never be embedded in the APK.

Use HTTPS.

Validate server responses.

Use authentication and authorization where backend functionality exists.

---

# 21. Testing

Every meaningful feature must have tests.

Required categories:

## Unit

- State machine
- Use cases
- Validation
- Retry logic
- Sync logic

## Repository

- Local persistence
- Remote failures
- Offline behavior
- Synchronization

## UI

- Main flows
- Error states
- Empty states
- Permission states
- Emergency states

## Integration

Test the complete emergency workflow.

---

# 22. Build Verification

After meaningful implementation:

Run:

./gradlew test

Then:

./gradlew assembleDebug

If UI/instrumentation tests are available:

./gradlew connectedDebugAndroidTest

On Windows:

.\gradlew.bat test

.\gradlew.bat assembleDebug

.\gradlew.bat connectedDebugAndroidTest

Never claim a feature is complete if the project does not compile.

---

# 23. Emulator

Primary development device:

Pixel_8

The configured emulator uses Android API 37.

Use the emulator for normal development.

Physical-device-only functionality must be clearly documented.

Do not fake physical hardware behavior and claim it was tested.

---

# 24. Git

Use small commits.

Examples:

feat: add Room database
feat: add emergency contacts
feat: implement emergency state machine
feat: add location provider
test: add emergency activation tests

Do not create one enormous commit for the entire project.

---

# 25. Documentation

Update documentation when architecture changes.

Important files:

README.md
ARCHITECTURE.md
SECURITY.md
PRIVACY.md
TESTING.md

---

# 26. Coding Style

Prefer:

- Small classes
- Small functions
- Immutable state
- Explicit interfaces
- Composition
- Dependency injection
- Clear naming

Avoid:

- God classes
- giant ViewModels
- giant Composables
- duplicated business logic
- magic numbers
- unexplained workarounds

---

# 27. No Fake Implementations

Do NOT create fake implementations that pretend functionality works.

Examples of prohibited behavior:

Showing "SMS sent" without sending an SMS.

Showing "Location shared" without actually sharing it.

Showing "BLE delivered" without BLE confirmation.

Showing "Cloud synced" without successful synchronization.

During early development, clearly label simulated/demo functionality.

---

# 28. Development Process

Before implementation:

1. Inspect repository.
2. Read `prd.md`.
3. Identify requirements.
4. Create implementation plan.
5. Identify dependencies.
6. Identify Android permissions.
7. Identify testing requirements.

Then implement vertically.

Preferred:

Database
 ↓
Domain
 ↓
Repository
 ↓
Use Case
 ↓
ViewModel
 ↓
UI
 ↓
Tests

---

# 29. Phase Strategy

## Phase 0

Project bootstrap.

## Phase 1

P0 MVP:

- Onboarding
- Contacts
- Emergency profile
- Home
- SOS state machine
- Local emergency event
- Location
- Alert pipeline
- Emergency mode
- History

## Phase 2

Reliability:

- Offline-first
- Sync queue
- Retry
- Background synchronization
- Battery handling
- Security hardening

## Phase 3

Advanced:

- BLE discovery
- BLE communication
- BLE acknowledgement
- Advanced diagnostics

## Phase 4

Polish:

- Accessibility
- Localization
- UI refinement
- Testing
- Documentation
- Release preparation

Do not jump directly to Phase 3.

---

# 30. Completion Criteria

A feature is complete only when:

- Code compiles
- Tests pass
- Error states exist
- Loading states exist where necessary
- Empty states exist where necessary
- Permission states exist where necessary
- Offline behavior is handled
- Accessibility is considered
- Documentation is updated
- No sensitive data is accidentally logged

---

# 31. Agent Behavior

Act like a senior engineer.

Do not blindly obey a request if it would:

- violate Android platform restrictions
- create unsafe behavior
- compromise privacy
- make emergency state unreliable
- introduce unnecessary technical debt

When requirements conflict, prioritize:

Safety
Reliability
Privacy
Correctness
Maintainability

---

# 32. Final Rule

Never say:

"Done"

unless the implementation has actually been built and verified.

Always report:

- What changed
- What was tested
- What passed
- What remains
- Known limitations

Build ResQLink as a real production-minded Android application, not as a tutorial demo.
```

---

# 3. Bootstrap prompt for your AI coding agent

After placing `prd.md` and `AGENTS.md` in the root, give your coding agent this:

```text
You are now the senior Android engineer for the ResQLink project.

Read these files first:

1. AGENTS.md
2. prd.md

Do NOT write application code yet.

First inspect the repository and development environment.

Determine:

- Android Studio/toolchain compatibility
- JDK version
- Android SDK configuration
- available Gradle/JDK configuration
- project directory state
- emulator availability
- current git state

The primary development emulator is:

Pixel_8

The emulator uses Android API 37.

After inspection, create a concise implementation plan for Phase 0 and Phase 1.

The initial goal is NOT to implement BLE, cloud synchronization, or advanced background functionality.

The first milestone is a clean, compiling Android application with:

- Kotlin
- Jetpack Compose
- Material 3
- MVVM
- Clean Architecture
- Hilt
- Room
- Coroutines
- Flow
- Gradle Version Catalog
- Navigation
- Unit-test infrastructure
- Compose UI test infrastructure

Implement only the project foundation first.

Requirements:

1. Use a modern stable Android toolchain compatible with the installed environment.
2. Do not blindly copy dependency versions from tutorials.
3. Do not install unnecessary dependencies.
4. Do not add fake emergency functionality.
5. Do not add cloud services yet.
6. Do not add BLE yet.
7. Do not add SMS automation yet.
8. Do not request sensitive permissions yet unless required by the implemented feature.
9. Keep the architecture ready for future location/BLE/sync modules.
10. Make the project compile before proceeding.

Create:

- settings.gradle.kts
- build.gradle.kts
- gradle/libs.versions.toml
- app module
- appropriate source sets
- package structure
- basic Compose theme
- navigation foundation
- dependency injection foundation
- Room foundation
- domain foundation
- test foundation
- README.md
- ARCHITECTURE.md

After implementation:

Run the appropriate Gradle verification commands.

At minimum:

.\gradlew.bat test
.\gradlew.bat assembleDebug

If those succeed, install the debug APK on the Pixel_8 emulator using ADB and launch the application.

Verify that:

- application installs
- application launches
- Compose UI renders
- no immediate crash occurs
- emulator recognizes the application

Do not proceed to the next product feature until the foundation passes.

At the end, report:

1. Files created
2. Architecture created
3. Dependencies selected and why
4. Gradle version
5. Kotlin version
6. Android Gradle Plugin version
7. compileSdk
8. minSdk
9. targetSdk
10. Tests executed
11. Build result
12. Emulator installation result
13. Known limitations
14. Recommended next implementation step

Do not claim successful verification unless you actually ran the commands.
```

# 4. One important change to our previous plan

Because you're using a **very recent Android 17/API 37 environment**, I don't want you or the agent to blindly hard-code old tutorial versions.

Let the agent inspect the installed environment and select a **compatible stable toolchain**.

Your emulator is already communicating correctly, so the next milestone is simply:

```text
AGENTS.md
     +
prd.md
     ↓
AI coding agent
     ↓
Android project foundation
     ↓
Gradle build
     ↓
Unit tests
     ↓
Install APK
     ↓
Pixel_8
     ↓
🚀 ResQLink
```

And your current emulator is confirmed as `device` on API 37, so you're ready for that first build. 
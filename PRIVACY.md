# Privacy

ResQLink stores contacts, the emergency profile, alert-attempt metadata, location points, and emergency history locally on the device. No analytics or cloud SDK is included, and no data is uploaded.

The app declares no `INTERNET` permission, so it has no capability to transmit data to any server even if one were added to a future dependency. `android.hardware.telephony` is optional, so the app also installs on devices without a radio and degrades to local records only.

Device backup and transfer are disabled: `allowBackup` is false, and data-extraction rules exclude every storage domain from both cloud backup and device-to-device transfer. Your saved data does not leave the device through any platform backup path.

Contacts, profile, history, alert metadata, and locations are stored in a SQLCipher-encrypted database. The configured emergency message is encrypted separately. Android Keystore protects the encryption key; theme, onboarding, and confirmation preferences are not encrypted separately. Existing plaintext data is migrated before use. Losing the device's app keys can make saved information unrecoverable; storage errors do not automatically erase files.

After the user explicitly activates SOS, Android may grant the app permission to submit an emergency message to enabled trusted-contact phone numbers and start a call to the selected primary contact. Carrier and phone applications then process those communications under their own privacy terms.

Because the message carries profile and location details, every enabled trusted contact receives that information, not only a short alert. Recipients should be chosen accordingly.

The automatic message contains the text configured in Settings plus the saved emergency profile: full name, emergency notes, home address, and important information. It also contains the phone number of the primary call contact and the phone's last known location as a map link, so a recipient can act without further instruction. Fields that are blank or whose location is unavailable are omitted rather than sent empty; when no usable fix exists the message says so. Location is accessed only during SOS or through the explicit location setup control; the emergency continues if location is unavailable.

The composed message is assembled at the moment it is sent and is not stored. Profile and Settings show the exact text recipients would receive before any emergency happens, so what is transmitted is visible in advance.

The Settings screen provides **Clear all local data**, which removes local contacts, profile, history, child records, and preferences, then returns to onboarding.

# Privacy

ResQLink stores contacts, the emergency profile, alert-attempt metadata, location points, and emergency history locally on the device. No analytics or cloud SDK is included, and no data is uploaded.

After the user explicitly activates SOS, Android may grant the app permission to submit the user-configured emergency message to enabled trusted-contact phone numbers and start a call to the selected primary contact. Carrier and phone applications then process those communications under their own privacy terms.

The automatic message contains only the text configured in Settings. Emergency-profile fields and locally captured coordinates are not automatically included. Location is accessed only during SOS or through the explicit location setup control; the emergency continues if location is unavailable.

The Settings screen provides **Clear all local data**, which removes local contacts, profile, history, child records, and preferences, then returns to onboarding.

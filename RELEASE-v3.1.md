# BFF v3.1 (versionCode 9)

Based on icon-v3-nunito / b510a65. This is a local implementation and test build; it has not been published.

## Permission onboarding

Each permission has its own explanation card. The app launches one system request at a time, and waits for the result before enabling the controls again. Successful grants show the next explanation without automatically opening its prompt. Existing grants and steps unsupported by the Android version are skipped.

Order: receive SMS (required), send SMS, contacts, notifications (33+), foreground location, background location (29+), overlay, full-screen notifications (34+), battery exemption, capability summary. Android can grant receive/send SMS together because they belong to the same system permission group; precise/approximate location are deliberately requested together as Android requires.

Optional denial offers retry or Skip. Permanent denial opens app Settings; background location on Android 11+ also uses Settings. Returning rechecks actual grants, regardless of the activity result code. Progress and a pending request survive recreation. Location disclosure is visible before any location permission request. Skipping SMS sending or either location grant disables location replies. Approximate location is supported.

Settings → Permissions reopens the flow. Existing v3.0 installations receive this updated flow once. The summary lists disabled location/notification/full-screen/battery capabilities instead of claiming everything is ready.

## Other fixes

- PIN rules deny commands until a PIN exists; a PIN must immediately follow the activation word as a full field. Both `BFF 1234 where` and legacy `BFF1234 where` work. Activation inside another word and PIN substrings in unrelated numbers are rejected.
- Privacy gates and PIN are committed together only after Done validates the draft; Back discards those edits. An existing unchanged PIN can be saved.
- Location requests keep their own recipients and 10/60-second deadlines; repeated commands from the same sender coalesce while active. Requests from different senders do not steal replies or cancel one another.
- Location callbacks retain actual fixes; replies reject fixes older than two minutes, prefer accuracy among recent fixes, report fix age, and do not claim that the second fix is necessarily better. No-fix acquisition sends a terminal failure at 60 seconds. Battery percentage was removed from replies.
- Service teardown cancels callbacks, location registrations, and bounded wake locks. Refused starts are recorded as failures and offer a permission-repair notification. SMS replies use the receiving SIM subscription and track sent-result callbacks; history distinguishes requested, sent and failed.
- Alarm recovery uses an audible notification channel if the service cannot start. A denied full-screen grant is supported. Android still controls background launch eligibility; no path guarantees a full-screen alarm when the owner disables the required capabilities.
- APK copying runs off the UI thread. Split installations and unavailable WhatsApp share the download link. FileProvider access remains restricted to the share cache.
- Sensitive preferences are excluded from backup and device transfer. SMS receiver requires the system BROADCAST_SMS permission. Phone numbers use platform E.164 formatting when the device locale provides a region, with the previous formatting fallback.
- Version display follows BuildConfig. API 36 targeting, AGP 8.10.1, Kotlin 2.2.21 and Gradle 8.11.1; system-bar and keyboard insets keep controls accessible under enforced edge-to-edge layout.

## Validation and release limits

Validation on this machine: `./gradlew testDebugUnitTest assembleDebug lintDebug` passed; 42 tests passed and lint reported zero errors. The APK is versionName 3.1 / versionCode 9, minSdk 26 / targetSdk 36.

The regression suite includes PIN grammar, gate combinations, independent request deadlines, stale/accuracy selection, flow state transitions, real Activity Result callback routing under Robolectric, recreation while a prompt is active, approximate access, Settings return, and atomic privacy edits. It also exercises permission screens on API 26/29/31/34. Robolectric does not replace real system permission UI, GPS or cellular SMS tests.

Before distribution, test on physical phones: fresh install and upgrade, denied/permanently denied permissions, rotation during prompts, returning from each Settings page with Allow and Back, disabled GPS, locked/unlocked SMS activation, battery optimization, dual-SIM replies, and two concurrent senders. Test Android 16 background launches in particular.

The downloadable APK is debug-signed for testing. A release requires the original signing key and production signing configuration; an existing app signed with another key cannot be updated by this APK. Play SMS-locator permission eligibility, background-location approval and FGS/full-screen declarations remain external release requirements. API targeting alone does not establish Play eligibility. Credentials remain in private app preferences; Keystore-backed encryption is not implemented by this patch, and SMS is still plaintext.

# BFF v3.11 (versionCode 10)

Based on v3.1 (ba85d7f). Location moved out of initial setup into Settings, per the 2026-11-07 rulings.

## What changed
- **Setup asks only for what the siren needs:** receive SMS (required), contacts, notifications (33+), display over other apps, full-screen notifications (34+), battery exemption. Same one-step-at-a-time wizard as v3.1. Six steps instead of nine.
- **Settings › Location** is a new screen with a single switch, off by default. Turning it on shows the Google Play prominent disclosure first, then asks for send SMS, then precise/approximate location, then background location ("Allow all the time"; Android 11+ opens the app's permission page with an explanation first). Anything short of the full grant leaves the switch off with the reason shown under it.
- **The switch never lies.** The screen re-derives On/Off from the live grants on every resume and turns the preference off if Android revoked anything. The SMS receiver re-checks the grants at command time. The Settings row shows On/Off.
- **"where" while Location is off:** the alarm still fires; History shows "Alarm · location off" so the owner learns why no map link went out. No SMS reply is sent.
- **Ready screen** keeps "Add where → it sends its location." and adds "Turn on in Settings › Location." underneath.
- **Privacy matrix** hides the Location column while Location is off; a PIN is only required for visible PIN gates.
- **Migration:** every install, including v3.0/v3.1 testers who had granted location, starts with Location off once (setup-version marker 311). Grants already given are not revoked, so turning the switch on again is a single tap.
- Portuguese strings added for the new screen.

## Validation
`./gradlew testDebugUnitTest assembleDebug lintDebug` on the project build box; see the build log for counts. New `LocationActivityTest` covers: off by default and disclosure before any prompt, background denied → switch off with reason, full grant → on and revocation → off on resume. Setup tests updated to assert the wizard never asks for SMS sending or location.

## Still external
Same as v3.1: Play declaration forms (SMS, background location), privacy policy URL, demo video, release signing key. Debug-signed APK for sideload testing only.

# BFF phone setup help

This guide can be updated without installing a new BFF version. Instructions last edited: 9 November 2026. Menu labels vary by phone and Android version; the brand notes below are troubleshooting starting points, not device certifications.

## Start here

BFF requires an Android phone with SMS reception (Android 8 or newer for this build).

1. Install BFF from the download supplied by the project. If Android asks, allow installation from the browser or file manager you are using. You may turn that installation permission off afterward.
2. Open BFF. Allow contacts and alerts, then receive SMS. Contacts identify senders for the Privacy rules.
3. Review the locked-phone ringing checks. You can enable the listed settings now or choose **Continue to test**. Skipping a setting does not prove ringing will work on your phone.
4. Review **Privacy settings** so the sending phone is allowed. If its rule requires a PIN, include that PIN in the test message.
5. Lock this phone. From the allowed second phone, send the activation word shown in BFF as a real SMS (not an RCS/chat message). Confirm an audible alarm, then stop it.
6. Repeat after restarting and unlocking once, and after leaving the phone idle. Repeat after major Android updates.

Location is off by default and is not required for ringing.

## SMS approval is unavailable

Open Android **Settings → Apps → BFF → Permissions** and look for SMS. Return to BFF and retry. If Android explicitly offers an **Allow restricted settings** action on the app-information screen, follow its explanation before retrying. This action is not present on every phone and does not guarantee SMS permission will be available. Work profiles, parental controls, installer restrictions, or administrator policy may prevent access. Do not keep repeating the same approval loop: record the exact message and phone details for support.

Without receive-SMS permission, remote SMS finding cannot work.

## The message arrives but the phone does not ring

First check the exact activation word, sender's Privacy rule, PIN, blacklist, and BFF history. Then open **Settings → Permissions** in BFF and review the ringing checks:

- **Display over other apps:** allows the alarm screen to appear over another app where Android permits it.
- **Full-screen alerts:** available on relevant Android versions; helps display the alarm while locked.
- **Battery optimization:** allowing background operation can help Android start the alarm while BFF is closed.

Granting these settings is not a substitute for the locked-phone test. If you force-stop BFF, open it again before testing. Check alarm sound and volume settings too.

## Phone-specific starting points

Use Android Settings search when a menu below is absent. Search for BFF, battery, background usage, display over other apps, or full-screen notifications. These paths need verification on your exact software version.

| Phone family | Where to look if ringing stops while idle |
|---|---|
| Samsung / One UI | BFF App info → Battery; also Battery → Background usage limits. Check whether BFF is in a sleeping-app list. |
| Google Pixel | BFF App info → App battery usage. Review background usage, then Special app access for display/full-screen options. |
| Motorola | BFF App info → Battery or App battery usage. Review background restrictions and Special app access. |
| Xiaomi / Redmi / Poco | BFF App info → Battery saver; search Settings for background autostart. Names differ between MIUI and HyperOS. |
| OnePlus / Oppo / Realme | BFF App info → Battery usage. Search for background activity and auto-launch options if available. |
| Other Android phones | Start at BFF App info → Permissions and Battery. Use Settings search for special access options. |

Change one setting at a time and repeat the same locked-phone SMS test, so you know what helped.

## Optional location replies

After setup, open **BFF Settings → Location**. Enable **Location** and follow the foreground and background location prompts. Enable **Send SMS** separately to allow the reply. Both switches and their permissions are needed. SMS charges may apply. Send the activation word followed by `where`, with the PIN when required by Privacy settings. Only share location with senders you authorize. Turn the switches off to disable replies.

## What to record when asking for help

Phone model, Android version, manufacturer software version (for example One UI or HyperOS), BFF version, fresh install or upgrade, the setting or message shown, and whether the locked-phone test passed. Do not include your PIN, activation secret, private SMS messages, or precise location.

## Compatibility status

V2.1 has user-reported successful ringing across their tested Android models; the exact model list is not recorded here. V3.14 needs fresh physical-device testing because it targets newer Android behavior. Automated checks cannot certify OEM battery behavior, SMS delivery, or locked-screen ringing.

See [the release testing matrix](COMPATIBILITY.md) for the current evidence.

## Capture a failure with Diagnostics (v3.15+)

1. Open **Settings → Diagnostics → Start recording**. This replaces the previous trace and records locally for up to 30 minutes.
2. Lock the phone and send one real SMS command from another phone. Note whether the siren was audible.
3. Reopen Diagnostics and tap **Refresh report**. Use **Copy report** or **Share report** to send it to the person helping you. Nothing is uploaded automatically.
4. Separately, use **Test siren for 5 seconds**. This is loud. Confirm the prompt and record **I heard the test siren** or **I did not hear the test siren**. **Stop siren now** is available at any time. A successful local test does not prove that SMS or background execution works.
5. Choose **Clear report and stop recording** when finished.

The report includes phone model, Android build, current permission/battery/audio settings, and up to 150 timestamped events. It excludes SMS content, phone numbers, contact names, the activation word, PIN and location. Error categories exclude exception messages.

Event landmarks: `SMS_RECEIVED` means Android called BFF; `ACTIVATION_NOT_MATCHED` means the command was not recognized; `ALARM_BLOCKED_PIN` or `ALARM_BLOCKED_RULE` points to Privacy settings; `SERVICE_REQUEST_FAILED` or `SERVICE_START_FAILED` indicates an alarm startup failure. `AUDIO_PLAYING` means Android accepted playback, not proof that sound was heard. No receiver event alone does not distinguish an SMS permission problem from RCS, force-stop or another delivery problem.

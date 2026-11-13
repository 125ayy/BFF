# BFF v3.15 — on-device diagnostics

Adds Settings → Diagnostics with a 30-minute opt-in local trace, live permission/battery/audio checks, copy/share controls, and a confirmed five-second local siren test with immediate stop and audible-result buttons.

The trace follows real SMS delivery, activation matching, sender category and Privacy decisions, service requests and startup, alarm-screen opening, notification fallback, audio playback acceptance/failure and stopping. It records only fixed event/error codes and timestamps, capped at 150 events. It excludes message contents, sender identities, PINs and location; reports leave the device only through user copy/share actions.

The five-second test uses the real alarm service and audio player. It restores the previous volume on stop, and does not replace the locked-phone SMS test. Audio thread failures are now captured and resources released; this is diagnostic instrumentation, not a confirmed fix for the reported S24 failure.

User field report: ringing works on Galaxy S25 and SMS arrives without a siren on Galaxy S24. The connected S24 reports Android 17/API 37. Its captured SMS was received and activation matched, but the sender was classified OTHER and rejected at the PIN gate. A separate boolean-only settings check confirmed that no PIN was configured while OTHER requires a PIN. Siren execution has not yet been tested with an authorized command.

Validation: 81 automated tests passed, including real SMS PDU parsing on API 28/33, privacy-rule diagnostics, background-start failure capture, bounded/redacted event storage, explicit siren confirmation, five-second stop and volume restoration. Android lint and debug APK build passed. Normal/small screens rendered at 100%/200% text scale.

Installed as an in-place update on the connected Galaxy S24 (SM-S921B, device reports Android 17/API 37). Diagnostics opens and recording has been verified on-device. Captured the reported failure: SMS_RECEIVED → ACTIVATION_MATCHED → SENDER_OTHER → ALARM_BLOCKED_PIN. No service launch occurred. No PIN configured with a PIN-required rule is the confirmed blocker for this test; no device compatibility conclusion follows.

The external Help URL follows codex/v3.15-diagnostics; retain the branch until a permanent guide URL is shipped.

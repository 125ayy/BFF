# BFF v3.14

Setup now asks for basic approvals, then receive-SMS access, then offers locked-phone ringing checks. The extra checks can be deferred to the guided SMS test instead of blocking installation. Location and SMS sending remain optional in Settings → Location.

Help opens a browser-hosted GitHub guide from Settings, setup, and the test screen. The guide can be edited independently of the APK. Its stable URL currently follows codex/v3.14-simpler-setup: retain that branch until a permanent help host is established and an app update migrates the URL.

Ringing implementation and target API 36 are retained. V2.1 used target API 28 and allowed continuation after special-setting requests, so its field results do not establish v3.14 compatibility. See docs/COMPATIBILITY.md for physical testing still needed.

This is a debug build. Distribution signing and physical device testing remain release tasks.

Validation: 64 automated tests passed; lintDebug and assembleDebug passed. Screen rendering covers normal/small layouts at 100% and 200% font scale. No Android device was connected for physical testing.

# Compatibility validation

Minimum Android: 8 / API 26. Target API: 36.

V2.1 targeted API 28; v3.13 and v3.14 target API 36. Re-test background behavior rather than assuming v2.1 results carry over. Android documents target-dependent foreground-service restrictions at https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start.

| Family | Exact device / OS | v3.14 physical result |
|---|---|---|
| Samsung | Galaxy S24 SM-S921B / reports Android 17 API 37 | v3.15 in-place install and Diagnostics recording verified; locked-phone SMS test pending |
| Pixel | To record | Not yet tested |
| Motorola | To record | Not yet tested |
| Xiaomi / Redmi / Poco | To record | Not yet tested |
| OnePlus / Oppo / Realme | To record | Not yet tested |
| Android without Google services | To record | Not yet tested |

For each device record: install/update path, SMS grant/denial/recovery, contacts/notification grants, locked-phone ringing with default settings, extra settings needed, idle and battery-saver behavior, restart then unlock, force-stop then reopen, permission revocation/recovery, optional location off/on, and browser Help link. Include date, tester, APK hash, device software version and exact results.

The earlier PR reports a v3.13 sideload on Galaxy S24 / Android 16; that is not a v3.14 result. No universal device compatibility claim is made.

# Ear Health Monitor — Android

Companion to the Windows app (`D:\Ear hygine`). Same concept — protect your hearing —
ported to Android with the limits that the platform imposes (see below).

## Features

- **Volume cap** — a foreground service (`VolumeCapService`) that clamps the media
  volume to a user-set cap (default 60%) the moment it exceeds it
- **60/60 session tracking** — Start/Pause/Stop with persistent elapsed time;
  alerts at 60 continuous minutes and at the 60-minute daily target
- **History** — today's / this week's listening minutes and today's session count,
  stored in `SharedPreferences`

## Building

1. Open `D:\EarHealthMonitor-Android` in Android Studio (Ladybug or newer).
2. Let Gradle sync (AGP 8.7.3 / Gradle 8.11.1 from the version catalog).
3. Run on a device/emulator (minSdk 30 / Android 11+, targetSdk 35).
4. APK outputs to `app/build/outputs/apk`.

If Studio asks to fix the Gradle wrapper, accept — it will download
`gradle-8.11.1-bin.zip` automatically.

## What the port had to accept (researched, not guessed)

| Desktop (Windows) | Android |
| --- | --- |
| Reads real output dBFS via pycaw | Only the media volume *index* (0–max steps), via `AudioManager` — actual loudness is not readable |
| Silently enforces the cap in the background | `setStreamVolume()` only works in the foreground or in a **foreground service with a visible notification** ("While-In-Use"). Android 17 (API 37) makes background volume calls fail silently, so this app shows a persistent mediaPlayback-service notification |
| Tray icon presence | Android has no tray; the persistent notification IS the presence indicator |
| Alerts as Windows toasts | Real notifications — better |

These constraints come from `developer.android.com/about/versions/17/changes/bg-audio`
and the `AudioManager` API reference.

## Permissions

- `MODIFY_AUDIO_SETTINGS` (normal) — set party volume
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (normal) — cap service
- `POST_NOTIFICATIONS` (runtime, API 33+) — service + alert notifications

## Layout

```
app/src/main/java/com/post247/earhealth/
  MainActivity.kt       UI + session ticking + alerts + history
  VolumeCapService.kt   foreground service enforcing the cap
  SessionLogic.kt       pure elapsed-time math
  Store.kt              settings + history persistence
  Alerts.kt             notification channels + builders
```

## Notes

- Dark theme matches the desktop app (`#0a0a0c` bg, `#151518` cards, emerald `#34d399`).
- The launcher icon repeats the desktop logo (ear ring + sound arcs).
- `.gitignore` mirrors Android defaults.
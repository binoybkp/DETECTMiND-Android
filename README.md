# DETECTMiND

**Developed by Binoy KP**

An Android research app for passive and active sensor data collection in academic studies. Participants enroll in a study and the app silently collects configured sensor streams, syncing data to a Supabase backend on a researcher-defined schedule.

## Package

`com.research.detectmind`

## Requirements

- Android Min SDK 26 (Android 8.0), Target SDK 35
- Kotlin, Jetpack Compose (Material 3)
- Hilt for dependency injection throughout

## Architecture

```
UI (Compose + ViewModel)
    ↓
Repository layer
    ↓
Room (local DB, synced=false queue)
    ↓
SyncWorker (WorkManager) → Supabase REST API
```

- **MVVM + Repository** — ViewModels read from Room via Flow; never touch network directly
- **Room** — all sensor data persisted locally first with `synced=false`; uploaded in batches of 500
- **WorkManager** — periodic sync + ESM scheduling + service watchdog
- **ForegroundService** (`SensorService`) — always-on sensor collection
- **AlarmManager** — precise ESM prompt delivery

## Backend

- **Supabase** — `https://mrljqdnwblmfcobzgzwh.supabase.co`
- RLS enabled; anon role can INSERT sensor data and SELECT study/sensor/ESM configs
- Required headers on every request: `apikey`, `Authorization: Bearer <anon_key>`, `Content-Type: application/json`

## Sensors

| Sensor | Mechanism | Privacy |
|--------|-----------|---------|
| App Usage | UsageStatsManager (poll) | Package + app name only |
| Notifications | NotificationListenerService | Title only; empty titles filtered |
| Battery | BroadcastReceiver (interval) | Level, charging state, temp, voltage |
| Calls | ContentObserver on call log | Phone numbers SHA-256 hashed |
| SMS | ContentObserver on content://sms | Phone + body SHA-256 hashed |
| Location | FusedLocationProvider | Raw coordinates |
| Light | SensorManager (poll) | Lux value only |
| Screen State | BroadcastReceiver | on/off/locked/unlocked |
| Screen Interaction | AccessibilityService | Interaction type, app name, coordinates |
| ESM/EMA | AlarmManager + notifications | Researcher-defined surveys |

## Privacy

- Phone numbers and SMS bodies are **SHA-256 hashed** before storage — never stored in plaintext
- Collected data is never displayed to the participant
- Consent screen must be accepted before enrollment

## Build

```bash
./gradlew assembleDebug
./gradlew installDebug
```

## Enrollment Flow

1. Splash → fetch available studies from Supabase
2. Consent screen → participant must accept
3. Enroll → POST to `participants` table, receive `participant_id`
4. Home screen → shows enabled sensors, sync status, permission warnings

## Sync Cycle

1. Re-fetch `sensor_configs` + `esm_schedules/questions` from Supabase
2. Verify participant status (abort if withdrawn)
3. PATCH participant `last_sync_at` and `permissions`
4. Batch POST all unsynced Room records (max 500/request per table)
5. POST sync log entry
6. Schedule next sync per `study.sync_interval_minutes`

## ESM/EMA

- **Fixed** schedule: fires at configured `times_of_day` daily; past times roll to next day
- **Random** schedule: picks `random_count` random times within window, re-randomizes daily
- Expiry: if participant ignores prompt within `expiry_minutes`, records `expired=true`, `responded_at=null`
- Question types: likert, slider, number, text, yes_no, single_choice, multi_choice, time, date
- Responses serialized as typed JSON (numbers, booleans, arrays — not all strings)

## Key Files

| Path | Purpose |
|------|---------|
| `service/SensorService.kt` | Foreground service, starts/stops all collectors |
| `service/collectors/` | One collector per sensor type |
| `worker/SyncWorker.kt` | WorkManager sync job |
| `worker/EsmSchedulerWorker.kt` | Daily ESM alarm scheduling |
| `esm/EsmActivity.kt` | Survey UI shown on ESM notification tap |
| `data/local/dao/SensorDataDao.kt` | All sensor table queries |
| `data/repository/SyncRepository.kt` | Upload logic per table |
| `di/NetworkModule.kt` | Retrofit + Kotlinx JSON config |
| `ui/screens/home/HomeScreen.kt` | Main participant-facing screen |

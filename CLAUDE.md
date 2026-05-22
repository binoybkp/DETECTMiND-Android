# DETECTMiND — Academic Sensor Research App

## App Name
DETECTMiND

## Package
com.research.detectmind

## Tech Stack
- Kotlin, Min SDK 26, Target SDK 35
- Jetpack Compose (Material 3)
- Architecture: MVVM + Repository pattern
- Local DB: Room (all sensor data tables + a `synced BOOLEAN` column for upload tracking)
- Background: WorkManager (sync), AlarmManager (ESM scheduling), ForegroundService (always-on sensors)
- Network: Retrofit + Kotlinx Serialization → Supabase REST API
- DI: Hilt throughout — no manual instantiation ever

## Supabase
- URL: https://mrljqdnwblmfcobzgzwh.supabase.co
- Anon key: BuildConfig.SUPABASE_ANON_KEY
- Headers required on every request:
  - `apikey: <anon_key>`
  - `Authorization: Bearer <anon_key>`
  - `Content-Type: application/json`
  - `Prefer: return=representation` (for POST/PATCH)
- RLS is enabled — anon role can INSERT sensor data and SELECT study/sensor/ESM configs

## Device Identity & Enrollment
- On first launch, generate a UUID and persist as `device_id` in SharedPreferences
- Show consent screen before enrollment — do not enroll without explicit consent
- Enroll by POST to `participants` table; store returned `participant_id` in SharedPreferences
- `participant_id` is the foreign key for all data uploads — never use `device_id` for data
- On each sync, check `participant.status` — collect and sync only if `active`; stop all collection if `withdrawn`

## DATABASE SCHEMA

studies
  id UUID, name TEXT, description TEXT, app_description TEXT,
  status TEXT (draft|active|paused|completed),
  created_by UUID, config JSONB,
  sync_interval_minutes INT (default 30),
  created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ

participants
  id UUID, study_id UUID, device_id TEXT, label TEXT,
  enrolled_at TIMESTAMPTZ, last_sync_at TIMESTAMPTZ,
  status TEXT (active|withdrawn),
  device_info JSONB, permissions JSONB,
  created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ

sensor_configs
  id UUID, study_id UUID,
  sensor_type TEXT (app_usage|notifications|battery|calls|sms|location|light|screen_state|screen_interaction),
  enabled BOOLEAN, interval_seconds INT,
  config JSONB (location only: {movement_threshold: INT}),
  created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ

esm_schedules
  id UUID, study_id UUID, name TEXT, description TEXT,
  schedule_type TEXT (fixed|random),
  times_of_day TEXT[] (fixed only: ["09:00","18:00"]),
  random_count INT, random_window_start TEXT, random_window_end TEXT,
  expiry_minutes INT (default 60),
  notification_title TEXT, notification_body TEXT,
  enabled BOOLEAN,
  created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ

esm_questions
  id UUID, schedule_id UUID, question_order INT,
  question_type TEXT (likert|text|number|single_choice|multi_choice|slider|yes_no|time|date),
  question_text TEXT, required BOOLEAN,
  options JSONB (single_choice/multi_choice only: ["A","B","C"]),
  config JSONB (likert/slider/number only: {min, max, step, label_min, label_max}),
  created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ

data_app_usage
  id BIGSERIAL, participant_id UUID,
  package_name TEXT, app_name TEXT,
  start_time TIMESTAMPTZ, end_time TIMESTAMPTZ, duration_seconds INT,
  recorded_at TIMESTAMPTZ

data_notifications
  id BIGSERIAL, participant_id UUID,
  package_name TEXT, app_name TEXT,
  posted_at TIMESTAMPTZ, removed_at TIMESTAMPTZ, title TEXT,
  recorded_at TIMESTAMPTZ

data_battery
  id BIGSERIAL, participant_id UUID,
  level INT (0-100), is_charging BOOLEAN,
  charging_type TEXT (usb|ac|wireless),
  temperature REAL, voltage REAL,
  recorded_at TIMESTAMPTZ

data_calls
  id BIGSERIAL, participant_id UUID,
  direction TEXT (incoming|outgoing|missed),
  event_time TIMESTAMPTZ, duration_seconds INT,
  contact_hash TEXT,
  recorded_at TIMESTAMPTZ

data_sms
  id BIGSERIAL, participant_id UUID,
  direction TEXT (incoming|outgoing),
  event_time TIMESTAMPTZ,
  contact_hash TEXT, body_hash TEXT,
  recorded_at TIMESTAMPTZ

data_esm_responses
  id BIGSERIAL, participant_id UUID, schedule_id UUID,
  triggered_at TIMESTAMPTZ, responded_at TIMESTAMPTZ,
  expired BOOLEAN,
  responses JSONB ({question_id: answer}),
  recorded_at TIMESTAMPTZ

data_location
  id BIGSERIAL, participant_id UUID,
  latitude DOUBLE, longitude DOUBLE, altitude DOUBLE,
  accuracy REAL, speed REAL,
  provider TEXT (gps|network|fused),
  recorded_at TIMESTAMPTZ

data_light
  id BIGSERIAL, participant_id UUID,
  lux REAL,
  recorded_at TIMESTAMPTZ

data_screen_state
  id BIGSERIAL, participant_id UUID,
  state TEXT (on|off|locked|unlocked),
  recorded_at TIMESTAMPTZ

data_screen_interaction
  id               BIGSERIAL
  participant_id   UUID
  interaction_type TEXT        (touch | swipe | long_press | scroll)
  app_name         TEXT
  app_category     TEXT
  interaction_data JSONB
  recorded_at      TIMESTAMPTZ

sync_log
  id BIGSERIAL, participant_id UUID,
  synced_at TIMESTAMPTZ, records_synced INT,
  status TEXT (success|partial|error),
  error_message TEXT, duration_ms INT

## Sensor Collection Rules
- On each sync, re-fetch `sensor_configs` and `esm_schedules` from Supabase — researcher may change settings anytime from the dashboard
- Only start collection for sensors where `enabled = true`
- For interval-based sensors (battery, light): collect every `interval_seconds` seconds
- For location: collect every `interval_seconds` seconds AND on movement >= `config.movement_threshold` meters
- For event-based sensors (app_usage, notifications, calls, sms, screen_state, screen_interaction): collect on event, `interval_seconds` is null

## Sync Cycle
1. Re-fetch `sensor_configs` + `esm_schedules/questions` from Supabase
2. Check `participant.status` — abort if `withdrawn`
3. PATCH participant `last_sync_at` and `permissions`
4. Batch POST all unsynced Room records (500 per request, mark `synced=true` on success)
5. POST one `sync_log` entry (success/partial/error)
6. Schedule next sync after `study.sync_interval_minutes`

## ESM/EMA Rules
- `fixed`: fire notification at each time in `times_of_day` daily
- `random`: pick `random_count` random times within `random_window_start`–`random_window_end`, re-randomize every day
- If participant does not respond within `expiry_minutes`: post response with `expired=true`, `responded_at=null`
- Present questions in `question_order` ascending
- `responses` JSONB answer format by type:
  - `likert` / `slider` / `number` → number
  - `text` → string
  - `yes_no` → boolean
  - `single_choice` → string
  - `multi_choice` → array of strings
  - `time` → "HH:MM"
  - `date` → "YYYY-MM-DD"

## Privacy Rules
- SHA-256 hash ALL phone numbers (`contact_hash`) and SMS bodies (`body_hash`) before storing — no exceptions
- Never display any collected sensor data to the participant
- Consent screen must appear and be accepted before enrollment begins

## Coding Rules
- Run `./gradlew assembleDebug` after completing each phase; fix ALL errors before proceeding
- All sensor data goes to Room first (`synced=false`) — never write directly to network
- Read active config from Room — app must function fully offline
- Use Hilt for every dependency — ViewModels, Workers, Repositories, Services
- Batch uploads: max 500 records per POST request

## UI Rules
- Material 3, clean research app aesthetic
- Dark/light theme support
- Show only enabled sensors in the "Currently collecting" status screen
- Never show raw collected data to the participant
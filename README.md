# Terminus

Terminus is a native Android departure board for Atlanta rail. It keeps the complete station list
and upcoming schedule on-device, so riders can check departures underground or anywhere a network
connection is unavailable. When online, the app overlays realtime arrivals and supports push
notifications for line delays.

## Features

- Offline departures for all 38 Atlanta rail stations
- Realtime arrival overlays with automatic offline fallback
- Station and line-specific delay notifications
- GTFS service calendars, exceptions, and trips that continue past midnight
- Anonymous device registration with Firebase token rotation
- Independently deployable API and polling worker

## Project structure

| Module | Purpose |
| --- | --- |
| `app` | Jetpack Compose Android client with a Room offline index |
| `shared` | Serializable API models and GTFS time utilities |
| `server` | Ktor API, PostgreSQL migrations, GTFS ingestion, and index generation |
| `worker` | Realtime rail polling and Firebase Cloud Messaging delivery |

The API and worker run as separate processes. The worker polls the complete rail feed every 20
seconds, stores the latest arrival state, and delivers qualifying alerts without polling once per
device or subscription.

## Requirements

- JDK 21
- Android SDK 36
- Docker with Docker Compose
- PostgreSQL 17
- MARTA developer API key
- Firebase project with Cloud Messaging enabled

## Local development

Copy the example configuration files:

```powershell
Copy-Item .env.example .env
Copy-Item local.properties.example local.properties
Copy-Item firebase-service-account.json.example firebase-service-account.json
```

Set the Android SDK path and API base URL in `local.properties`. Add the Android Firebase
configuration as `app/google-services.json`, add the Firebase service account, and set
`MARTA_API_KEY` in `.env`.

Start PostgreSQL and the API:

```powershell
docker compose up -d postgres api
```

Import the rail schedule. The importer keeps rail routes, loads the GTFS source tables, applies
service exceptions, and builds the next seven service days:

```powershell
$env:DATABASE_URL = 'jdbc:postgresql://localhost:5432/terminus'
$env:DATABASE_USER = 'terminus'
$env:DATABASE_PASSWORD = 'terminus'
.\gradlew.bat :server:importGtfs
```

Start the worker after adding the MARTA key and Firebase service account:

```powershell
docker compose up -d worker
```

Build the Android application:

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. The Android emulator uses
`http://10.0.2.2:8080` for the local API by default.

## API

- `GET /bundle?version=` returns the station list and precomputed schedule window
- `GET /stations/{id}/departures` returns realtime arrivals or scheduled departures
- `POST /devices` registers an anonymous device and handles FCM token rotation
- `POST /subscriptions` saves a station and line delay subscription
- `GET /health` reports API availability

## Fly.io

Set a unique application name in `fly.toml`, then create and attach managed PostgreSQL. Add the
runtime secrets without committing them:

```bash
fly launch --no-deploy
fly postgres create
fly postgres attach <postgres-app-name>
fly secrets set MARTA_API_KEY=... FIREBASE_SERVICE_ACCOUNT_JSON='...'
fly scale count api=1 worker=1
fly deploy
fly ssh console -C '/app/server/bin/server --import-gtfs'
```

The `api` process receives HTTP traffic while the `worker` process runs independently. Scale them
separately with `fly scale count api=1 worker=1`.

## Build verification

Run the complete JVM test suite and package every application component:

```powershell
.\gradlew.bat test :app:assembleDebug :server:installDist :worker:installDist
```

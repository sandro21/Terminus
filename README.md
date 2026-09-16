# Terminus

Terminus is an Android app for Atlanta rail departures. It downloads a precomputed schedule into
Room, renders departures without a connection, overlays live rail data when available, and lets a
device subscribe to delay alerts. It uses no transit marks or logos.

## Architecture

- `shared`: serializable API models and GTFS time parsing
- `app`: Kotlin, Jetpack Compose, Room, and Firebase Cloud Messaging
- `server`: Ktor API, PostgreSQL migrations, GTFS ingestion, and index generation
- `worker`: independent 20-second rail feed poller and FCM sender

The worker and API are separate Fly process groups. One MARTA rail request covers all stations, so
the worker does not poll per subscription.

## Local setup

Requirements are JDK 21, Android SDK 36, and Docker. Copy the example configuration files first:

```powershell
Copy-Item .env.example .env
Copy-Item local.properties.example local.properties
Copy-Item firebase-service-account.json.example firebase-service-account.json
```

Update `sdk.dir` and `terminus.apiBaseUrl` in `local.properties`. Add the Android Firebase config as
`app/google-services.json`. Replace the service account example and set `MARTA_API_KEY` in `.env`.

Start PostgreSQL and the API:

```powershell
docker compose up -d postgres api
```

Import the rail schedule. The importer downloads the feed once, keeps only `route_type=1`, loads the
six GTFS source tables, applies `calendar_dates`, and rebuilds the next seven service days:

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

Build the Android app with `./gradlew :app:assembleDebug` or run it from Android Studio. The emulator
default API URL is `http://10.0.2.2:8080`.

## API

- `GET /bundle?version=` returns stations and the next schedule window, or an empty unchanged bundle
- `GET /stations/{id}/departures` returns a fresh realtime overlay when the worker has one, otherwise schedule data
- `POST /devices` upserts an anonymous device and handles token rotation
- `POST /subscriptions` upserts a station and line delay subscription
- `GET /health` is the deployment health check

## Fly.io

Change the placeholder `app` value in `fly.toml`, then create and attach managed Postgres. Set
secrets without committing them:

```bash
fly launch --no-deploy
fly postgres create
fly postgres attach <postgres-app-name>
fly secrets set MARTA_API_KEY=... FIREBASE_SERVICE_ACCOUNT_JSON='...'
fly scale count api=1 worker=1
fly deploy
fly ssh console -C '/app/server/bin/server --import-gtfs'
```

The `api` process alone receives HTTP traffic. The `worker` process stays separate and polls the
MARTA rail endpoint every 20 seconds.

## Measurements

See [docs/MEASUREMENTS.md](docs/MEASUREMENTS.md). The performance and alert-latency values remain
explicit placeholders until they are recorded on a physical device and deployed worker.

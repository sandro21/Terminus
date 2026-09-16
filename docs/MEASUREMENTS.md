# Measurement checklist

The implementation is complete, but performance numbers must come from a physical Android device
and the deployed worker. Do not replace placeholders with estimates.

## Offline render

1. Install the debug app, start the API, and run a GTFS import.
2. Open the app once online so the bundle is stored in Room.
3. Enable airplane mode and force-stop the app.
4. Start an Android Studio system trace, open a station, and record the interval from the tap to
   the first departure row drawn.
5. Repeat five times and report the median as `[OFFLINE_RENDER_MS]`.

The pre-offline baseline is a fresh app install with the embedded station list but without a
downloaded schedule. In airplane mode, a station correctly has no departure rows.

## Delay notification

1. Deploy one `api` Machine and one `worker` Machine.
2. Subscribe a device to a station and line.
3. When the feed reports a delayed train, save its `EVENT_TIME` and the notification receipt time
   from the device.
4. Repeat for several alerts and report the median as `[PUSH_LATENCY_SECONDS]`.

Record the final results as `[OFFLINE_RENDER_MS]` and `[PUSH_LATENCY_SECONDS]` after completing the
procedures above.

package com.terminus.server

import com.terminus.shared.BundleDto
import com.terminus.shared.DepartureDto
import com.terminus.shared.DeviceRegistration
import com.terminus.shared.StationDto
import com.terminus.shared.SubscriptionRequest
import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TransitRepository {
    private val zone = ZoneId.of("America/New_York")

    fun stations(): List<StationDto> = Database.query { connection ->
        connection.prepareStatement(
            "SELECT id, name, lines FROM stations ORDER BY name",
        ).use { statement ->
            statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.station()) } }
        }
    }

    fun bundle(clientVersion: String?): BundleDto = Database.query { connection ->
        val today = LocalDate.now(zone)
        val end = today.plusDays(Env.bundleDays.toLong())
        val stations = connection.prepareStatement(
            "SELECT id, name, lines FROM stations ORDER BY name",
        ).use { statement ->
            statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.station()) } }
        }
        val departures = connection.prepareStatement(
            """
            SELECT station_id, service_date, departure_seconds, route_id, line, direction,
                   destination, trip_id
            FROM departure_index
            WHERE service_date >= ? AND service_date < ?
            ORDER BY station_id, service_date, departure_seconds
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, today.minusDays(1))
            statement.setObject(2, end)
            statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.departure()) } }
        }
        val version = version(connection, departures.size)
        BundleDto(
            version = version,
            generatedAt = Instant.now().toString(),
            validThrough = end.minusDays(1).toString(),
            stations = if (clientVersion == version) emptyList() else stations,
            departures = if (clientVersion == version) emptyList() else departures,
        )
    }

    fun departures(stationId: String): Pair<StationDto?, List<DepartureDto>> = Database.query { connection ->
        val station = connection.prepareStatement(
            "SELECT id, name, lines FROM stations WHERE id = ?",
        ).use { statement ->
            statement.setString(1, stationId)
            statement.executeQuery().use { rows -> if (rows.next()) rows.station() else null }
        }
        if (station == null) return@query null to emptyList()

        val scheduled = connection.prepareStatement(
            """
            SELECT station_id, service_date, departure_seconds, route_id, line, direction,
                   destination, trip_id
            FROM departure_index
            WHERE station_id = ?
              AND service_date >= CURRENT_DATE - 1
              AND service_date <= CURRENT_DATE + 1
              AND service_date + make_interval(secs => departure_seconds) >= now() - interval '2 minutes'
            ORDER BY service_date + make_interval(secs => departure_seconds)
            LIMIT 30
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, stationId)
            statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.departure()) } }
        }
        val realtime = connection.prepareStatement(
            """
            SELECT station_id, line, direction, destination, train_id, event_time,
                   waiting_seconds, delay_seconds, is_realtime
            FROM realtime_arrivals
            WHERE station_id = ? AND observed_at > now() - interval '90 seconds'
            ORDER BY waiting_seconds
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, stationId)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        add(
                            DepartureDto(
                                stationId = rows.getString("station_id"),
                                serviceDate = LocalDate.now(zone).toString(),
                                departureSeconds = 0,
                                routeId = rows.getString("line"),
                                line = rows.getString("line"),
                                direction = rows.getString("direction"),
                                destination = rows.getString("destination"),
                                tripId = "realtime-${rows.getString("train_id")}",
                                trainId = rows.getString("train_id"),
                                eventTime = rows.getString("event_time"),
                                waitingSeconds = rows.getInt("waiting_seconds"),
                                delaySeconds = rows.getInt("delay_seconds"),
                                realtime = rows.getBoolean("is_realtime"),
                            ),
                        )
                    }
                }
            }
        }
        station to if (realtime.isNotEmpty()) realtime else scheduled
    }

    fun registerDevice(registration: DeviceRegistration) = Database.query { connection ->
        connection.prepareStatement(
            """
            INSERT INTO devices(device_id, fcm_token, platform, updated_at)
            VALUES (?, ?, ?, now())
            ON CONFLICT (device_id) DO UPDATE
            SET fcm_token = excluded.fcm_token, platform = excluded.platform,
                valid = true, updated_at = now()
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, registration.deviceId)
            statement.setString(2, registration.fcmToken)
            statement.setString(3, registration.platform)
            statement.executeUpdate()
        }
    }

    fun saveSubscription(request: SubscriptionRequest) = Database.query { connection ->
        connection.prepareStatement(
            """
            INSERT INTO subscriptions(device_id, station_id, line, minimum_delay_seconds, enabled)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (device_id, station_id, line) DO UPDATE
            SET minimum_delay_seconds = excluded.minimum_delay_seconds,
                enabled = excluded.enabled, updated_at = now()
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, request.deviceId)
            statement.setString(2, request.stationId)
            statement.setString(3, request.line.uppercase())
            statement.setInt(4, request.minimumDelaySeconds)
            statement.setBoolean(5, request.enabled)
            statement.executeUpdate()
        }
    }

    private fun version(connection: Connection, count: Int): String {
        val lastDate = connection.prepareStatement("SELECT max(service_date) FROM departure_index").use { statement ->
            statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) ?: "empty" else "empty" }
        }
        return "$lastDate-$count"
    }

    private fun ResultSet.station() = StationDto(
        id = getString("id"),
        name = getString("name"),
        lines = (getArray("lines")?.array as? Array<*>)?.map { it.toString() }.orEmpty(),
    )

    private fun ResultSet.departure() = DepartureDto(
        stationId = getString("station_id"),
        serviceDate = getString("service_date"),
        departureSeconds = getInt("departure_seconds"),
        routeId = getString("route_id"),
        line = getString("line"),
        direction = getString("direction"),
        destination = getString("destination"),
        tripId = getString("trip_id"),
    )
}

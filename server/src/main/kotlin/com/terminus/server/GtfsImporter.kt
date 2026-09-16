package com.terminus.server

import com.terminus.shared.parseGtfsTime
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVRecord
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.net.URI
import java.sql.Connection
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream

private const val DEFAULT_GTFS_URL = "https://itsmarta.com/google_transit_feed/google_transit.zip"
private val dateFormat = DateTimeFormatter.BASIC_ISO_DATE

private data class Stop(val id: String, val name: String, val parent: String?, val locationType: Int)
private data class Route(val id: String, val shortName: String, val type: Int)
private data class Trip(val id: String, val routeId: String, val serviceId: String, val headsign: String, val direction: String)
private data class StopTime(val tripId: String, val stopId: String, val sequence: Int, val departure: String)
private data class ServiceCalendar(
    val id: String,
    val weekdays: Set<DayOfWeek>,
    val start: LocalDate,
    val end: LocalDate,
)
private data class ServiceException(val serviceId: String, val date: LocalDate, val type: Int)

fun main() = importGtfs()

fun importGtfs() {
    val source = System.getenv("GTFS_URL") ?: DEFAULT_GTFS_URL
    val bytes = URI(source).toURL().openConnection().apply {
        setRequestProperty("User-Agent", "Terminus/1.0 contact=sandro21@users.noreply.github.com")
        connectTimeout = 20_000
        readTimeout = 60_000
    }.getInputStream().use { it.readBytes() }

    val files = unzip(bytes)
    val stops = records(files, "stops.txt").map { row ->
        Stop(row["stop_id"], row["stop_name"], row.optional("parent_station"), row.optional("location_type")?.toIntOrNull() ?: 0)
    }
    val routes = records(files, "routes.txt").map { row ->
        Route(row["route_id"], row.optional("route_short_name") ?: row["route_id"], row["route_type"].toInt())
    }
    val railRoutes = routes.filter { it.type == 1 }.associateBy { it.id }
    val trips = records(files, "trips.txt").map { row ->
        Trip(row["trip_id"], row["route_id"], row["service_id"], row.optional("trip_headsign").orEmpty(), row.optional("direction_id").orEmpty())
    }.filter { it.routeId in railRoutes }.associateBy { it.id }
    val stopTimes = records(files, "stop_times.txt").mapNotNull { row ->
        if (row["trip_id"] !in trips) null else StopTime(
            row["trip_id"], row["stop_id"], row["stop_sequence"].toInt(),
            row.optional("departure_time") ?: row["arrival_time"],
        )
    }
    val calendars = files["calendar.txt"]?.let { data ->
        records(data).map { row ->
            ServiceCalendar(
                id = row["service_id"],
                weekdays = buildSet {
                    if (row["monday"] == "1") add(DayOfWeek.MONDAY)
                    if (row["tuesday"] == "1") add(DayOfWeek.TUESDAY)
                    if (row["wednesday"] == "1") add(DayOfWeek.WEDNESDAY)
                    if (row["thursday"] == "1") add(DayOfWeek.THURSDAY)
                    if (row["friday"] == "1") add(DayOfWeek.FRIDAY)
                    if (row["saturday"] == "1") add(DayOfWeek.SATURDAY)
                    if (row["sunday"] == "1") add(DayOfWeek.SUNDAY)
                },
                start = LocalDate.parse(row["start_date"], dateFormat),
                end = LocalDate.parse(row["end_date"], dateFormat),
            )
        }
    }.orEmpty()
    val exceptions = files["calendar_dates.txt"]?.let { data ->
        records(data).map { row -> ServiceException(row["service_id"], LocalDate.parse(row["date"], dateFormat), row["exception_type"].toInt()) }
    }.orEmpty()

    Database.transaction { connection ->
        replaceRawTables(connection, stops, routes, trips.values.toList(), stopTimes, calendars, exceptions)
        rebuildIndex(connection, stops, railRoutes, trips, stopTimes, calendars, exceptions)
    }
    println("Imported ${trips.size} rail trips and ${stopTimes.size} stop times")
}

private fun rebuildIndex(
    connection: Connection,
    stops: List<Stop>,
    routes: Map<String, Route>,
    trips: Map<String, Trip>,
    stopTimes: List<StopTime>,
    calendars: List<ServiceCalendar>,
    exceptions: List<ServiceException>,
) {
    val stopById = stops.associateBy { it.id }
    val lineByStation = mutableMapOf<String, MutableSet<String>>()
    val today = LocalDate.now()
    val dates = (0..Env.bundleDays).map { today.minusDays(1).plusDays(it.toLong()) }
    val exceptionMap = exceptions.associateBy { it.serviceId to it.date }
    val calendarById = calendars.associateBy { it.id }
    val stationByStopId = stops.associate { stop ->
        val parent = stop.parent?.let(stopById::get)
        val name = cleanStationName(parent?.name ?: stop.name)
        stop.id to (stationId(name) to name)
    }

    stationByStopId.values.distinctBy { it.first }.forEach { (id, name) -> upsertStation(connection, id, name) }

    fun active(serviceId: String, date: LocalDate): Boolean {
        exceptionMap[serviceId to date]?.let { return it.type == 1 }
        val calendar = calendarById[serviceId] ?: return false
        return date in calendar.start..calendar.end && date.dayOfWeek in calendar.weekdays
    }

    connection.createStatement().use { it.executeUpdate("TRUNCATE departure_index") }
    connection.prepareStatement(
        """
        INSERT INTO departure_index(station_id, service_date, departure_seconds, route_id, line, direction, destination, trip_id)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
        """.trimIndent(),
    ).use { insert ->
        var pending = 0
        for (stopTime in stopTimes) {
            val trip = trips.getValue(stopTime.tripId)
            val route = routes.getValue(trip.routeId)
            val (stationId, _) = stationByStopId[stopTime.stopId] ?: continue
            lineByStation.getOrPut(stationId) { linkedSetOf() }.add(route.shortName.uppercase())
            val departureSeconds = parseGtfsTime(stopTime.departure)
            for (date in dates) {
                if (!active(trip.serviceId, date)) continue
                insert.setString(1, stationId)
                insert.setObject(2, date)
                insert.setInt(3, departureSeconds)
                insert.setString(4, route.id)
                insert.setString(5, route.shortName.uppercase())
                insert.setString(6, trip.direction)
                insert.setString(7, trip.headsign.ifBlank { route.shortName })
                insert.setString(8, trip.id)
                insert.addBatch()
                if (++pending % 2_000 == 0) insert.executeBatch()
            }
        }
        insert.executeBatch()
    }
    connection.prepareStatement("UPDATE stations SET lines = ? WHERE id = ?").use { update ->
        for ((station, lines) in lineByStation) {
            update.setArray(1, connection.createArrayOf("text", lines.toTypedArray()))
            update.setString(2, station)
            update.addBatch()
        }
        update.executeBatch()
    }
}

private fun replaceRawTables(
    connection: Connection,
    stops: List<Stop>,
    routes: List<Route>,
    trips: List<Trip>,
    stopTimes: List<StopTime>,
    calendars: List<ServiceCalendar>,
    exceptions: List<ServiceException>,
) {
    connection.createStatement().use {
        it.executeUpdate("TRUNCATE gtfs_stops, gtfs_routes, gtfs_trips, gtfs_stop_times, gtfs_calendar, gtfs_calendar_dates")
    }
    batch(connection, "INSERT INTO gtfs_stops VALUES (?, ?, ?, ?)", stops) { statement, item ->
        statement.setString(1, item.id); statement.setString(2, item.name); statement.setString(3, item.parent); statement.setInt(4, item.locationType)
    }
    batch(connection, "INSERT INTO gtfs_routes VALUES (?, ?, ?)", routes) { statement, item ->
        statement.setString(1, item.id); statement.setString(2, item.shortName); statement.setInt(3, item.type)
    }
    batch(connection, "INSERT INTO gtfs_trips VALUES (?, ?, ?, ?, ?)", trips) { statement, item ->
        statement.setString(1, item.id); statement.setString(2, item.routeId); statement.setString(3, item.serviceId); statement.setString(4, item.headsign); statement.setString(5, item.direction)
    }
    batch(connection, "INSERT INTO gtfs_stop_times VALUES (?, ?, ?, ?)", stopTimes) { statement, item ->
        statement.setString(1, item.tripId); statement.setString(2, item.stopId); statement.setInt(3, item.sequence); statement.setString(4, item.departure)
    }
    batch(connection, "INSERT INTO gtfs_calendar VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", calendars) { statement, item ->
        statement.setString(1, item.id)
        DayOfWeek.entries.forEachIndexed { index, day -> statement.setBoolean(index + 2, day in item.weekdays) }
        statement.setObject(9, item.start); statement.setObject(10, item.end)
    }
    batch(connection, "INSERT INTO gtfs_calendar_dates VALUES (?, ?, ?)", exceptions) { statement, item ->
        statement.setString(1, item.serviceId); statement.setObject(2, item.date); statement.setInt(3, item.type)
    }
}

private fun <T> batch(connection: Connection, sql: String, items: List<T>, bind: (java.sql.PreparedStatement, T) -> Unit) {
    connection.prepareStatement(sql).use { statement ->
        items.forEachIndexed { index, item ->
            bind(statement, item)
            statement.addBatch()
            if ((index + 1) % 2_000 == 0) statement.executeBatch()
        }
        statement.executeBatch()
    }
}

private fun upsertStation(connection: Connection, id: String, name: String) {
    connection.prepareStatement("INSERT INTO stations(id, name) VALUES (?, ?) ON CONFLICT (id) DO UPDATE SET name = excluded.name").use {
        it.setString(1, id); it.setString(2, name); it.executeUpdate()
    }
}

private fun unzip(bytes: ByteArray): Map<String, ByteArray> = buildMap {
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (!entry.isDirectory) put(entry.name.substringAfterLast('/'), zip.readBytes())
        }
    }
}

private fun records(files: Map<String, ByteArray>, name: String): List<CSVRecord> =
    records(files[name] ?: error("GTFS archive is missing $name"))

private fun records(bytes: ByteArray): List<CSVRecord> = InputStreamReader(ByteArrayInputStream(bytes)).use { reader ->
    CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(reader).toList()
}

private fun CSVRecord.optional(name: String): String? =
    if (isMapped(name)) get(name).trim().takeIf { it.isNotEmpty() } else null

fun cleanStationName(value: String): String {
    val cleaned = value.replace(Regex("(?i)\\s+station$"), "").trim()
    return when (stationId(cleaned)) {
        "BROOKHAVEN_OGLETHORPE" -> "Brookhaven-Oglethorpe"
        "EDGEWOOD_CANDLER_PARK" -> "Edgewood-Candler Park"
        "INMAN_PARK_REYNOLDSTOWN" -> "Inman Park-Reynoldstown"
        "LAKEWOOD_FT_MCPHERSON", "LAKEWOOD_FORT_MCPHERSON" -> "Lakewood-Fort McPherson"
        "H_E_HOLMES", "HAMILTON_E_HOLMES" -> "Hamilton E Holmes"
        "GWCC_CNN_CENTER" -> "GWCC/CNN Center"
        else -> cleaned.split(' ').joinToString(" ") { word -> word.lowercase().replaceFirstChar(Char::titlecase) }
    }
}

fun stationId(name: String): String = name.uppercase()
    .replace("/", "_")
    .replace("-", "_")
    .replace(Regex("[^A-Z0-9]+"), "_")
    .trim('_')

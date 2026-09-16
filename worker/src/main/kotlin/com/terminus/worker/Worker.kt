package com.terminus.worker

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.Notification
import com.terminus.server.Database
import com.terminus.server.Env
import com.terminus.server.cleanStationName
import com.terminus.server.stationId
import com.terminus.shared.MartaArrival
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant
import kotlin.coroutines.coroutineContext

private const val MARTA_URL = "https://developerservices.itsmarta.com:18096/itsmarta/railrealtimearrivals/developerservices/traindata"
private val logger = LoggerFactory.getLogger("TerminusWorker")

private data class Target(
    val deviceId: String,
    val token: String,
    val stationId: String,
    val stationName: String,
    val trainId: String,
    val line: String,
    val destination: String,
    val delaySeconds: Int,
)

fun main() = runBlocking {
    val key = Env.value("MARTA_API_KEY")
    val firebase = initializeFirebase()
    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; coerceInputValues = true })
        }
    }

    logger.info("Delay worker started")
    while (coroutineContext.isActive) {
        val started = Instant.now()
        try {
            val arrivals: List<MartaArrival> = client.get(MARTA_URL) {
                url { parameters.append("apiKey", key) }
                header(HttpHeaders.UserAgent, "Terminus/1.0 contact=sandro21@users.noreply.github.com")
            }.body()
            val targets = persistAndFindTargets(arrivals)
            if (firebase != null) sendNotifications(firebase, targets)
            logger.info("Processed {} arrivals and {} alert candidates", arrivals.size, targets.size)
        } catch (error: Throwable) {
            logger.error("Polling cycle failed", error)
        }
        val elapsed = java.time.Duration.between(started, Instant.now()).toMillis()
        delay((20_000L - elapsed).coerceAtLeast(1_000L))
    }
    client.close()
}

private fun persistAndFindTargets(arrivals: List<MartaArrival>): List<Target> = Database.transaction { connection ->
    val stationNames = connection.prepareStatement("SELECT id, name FROM stations").use { statement ->
        statement.executeQuery().use { rows -> buildMap { while (rows.next()) put(stationId(rows.getString("name")), rows.getString("id")) } }
    }
    val resolved = arrivals.mapNotNull { arrival ->
        val id = stationNames[stationId(cleanStationName(arrival.station))]
        if (id == null) {
            logger.warn("Ignoring unmatched station '{}'", arrival.station)
            null
        } else {
            id to arrival
        }
    }

    connection.prepareStatement(
        """
        INSERT INTO realtime_arrivals(station_id, train_id, line, direction, destination, event_time,
                                      waiting_seconds, delay_seconds, is_realtime, observed_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now())
        ON CONFLICT (station_id, train_id) DO UPDATE SET
            line = excluded.line, direction = excluded.direction, destination = excluded.destination,
            event_time = excluded.event_time, waiting_seconds = excluded.waiting_seconds,
            delay_seconds = excluded.delay_seconds, is_realtime = excluded.is_realtime,
            observed_at = now()
        """.trimIndent(),
    ).use { statement ->
        for ((id, arrival) in resolved) {
            statement.setString(1, id)
            statement.setString(2, arrival.trainId)
            statement.setString(3, arrival.line.uppercase())
            statement.setString(4, arrival.direction)
            statement.setString(5, arrival.destination)
            statement.setString(6, arrival.eventTime)
            statement.setInt(7, arrival.waitingSeconds.toIntOrNull() ?: 0)
            statement.setInt(8, arrival.delay.toIntOrNull() ?: 0)
            statement.setBoolean(9, arrival.isRealtime == "1" || arrival.isRealtime.equals("true", true))
            statement.addBatch()
        }
        statement.executeBatch()
    }
    connection.createStatement().use {
        it.executeUpdate("DELETE FROM realtime_arrivals WHERE observed_at < now() - interval '5 minutes'")
    }

    val targets = mutableListOf<Target>()
    connection.prepareStatement(
        """
        SELECT d.device_id, d.fcm_token, s.station_id, st.name, r.train_id, r.line,
               r.destination, r.delay_seconds
        FROM realtime_arrivals r
        JOIN subscriptions s ON s.station_id = r.station_id AND s.line = r.line
        JOIN devices d ON d.device_id = s.device_id
        JOIN stations st ON st.id = r.station_id
        LEFT JOIN delay_notifications n
          ON n.device_id = d.device_id AND n.train_id = r.train_id AND n.station_id = r.station_id
        WHERE s.enabled AND d.valid
          AND r.delay_seconds >= s.minimum_delay_seconds
          AND (n.delay_seconds IS NULL OR n.delay_seconds < r.delay_seconds)
          AND r.observed_at > now() - interval '90 seconds'
        """.trimIndent(),
    ).use { statement ->
        statement.executeQuery().use { rows ->
            while (rows.next()) {
                targets += Target(
                    rows.getString("device_id"), rows.getString("fcm_token"), rows.getString("station_id"),
                    rows.getString("name"), rows.getString("train_id"), rows.getString("line"),
                    rows.getString("destination"), rows.getInt("delay_seconds"),
                )
            }
        }
    }
    targets
}

private fun sendNotifications(firebase: FirebaseMessaging, targets: List<Target>) {
    if (targets.isEmpty()) return
    val messages = targets.map { target ->
        Message.builder()
            .setToken(target.token)
            .setNotification(
                Notification.builder()
                    .setTitle("${target.line} line delay")
                    .setBody("Train to ${target.destination} is delayed ${target.delaySeconds / 60} min at ${target.stationName}")
                    .build(),
            )
            .putData("stationId", target.stationId)
            .putData("trainId", target.trainId)
            .putData("delaySeconds", target.delaySeconds.toString())
            .build()
    }
    val response = firebase.sendEach(messages)
    response.responses.forEachIndexed { index, result ->
        val target = targets[index]
        if (result.isSuccessful) {
            recordSent(target)
        } else if ((result.exception as? FirebaseMessagingException)?.messagingErrorCode == MessagingErrorCode.UNREGISTERED) {
            invalidateDevice(target.deviceId)
        } else {
            logger.warn("FCM rejected alert for device {}: {}", target.deviceId, result.exception?.message)
        }
    }
}

private fun recordSent(target: Target) = Database.query { connection ->
    connection.prepareStatement(
        """
        INSERT INTO delay_notifications(device_id, train_id, station_id, delay_seconds, sent_at)
        VALUES (?, ?, ?, ?, now())
        ON CONFLICT (device_id, train_id, station_id) DO UPDATE
        SET delay_seconds = excluded.delay_seconds, sent_at = now()
        """.trimIndent(),
    ).use {
        it.setString(1, target.deviceId); it.setString(2, target.trainId); it.setString(3, target.stationId)
        it.setInt(4, target.delaySeconds); it.executeUpdate()
    }
}

private fun invalidateDevice(deviceId: String) = Database.query { connection ->
    connection.prepareStatement("UPDATE devices SET valid = false, updated_at = now() WHERE device_id = ?").use {
        it.setString(1, deviceId); it.executeUpdate()
    }
}

private fun initializeFirebase(): FirebaseMessaging? {
    val raw = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON")?.takeIf { it.isNotBlank() } ?: run {
        logger.warn("FIREBASE_SERVICE_ACCOUNT_JSON is not set; realtime data will update without push delivery")
        return null
    }
    val json = if (raw.trimStart().startsWith("{")) raw.toByteArray() else File(raw).readBytes()
    val options = FirebaseOptions.builder()
        .setCredentials(GoogleCredentials.fromStream(ByteArrayInputStream(json)))
        .build()
    val app = FirebaseApp.getApps().firstOrNull() ?: FirebaseApp.initializeApp(options)
    return FirebaseMessaging.getInstance(app)
}

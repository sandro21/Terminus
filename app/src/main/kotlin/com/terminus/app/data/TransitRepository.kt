package com.terminus.app.data

import android.content.Context
import androidx.room.withTransaction
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.terminus.shared.BundleDto
import com.terminus.shared.DepartureDto
import com.terminus.shared.DeviceRegistration
import com.terminus.shared.StationDto
import com.terminus.shared.SubscriptionRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TransitRepository(private val context: Context) {
    private val database = TerminusDatabase.get(context)
    private val dao = database.transitDao()
    private val api = ApiClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val realtime = MutableStateFlow<Map<String, List<DepartureDto>>>(emptyMap())
    private val zone = ZoneId.of("America/New_York")

    val stations: Flow<List<StationDto>> = dao.observeStations().combine(MutableStateFlow(Unit)) { rows, _ ->
        rows.map { it.toDto() }
    }

    suspend fun seedIfEmpty() {
        if (dao.stationCount() > 0) return
        val bundle = context.assets.open("bootstrap_bundle.json").bufferedReader().use { reader ->
            json.decodeFromString<BundleDto>(reader.readText())
        }
        dao.upsertStations(bundle.stations.map { it.toEntity() })
    }

    suspend fun syncBundle() {
        val current = dao.bundleMeta()
        val bundle = api.bundle(current?.version)
        if (bundle.stations.isEmpty() && bundle.departures.isEmpty()) return
        database.withTransaction {
            dao.upsertStations(bundle.stations.map { it.toEntity() })
            dao.deleteDepartures()
            dao.upsertDepartures(bundle.departures.map { it.toEntity() })
            dao.saveBundleMeta(BundleMetaEntity(version = bundle.version, validThrough = bundle.validThrough))
        }
    }

    fun station(id: String): Flow<StationDto?> = dao.observeStation(id).combine(MutableStateFlow(Unit)) { row, _ -> row?.toDto() }

    fun departures(stationId: String): Flow<List<DepartureDto>> {
        val earliest = LocalDate.now(zone).minusDays(1).toString()
        return combine(dao.observeDepartures(stationId, earliest), realtime) { scheduled, live ->
            live[stationId]?.takeIf { it.isNotEmpty() } ?: scheduled
                .map { it.toDto() }
                .filter { it.departureInstantMillis() >= System.currentTimeMillis() - 120_000 }
                .sortedBy { it.departureInstantMillis() }
                .take(30)
        }
    }

    suspend fun refreshDepartures(stationId: String) {
        val response = api.departures(stationId)
        realtime.value = realtime.value + (stationId to response.departures)
    }

    suspend fun saveAlert(stationId: String, line: String, minimumDelaySeconds: Int) {
        val token = firebaseToken() ?: error("Add app/google-services.json to enable push alerts")
        val deviceId = deviceId()
        api.registerDevice(DeviceRegistration(deviceId, token))
        api.subscribe(SubscriptionRequest(deviceId, stationId, line, minimumDelaySeconds))
    }

    suspend fun registerRotatedToken(token: String) {
        api.registerDevice(DeviceRegistration(deviceId(), token))
    }

    private fun deviceId(): String {
        val preferences = context.getSharedPreferences("terminus", Context.MODE_PRIVATE)
        return preferences.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            preferences.edit().putString("device_id", it).apply()
        }
    }

    private suspend fun firebaseToken(): String? {
        if (FirebaseApp.getApps(context).isEmpty()) return null
        return suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }
    }

    private fun StationEntity.toDto() = StationDto(id, name, lines.split(',').filter(String::isNotBlank))
    private fun StationDto.toEntity() = StationEntity(id, name, lines.joinToString(","))
    private fun DepartureEntity.toDto() = DepartureDto(
        stationId, serviceDate, departureSeconds, routeId, line, direction, destination, tripId,
    )
    private fun DepartureDto.toEntity() = DepartureEntity(
        stationId, serviceDate, departureSeconds, routeId, line, direction, destination, tripId,
    )
    private fun DepartureDto.departureInstantMillis(): Long = LocalDate.parse(serviceDate)
        .atStartOfDay(zone).plusSeconds(departureSeconds.toLong()).toInstant().toEpochMilli()
}

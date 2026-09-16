package com.terminus.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StationDto(
    val id: String,
    val name: String,
    val lines: List<String>,
)

@Serializable
data class DepartureDto(
    val stationId: String,
    val serviceDate: String,
    val departureSeconds: Int,
    val routeId: String,
    val line: String,
    val direction: String,
    val destination: String,
    val tripId: String,
    val trainId: String? = null,
    val eventTime: String? = null,
    val waitingSeconds: Int? = null,
    val delaySeconds: Int = 0,
    val realtime: Boolean = false,
)

@Serializable
data class BundleDto(
    val version: String,
    val generatedAt: String,
    val validThrough: String,
    val stations: List<StationDto>,
    val departures: List<DepartureDto>,
)

@Serializable
data class DepartureResponse(
    val station: StationDto,
    val generatedAt: String,
    val departures: List<DepartureDto>,
)

@Serializable
data class DeviceRegistration(
    val deviceId: String,
    val fcmToken: String,
    val platform: String = "android",
)

@Serializable
data class SubscriptionRequest(
    val deviceId: String,
    val stationId: String,
    val line: String,
    val minimumDelaySeconds: Int = 60,
    val enabled: Boolean = true,
)

@Serializable
data class ApiMessage(val message: String)

@Serializable
data class MartaArrival(
    @SerialName("STATION") val station: String,
    @SerialName("LINE") val line: String,
    @SerialName("DIRECTION") val direction: String,
    @SerialName("DESTINATION") val destination: String,
    @SerialName("TRAIN_ID") val trainId: String,
    @SerialName("EVENT_TIME") val eventTime: String,
    @SerialName("WAITING_SECONDS") val waitingSeconds: String = "0",
    @SerialName("WAITING_TIME") val waitingTime: String = "",
    @SerialName("DELAY") val delay: String = "0",
    @SerialName("IS_REALTIME") val isRealtime: String = "0",
)

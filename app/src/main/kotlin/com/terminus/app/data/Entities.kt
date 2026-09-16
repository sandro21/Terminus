package com.terminus.app.data

import androidx.room.Entity

@Entity(tableName = "stations", primaryKeys = ["id"])
data class StationEntity(
    val id: String,
    val name: String,
    val lines: String,
)

@Entity(
    tableName = "departures",
    primaryKeys = ["stationId", "serviceDate", "tripId", "departureSeconds"],
)
data class DepartureEntity(
    val stationId: String,
    val serviceDate: String,
    val departureSeconds: Int,
    val routeId: String,
    val line: String,
    val direction: String,
    val destination: String,
    val tripId: String,
)

@Entity(tableName = "bundle_meta", primaryKeys = ["key"])
data class BundleMetaEntity(
    val key: String = "current",
    val version: String,
    val validThrough: String,
)

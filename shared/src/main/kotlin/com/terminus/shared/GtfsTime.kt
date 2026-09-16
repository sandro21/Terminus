package com.terminus.shared

/** GTFS permits hours above 23 for service that continues after midnight. */
fun parseGtfsTime(value: String): Int {
    val parts = value.split(':')
    require(parts.size == 3) { "Invalid GTFS time: $value" }
    val hours = parts[0].toInt()
    val minutes = parts[1].toInt()
    val seconds = parts[2].toInt()
    require(hours >= 0 && minutes in 0..59 && seconds in 0..59) { "Invalid GTFS time: $value" }
    return hours * 3600 + minutes * 60 + seconds
}

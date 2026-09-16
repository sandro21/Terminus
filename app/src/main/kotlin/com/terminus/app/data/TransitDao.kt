package com.terminus.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransitDao {
    @Query("SELECT * FROM stations ORDER BY name")
    fun observeStations(): Flow<List<StationEntity>>

    @Query("SELECT * FROM stations WHERE id = :id")
    fun observeStation(id: String): Flow<StationEntity?>

    @Query("SELECT COUNT(*) FROM stations")
    suspend fun stationCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStations(stations: List<StationEntity>)

    @Query("SELECT * FROM departures WHERE stationId = :stationId AND serviceDate >= :earliestDate ORDER BY serviceDate, departureSeconds")
    fun observeDepartures(stationId: String, earliestDate: String): Flow<List<DepartureEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDepartures(departures: List<DepartureEntity>)

    @Query("DELETE FROM departures")
    suspend fun deleteDepartures()

    @Query("DELETE FROM departures WHERE serviceDate < :date")
    suspend fun deleteBefore(date: String)

    @Query("SELECT * FROM bundle_meta WHERE `key` = 'current'")
    suspend fun bundleMeta(): BundleMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveBundleMeta(meta: BundleMetaEntity)
}

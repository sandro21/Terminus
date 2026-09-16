package com.terminus.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.terminus.app.data.TransitRepository
import com.terminus.shared.DepartureDto
import com.terminus.shared.StationDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppViewModel(private val repository: TransitRepository) : ViewModel() {
    val stations: StateFlow<List<StationDto>> = repository.stations.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )
    val selectedStationId = MutableStateFlow<String?>(null)
    val selectedStation: StateFlow<StationDto?> = selectedStationId.flatMapLatest { id ->
        if (id == null) flowOf(null) else repository.station(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val departures: StateFlow<List<DepartureDto>> = selectedStationId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.departures(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val status = MutableStateFlow<String?>(null)

    fun openStation(id: String) {
        selectedStationId.value = id
        refresh()
    }

    fun closeStation() {
        selectedStationId.value = null
        status.value = null
    }

    fun refresh() {
        val id = selectedStationId.value ?: return
        viewModelScope.launch {
            runCatching { repository.refreshDepartures(id) }
                .onSuccess { status.value = "Realtime updated" }
                .onFailure { status.value = "Offline schedule" }
        }
    }

    fun saveAlert(line: String, minimumDelaySeconds: Int) {
        val id = selectedStationId.value ?: return
        viewModelScope.launch {
            status.value = "Saving alert…"
            runCatching { repository.saveAlert(id, line, minimumDelaySeconds) }
                .onSuccess { status.value = "Alert saved" }
                .onFailure { status.value = it.message ?: "Could not save alert" }
        }
    }

    class Factory(private val repository: TransitRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(repository) as T
    }
}

package com.pixeleye.plantdoctor.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixeleye.plantdoctor.BuildConfig
import com.pixeleye.plantdoctor.data.api.PlantScanDto
import com.pixeleye.plantdoctor.data.api.PlantScanRepository
import com.pixeleye.plantdoctor.data.api.SupabaseClientProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.launch

sealed class HomeUiState {
    data object Loading : HomeUiState()
    data object Empty : HomeUiState()
    data class Success(val scans: List<PlantScanDto>) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

class HomeViewModel(private val repository: PlantScanRepository) : ViewModel() {

    companion object {
        private const val TAG = "HomeViewModel"
    }

    val uiState: StateFlow<HomeUiState> = repository.getHistoryFlow()
        .map { scans ->
            if (scans.isEmpty()) {
                HomeUiState.Empty
            } else {
                HomeUiState.Success(scans)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState.Loading
        )

    // Holds the last-deleted scan so the caller can show an "Undo" snackbar
    private val _lastDeletedScan = MutableStateFlow<PlantScanDto?>(null)
    val lastDeletedScan: StateFlow<PlantScanDto?> = _lastDeletedScan.asStateFlow()

    init {
        fetchHistory()
    }

    fun fetchHistory() {
        viewModelScope.launch {
            try {
                repository.refreshHistory()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh history from remote", e)
                // We keep UI state derived from Room, no direct error emission here unless desired
            }
        }
    }

    /**
     * Optimistically removes the scan from the UI list, then deletes it in the background.
     * If the server delete fails, the item is re-fetched on the next fetchHistory().
     */
    fun deleteScan(scan: PlantScanDto) {
        val currentState = uiState.value
        if (currentState !is HomeUiState.Success) return

        // Wait... optimistic UI is no longer explicitly needed because DB reacts instantly and Flow updates.
        // We'll keep the track for the "Undo" if it was used that way.
        _lastDeletedScan.value = scan

        viewModelScope.launch {
            try {
                repository.deleteScan(scan)
                Log.d(TAG, "Scan deleted successfully: ${scan.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete scan, refreshing list", e)
                // Re-fetch to restore the item if server deletion failed
                fetchHistory()
            }
        }
    }

    fun clearLastDeletedScan() {
        _lastDeletedScan.value = null
    }

    class Factory(
        private val repository: PlantScanRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(repository) as T
        }
    }
}

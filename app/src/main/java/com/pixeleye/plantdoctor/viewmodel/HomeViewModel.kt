package com.pixeleye.plantdoctor.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixeleye.plantdoctor.BuildConfig
import com.pixeleye.plantdoctor.data.api.PlantScanDto
import com.pixeleye.plantdoctor.data.api.PlantScanRepository
import com.pixeleye.plantdoctor.data.UserPreferencesRepository
import com.pixeleye.plantdoctor.data.api.SupabaseClientProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

sealed class HomeUiState {
    data object Loading : HomeUiState()
    data object Empty : HomeUiState()
    data class Success(val scans: List<PlantScanDto>) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

class HomeViewModel(
    private val repository: PlantScanRepository,
    private val prefsRepository: UserPreferencesRepository
) : ViewModel() {

    companion object {
        private const val TAG = "HomeViewModel"
    }

    val uiState: StateFlow<HomeUiState> = repository.getHistoryFlow()
        .map { scans ->
            val rootScans = scans.filter { it.parentId.isNullOrBlank() }
            if (rootScans.isEmpty()) {
                HomeUiState.Empty
            } else {
                HomeUiState.Success(rootScans)
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

    private val _threadScans = MutableStateFlow<List<PlantScanDto>>(emptyList())
    val threadScans: StateFlow<List<PlantScanDto>> = _threadScans.asStateFlow()

    private var currentThreadParentId: String? = null

    fun loadThreadScans(parentId: String) {
        viewModelScope.launch {
            if (currentThreadParentId != parentId) {
                currentThreadParentId = parentId
                _threadScans.value = emptyList()
            }
            try {
                val local = repository.getThreadScansLocal(parentId)
                if (local.isNotEmpty()) {
                    _threadScans.value = local
                }
                val remote = repository.getThreadScansRemote(parentId)
                if (remote.isNotEmpty()) {
                    _threadScans.value = remote
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load thread scans for parentId: $parentId", e)
            }
        }
    }

    fun clearThreadScans() {
        currentThreadParentId = null
        _threadScans.value = emptyList()
    }

    fun savePlantNameLocal(scanId: String, name: String) {
        val normalizedName = java.text.Normalizer.normalize(name.trim(), java.text.Normalizer.Form.NFC)
            .replace("\\s+".toRegex(), " ")
        viewModelScope.launch {
            try {
                val existing = repository.getHistoryByIdLocal(scanId)
                if (existing == null || existing.plantName != normalizedName) {
                    repository.updatePlantNameLocal(scanId, normalizedName)
                    Log.d(TAG, "Successfully saved plant name locally: $scanId -> $normalizedName")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save plant name locally: $scanId", e)
            }
        }
    }

    private var isPremiumUser: Boolean = false

    fun setPremiumStatus(isPremium: Boolean) {
        if (isPremiumUser != isPremium) {
            isPremiumUser = isPremium
            fetchHistory()
        }
    }

    // One-time event for showing snackbar messages (e.g., slow connection)
    private val _snackbarEvent = MutableStateFlow<com.pixeleye.plantdoctor.ui.components.SnackbarState?>(null)
    val snackbarEvent: StateFlow<com.pixeleye.plantdoctor.ui.components.SnackbarState?> = _snackbarEvent.asStateFlow()

    // ── Showcase walkthrough state ─────────────────────────────
    private val _hasSeenCameraShowcase = MutableStateFlow(false)
    val hasSeenCameraShowcase: StateFlow<Boolean> = _hasSeenCameraShowcase.asStateFlow()

    private val _hasSeenLongPressShowcase = MutableStateFlow(false)
    val hasSeenLongPressShowcase: StateFlow<Boolean> = _hasSeenLongPressShowcase.asStateFlow()

    init {
        fetchHistory()
        // Collect persisted showcase flags from DataStore
        viewModelScope.launch {
            prefsRepository.userPreferences.collect { prefs ->
                _hasSeenCameraShowcase.value = prefs.hasSeenCameraShowcase
                _hasSeenLongPressShowcase.value = prefs.hasSeenLongPressShowcase
            }
        }
    }

    fun fetchHistory() {
        viewModelScope.launch {
            try {
                repository.syncPlantNamesFromReminders()
                val result = withTimeoutOrNull(10_000L) {
                    repository.refreshHistory()
                }
                repository.syncPlantNamesFromReminders()
                if (result == null) {
                    Log.w(TAG, "History fetch timed out after 10 seconds")
                    _snackbarEvent.value = com.pixeleye.plantdoctor.ui.components.SnackbarState(
                        message = "Connection is slow. Could not load recent scans.",
                        type = com.pixeleye.plantdoctor.ui.components.SnackbarType.ERROR
                    )
                }
            } catch (e: IOException) {
                Log.e(TAG, "Network error fetching history", e)
                _snackbarEvent.value = com.pixeleye.plantdoctor.ui.components.SnackbarState(
                    message = "Connection is slow. Could not load recent scans.",
                    type = com.pixeleye.plantdoctor.ui.components.SnackbarType.ERROR
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh history from remote", e)
            }
        }
    }

    fun consumeSnackbarEvent() {
        _snackbarEvent.value = null
    }

    fun markCameraShowcaseSeen() {
        viewModelScope.launch {
            prefsRepository.markCameraShowcaseSeen()
            _hasSeenCameraShowcase.value = true
        }
    }

    fun markLongPressShowcaseSeen() {
        viewModelScope.launch {
            prefsRepository.markLongPressShowcaseSeen()
            _hasSeenLongPressShowcase.value = true
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
                showSnackbar("Scan deleted successfully", com.pixeleye.plantdoctor.ui.components.SnackbarType.SUCCESS)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete scan, refreshing list", e)
                showSnackbar("Failed to delete scan. Re-syncing history...", com.pixeleye.plantdoctor.ui.components.SnackbarType.ERROR)
            } finally {
                // Refresh to repopulate the list with the next available item from Supabase
                fetchHistory()
            }
        }
    }

    fun clearLastDeletedScan() {
        _lastDeletedScan.value = null
    }

    fun deleteSelectedScans(scanIds: List<String>) {
        val currentState = uiState.value
        if (currentState !is HomeUiState.Success) return

        val scansToDelete = currentState.scans.filter { it.id in scanIds }
        
        viewModelScope.launch {
            var deletedCount = 0
            scansToDelete.forEach { scan ->
                try {
                    repository.deleteScan(scan)
                    Log.d(TAG, "Scan deleted successfully: ${scan.id}")
                    deletedCount++
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete scan: ${scan.id}", e)
                }
            }
            if (deletedCount > 0) {
              showSnackbar("$deletedCount scan(s) deleted", com.pixeleye.plantdoctor.ui.components.SnackbarType.SUCCESS)
            }
            // Refresh to restore any items that failed to delete
            fetchHistory()
        }
    }

    fun showSnackbar(message: String, type: com.pixeleye.plantdoctor.ui.components.SnackbarType) {
        _snackbarEvent.value = com.pixeleye.plantdoctor.ui.components.SnackbarState(message, type)
    }

    class Factory(
        private val repository: PlantScanRepository,
        private val prefsRepository: UserPreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(repository, prefsRepository) as T
        }
    }
}

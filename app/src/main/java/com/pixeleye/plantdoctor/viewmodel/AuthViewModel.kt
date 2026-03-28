package com.pixeleye.plantdoctor.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pixeleye.plantdoctor.BuildConfig
import com.pixeleye.plantdoctor.data.api.AuthManager
import com.pixeleye.plantdoctor.data.api.SupabaseClientProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthState {
    data object Loading : AuthState()
    data object Authenticated : AuthState()
    data object Unauthenticated : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "AuthViewModel"
    }

    private val authManager = AuthManager(
        supabaseClient = SupabaseClientProvider.getClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ),
        webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
    )

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        checkSession()
    }

    private fun checkSession() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val restored = authManager.restoreSession()
            _authState.value = if (restored) {
                Log.d(TAG, "Session restored: ${authManager.currentUserEmail}")
                AuthState.Authenticated
            } else {
                AuthState.Unauthenticated
            }
        }
    }

    fun signInWithGoogle() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val context = getApplication<Application>().applicationContext
            val result = authManager.signInWithGoogle(context)
            result.fold(
                onSuccess = {
                    Log.d(TAG, "Sign-in successful: ${authManager.currentUserEmail}")
                    _authState.value = AuthState.Authenticated
                },
                onFailure = { e ->
                    Log.e(TAG, "Sign-in failed", e)
                    _authState.value = AuthState.Error(
                        e.message ?: "Sign-in failed. Please try again."
                    )
                }
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authManager.signOut()
            _authState.value = AuthState.Unauthenticated
        }
    }

    fun clearError() {
        _authState.value = AuthState.Unauthenticated
    }
}
